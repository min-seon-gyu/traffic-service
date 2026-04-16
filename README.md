# Traffic Service

운영 중인 서비스에 HTTP 부하를 생성하는 웹 기반 부하 테스트 서비스.

## 요구 사항

- JDK 17 이상
- Docker

## 실행

```bash
./gradlew bootRun
```

Spring Boot Docker Compose 지원에 의해 MySQL 컨테이너가 자동으로 시작된다. 실행 후 http://localhost:8080/plans 에 접속한다.

## 사용법

### 1. 테스트 계획 생성

`/plans` 페이지에서 `+ New Plan` 버튼을 클릭한다.

#### 기본 설정

| 항목 | 설명 | 예시 |
|------|------|------|
| Name | 테스트 이름 | "주문 API 부하 테스트" |
| Target Base URL | 대상 서비스의 기본 URL | `https://api.example.com` |
| Description | 테스트 설명 (선택) | "피크 시간대 시뮬레이션" |

#### 부하 설정

| 항목 | 설명 | 기본값 |
|------|------|--------|
| Virtual Users (VU) | 동시에 요청을 보내는 가상 유저 수 | 1 |
| Requests per VU | VU당 반복 요청 횟수. 비워두면 Duration 기반 | - |
| Ramp-up (seconds) | VU를 점진적으로 증가시키는 시간 | 0 |
| Duration (seconds) | 테스트 지속 시간. 비워두면 Requests per VU 기반 | - |
| Max Duration (seconds) | 전체 테스트 최대 실행 시간 (안전장치) | 600 |

`Requests per VU`와 `Duration`을 둘 다 설정하면 먼저 도달하는 조건에서 종료된다.

#### 안전 설정

| 항목 | 설명 | 기본값 |
|------|------|--------|
| Abort on Error Rate (%) | 에러율 초과 시 자동 중단 | 50 |
| Graceful Stop Timeout (seconds) | 종료 유예 시간 | 30 |
| Request Timeout (ms) | 개별 요청 타임아웃 | 10000 |

설정 완료 후 `Create` 버튼을 클릭한다.

---

### 2. 시나리오 Step 구성

계획 생성 후 `Edit Steps` 버튼을 클릭한다.

모든 테스트는 Step 리스트 순서대로 실행된다. 단건 API 호출이면 Step 1개만 추가하면 된다.

#### Step 추가

| 항목 | 설명 |
|------|------|
| Name | Step 이름 (예: "로그인") |
| HTTP Method | GET / POST / PUT / DELETE |
| Path | 요청 경로 (예: `/api/login`) |
| Body | 요청 본문. POST/PUT 시 JSON 입력 (선택) |
| Think Time (ms) | Step 실행 후 대기 시간 (선택) |

#### 예시: 주문 플로우

```
Step 1: POST /api/login        → 로그인
Step 2: GET  /api/products      → 상품 목록 조회
Step 3: POST /api/orders        → 주문 생성
```

각 VU(가상 유저)가 Step 1 → 2 → 3을 순서대로 반복 실행한다.

---

### 3. 변수 시스템

앞 Step의 응답 값을 뒷 Step에서 사용할 수 있다.

#### 변수 추출 (Extractor)

응답 Body에서 JSONPath로 값을 추출한다.

```json
{
  "source": "BODY",
  "jsonPath": "$.data.token",
  "variableName": "token"
}
```

위 설정은 응답의 `$.data.token` 값을 `token` 변수에 저장한다.

#### 변수 사용

후속 Step의 Path, Header, Body에서 `{{변수명}}`으로 사용한다.

```
Path:   /api/users/{{userId}}
Header: Authorization: Bearer {{token}}
Body:   {"productId": "{{productId}}"}
```

#### 응답 검증 (Validator)

응답이 기대한 조건을 만족하는지 검증한다.

| type | 설명 | 예시 |
|------|------|------|
| STATUS | HTTP 상태 코드 일치 확인 | `"expected": "200"` |
| BODY_CONTAINS | 응답 Body에 특정 문자열 포함 확인 | `"expected": "success"` |

검증 실패 시 해당 요청은 실패로 집계된다.

---

### 4. 테스트 실행

`/plans` 목록에서 `Run` 버튼을 클릭하면 테스트가 시작되고, 실시간 대시보드로 이동한다.

#### 실시간 대시보드

1초마다 갱신되는 실시간 모니터링 화면:

- **TPS** - 초당 처리량
- **Avg Latency** - 평균 응답 시간 (ms)
- **Error Rate** - 에러율 (%)
- **Active VU** - 현재 활성 가상 유저 수
- **Response Time 차트** - 응답 시간 추이 (시계열)
- **TPS 차트** - TPS 추이 (시계열)

`Abort` 버튼으로 테스트를 즉시 중단할 수 있다. 테스트 완료 시 자동으로 결과 페이지로 이동한다.

---

### 5. 결과 확인

`/executions` 에서 실행 이력을 조회하고, 각 실행의 결과 상세를 확인한다.

- **총 요청 수 / 성공 / 실패**
- **평균 응답 시간, TPS, 에러율**
- **응답 시간 분포** - p50, p90, p95, p99
- **결과 판정** - PASS / FAIL (Threshold 설정 시)

`Rerun` 버튼으로 동일한 계획을 다시 실행할 수 있다.

---

### 6. 인증 처리

#### 방식 A: 시나리오 내 로그인 Step (기본)

첫 번째 Step에서 로그인 후 토큰을 추출하고, 이후 Step에서 사용한다.

```
Step 1: POST /api/login  → Extractor: $.data.token → 변수 "token"
Step 2: GET  /api/data   → Header: Authorization: Bearer {{token}}
```

VU마다 독립적으로 로그인하므로 실제 사용자 흐름과 동일하다.

#### 방식 B: 토큰 풀

미리 발급한 토큰 목록을 입력하면, 각 VU에 하나씩 분배된다. 로그인 API를 거치지 않고 바로 테스트할 때 유용하다.

---

### 7. 부하 모델

#### 간편 모드

VU 수 + Ramp-up + 종료 조건으로 설정한다.

```
예: VU 100, Ramp-up 10초, Duration 60초
→ 10초에 걸쳐 VU 0 → 100 증가, 이후 60초간 부하 유지
```

#### Stages 모드 (고급)

단계별 VU 수를 정의한다. stages가 설정되면 간편 모드 설정은 무시된다.

```
Stage 1: 30초 동안 VU 50까지 증가
Stage 2: 60초 동안 VU 50 유지
Stage 3: 30초 동안 VU 100까지 증가
Stage 4: 60초 동안 VU 100 유지
Stage 5: 10초 동안 VU 0으로 감소
```

---

### 8. Think Time

VU가 Step 사이에 대기하는 시간을 제어한다.

| 전략 | 설명 |
|------|------|
| CONSTANT | 매 Step 후 고정 시간 대기 |
| RANDOM | 최소~최대 사이 랜덤 대기 |
| PACING | 1회 반복 총 소요시간이 목표 시간이 되도록 동적 조절 |

개별 Step에 `Think Time (ms)`를 설정하면 전역 설정보다 우선한다.

---

### 9. 데이터 피더

외부 파일에서 테스트 데이터를 읽어 변수로 사용한다.

#### CSV

```csv
username,password
alice,pass1
bob,pass2
```

#### JSON

```json
[
  {"username": "alice", "password": "pass1"},
  {"username": "bob", "password": "pass2"}
]
```

파일의 각 행이 변수 맵으로 변환되어 `{{username}}`, `{{password}}`로 사용할 수 있다.

#### 분배 전략

| 전략 | 설명 |
|------|------|
| SEQUENTIAL | 순서대로 한 행씩 |
| RANDOM | 랜덤 선택 |
| CIRCULAR | 순서대로 사용, 끝나면 처음부터 반복 |

#### 데이터 소진 시

| 전략 | 설명 |
|------|------|
| RECYCLE | 처음부터 재사용 |
| STOP_VU | 해당 VU 중지 |

---

### 10. Threshold (임계치)

테스트 결과의 PASS/FAIL 자동 판정 기준을 설정한다.

```
p95 < 500      → p95 응답 시간이 500ms 미만이면 PASS
errorRate < 5  → 에러율 5% 미만이면 PASS
tps > 100      → TPS 100 이상이면 PASS
```

**메트릭**: `AVG`, `P50`, `P90`, `P95`, `P99`, `ERROR_RATE`, `TPS`

**연산자**: `LT` (<), `LTE` (<=), `GT` (>), `GTE` (>=)

모든 Threshold를 통과하면 PASS, 하나라도 실패하면 FAIL로 판정된다.

---

## 페이지 요약

| 경로 | 설명 |
|------|------|
| `/plans` | 테스트 계획 목록 |
| `/plans/new` | 새 계획 생성 |
| `/plans/{id}` | 계획 수정 |
| `/plans/{id}/steps` | Step 편집 |
| `/executions` | 실행 이력 |
| `/executions/{id}` | 결과 상세 |
| `/executions/{id}/live` | 실시간 대시보드 |

## DB 설정

기본값은 MySQL이며, `./gradlew bootRun` 시 Docker Compose로 MySQL 컨테이너가 자동 시작/종료된다.

별도로 MySQL을 운영하려면 `application.yml`의 datasource를 수정하고, `spring.docker.compose.enabled`를 `false`로 설정한다.

테스트는 H2 인메모리 DB를 사용한다 (`src/test/resources/application.yml`).
