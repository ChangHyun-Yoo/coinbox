# CoinBox Development Checklist

이 문서는 현재 소스와 확정된 설계 문서의 차이를 기록하고, 남은 개발 작업의 순서와 완료 조건을 관리한다.
업무 규칙을 중복해서 정의하지 않으며 다음 문서를 구현 기준으로 사용한다.

- 데이터 모델과 Enum: [`ERD.md`](./ERD.md)
- API와 예외 처리: [`API.md`](./API.md)
- 호출 순서와 트랜잭션: [`SEQUENCE_DIAGRAM.md`](./SEQUENCE_DIAGRAM.md)
- 단계별 데이터 변화: [`DATA_FLOW.md`](./DATA_FLOW.md)
- 목표 클래스 구조: [`CLASS_DIAGRAM.md`](./CLASS_DIAGRAM.md)

---

## 1. 기준선 점검 결과

점검일은 `2026-08-27`이다. 이 단계에서는 구현 코드를 변경하지 않았다.

| 구분 | 현재 상태 | 판단 |
|---|---|---|
| 빌드 | Java 21, Spring Boot 4.0.8, Gradle 9.5.1 구성. `compileJava` 성공 | 기본 프로젝트 구성은 사용 가능 |
| 프로덕션 코드 | Entity, 단순 JPA Repository, 공통 예외 처리, Snowflake만 일부 구현 | 확정 전 초기 모델이 남아 있어 ERD 동기화가 먼저 필요 |
| 온라인 업무 | Controller, DTO, Service와 이체 로직 없음 | 미구현 |
| 배치 | Spring Batch 의존성과 Job·Step 구현 없음 | 미구현 |
| 테스트 | 총 8개 중 6개 성공, Repository 테스트 2개 실패 | 로컬 MySQL 직접 의존을 제거해야 함 |
| 테스트 커버리지 | JaCoCo 설정 없음 | 미구현 |
| API 문서 | Swagger/OpenAPI 의존성과 설정 없음 | 미구현 |
| 실행 환경 | MySQL Docker Compose는 있으나 README가 비어 있음 | 실행 안내와 상태 확인 보완 필요 |

### 1.1 테스트 기준선

`./gradlew test` 실행 결과는 다음과 같다.

- 전체 8개 테스트 중 6개 성공
- `CustomerRepositoryTest`, `FinancialTransactionRepositoryTest` 실패
- 실패 원인: 테스트가 `localhost:3306`의 MySQL에 직접 연결하며 실행 시 DB가 준비되어 있지 않음
- 컴파일 오류나 단위 테스트 실패가 아니라 테스트 DB 환경의 재현성 문제

따라서 이후 기능 개발 전에 MySQL Testcontainers 기반을 구성해 로컬 DB 실행 여부와 무관하게
Repository·통합 테스트가 같은 조건에서 실행되도록 한다. MySQL의 비관적 잠금, 복합 UK와 native query를
검증해야 하므로 H2로 대체하지 않는다.

---

## 2. 데이터 모델 차이

### 2.1 수정이 필요한 기존 모델

- [ ] `Customer`에서 `customerType`을 제거하고 `CustomerType`을 삭제한다.
- [ ] `CustomerStatus.SUSPENDED`를 문서의 `RESTRICTED`로 변경한다.
- [ ] `Account`에 `ProductType productType`, `LocalDate accountOpenDate`를 추가한다.
- [ ] `AccountStatus.BLOCKED`를 문서의 `RESTRICTED`로 변경한다.
- [ ] `Account`에 잔액 증감과 해지를 위한 업무 메서드를 추가한다.
- [ ] `AccountContract.contractType`과 `ContractType`을 제거하고 `productVersionId`를 추가한다.
- [ ] 활성 계약의 `contractEndDate`를 `9999-12-31`로 생성하고 실제 해지일로 종료할 수 있게 한다.
- [ ] Java 클래스명을 클래스 다이어그램과 일치하는 `CoinBox`로 정리한다.
- [ ] `CoinBox.accountContractId`를 `accountId`로 변경한다.
- [ ] `CoinBox.maxAmount`와 과제 범위 밖의 자동저축·브랜드 캐시백 설정을 제거한다.
- [ ] `coinSavingEnabledDate`를 `coinSavingStartDate`로 변경하고 해지 시 설정을 종료할 수 있게 한다.
- [ ] `TransactionType`은 `TRANSFER`만 유지한다.
- [ ] `AccountEntry`에 `transactionDatetime`, `entryDescription`을 추가한다.
- [ ] `EntryCode`에 `COINBOX_EMPTY`, `COINBOX_TERMINATION`을 추가한다.
- [ ] `AccountDailyBalance`에 `(account_id, balance_date)` 복합 UK를 적용한다.
- [ ] `CoinSavingExecution.baseBalance`를 제거한다.
- [ ] `CoinSavingFailureReason`을 `CoinSavingReasonCode`로 변경하고 문서의 Enum 값으로 동기화한다.
- [ ] `CoinSavingExecution`에 `(coinbox_id, execution_date)` 복합 UK와 `transaction_id` 단일 UK를 적용한다.

### 2.2 새로 필요한 모델

- [ ] `Product`, `ProductType`, `ProductRepository`
- [ ] `ProductVersion`, `ProductVersionRepository`
- [ ] `CoinBoxPolicy`, 정책 조회 전용 Repository 또는 Query Repository

### 2.3 유지 가능한 기반

- [x] 모든 PK를 애플리케이션에서 생성하는 `Long`으로 선언한 구조
- [x] 객체 연관관계 대신 FK를 `Long`으로 보관하는 구조
- [x] Enum에 `EnumType.STRING`을 적용하는 원칙
- [x] 금액을 `Long`으로 관리하는 원칙
- [x] `BaseEntity.createdDatetime`, `updatedDatetime` JPA Auditing
- [x] Snowflake 생성기와 단일·동시성 기본 테스트
- [x] `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`, `ErrorResponse`의 기본 골격

---

## 3. 기능 구현 차이

### 3.1 공통 금융 기능

- [ ] 두 계좌를 `account_id` 오름차순으로 비관적 잠금하는 Repository 조회
- [ ] 두 계좌의 존재·상태·잔액을 잠금 후 검증하는 당행 이체 Service
- [ ] `FINANCIAL_TRANSACTION` 한 행과 `ACCOUNT_ENTRY` 두 행 생성
- [ ] 두 계좌 잔액, 금융거래와 원장을 한 트랜잭션으로 Commit·Rollback
- [ ] 업무별 `TransferLedgerSpec`과 `TransferResult`

### 3.2 온라인 저금통

- [ ] 가입 가능한 근거계좌 조회
- [ ] 고객·선택 계좌 잠금 후 저금통 개설
- [ ] 저금통 비우기와 전액 당행 이체
- [ ] 잔액 이전을 포함한 저금통 해지
- [ ] Controller, 요청·응답 DTO와 Bean Validation
- [ ] `API.md`와 일치하는 오류 코드·메시지 정비

### 3.3 배치

- [ ] Spring Batch 의존성 및 메타데이터 설정
- [ ] 일별 최종 잔액 Tasklet과 JdbcTemplate 일괄 저장
- [ ] 동전모으기 `JdbcPagingItemReader`
- [ ] 후보별 잠금·재검증과 금액 계산 Service
- [ ] `SUCCESS`, `SKIPPED` 실행 이력 및 복합 UK 기반 멱등성
- [ ] 월요일부터 금요일 오전 10시 실행 스케줄

---

## 4. 테스트 전략과 완료 조건

테스트는 구현이 끝난 뒤 한꺼번에 추가하지 않고 각 기능과 같은 단계에서 작성한다.

| 테스트 종류 | 대상 | 완료 조건 |
|---|---|---|
| 단위 테스트 | Service의 분기, 금액 계산, 원장 사양, 예외 선택 | 외부 저장소는 Mock으로 격리하고 given-when-then 및 한글 `DisplayName` 사용 |
| Repository 테스트 | native query, 비관적 잠금, UK·인덱스 | MySQL Testcontainers에서 실제 쿼리와 제약 검증 |
| Controller 테스트 | 입력 검증, 상태 코드, 성공·오류 JSON | MockMvc 기반으로 Service를 격리 |
| 통합 테스트 | 가입·비우기·해지·배치의 트랜잭션과 데이터 결과 | MySQL에서 관련 테이블의 최종 상태와 Rollback 검증 |
| 동시성 테스트 | 중복 가입, 계좌 이체, 동일 저금통 배치 실행 | 중복 생성·잔액 유실·교착 상태가 없음을 검증 |

JaCoCo 최종 기준은 전체 line 80%, branch 70%를 기본값으로 사용한다. 단순 설정·DTO를 무리하게
테스트해 수치만 높이지 않고 Service와 금액 계산 같은 핵심 업무 코드의 분기 검증을 우선한다.

---

## 5. 이후 개발 순서

### 2단계 — 반복 가능한 테스트 기반과 데이터 모델 동기화

- [ ] Testcontainers MySQL 테스트 기반을 먼저 구성해 기존 Repository 테스트를 통과시킨다.
- [ ] 2절의 Entity·Enum·제약을 `ERD.md`와 동기화한다.
- [ ] 새 Product 관련 Entity와 Repository를 추가한다.
- [ ] 각 Repository의 저장 및 DB 제약 테스트를 작성한다.
- [ ] 완료 조건: `./gradlew test`가 외부 MySQL 없이 성공한다.

### 3단계 — 공통 예외와 당행 이체

- [ ] `API.md` 기준으로 `ErrorCode`를 정비한다.
- [ ] 잠금 조회와 `InternalTransferService`를 구현한다.
- [ ] 성공, 잔액 부족, 거래 불가, Rollback과 동시성 테스트를 작성한다.

### 4단계 — 온라인 저금통 API

- [ ] 가입 가능 계좌 조회, 개설, 비우기, 해지를 순서대로 구현한다.
- [ ] 각 Service 단위 테스트와 Controller 테스트를 같은 작업에서 작성한다.
- [ ] 주요 흐름을 MySQL 통합 테스트로 검증한다.

### 5단계 — 배치

- [ ] 일별 최종 잔액 Tasklet을 구현하고 멱등 재실행을 검증한다.
- [ ] 동전모으기 Chunk Job을 구현하고 계산 경계값·실행 이력·후보별 Rollback을 검증한다.

### 6단계 — 통합·동시성 보강

- [ ] 문서의 정상·예외 흐름을 End-to-End 수준의 통합 테스트로 연결한다.
- [ ] 잠금 순서와 복합 UK가 실제 동시 요청에서 최종 정합성을 보장하는지 검증한다.

### 7단계 — Swagger/OpenAPI

- [ ] Springdoc OpenAPI를 추가한다.
- [ ] 요청·응답 DTO, 공통 오류 형식과 인증 고객 식별 방식이 Swagger UI에 드러나게 한다.
- [ ] `API.md`와 실제 OpenAPI 명세의 경로·상태 코드를 대조한다.

### 8단계 — JaCoCo

- [ ] XML·HTML 리포트와 `jacocoTestCoverageVerification`을 구성한다.
- [ ] line 80%, branch 70% 기준을 빌드 검증에 연결한다.
- [ ] 제외 대상은 기술적 이유와 함께 최소한으로 명시한다.

### 9단계 — 실행과 제출 환경

- [ ] Docker Compose에 MySQL healthcheck를 추가한다.
- [ ] 실행 환경변수와 로컬 기본값을 정리한다.
- [ ] README에 빌드, 테스트, 커버리지, 애플리케이션, Swagger와 배치 실행 방법을 작성한다.
- [ ] 새 환경에서 README만 따라 전체 실행이 가능한지 확인한다.

### 10단계 — 최종 검토와 회고

- [ ] 구현과 ERD·API·시퀀스·클래스 다이어그램의 차이를 최종 점검한다.
- [ ] `RETROSPECTIVE.md`에 배운 점, 선택의 근거, 한계와 개선 방향을 작성한다.
- [ ] 전체 테스트와 커버리지 검증을 통과한 상태로 제출본을 정리한다.

---

## 6. 1단계 완료 판단

- [x] 현재 파일과 패키지 구조 확인
- [x] 빌드 의존성 확인
- [x] 문서와 Entity·Enum 차이 확인
- [x] 온라인·배치 미구현 범위 확인
- [x] 기존 테스트 실행 및 실패 원인 확인
- [x] 테스트·Swagger·JaCoCo·실행 문서의 적용 시점 결정
- [x] 다음 단계의 선행 작업과 완료 조건 확정
