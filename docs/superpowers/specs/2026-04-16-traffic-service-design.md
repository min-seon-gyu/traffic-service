# Traffic Service - 설계 문서

## 개요

운영 중인 서비스에 HTTP 부하를 생성하는 부하 테스트 웹 서비스.
현재 운영 환경에서 유의미한 트래픽이 없어 트래픽 처리 역량(동시성 제어, 비동기/논블로킹 I/O)을 학습하기 위한 목적으로 구축한다.

## 기술 스택

| 항목 | 선택 | 이유 |
|------|------|------|
| 언어 | Kotlin | 코루틴 기반 동시성, Spring Boot와의 호환성 |
| 프레임워크 | Spring Boot 4.x.x | 최신 Spring 생태계, 코루틴 지원 |
| 부하 엔진 | Kotlin Coroutines + WebClient | 동시성 제어 + 논블로킹 I/O 학습 목표에 부합 |
| 프론트엔드 | Thymeleaf (SSR) | Spring Boot 내장, 별도 프론트 서버 불필요 |
| DB | MySQL | 데이터 영속, 한국 업계에서 널리 사용 |
| 실시간 통신 | SSE (Server-Sent Events) | 서버→클라이언트 단방향 스트림, WebSocket보다 구현 간단 |

## 전체 아키텍처

```
┌─────────────────────────────────────────────┐
│            Traffic Service (Spring Boot 4)    │
│                                               │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐ │
│  │  Web UI   │  │  Load     │  │  Metrics  │ │
│  │(Thymeleaf)│  │  Engine   │  │  Collector│ │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘ │
│        │              │              │         │
│  ┌─────┴──────────────┴──────────────┴─────┐  │
│  │           Core Domain Layer              │  │
│  │  (TestPlan, TestStep, Execution, Result) │  │
│  └─────────────────┬───────────────────────┘  │
│                    │                           │
│  ┌─────────────────┴───────────────────────┐  │
│  │           MySQL (JPA/Spring Data)        │  │
│  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                     │
                     ▼ HTTP (WebClient, 논블로킹)
            ┌────────────────┐
            │  Target Service │
            │  (로컬/원격)     │
            └────────────────┘
```

### 3개 주요 모듈

- **Web UI**: Thymeleaf 기반. 테스트 설정, 실행 제어, 결과 조회
- **Load Engine**: Kotlin Coroutines로 가상 유저(VU) 생성, WebClient로 부하 발생
- **Metrics Collector**: 실시간 메트릭 수집, 집계, SSE 스트리밍

모든 모듈은 하나의 Spring Boot 애플리케이션 안에서 동작한다.

## 시나리오 엔진

### 실행 모델

모든 테스트는 **Step 리스트 기반**으로 통합한다. 단건 API 호출은 Step이 1개인 시나리오로 처리한다. 별도의 실행 모드 분기 없이 항상 Step 리스트를 순회하는 하나의 로직으로 동작한다.

### 시나리오 구조

시나리오는 순서가 있는 Step의 리스트이다. 각 Step은 이전 Step의 응답에서 추출한 변수를 참조할 수 있다.

```
시나리오: "주문 플로우"
  Step 1: POST /api/login    → 응답에서 token 추출
  Step 2: GET  /api/products  (Header: Authorization = Bearer {{token}})
  Step 3: POST /api/orders    (Header: Authorization = Bearer {{token}}, Body에 {{productId}} 사용)
```

### 변수 시스템

- Step의 응답에서 JSONPath로 값을 추출하여 변수에 저장
- 후속 Step의 URL, Header, Body에서 `{{변수명}}`으로 참조
- 데이터 피더(CSV/JSON)에서 읽은 값도 동일한 변수 치환 방식으로 사용

## 인증 처리

두 가지 방식을 지원하며, 방식 A가 기본이다.

### 방식 A: 시나리오 내 로그인 Step (기본)

- 시나리오의 첫 번째 Step으로 로그인 API 호출
- 응답에서 토큰을 추출하여 변수에 저장
- 이후 Step에서 `{{token}}` 변수로 자동 사용
- VU마다 독립적인 인증 상태 유지
- 실제 사용자 흐름과 동일하므로 부하 측정이 현실적

### 방식 B: 토큰 풀 (선택)

- 테스트 시작 전에 인증 토큰을 미리 N개 입력
- 각 VU에 토큰을 분배하여 사용
- 로그인 API에 부하를 주지 않고 다른 API만 테스트할 때 유용
- 토큰 만료 시에는 테스트 시작 전 갱신 필요

## 부하 설정 모델

### 기본 설정

| 설정 | 타입 | 설명 |
|------|------|------|
| targetBaseUrl | String | 대상 서비스 기본 URL |

### 부하 모델

| 설정 | 타입 | 설명 |
|------|------|------|
| vuCount | Int | 가상 유저 수 |
| requestsPerVu | Int? | VU당 요청 수 (null이면 duration 기반 무한 반복) |
| rampUpSeconds | Int | VU 점진 증가 시간 |
| durationSeconds | Int? | 테스트 지속 시간 (null이면 requestsPerVu 기반 종료) |
| stages | List | 단계별 부하 프로필 [{duration, targetVU}] |
| maxDuration | Int | 전체 테스트 최대 실행 시간 (안전장치) |

#### 우선순위 규칙

- `stages`가 비어있지 않으면 stages 사용 (고급 모드). vuCount, rampUpSeconds 무시.
- `stages`가 비어있으면 vuCount + rampUpSeconds + durationSeconds 사용 (간편 모드).
- `requestsPerVu`와 `durationSeconds`가 둘 다 설정되면 먼저 도달하는 조건에서 종료.

### 안전 설정

| 설정 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| abortOnErrorRate | Int | 50 | 에러율(%) 초과 시 자동 중단 |
| gracefulStopTimeout | Int | 30 | 종료 유예 시간 (초) |

### 임계치 (Thresholds)

테스트 결과를 자동으로 Pass/Fail 판정하는 기준 목록.

```
예시:
  - { metric: "p95", operator: "lt", value: 500 }     → p95 응답시간 < 500ms
  - { metric: "errorRate", operator: "lt", value: 5 }  → 에러율 < 5%
```

### 부가 설정

| 설정 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| thinkTimeStrategy | Enum | CONSTANT | CONSTANT / RANDOM / PACING |
| thinkTimeMs | Int | 0 | 고정 대기 시간 (CONSTANT 모드), 목표 반복 시간 (PACING 모드) |
| thinkTimeMin | Int | 0 | 최소 대기 시간 (RANDOM 모드) |
| thinkTimeMax | Int | 0 | 최대 대기 시간 (RANDOM 모드) |

Think Time 전략 상세:
- **CONSTANT**: 매 Step 후 thinkTimeMs만큼 고정 대기
- **RANDOM**: thinkTimeMin ~ thinkTimeMax 사이 랜덤 대기
- **PACING**: 1회 반복(iteration)의 총 소요시간이 thinkTimeMs가 되도록 대기 시간을 동적 조절. 실제 처리가 thinkTimeMs보다 오래 걸리면 대기 없이 즉시 진행.
| timeoutMs | Int | 10000 | 요청 타임아웃 |
| discardResponseBody | Boolean | false | 응답 본문 폐기 (메모리 절약) |

### 데이터 소스 (선택)

| 설정 | 타입 | 설명 |
|------|------|------|
| filePath | String | CSV/JSON 파일 경로 |
| distribution | Enum | SEQUENTIAL / RANDOM / CIRCULAR |
| onEof | Enum | RECYCLE / STOP_VU |

## 데이터 모델

### TestPlan (테스트 계획)

테스트 설정 전체를 저장하는 루트 엔티티. 재사용 가능.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| name | String | 테스트 이름 |
| description | String? | 설명 |
| targetBaseUrl | String | 대상 URL |
| loadConfig | JSON | 부하 설정 (vuCount, stages 등) |
| safetyConfig | JSON | 안전 설정 (abortOnErrorRate 등) |
| thresholds | JSON | 임계치 목록 |
| authConfig | JSON | 인증 설정 (mode, tokens) |
| advancedConfig | JSON | 부가 설정 (thinkTime, timeout 등) |
| dataSourceConfig | JSON? | 데이터 소스 설정 |
| createdAt | DateTime | 생성일 |
| updatedAt | DateTime | 수정일 |

### TestStep (시나리오 Step)

TestPlan에 속하는 순서가 있는 HTTP 호출 단위.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| testPlanId | Long | FK → TestPlan |
| orderIndex | Int | 실행 순서 |
| name | String | Step 이름 |
| httpMethod | Enum | GET / POST / PUT / DELETE |
| path | String | 요청 경로 (baseUrl 뒤에 붙음) |
| headers | JSON | 요청 헤더 |
| body | String? | 요청 본문 (변수 치환 가능) |
| thinkTimeMs | Int? | Step별 대기 시간 (null이면 전역 설정 사용) |
| extractors | JSON | 변수 추출 규칙 [{source, jsonPath, variableName}] |
| validators | JSON | 응답 검증 규칙 [{type, expected}] |

#### Extractor 구조

```json
{
  "source": "BODY",
  "jsonPath": "$.data.token",
  "variableName": "token"
}
```

#### Validator 구조

```json
[
  { "type": "STATUS", "expected": "200" },
  { "type": "BODY_CONTAINS", "value": "success" }
]
```

### TestExecution (실행 이력)

TestPlan을 실행할 때마다 생성되는 실행 기록.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| testPlanId | Long | FK → TestPlan |
| status | Enum | RUNNING / COMPLETED / ABORTED |
| result | Enum? | PASS / FAIL (threshold 기반 판정, 종료 후 결정) |
| startedAt | DateTime | 시작 시각 |
| finishedAt | DateTime? | 종료 시각 |
| totalRequests | Long | 총 요청 수 |
| successCount | Long | 성공 수 |
| failCount | Long | 실패 수 |
| avgResponseTime | Double | 평균 응답시간 (ms) |
| p50 | Double | 50퍼센타일 응답시간 |
| p90 | Double | 90퍼센타일 응답시간 |
| p95 | Double | 95퍼센타일 응답시간 |
| p99 | Double | 99퍼센타일 응답시간 |
| tps | Double | 초당 처리량 |
| errorRate | Double | 에러율 (%) |

### MetricSnapshot (시계열 메트릭)

실행 중 1초 주기로 수집하는 시계열 데이터. 대시보드 차트와 Step별 분석에 사용.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| executionId | Long | FK → TestExecution |
| stepName | String? | null이면 전체 집계, 값이 있으면 해당 Step 메트릭 |
| timestamp | DateTime | 수집 시각 |
| currentVu | Int | 현재 활성 VU 수 |
| requestCount | Int | 해당 구간 요청 수 |
| errorCount | Int | 해당 구간 에러 수 |
| avgResponseTime | Double | 평균 응답시간 (ms) |
| p95ResponseTime | Double | 95퍼센타일 응답시간 (ms) |
| tps | Double | 초당 처리량 |

## 메트릭 수집 아키텍처

```
Load Engine (Coroutine per VU)
  │ 매 요청마다 {stepName, latency, statusCode, timestamp}
  ▼
MetricBuffer (ConcurrentLinkedQueue)
  │ 1초 주기 소비 및 집계
  ▼
MetricAggregator
  ├─→ MySQL (MetricSnapshot 저장, 전체 + Step별)
  ├─→ SSE (실시간 브라우저 푸시)
  └─→ 최종 집계 (테스트 종료 시 TestExecution에 p50/p90/p95/p99 계산)
```

### 집계 흐름

1. 각 VU(Coroutine)가 요청을 보내고 {stepName, 응답시간, 상태코드}를 MetricBuffer에 적재
2. MetricAggregator가 1초마다 Buffer를 소비하여 집계
   - 전체 집계 (stepName = null)
   - Step별 집계 (stepName = 각 Step 이름)
3. 집계 결과를 3곳에 전달:
   - MySQL: MetricSnapshot으로 영속 저장
   - SSE: 브라우저에 실시간 푸시 (대시보드 갱신)
   - 메모리: 전체 응답시간 목록 유지 (종료 시 퍼센타일 계산용)

## 대시보드 UI

### 실시간 대시보드 (`/executions/{id}/live`)

SSE로 1초마다 갱신되는 실시간 모니터링 화면.

**구성 요소:**
- 상단 카드: TPS, 평균 응답시간, 에러율, 현재 VU 수
- 차트 1: 응답시간 추이 (시계열 라인 차트)
- 차트 2: TPS 추이 (시계열 라인 차트)
- Step별 테이블: 각 Step의 Avg, p95, Error Rate 실시간 비교
- 중단 버튼: Graceful Stop으로 테스트 종료

### 결과 Summary (`/executions/{id}`)

테스트 완료 후 최종 결과 화면.

**구성 요소:**
- 결과 판정: PASS / FAIL (threshold 기반)
- 요약: 총 요청, 성공, 실패, 소요시간, 에러율
- 응답시간 분포: avg, p50, p90, p95, p99
- 임계치 판정 상세: 각 threshold의 실측값과 Pass/Fail
- Step별 결과: 각 Step의 avg, p95, 에러율
- 액션: 차트 보기, 다시 실행, 목록으로

## Web UI 페이지 구성

| 경로 | 설명 |
|------|------|
| `/plans` | 테스트 계획 목록. 실행/복제/삭제 액션 |
| `/plans/new` | 새 테스트 계획 생성 (폼 기반) |
| `/plans/{id}` | 계획 상세 조회 및 수정 |
| `/plans/{id}/steps` | 시나리오 Step 순서 편집, 변수 추출/검증 설정 |
| `/executions` | 실행 이력 목록. 상태, 결과(Pass/Fail), 날짜 |
| `/executions/{id}` | 최종 결과 Summary |
| `/executions/{id}/live` | SSE 기반 실시간 대시보드 |

## 주요 설계 결정 요약

| 결정 | 근거 |
|------|------|
| Coroutines + WebClient | 동시성 제어 + 비동기 I/O 학습 목표에 모두 부합 |
| Step 리스트 통합 (SINGLE 모드 제거) | 엔진 코드 단순화, 모드 분기 없이 하나의 로직 |
| Step별 thinkTimeMs (nullable) | 전역 기본값 + Step별 오버라이드로 유연성 확보 |
| 인증 방식 A + B 모두 지원 | 로그인 포함/제외 테스트 모두 가능 |
| stages 우선순위 규칙 | stages가 있으면 간편 모드 무시, 충돌 방지 |
| 먼저 도달하는 종료 조건 | requestsPerVu와 durationSeconds 동시 설정 시 명확한 규칙 |
| SSE (WebSocket 대신) | 서버→클라이언트 단방향이면 충분, 구현 간단 |
| MetricSnapshot에 stepName 추가 | Step별 병목 분석 가능 |
