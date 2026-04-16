# Traffic Service

운영 중인 서비스에 HTTP 부하를 생성하는 웹 기반 부하 테스트 서비스.

## 부하 테스트란?

부하 테스트는 서버에 많은 요청을 동시에 보내서, 서버가 얼마나 잘 버티는지 확인하는 것이다.

예를 들어 쇼핑몰에 100명이 동시에 접속해서 주문을 넣는 상황을 가정해보자. 실제로 100명을 모을 수는 없으니, **가상 유저(VU)** 100명을 만들어서 대신 요청을 보낸다. 이때 서버가 요청을 얼마나 빨리 처리하는지, 에러 없이 처리하는지를 측정한다.

### 알아야 할 용어

| 용어 | 뜻 | 쉬운 설명 |
|------|------|------|
| **VU (Virtual User)** | 가상 유저 | 실제 사용자 대신 요청을 보내는 가상의 사람. VU 10이면 10명이 동시에 접속한 것과 같다 |
| **TPS (Transactions Per Second)** | 초당 처리량 | 서버가 1초에 처리하는 요청 수. 높을수록 좋다 |
| **응답 시간 (Latency)** | 요청~응답 소요 시간 | 요청을 보내고 응답이 돌아오기까지 걸린 시간 (ms). 낮을수록 좋다 |
| **에러율 (Error Rate)** | 실패한 요청 비율 | 전체 요청 중 실패한 요청의 비율 (%). 0%에 가까울수록 좋다 |
| **p95, p99** | 퍼센타일 응답 시간 | 전체 요청을 빠른 순서대로 줄 세웠을 때 95번째, 99번째 요청의 응답 시간. 평균보다 현실적인 지표다 |
| **Ramp-up** | 점진적 증가 | VU를 한꺼번에 시작하지 않고, 일정 시간에 걸쳐 천천히 늘리는 것 |
| **Think Time** | 사용자 대기 시간 | 실제 사용자가 페이지를 읽거나 입력하는 시간을 흉내낸 대기 시간 |

### p95가 왜 중요한가?

평균 응답 시간이 100ms라고 해도, 일부 사용자는 2초 이상 기다릴 수 있다. p95는 "100명 중 95명은 이 시간 안에 응답을 받았다"는 뜻이다. 실서비스에서는 평균보다 p95, p99를 더 중요하게 본다.

```
예시: 요청 100건의 응답 시간
대부분 50~100ms인데, 5건이 800ms, 1건이 2000ms

→ 평균: 120ms (괜찮아 보인다)
→ p95:  800ms (100명 중 5명은 0.8초 이상 기다렸다)
→ p99: 2000ms (100명 중 1명은 2초 이상 기다렸다)
```

---

## 요구 사항

- JDK 17 이상
- Docker

## 실행

```bash
./gradlew bootRun
```

Spring Boot Docker Compose 지원에 의해 MySQL 컨테이너가 자동으로 시작된다. 실행 후 http://localhost:8080/plans 에 접속한다.

---

## 빠른 시작 가이드

처음 사용하는 사람을 위한 단계별 가이드. 공개 테스트 서비스인 httpbin.org에 간단한 부하를 보내본다.

### Step 1: 테스트 계획 만들기

1. http://localhost:8080/plans/new 에 접속
2. 다음과 같이 입력:
   - **Name**: `첫 번째 테스트`
   - **Target Base URL**: `https://httpbin.org`
   - **Virtual Users (VU)**: `3` (3명이 동시에 요청)
   - **Requests per VU**: `5` (1명당 5번 요청, 총 15건)
3. `Create` 클릭

### Step 2: 시나리오 Step 추가

1. 생성된 계획 페이지에서 `Edit Steps` 클릭
2. 다음과 같이 입력:
   - **Name**: `GET 테스트`
   - **HTTP Method**: `GET`
   - **Path**: `/get`
3. `Add Step` 클릭

### Step 3: 테스트 실행

1. `/plans` 목록으로 돌아가서 `Run` 클릭
2. 실시간 대시보드에서 차트가 움직이는 것을 확인
3. 테스트가 끝나면 자동으로 결과 페이지로 이동

### Step 4: 결과 읽기

결과 페이지에서 다음을 확인한다:

- **Total Requests: 15** → VU 3명 x 5회 = 15건
- **Avg Response Time** → 평균 응답 시간
- **Error Rate: 0%** → 모든 요청이 성공
- **p95** → 15건 중 가장 느린 축에 속하는 응답 시간

이것이 부하 테스트의 기본 흐름이다. 아래에서 각 기능을 자세히 설명한다.

---

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

**종료 조건은 두 가지 방식 중 택일한다:**

- **횟수 기반**: `Requests per VU`를 설정하면, 각 VU가 정해진 횟수만큼 요청 후 종료
- **시간 기반**: `Duration`을 설정하면, 정해진 시간 동안 계속 요청

둘 다 설정하면 먼저 도달하는 조건에서 종료된다.

#### 안전 설정

| 항목 | 설명 | 기본값 |
|------|------|--------|
| Abort on Error Rate (%) | 에러율 초과 시 자동 중단 | 50 |
| Graceful Stop Timeout (seconds) | 종료 유예 시간 | 30 |
| Request Timeout (ms) | 개별 요청 타임아웃 | 10000 |

서버가 다운되었는데 계속 요청을 보내면 안 되므로, 에러율이 임계치를 넘으면 자동으로 테스트를 중단한다.

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
| Path | 요청 경로 (예: `/api/login`). Target Base URL 뒤에 붙는다 |
| Body | 요청 본문. POST/PUT 시 JSON 입력 (선택) |
| Think Time (ms) | Step 실행 후 대기 시간 (선택) |

Target Base URL이 `https://api.example.com`이고 Path가 `/api/login`이면, 실제 요청은 `https://api.example.com/api/login`으로 보내진다.

#### 왜 Step을 여러 개 만드는가?

실제 사용자는 한 가지 API만 호출하지 않는다. 로그인 → 상품 조회 → 주문처럼 여러 API를 순서대로 호출한다. 이 흐름 전체에 부하를 줘야 현실적인 테스트가 된다.

```
Step 1: POST /api/login        → 로그인
Step 2: GET  /api/products      → 상품 목록 조회
Step 3: POST /api/orders        → 주문 생성
```

각 VU(가상 유저)가 Step 1 → 2 → 3을 순서대로 반복 실행한다.

---

### 3. 변수 시스템

앞 Step의 응답 값을 뒷 Step에서 사용할 수 있다. 로그인 후 받은 토큰을 다음 요청에 사용하는 것이 대표적인 예시다.

#### 변수 추출 (Extractor)

응답 Body에서 JSONPath로 값을 추출한다.

```json
{
  "source": "BODY",
  "jsonPath": "$.data.token",
  "variableName": "token"
}
```

예를 들어, 로그인 API의 응답이 아래와 같다면:

```json
{
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

`$.data.token` 경로의 값인 `eyJhbGciOiJIUzI1NiJ9...`가 `token` 변수에 저장된다.

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

검증 실패 시 해당 요청은 실패로 집계된다. 검증 규칙을 설정하지 않으면, HTTP 상태 코드 200~399를 성공으로 간주한다.

---

### 4. 테스트 실행

`/plans` 목록에서 `Run` 버튼을 클릭하면 테스트가 시작되고, 실시간 대시보드로 이동한다.

#### 실시간 대시보드

1초마다 갱신되는 실시간 모니터링 화면:

| 지표 | 의미 | 좋은 상태 |
|------|------|-----------|
| **TPS** | 서버가 1초에 처리한 요청 수 | 높을수록 좋음 |
| **Avg Latency** | 평균 응답 시간 (ms) | 낮을수록 좋음 |
| **Error Rate** | 실패한 요청 비율 (%) | 0%에 가까울수록 좋음 |
| **Active VU** | 현재 활성 가상 유저 수 | 설정한 VU 수와 일치해야 정상 |

차트 2개가 시간에 따른 응답 시간과 TPS 변화를 보여준다. 차트가 급격히 튀거나 에러율이 올라가면 서버에 문제가 있다는 신호다.

`Abort` 버튼으로 테스트를 즉시 중단할 수 있다. 테스트 완료 시 자동으로 결과 페이지로 이동한다.

---

### 5. 결과 확인

`/executions` 에서 실행 이력을 조회하고, 각 실행의 결과 상세를 확인한다.

#### 결과 페이지에서 확인하는 것

| 항목 | 의미 |
|------|------|
| Total Requests | 전체 요청 수 |
| Success / Failed | 성공, 실패 건수 |
| Avg Response Time | 평균 응답 시간 |
| TPS | 평균 초당 처리량 |
| Error Rate | 에러율 |
| p50 | 절반의 요청이 이 시간 안에 완료 |
| p90 | 90%의 요청이 이 시간 안에 완료 |
| p95 | 95%의 요청이 이 시간 안에 완료 |
| p99 | 99%의 요청이 이 시간 안에 완료 |
| PASS / FAIL | Threshold 기준 자동 판정 (설정 시) |

`Rerun` 버튼으로 동일한 계획을 다시 실행할 수 있다.

---

### 6. 인증 처리

대부분의 API는 인증이 필요하다. 두 가지 방식을 지원한다.

#### 방식 A: 시나리오 내 로그인 Step (기본)

첫 번째 Step에서 로그인 후 토큰을 추출하고, 이후 Step에서 사용한다.

```
Step 1: POST /api/login  → Extractor: $.data.token → 변수 "token"
Step 2: GET  /api/data   → Header: Authorization: Bearer {{token}}
```

VU마다 독립적으로 로그인하므로 실제 사용자 흐름과 동일하다. 로그인 API 자체에도 부하가 걸리므로, 로그인 성능까지 함께 측정된다.

#### 방식 B: 토큰 풀

미리 발급한 토큰 목록을 입력하면, 각 VU에 하나씩 분배된다. 로그인 API에 부하를 주지 않고 다른 API만 테스트할 때 유용하다.

---

### 7. 부하 모델

#### 간편 모드

VU 수 + Ramp-up + 종료 조건으로 설정한다.

```
예: VU 100, Ramp-up 10초, Duration 60초
```

```
시간 0초:  VU 0명
시간 5초:  VU 50명 (점진 증가 중)
시간 10초: VU 100명 (전원 시작)
시간 70초: 테스트 종료
```

Ramp-up을 0으로 설정하면 모든 VU가 동시에 시작한다. 실제 서비스에서 트래픽이 한꺼번에 몰리는 경우(예: 선착순 이벤트)를 시뮬레이션할 수 있다.

#### Stages 모드 (고급)

단계별 VU 수를 정의한다. stages가 설정되면 간편 모드 설정은 무시된다.

```
Stage 1: 30초 동안 VU 50까지 증가    ← 워밍업
Stage 2: 60초 동안 VU 50 유지        ← 평상시 부하
Stage 3: 30초 동안 VU 100까지 증가   ← 피크 시뮬레이션
Stage 4: 60초 동안 VU 100 유지       ← 피크 유지
Stage 5: 10초 동안 VU 0으로 감소     ← 정리
```

이 방식으로 실제 트래픽 패턴(점심시간 피크, 퇴근 후 감소 등)을 흉내낼 수 있다.

---

### 8. Think Time

VU가 Step 사이에 대기하는 시간을 제어한다.

실제 사용자는 버튼을 클릭한 후 바로 다음 버튼을 누르지 않는다. 페이지를 읽거나, 폼을 입력하는 시간이 있다. Think Time은 이 행동을 시뮬레이션한다. Think Time 없이 테스트하면 실제보다 훨씬 높은 부하가 걸린다.

| 전략 | 설명 | 사용 예시 |
|------|------|-----------|
| CONSTANT | 매 Step 후 고정 시간 대기 | 모든 Step 사이에 1초씩 대기 |
| RANDOM | 최소~최대 사이 랜덤 대기 | 0.5초~3초 사이 랜덤 대기 (가장 현실적) |
| PACING | 1회 반복 총 소요시간이 목표 시간이 되도록 동적 조절 | 1회 반복이 정확히 10초 간격이 되도록 |

개별 Step에 `Think Time (ms)`를 설정하면 전역 설정보다 우선한다.

---

### 9. 데이터 피더

외부 파일에서 테스트 데이터를 읽어 변수로 사용한다. 예를 들어, 여러 계정으로 동시에 로그인 테스트를 하고 싶을 때 사용한다.

#### CSV

```csv
username,password
alice,pass1
bob,pass2
charlie,pass3
```

#### JSON

```json
[
  {"username": "alice", "password": "pass1"},
  {"username": "bob", "password": "pass2"},
  {"username": "charlie", "password": "pass3"}
]
```

파일의 각 행이 변수 맵으로 변환되어 `{{username}}`, `{{password}}`로 사용할 수 있다.

예: Step의 Body를 아래처럼 설정하면, VU마다 다른 계정으로 로그인한다.

```json
{"username": "{{username}}", "password": "{{password}}"}
```

#### 분배 전략

| 전략 | 설명 | 사용 예시 |
|------|------|-----------|
| SEQUENTIAL | 순서대로 한 행씩 | VU1=alice, VU2=bob, VU3=charlie |
| RANDOM | 랜덤 선택 | 매번 랜덤으로 계정 선택 |
| CIRCULAR | 순서대로, 끝나면 처음부터 반복 | alice → bob → charlie → alice → ... |

#### 데이터 소진 시

| 전략 | 설명 |
|------|------|
| RECYCLE | 처음부터 재사용 |
| STOP_VU | 해당 VU 중지 |

---

### 10. Threshold (임계치)

테스트 결과의 PASS/FAIL 자동 판정 기준을 설정한다. 설정하지 않으면 항상 PASS로 판정된다.

```
p95 < 500      → p95 응답 시간이 500ms 미만이면 PASS
errorRate < 5  → 에러율 5% 미만이면 PASS
tps > 100      → TPS 100 이상이면 PASS
```

**사용 가능한 메트릭**: `AVG`, `P50`, `P90`, `P95`, `P99`, `ERROR_RATE`, `TPS`

**연산자**: `LT` (<), `LTE` (<=), `GT` (>), `GTE` (>=)

모든 Threshold를 통과하면 PASS, 하나라도 실패하면 FAIL로 판정된다.

#### 언제 사용하는가?

- 배포 전에 "p95가 500ms를 넘으면 배포하지 않는다" 같은 기준을 정할 때
- 정기적으로 성능을 측정하고 기준 이하로 떨어지는지 감시할 때

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
