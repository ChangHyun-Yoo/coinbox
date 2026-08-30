# CoinBox

카카오뱅크 저금통의 신규 가입, 비우기, 자동저축인 동전모으기와 해지를 설계하고 구현한
Spring Boot 과제입니다. 문서의 설계가 실제 코드와 일치하는지 확인할 수 있도록 온라인 API와 배치,
단위·통합·동시성 테스트까지 함께 구현했습니다.

## 1. 제출 과제 안내

채점 시 필수 산출물을 과제에서 제시한 순서대로 확인할 수 있도록 구성했습니다.

| 과제 항목                             | 제출 문서                                        | 주요 내용                                                     |
| ------------------------------------- | ------------------------------------------------ | ------------------------------------------------------------- |
| 1. ERD로 작성된 데이터 모델링         | [ERD와 Enum](docs/01_ERD.md)                     | ERD, 테이블·컬럼 의미, PK·UK, Enum과 관계 설계                |
| 2. 프로세스 다이어그램과 상세 COMMENT | [시퀀스 다이어그램](docs/02_SEQUENCE_DIAGRAM.md) | 신규 가입, 비우기, 동전모으기, 해지의 처리 순서와 단계별 설명 |
| 3. 중요 단계별 예시 데이터            | [예시 데이터 흐름](docs/03_DATA_FLOW.md)         | 시퀀스 단계와 연결된 테이블별 Before·After 데이터             |
| 4. 클래스 다이어그램                  | [클래스 다이어그램](docs/04_CLASS_DIAGRAM.md)    | 프로세스를 구성하는 클래스의 책임, 관계와 호출 흐름           |

필수 산출물을 보완하기 위해 [API와 예외 처리](docs/05_API.md), 실행 가능한 백엔드 소스,
Swagger/OpenAPI 명세와 자동화 테스트도 함께 제공합니다.

## 2. 구현 범위와 핵심 결과

다음 저금통 프로세스를 구현했습니다.

- 선택한 입출금계좌를 근거계좌로 사용하는 저금통 신규 가입을 구현했습니다.
- 저금통 잔액 전액을 연결 입출금계좌로 이체하는 비우기를 구현했습니다.
- 일별 최종 잔액과 평일 동전모으기를 Spring Batch로 구현했습니다.
- 남은 잔액을 근거계좌로 이전한 뒤 계좌·계약·설정을 종료하는 해지를 구현했습니다.
- 고객의 ACTIVE 계좌를 부모·자식 구조로 조회하는 API를 추가했습니다.

정상 흐름뿐 아니라 다음 정합성 조건을 구현과 테스트의 중심에 두었습니다.

- 한 고객에게 이용 중인 저금통이 동시에 두 개 생성되지 않도록 고객 행을 잠갔습니다.
- 한 번의 이체를 금융거래 한 건과 출금·입금 원장 두 건으로 추적할 수 있게 했습니다.
- 여러 계좌의 잠금 순서를 `account_id` 오름차순으로 통일해 교착 가능성을 낮췄습니다.
- 비우기·동전모으기·해지의 잔액, 금융거래와 원장을 하나의 트랜잭션으로 반영했습니다.
- 같은 저금통의 같은 실행일 동전모으기가 한 번만 처리되도록 복합 UK를 적용했습니다.
- 테스트가 개발자의 로컬 MySQL 상태에 의존하지 않도록 MySQL Testcontainers를 사용했습니다.

주요 기술 구성은 다음과 같습니다.

| 구분        | 기술과 적용 내용                               |
| ----------- | ---------------------------------------------- |
| Language    | Java 21                                        |
| Framework   | Spring Boot 4.0.8, Spring Batch                |
| Persistence | Spring Data JPA, 배치 대량 저장용 JdbcTemplate |
| Database    | MySQL 8.3.0                                    |
| Test        | JUnit 5, AssertJ, Spring Test, Testcontainers  |
| Quality     | JaCoCo, Swagger/OpenAPI                        |
| Build       | Gradle Wrapper                                 |

### 2.1 프로젝트 구조

채점에 필요한 문서, 핵심 도메인 코드, 테스트와 실행 설정을 중심으로 정리했습니다.

```text
coinbox
├── docs
│   ├── 01_ERD.md                 # ERD, 테이블·컬럼, 관계와 Enum 정의
│   ├── 02_SEQUENCE_DIAGRAM.md    # 신규가입·비우기·동전모으기·해지 시퀀스와 COMMENT
│   ├── 03_DATA_FLOW.md           # 프로세스 중요 단계별 Before·After 예시 데이터
│   ├── 04_CLASS_DIAGRAM.md       # 프로세스 구성 클래스의 책임과 관계
│   └── 05_API.md                 # API 계약, 입력 검증과 공통 예외 처리
├── src
│   ├── main
│   │   ├── java/com/kakaobank/coinbox
│   │   │   ├── CoinboxApplication.java
│   │   │   ├── account                  # 계좌 조회, 계좌번호 채번, 테스트 잔고 조정 API
│   │   │   ├── accountcontract          # 계좌 계약과 계약 상태
│   │   │   ├── accountdailybalance      # 일별 최종 잔고 Entity와 배치 처리
│   │   │   ├── accountentry             # 계좌별 입금·출금 원장
│   │   │   ├── coinbox                  # 저금통 가입·비우기·해지 핵심 업무
│   │   │   ├── coinboxpolicy            # 저금통 최대 보유 한도 정책
│   │   │   ├── coinsavingexecution      # 동전모으기 실행 이력, 서비스와 Chunk 배치
│   │   │   ├── customer                 # 고객과 고객 상태
│   │   │   ├── financialtransaction     # 금융거래와 공통 당행 이체 서비스
│   │   │   ├── product                  # 상품군과 상품 버전
│   │   │   └── common                   # 배치 실행, 예외, OpenAPI, Snowflake, 공통 Entity
│   │   └── resources
│   │       ├── application.yaml         # 애플리케이션·DB·Batch·Swagger 설정
│   │       └── data.sql                 # Swagger와 배치 확인용 초기 샘플 데이터
│   └── test
│       ├── java/com/kakaobank/coinbox
│       │   ├── account                  # 계좌 Controller·Service·Repository 테스트
│       │   ├── accountcontract          # 계좌 계약 Repository 테스트
│       │   ├── accountdailybalance      # 일별 최종 잔고 Repository 테스트
│       │   ├── accountentry             # 계좌 원장 Repository 테스트
│       │   ├── coinbox                  # 저금통 온라인 업무와 Swagger 예시 테스트
│       │   ├── coinboxpolicy            # 저금통 정책 Repository 테스트
│       │   ├── coinsavingexecution      # 동전모으기 배치·서비스·멱등성 테스트
│       │   ├── customer                 # 고객 Repository 테스트
│       │   ├── financialtransaction     # 당행 이체 단위·통합·Rollback 테스트
│       │   ├── product                  # 상품·상품 버전 Repository 테스트
│       │   ├── common                   # 배치 실행, OpenAPI, 예외와 Snowflake 테스트
│       │   └── support                  # 공용 MySQL Testcontainer와 초기 데이터 검증
│       └── resources
│           └── application.properties   # 테스트 공통 설정
├── gradle
│   └── wrapper                          # 고정된 Gradle Wrapper 실행 환경
├── docker-compose.yml                   # 로컬 MySQL 8.3 실행 구성
├── build.gradle                         # 의존성, 테스트와 JaCoCo 설정
├── settings.gradle
├── gradlew
├── gradlew.bat
└── README.md
```

## 3. 실행 방법

### 3.1 소스 다운로드

제출한 프로젝트 압축 파일을 사용하거나 다음 GitHub 저장소에서 동일한 프로젝트를 내려받을 수 있습니다.

- GitHub 저장소: [ChangHyun-Yoo/coinbox](https://github.com/ChangHyun-Yoo/coinbox)

Git을 사용하는 경우 다음 명령으로 프로젝트를 내려받은 뒤 프로젝트 루트로 이동합니다.

```bash
git clone https://github.com/ChangHyun-Yoo/coinbox.git
cd coinbox
```

### 3.2 실행 전 준비

다음 프로그램이 필요합니다.

- JDK 21
- Docker Desktop 또는 Docker Engine과 Docker Compose
- 사용 가능한 MySQL `3306` 포트와 애플리케이션 `8080` 포트

Gradle과 MySQL을 별도로 설치할 필요는 없습니다. 빌드에는 저장소의 Gradle Wrapper를 사용하고,
MySQL은 Docker Compose로 실행합니다.

### 3.3 빠른 실행

프로젝트 루트에서 MySQL을 시작하고 healthcheck 통과까지 기다립니다.

```bash
docker compose up -d --wait mysql
docker compose ps
```

`mysql` 서비스가 `healthy` 상태가 되면 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

실행 후 다음 주소에서 API 명세를 확인할 수 있습니다.

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 3.4 초기 샘플 데이터

Hibernate가 업무 테이블을 생성한 뒤 `data.sql`의 샘플 데이터를 자동으로 입력합니다. Swagger 예시를
실행하는 순서 때문에 다른 API의 성공 조건이 소모되지 않도록 조회·개설·비우기·해지 데이터를 분리했습니다.

| 테스트 고객 | `X-Customer-Id` | 대표 계좌번호·ID | 확인 용도 |
|---|---:|---|---|
| 1번 고객 | `700000000000000001` | 저금통 `3310000000001` | ACTIVE 계좌 조회·저금통 비우기·동전모으기 |
| 2번 고객 | `700000000000000002` | 입출금계좌 `3333000000003`, 개인사업자통장 `3333000000006` | 가입 가능 조회에서 일반 입출금계좌만 반환되는지 확인 |
| 3번 고객 | `700000000000000003` | 입출금계좌 ID `710000000000000005` | 저금통 신규 개설 |
| 4번 고객 | `700000000000000004` | 저금통 `3310000000002` | 저금통 해지 |

### 3.5 종료와 초기화

애플리케이션은 `Ctrl+C`로 종료할 수 있습니다. MySQL 데이터 볼륨을 보존하면서 컨테이너만 종료하려면
다음 명령을 실행합니다.

```bash
docker compose down
```

다음 명령은 MySQL 볼륨과 저장 데이터를 함께 삭제하므로 완전히 초기화할 때만 실행해야 합니다.

```bash
docker compose down --volumes
```

### 3.6 로컬 고정 설정

채점자가 별도 환경변수 없이 같은 구성으로 실행할 수 있도록 설정값을 파일에 고정했습니다.

| 항목                       | 값                                | 설정 파일                                 |
| -------------------------- | --------------------------------- | ----------------------------------------- |
| MySQL DB·사용자·비밀번호   | `coinbox` / `coinbox` / `coinbox` | `docker-compose.yml`                      |
| MySQL root 비밀번호        | `root`                            | `docker-compose.yml`                      |
| MySQL·애플리케이션 포트    | `3306` / `8080`                   | `docker-compose.yml` / Spring Boot 기본값 |
| Hibernate 스키마 전략      | `create`                          | `application.yaml`                        |
| Spring Batch 스키마 초기화 | `always`                          | `application.yaml`                        |
| Snowflake 노드 ID          | `0`                               | `application.yaml`                        |

`ddl-auto=create`를 사용하므로 애플리케이션을 시작할 때 업무 테이블과 기존 데이터가 재생성되고
`data.sql`의 샘플 데이터가 다시 입력됩니다. 데이터를 보존해야 하는 운영 환경에서는 Flyway 또는
Liquibase 같은 스키마 마이그레이션 도구와 `validate` 전략이 필요하지만 현재 과제 범위에서는
제외했습니다.

## 4. API 구성

고객 온라인 API의 Base URL은 `/api/v1`입니다. 인증 서버는 과제 범위에서 제외했으므로 인증 계층이 검증한
고객 ID를 `X-Customer-Id` 헤더로 전달한다고 가정했습니다. 계좌번호는 하이픈 없는 13자리 숫자로
관리했고, Snowflake ID는 JavaScript 정밀도 손실을 방지하기 위해 JSON 문자열로 반환했습니다. 배치와
로컬 테스트용 잔고 충전은 고객 API와 구분한 `/internal/v1` 경로에서 수동 실행할 수 있습니다.

| 메서드   | 경로                                      | 기능                                    |
| -------- | ----------------------------------------- | --------------------------------------- |
| `GET`    | `/api/v1/accounts`                        | 고객의 ACTIVE 계좌를 조회합니다.        |
| `GET`    | `/api/v1/coinboxes/eligible-accounts`     | 저금통 가입 가능 근거계좌를 조회합니다. |
| `POST`   | `/api/v1/coinboxes`                       | 저금통을 신규 개설합니다.               |
| `POST`   | `/api/v1/coinboxes/{accountNumber}/empty` | 저금통 잔액 전액을 비웁니다.            |
| `POST`   | `/internal/v1/batches/daily-balance`      | 일별 최종 잔액 배치를 수동 실행합니다.  |
| `POST`   | `/internal/v1/batches/coin-saving`        | 동전모으기 배치를 수동 실행합니다.      |
| `DELETE` | `/api/v1/coinboxes/{accountNumber}`       | 저금통을 해지합니다.                    |
| `POST`   | `/internal/v1/test-account-deposits`      | 입출금계좌 잔고를 테스트용으로 증가시킵니다. |
| `POST`   | `/internal/v1/test-account-withdrawals`   | 입출금계좌 잔고를 테스트용으로 감소시킵니다. |

상세 요청·응답, 업무 검증과 오류 코드는 [API와 예외 처리](docs/05_API.md)에 정리했습니다.

### 4.1 테스트 전용 계좌 잔고 조정

`POST /internal/v1/test-account-deposits`는 테스트 데이터 준비를 위해 입출금계좌의 `ACCOUNT.balance`만
증가시킵니다. 계좌번호와 양수 금액을 요청하며 저금통 등 다른 상품 유형은 거부합니다. 실제 입금이나
이체 업무가 아니므로 `FINANCIAL_TRANSACTION`과 `ACCOUNT_ENTRY`는 생성하지 않고, 계좌 상태 검증과
비관적 잠금도 적용하지 않습니다. 운영 환경에 노출해서는 안 되는 로컬 테스트 전용 API입니다.

```json
{
  "accountNumber": "3333000000003",
  "amount": 10000
}
```

`POST /internal/v1/test-account-withdrawals`도 같은 범위에서 입출금계좌 잔고만 감소시킵니다. 현재 잔고를
초과하는 요청은 `INSUFFICIENT_ACCOUNT_BALANCE`로 거부하여 음수 잔고를 만들지 않습니다.

```json
{
  "accountNumber": "3333000000004",
  "amount": 5000
}
```

## 5. 배치 구성

`spring.batch.job.enabled=false`를 적용했으므로 애플리케이션 시작만으로 Job이 즉시 실행되지는 않습니다.
실행 중인 서버의 스케줄러가 서울 시간 기준으로 다음 Job을 시작합니다.

| Job            | 일정                  | 처리 기준                                                  |
| -------------- | --------------------- | ---------------------------------------------------------- |
| 일별 최종 잔고 | 매일 `00:00`          | 실행일의 전날 잔고를 `ACCOUNT_DAILY_BALANCE`에 저장합니다. |
| 동전모으기     | 월요일~금요일 `10:00` | 공휴일 여부와 무관하게 전날 잔고 기준으로 저축합니다.      |

동일 기준일의 재실행은 데이터 유일 키를 이용해 중복 잔고와 중복 이체를 방지했습니다. 운영자용 수동
실행 API는 `executionDate`를 필수로 받으며 Job 조회·중지·재시작 API는 과제 범위에서 제외했습니다.

실행 중인 애플리케이션에서 다음 요청으로 두 Job을 즉시 실행할 수 있습니다. `data.sql`의 전일 잔액은
DB의 현재 날짜를 기준으로 생성되므로 동전모으기 확인 시 `executionDate`에는 실행 당일을 입력합니다.

```http
POST /internal/v1/batches/daily-balance?executionDate=2026-08-30
POST /internal/v1/batches/coin-saving?executionDate=2026-08-30
```

Swagger UI의 `배치 수동 실행` 항목에서도 같은 요청을 실행할 수 있습니다. 동일 실행일을 다시 지정해도
`ACCOUNT_DAILY_BALANCE`와 `COIN_SAVING_EXECUTION`의 복합 UK 및 잠금 후 재검증으로 업무 데이터가
중복 반영되지 않습니다. `/internal` 경로에는 현재 인증·인가가 구현되어 있지 않으므로 과제의 로컬
검증 용도로만 사용하며 운영 환경에서는 관리자 접근 제어가 필요합니다.

## 6. 테스트와 검증 결과

전체 Repository·통합 테스트는 Compose로 실행한 로컬 MySQL이 아니라 테스트마다 격리된 MySQL
Testcontainers를 사용합니다. 따라서 로컬 `localhost:3306/coinbox`의 데이터에 접근하지 않습니다.

전체 테스트는 다음 명령으로 실행할 수 있습니다.

```bash
./gradlew clean test
```

JaCoCo 커버리지 기준까지 포함한 최종 검증은 다음 명령으로 실행할 수 있습니다.

```bash
./gradlew clean check
```

최종 검증에서 테스트 126개가 모두 통과했습니다. JaCoCo line `91.06%`, branch `70.51%`로 프로젝트
검증 기준인 line `80%`, branch `70%`를 통과했습니다.

| 결과          | 위치                                             |
| ------------- | ------------------------------------------------ |
| 테스트 리포트 | `build/reports/tests/test/index.html`            |
| JaCoCo HTML   | `build/reports/jacoco/test/html/index.html`      |
| JaCoCo XML    | `build/reports/jacoco/test/jacocoTestReport.xml` |

## 7. 주요 설계 선택과 트레이드오프

| 선택                                              | 적용 이유                                                                                  | 감수한 트레이드오프                                                                         |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| `ACCOUNT.product_type` 비정규화                   | 현재 계좌 유형을 계약 조인 없이 빠르게 판단하고 일반 계좌 업무를 단순화했습니다.           | 계약 상품과 값이 어긋나지 않도록 같은 트랜잭션에서 관리해야 합니다.                         |
| `PRODUCT`–`PRODUCT_VERSION`–`COINBOX_POLICY` 분리 | 상품군, 계약 당시 버전과 저금통 전용 한도 정책의 책임을 분리했습니다.                      | 과제 규모에 비해 테이블과 조인이 늘어나며 운영자용 버전 관리 기능이 별도로 필요합니다.      |
| `ACCOUNT_CONTRACT.product_version_id` 저장        | 가입 당시 적용된 정책을 확정하고 향후 정책 변경이 기존 계약을 임의로 바꾸지 않게 했습니다. | 최신 정책을 적용하려면 명시적인 계약 전환 절차가 필요합니다.                                |
| `FINANCIAL_TRANSACTION`과 `ACCOUNT_ENTRY` 분리    | 하나의 금융 이벤트와 계좌별 방향·거래 전후 잔액·적요를 각각 추적할 수 있게 했습니다.       | 단순 이체도 최소 세 행을 기록하므로 저장 비용이 증가합니다.                                 |
| 객체 연관관계 대신 `Long` 참조                    | 잠금 SQL과 조회 범위를 명시하고 예상하지 못한 지연 로딩을 피했습니다.                      | 객체 탐색 편의성이 줄고 논리 참조 무결성을 Service에서 적극적으로 검증해야 합니다.          |
| 비관적 잠금과 고정 잠금 순서                      | 잔액 변경과 중복 가입처럼 충돌 가능성이 높은 쓰기의 최종 상태를 직렬화했습니다.            | 잠금 대기 시간이 생기므로 조회 단계에는 사용하지 않고 업무 트랜잭션을 짧게 유지해야 합니다. |
| 사전 후보 조회 후 후보별 재검증                   | 배치가 전체 업무 테이블을 잠그지 않으면서 처리량과 정합성을 함께 확보했습니다.             | 후보 조회 SQL과 잠금 후 검증 규칙을 함께 유지해야 합니다.                                   |
| 동전모으기 정책 Step 범위 캐시                   | 매 배치 실행은 정책을 새로 읽되, 같은 실행 안에서 동일 상품 버전 정책을 후보마다 반복 조회하지 않습니다. | 정책이 실행 중 변경되어도 해당 Step은 최초 조회한 불변 스냅샷을 사용합니다.                 |
| 상위 업무에서 획득한 계좌 잠금 재사용            | 동전모으기와 해지는 이미 잠근 계좌를 공통 이체에 전달해 동일 행의 재조회·재잠금을 제거했습니다. | 잠금 재사용 메서드는 반드시 기존 트랜잭션 안에서 호출해야 하므로 `MANDATORY` 경계를 지켜야 합니다. |
| DB UK 기반 멱등성                                 | 애플리케이션 사전 조회 사이의 경쟁도 DB가 최종적으로 차단하게 했습니다.                    | 유일 키 충돌을 정상적인 중복 결과와 시스템 오류로 구분하는 처리가 필요합니다.               |
| 일별 잔액 Tasklet과 JDBC 일괄 저장                | 한 기준일의 단순 스냅샷을 Chunk 객체 흐름보다 간결하게 일괄 처리했습니다.                  | 데이터가 매우 커지면 단일 Step의 메모리와 트랜잭션 크기를 다시 설계해야 합니다.             |
| 동전모으기 Chunk 크기 1                           | 한 저금통의 실패가 앞서 완료된 다른 저금통 결과를 되돌리지 않게 했습니다.                  | 트랜잭션 수가 늘어 처리량이 낮아질 수 있습니다.                                             |

## 8. 구현하며 배운 점

### 8.1 잠금은 조회 문법보다 업무 순서가 중요했습니다

조인 조회에도 비관적 잠금을 적용할 수 있지만 실제로 어떤 테이블의 몇 행이 잠기는지는 실행 계획과
DBMS 동작에 영향을 받습니다. 이번 구현에서는 잠금 대상을 명확히 하기 위해 핵심 Entity를 별도 쿼리로
조회했고, 여러 계좌는 항상 ID 오름차순으로 잠갔습니다. 잠금 전 조회값은 화면 표시나 빠른 탈락에만
사용했고 잔액과 상태의 최종 판단은 잠금 후 다시 수행했습니다.

### 8.2 애플리케이션 검증만으로 멱등성을 보장할 수 없었습니다

`존재 여부 조회 → 저장` 사이에는 다른 트랜잭션이 끼어들 수 있습니다. 따라서
`ACCOUNT_DAILY_BALANCE(account_id, balance_date)`와
`COIN_SAVING_EXECUTION(coinbox_id, execution_date)`에 복합 UK를 적용했고, 애플리케이션 조회는
불필요한 작업을 줄이는 1차 방어로 사용했습니다. 사전 검증과 DB 제약이 서로 다른 경쟁 구간을 막는
보완 관계라는 점을 확인했습니다.

### 8.3 계좌 상태와 계약 상태의 책임이 달랐습니다

일반 당행 이체에서는 두 `ACCOUNT`의 거래 가능 상태만 검증했습니다. 상품 계약과 상품 유형은
비우기·동전모으기·해지처럼 업무 문맥을 판별하는 상위 Service에서 검증했습니다. 공통 이체 Service가
모든 상품 규칙을 알게 하면 재사용성이 낮아지고, 반대로 업무 Service가 계좌 잠금과 원장 생성을
반복하면 금융 정합성 규칙이 분산됩니다. 두 책임을 분리하면서 검증 위치가 더 명확해졌습니다.

### 8.4 배치는 후보 조회와 금융 처리를 분리해야 했습니다

후보 조회에서 모든 조건을 확정하려고 잠금을 적용하면 페이지 전체의 잠금 시간이 길어집니다. 반대로
후보 조회 결과만 신뢰하면 처리 시점의 계좌 상태 변경을 놓칠 수 있습니다. 잠금 없는 페이징 Reader로
후보를 줄이고, Writer가 호출하는 Service에서 후보 한 건씩 잠금·재검증·이체하도록 구성해 처리량과
정합성의 균형을 맞췄습니다.

### 8.5 테스트 DB의 재현성도 설계의 일부였습니다

로컬 MySQL에 직접 의존한 테스트는 개발자 환경과 데이터 상태에 따라 결과가 달라질 수 있습니다.
Repository와 통합 테스트를 MySQL Testcontainers로 통일하면서 Native Query, 비관적 잠금과 MySQL UK
동작을 실제와 같은 엔진에서 반복 검증할 수 있었습니다. 단위 테스트, Controller 테스트와 실제 DB
테스트의 책임을 분리해 전체 실행 시간도 관리했습니다.

## 9. 잘한 점

- ERD, 시퀀스 다이어그램과 예시 데이터의 단계 ID를 연결해 설계 판단과 데이터 변화를 추적할 수 있게
  했습니다.
- `InternalTransferService`에 잔액 변경, 금융거래와 양방향 원장 생성을 집중해 비우기·동전모으기·해지가
  같은 금융 규칙을 사용하게 했습니다.
- 고객 잠금, 계좌 ID 오름차순 잠금과 잠금 후 재검증을 동시성 테스트로 확인했습니다.
- 상품 기준 정보, 계약 당시 버전과 고객별 동전모으기 설정을 분리해 각 데이터의 변경 이유를 명확하게
  했습니다.
- 모든 ID를 Snowflake `Long`, Enum을 문자열, 계좌번호를 하이픈 없는 13자리 문자열로 일관되게
  관리했습니다.
- OpenAPI 명세와 공개 API 경로·응답 상태를 자동 테스트했고 README만으로 실행·테스트할 수 있게
  정리했습니다.
- 커버리지 제외 범위를 넓히지 않고 주요 Service 분기를 추가로 테스트해 기준을 통과했습니다.

## 10. 현재 한계

### 10.1 데이터베이스 스키마 관리

현재 로컬 설정은 `ddl-auto=create`이며 Entity가 물리 스키마를 생성합니다. `Long` 참조는 ERD의 논리
FK이지만 물리 FK를 생성하지 않았고, 금액·기간·활성 계약 개수에 대한 CHECK 또는 조건부 유일 제약도
없습니다. 운영 환경에서는 Flyway나 Liquibase 기반 버전 관리, 물리 FK와 필요한 CHECK 제약이
필요합니다.

### 10.2 인증과 온라인 요청 멱등성

`X-Customer-Id`는 인증·게이트웨이 계층이 검증해서 전달한다고 가정했고 현재 애플리케이션에서 직접
인증하지 않습니다. 비우기와 해지에는 별도 Idempotency-Key가 없어 네트워크 타임아웃 후 같은 성공
응답을 재생할 수 없습니다. 현재는 최종 상태를 이용해 중복 자금 이동만 방지합니다.

### 10.3 배치 실패 복구

동전모으기는 `SUCCESS`와 `SKIPPED`를 저장하지만 자동 Retry·Skip, 실패한 Job의 메타데이터 기반 재시작
API와 별도 복구 트랜잭션의 `FAILED / SYSTEM_ERROR` 기록은 구현하지 않았습니다. 예상하지 못한 후보
오류는 해당 청크를
Rollback하고 Step을 실패시킵니다. 현재 수동 API는 새 Job 실행만 지원하므로 재시도 가능한 예외 분류와
재시도 소진 후의 운영 절차가 필요합니다.

### 10.4 다중 인스턴스 운영

현재 `@Scheduled`는 애플리케이션 인스턴스마다 실행됩니다. 업무 테이블 UK가 중복 이체를 최종적으로
방어하지만, 불필요한 동시 Job 실행과 메타데이터 경쟁을 줄이려면 분산 스케줄 잠금 또는 전용 배치 실행
노드가 필요합니다. 데이터 규모가 커지면 파티셔닝과 처리량 기준의 청크 크기 재측정도 필요합니다.

### 10.5 금융 도메인 단순화

금액은 단일 원화만 가정한 `Long`이며 이자, 수수료, 거래 취소·정정, 승인과 원장 확정의 분리, 회계일과
영업일 마감은 과제 범위에서 제외했습니다. 일별 잔액도 공식 마감 원장이 아니라 조회 SQL 시작 시점의
일관된 스냅샷입니다. 계좌번호 역시 상품 prefix와 난수 중복 확인만 구현했고 중앙 채번과 체크 디지트는
다루지 않았습니다.

## 11. 다음 개선 우선순위

1. Flyway와 명시적인 DDL을 도입해 물리 FK, CHECK, 인덱스와 스키마 변경 이력을 관리하겠습니다.
2. 인증 주체를 연결하고 온라인 변경 API에 Idempotency-Key 저장·응답 재생 정책을 추가하겠습니다.
3. 배치 예외를 재시도 가능·불가능으로 분류하고 Retry·Skip, 실패 이력과 실패 Job 재시작 절차를
   구현하겠습니다.
4. 다중 인스턴스의 스케줄 중복 실행 방지와 배치 파티셔닝·성능 측정을 추가하겠습니다.
5. 구조화 로그, 메트릭, 분산 추적과 거래·개인정보 마스킹 기준을 운영 수준으로 보강하겠습니다.

## 12. 최종 평가

이번 결과물은 금융 시스템 전체를 재현하기보다 저금통 과제의 핵심인 잔액 정합성, 거래 추적, 동시성
제어와 배치 멱등성을 실행 가능한 범위로 좁혀 구현했습니다. 특히 잠금과 트랜잭션을 문서의 추상적인
표현으로 남기지 않고 MySQL 통합·동시성 테스트로 검증했습니다. 다음 단계에서는 기능 수를 늘리는 것보다
스키마 마이그레이션, 인증·멱등 요청과 배치 복구처럼 운영 실패를 다루는 구조를 우선적으로 보강해야
합니다.

## 13. 문제 해결

- MySQL이 준비되지 않으면 `docker compose ps`에서 상태를 확인하고 `docker compose logs mysql`로
  초기화 로그를 확인해야 합니다.
- `3306` 또는 `8080` 포트가 이미 사용 중이면 `docker-compose.yml`과 `application.yaml`의 관련 값을
  함께 변경해야 합니다.
- 테스트에서 Docker 연결 오류가 발생하면 Docker가 실행 중인지 확인해야 합니다. Compose MySQL을 따로
  시작했더라도 Testcontainers가 Docker에 연결할 수 없으면 테스트가 실행되지 않습니다.
- Java 버전 문제가 발생하면 `./gradlew javaToolchains`로 Gradle이 인식한 Java 21 toolchain을 확인할
  수 있습니다.
