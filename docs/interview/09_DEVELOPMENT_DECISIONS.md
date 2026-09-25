# 9. 개발 선택 색인과 보충 질문

[전체 목차](README.md)

최종 구현을 기준으로 '왜 이 방법을 선택했습니까?'라는 질문을 준비하는 자료입니다. 기존 답변을 다시 싣지 않고 찾아갈 위치를 정리했으며, 기존 자료에 부족했던 선택만 S01~S12로 보충했습니다. Snowflake와 Tasklet·Chunk의 대안 비교는 각각 기존 D16, B03~B05에 보충했습니다.

설명 가능한 설계 근거와 실제 개발 당시의 개인적인 동기는 다를 수 있습니다. 답변 초안은 본인의 사실에 맞게 사용합니다. 중간 변경 이력, 측정하지 않은 성능 수치, 구현하지 않은 운영 기능은 포함하지 않습니다.

## 9.1 이미 정리된 선택: 답변 위치

질문 번호는 연결한 문서 안의 번호입니다. 이 표는 별도의 암기 목록이 아니라 기존 답변을 찾기 위한 색인입니다.

### 9.1.1 모델·식별자·조회

| 면접에서 물을 수 있는 선택 | 기존 답변 |
|---|---|
| 저금통을 독립 계좌로 만들고 설정을 분리한 이유 | [데이터 모델 D01·D06](03_DATA_MODEL_QA.md) |
| 계좌와 계약의 책임·상태를 분리한 이유 | [데이터 모델 D02·D05](03_DATA_MODEL_QA.md) |
| 상품·상품 버전·정책을 나누고 계약 버전으로 한도를 정하는 이유 | [데이터 모델 D03·D07·D08](03_DATA_MODEL_QA.md), [과도한 설계 여부 R08](07_DESIGN_REVIEW_AND_MOCK_INTERVIEW.md) |
| ACCOUNT.product_type과 현재 잔액을 중복 보관한 이유 | [데이터 모델 D04·D10](03_DATA_MODEL_QA.md) |
| 거래와 계좌 원장을 나누고 before·after·적요를 저장한 이유 | [데이터 모델 D09·D11](03_DATA_MODEL_QA.md) |
| PK와 업무 UK의 분리, 복합 UK, nullable 거래 참조 | [데이터 모델 D12·D13·D18](03_DATA_MODEL_QA.md) |
| 객체 연관관계 대신 ID 참조, 물리 FK의 현재 보장 범위 | [데이터 모델 D14](03_DATA_MODEL_QA.md) |
| 금액 Long, 계좌번호 String, EnumType.STRING, 무기한 종료일 | [데이터 모델 D15·D17](03_DATA_MODEL_QA.md) |
| Snowflake와 AUTO_INCREMENT·UUID 비교, JSON ID 문자열 | [데이터 모델 D16](03_DATA_MODEL_QA.md), [선할당 ID와 synchronized J09·J15](06_TECH_TEST_OPERATIONS_QA.md) |
| JPA 변경 감지와 Native Query를 조합한 이유 | [기술 J03·J04·J07·J08](06_TECH_TEST_OPERATIONS_QA.md) |
| 조회 projection·record, EXISTS, 계좌 목록 Map 조립 | [기술 J05·J06·J11·J12](06_TECH_TEST_OPERATIONS_QA.md) |
| 인덱스·조회 수 최적화의 판단 기준 | [기술 J10·J24](06_TECH_TEST_OPERATIONS_QA.md) |

### 9.1.2 트랜잭션·배치

| 면접에서 물을 수 있는 선택 | 기존 답변 |
|---|---|
| 고객 잠금으로 고객당 한 개를 관리하고 근거계좌도 잠그는 이유 | [동시성 T03~T05](04_TRANSACTION_CONCURRENCY_QA.md), [DB UK 미사용 R01](07_DESIGN_REVIEW_AND_MOCK_INTERVIEW.md) |
| 비관적 잠금과 낙관적 잠금·조건부 UPDATE 비교 | [동시성 T01·T06·T07](04_TRANSACTION_CONCURRENCY_QA.md) |
| 계좌 ID 순서 잠금과 잠금 범위·교착 가능성 | [동시성 T08~T10·T20](04_TRANSACTION_CONCURRENCY_QA.md) |
| 공통 이체와 상품별 검증의 경계, 계약 상태 검사 범위 | [동시성 T11·T12](04_TRANSACTION_CONCURRENCY_QA.md) |
| 이미 잠근 객체 전달, MANDATORY·REQUIRED와 롤백 | [동시성 T13~T15·T19](04_TRANSACTION_CONCURRENCY_QA.md) |
| 잠금 후 객체 최신성, 격리 수준, 온라인 요청 멱등성의 한계 | [동시성 T16~T18](04_TRANSACTION_CONCURRENCY_QA.md) |
| Spring Batch와 스케줄러의 역할 분리, 수동 실행 | [배치 B01·B02·B19](05_BATCH_QA.md) |
| 잔액 Tasklet·동전모으기 Chunk, JDBC 저장, Processor 미사용 | [배치 B03~B05](05_BATCH_QA.md) |
| 후보를 먼저 읽고 다시 검증하는 이유, 페이징·정렬 키 | [배치 B06~B09](05_BATCH_QA.md) |
| 페이지 1,000과 청크 1, 한 저금통 단위 커밋 | [배치 B08·B10·B20](05_BATCH_QA.md) |
| 정책을 버전별·Step별로 캐시하고 계좌 상태는 캐시하지 않는 이유 | [배치 B11~B14](05_BATCH_QA.md) |
| 전일 잔액과 현재 잔액의 역할, 가입 다음 날부터의 대상 조건 | [배치 B15·B16](05_BATCH_QA.md) |
| 실행 이력 UK와 Batch 메타데이터를 함께 쓰는 이유 | [배치 B17·B18](05_BATCH_QA.md) |
| SKIPPED·EXCLUDED·FAILED 구분, 중단·재실행 범위 | [배치 B19~B22](05_BATCH_QA.md) |
| 잔액 중복 저장 시 덮어쓰지 않는 이유와 마감 한계 | [배치 B23·B24](05_BATCH_QA.md), [업무 흐름 2.4](02_BUSINESS_FLOWS.md) |

### 9.1.3 프레임워크·테스트·실행 환경

| 면접에서 물을 수 있는 선택 | 기존 답변 |
|---|---|
| Spring DI·생성자 주입·Lombok·readOnly 기본값 | [기술 J01·J02](06_TECH_TEST_OPERATIONS_QA.md) |
| Java 21·버전 명시·Gradle Wrapper와 JVM 질문 | [기술 J14](06_TECH_TEST_OPERATIONS_QA.md) |
| 단위·Repository·통합·E2E 테스트를 나눈 이유 | [기술 J16·J18](06_TECH_TEST_OPERATIONS_QA.md) |
| MySQL Testcontainers와 Compose DB를 나눈 이유 | [기술 J17·J20](06_TECH_TEST_OPERATIONS_QA.md) |
| JaCoCo line·branch 지표와 빌드 기준 | [기술 J19](06_TECH_TEST_OPERATIONS_QA.md) |
| 고정 로컬 설정, JPA create 후 data.sql, 재실행 편의와 비용 | [기술 J21](06_TECH_TEST_OPERATIONS_QA.md) |
| 공통 오류 응답, 신뢰 헤더, 내부·테스트 API의 보안 한계 | [기술 J22](06_TECH_TEST_OPERATIONS_QA.md), [테스트 잔고 변경과 원장 D10](03_DATA_MODEL_QA.md) |
| Redis·Kafka를 사용하지 않은 이유와 도입 조건 | [기술 J23](06_TECH_TEST_OPERATIONS_QA.md) |

## 9.2 S01. 왜 하나의 애플리케이션과 관계형 DB로 구성했습니까?

**답변 초안**

> 저금통 업무에서는 두 계좌 잔액과 금융거래·원장을 함께 확정하는 것이 중요했습니다. 현재 범위에서는 하나의 관계형 DB 트랜잭션으로 묶는 구성이 요구를 직접 표현하고, 애플리케이션과 DB를 각각 실행하는 단순한 구성이 과제 확인에도 적합하다고 판단했습니다.

마이크로서비스와 서비스별 DB로 나누면 독립 배포·확장의 여지를 얻지만 자금 이동의 원자성을 그대로 유지할 수 있는지부터 다시 설계해야 합니다. 분산 트랜잭션 또는 보상·중복·실패 복구 문제가 생깁니다. 단일 구조가 무조건 더 우수한 것이 아니라, 현재 요구에 서비스 간 분리가 필요한 근거가 부족합니다.

현재는 여러 Gradle 모듈이나 독립 배포 서비스가 아닌 단일 프로젝트입니다. 패키지를 나눴다는 이유로 강제된 모듈 경계나 MSA라고 설명하지 않습니다. 기술 미사용 판단은 [J23](06_TECH_TEST_OPERATIONS_QA.md)과 연결합니다.

근거: [빌드 구성](../../build.gradle), [공통 이체 서비스](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java).

## 9.3 S02. 왜 업무별 패키지 안에 Controller·Service·Repository를 두었습니까?

**답변 초안**

> 계좌·계약·저금통·동전모으기처럼 업무 단위로 관련 코드를 찾을 수 있게 묶었습니다. 그 안에서는 Controller가 HTTP 변환, Service가 업무 순서와 트랜잭션, Repository가 조회·저장을 담당하도록 나눴습니다.

계층별로 모든 Controller와 모든 Service를 한곳에 모으는 구조도 작은 프로젝트에서는 간단합니다. 현재 구조는 업무 탐색에 유리한 대신 여러 업무를 연결하는 서비스의 의존성이 커지지 않도록 관리해야 합니다. 패키지 이름만으로 DDD나 헥사고날 아키텍처의 의존성 규칙을 모두 적용한 것은 아닙니다.

Controller에서 바로 Repository를 호출하면 간단한 조회는 짧아질 수 있지만, 가입·이체 같은 여러 테이블의 변경 순서와 트랜잭션을 HTTP 계층에 흩뜨리게 됩니다. 온라인 호출과 배치가 함께 사용하는 금융 처리는 서비스로 모았습니다.

근거: [업무 패키지](../../src/main/java/com/kakaobank/coinbox), [클래스 다이어그램](../04_CLASS_DIAGRAM.md).

## 9.4 S03. 왜 엔티티에 공개 Setter 대신 create·debit·credit·close를 두었습니까?

**답변 초안**

> 잔액과 상태를 임의로 덮어쓰는 대신 출금·입금·해지라는 행위로 변경 의도를 드러내려고 했습니다. Account 내부에서 양수 금액, 출금 가능 잔액, 해지 전 잔액 0원 같은 해당 객체의 규칙을 확인합니다. 서비스는 여러 객체의 관계와 처리 순서를 조정합니다.

`@Getter`와 보호된 기본 생성자를 사용하고 생성은 정적 팩터리로 제공합니다. 범용 Setter나 `@Data`로 변경 경로를 넓히는 대신 업무 메서드를 작성하는 비용을 감수했습니다. 서비스에도 일부 거래 조건 검사가 있지만, 서비스는 업무 오류로 설명하고 엔티티는 자신의 상태 변경을 방어한다는 책임 차이가 있습니다.

이 메서드가 DB 잠금까지 획득하는 것은 아닙니다. 잠금과 트랜잭션은 호출 측 책임이며, 직접 SQL은 엔티티 검증을 우회합니다. 모든 엔티티의 모든 불변식을 완전히 강제했다고 설명하지 않습니다.

근거: [Account](../../src/main/java/com/kakaobank/coinbox/account/entity/Account.java), [InternalTransferService](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java).

## 9.5 S04. 왜 저축 금액 계산을 별도 클래스로 분리했습니까?

**답변 초안**

> 잔돈·현재 잔액·한도로 실제 저축 금액을 결정하는 계산은 DB나 잠금 없이 검증할 수 있습니다. 계산기를 분리해 한도 직전·도달·초과와 1,000원 경계값을 작은 단위 테스트로 확인하고, 서비스는 조회·잠금·이체·이력 저장에 집중하게 했습니다.

서비스의 private 메서드로 두면 클래스 수는 줄지만 계산을 확인할 때 서비스 의존성까지 준비하게 됩니다. 반대로 규칙이 거의 없다면 별도 클래스가 과할 수 있습니다. 이 과제에서는 여러 경계 조건을 독립적으로 검증할 이유가 있습니다.

계산기는 금액 또는 업무상 건너뜀 사유를 반환합니다. 잔돈 없음처럼 예상 가능한 결과를 시스템 오류와 같은 예외로 처리하지 않습니다. 계산식과 SKIPPED 처리의 상세 설명은 [B15·B21](05_BATCH_QA.md)을 사용합니다.

근거: [계산기](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingAmountCalculator.java), [경계값 테스트](../../src/test/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingAmountCalculatorTest.java).

## 9.6 S05. 왜 공통 이체에 TransferLedgerSpec을 전달합니까?

**답변 초안**

> 동전모으기·비우기·해지는 같은 자금 이동을 사용하지만 출금·입금 원장의 코드와 적요가 다릅니다. 업무별 설명을 하나의 값 객체로 묶어 전달하면 공통 이체가 각 상품의 분기문을 알지 않아도 됩니다.

문자열과 Enum 네 개를 개별 인자로 전달하는 대신 의미 있는 이름으로 묶었으며, 생성 시 코드 존재와 적요 길이를 검증합니다. 이 차이만을 위해 업무별 이체 서비스의 상속 구조를 만들 필요는 없다고 판단한 구성입니다.

실행 동작 자체가 업무마다 달라진다면 단순 명세 전달만으로 부족할 수 있습니다. 현재 TransferLedgerSpec은 값의 묶음이지 다양한 이체 알고리즘을 실행하는 전략 객체는 아닙니다. 공통 서비스의 검증 범위는 [T11~T14](04_TRANSACTION_CONCURRENCY_QA.md)에서 설명합니다.

근거: [TransferLedgerSpec](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/TransferLedgerSpec.java).

## 9.7 S06. 왜 계좌번호는 난수 후보와 중복 확인으로 생성했습니까?

**답변 초안**

> 과제에서는 중앙 채번 시스템 자체보다 정해진 13자리 형식과 중복 저장 방지가 필요했습니다. 상품 접두어와 9자리 난수로 후보를 만들고, 이미 사용 중이면 다시 생성합니다. DB 유일 제약을 최종 방어선으로 두었습니다.

SecureRandom을 사용했지만 유일성은 난수 생성기가 보장하지 않습니다. 후보 조회 직후 다른 요청이 같은 번호를 저장할 수 있고, 그때는 DB 제약에 의해 저장이 실패할 수 있습니다. 현재 최대 100회는 이미 존재하는 후보를 다시 생성하는 횟수이며, 커밋 시 UK 충돌까지 자동 재시도하는 구현은 아닙니다.

별도 채번 테이블이나 번호 범위 할당은 번호 발급을 더 체계적으로 관리할 수 있는 대안입니다. 대신 경쟁 제어·장애 복구·번호 소진과 재사용 정책이 필요합니다. 현재 방식도 번호 공간 사용률이 높아지면 충돌과 재조회 비용이 커질 수 있습니다. 계좌번호의 추측 어려움을 인증·인가로 간주하지 않습니다.

형식과 PK와의 구분은 [D15·D18](03_DATA_MODEL_QA.md)을 참고합니다.

근거: [AccountNumberGenerator](../../src/main/java/com/kakaobank/coinbox/account/service/AccountNumberGenerator.java), [채번 테스트](../../src/test/java/com/kakaobank/coinbox/account/service/AccountNumberGeneratorTest.java).

## 9.8 S07. 왜 BaseEntity와 JPA Auditing을 사용했습니까?

**답변 초안**

> 생성·최종 수정 시각은 여러 업무 엔티티의 공통 속성이므로 BaseEntity와 Auditing으로 반복 코드를 줄였습니다. 금융거래의 실행 시각은 별도 업무 값으로 두어 레코드 생성 시각과 혼동하지 않도록 했습니다.

BaseEntity는 `@MappedSuperclass`이며 별도 테이블과 조인하는 구조가 아닙니다. 공통 매핑을 재사용하는 선택입니다. DB 기본값·트리거로 시각을 채우는 대안은 직접 SQL에도 공통 규칙을 적용하기 쉽지만 DB 쪽 동작과 애플리케이션 매핑을 함께 관리해야 합니다.

Auditing이 JDBC 저장이나 벌크 SQL까지 엔티티 리스너처럼 처리하는 것은 아닙니다. 잔액 JDBC 저장은 감사 시각을 직접 전달합니다. 생성·최종 수정 시각 두 개는 누가 어떤 값을 바꿨는지 보관하는 전체 변경 이력이나 보안 감사 로그를 대신하지 않습니다.

근거: [BaseEntity](../../src/main/java/com/kakaobank/coinbox/common/entity/BaseEntity.java), [감사 설정](../../src/main/java/com/kakaobank/coinbox/common/config/JpaAuditingConfiguration.java), [잔액 Tasklet](../../src/main/java/com/kakaobank/coinbox/accountdailybalance/batch/DailyBalanceTasklet.java).

## 9.9 S08. 왜 Clock을 주입하고 날짜와 시각을 나누었습니까?

**답변 초안**

> 가입일·동전모으기 시작일·배치 기준일처럼 날짜에 따라 결과가 달라지는 로직을 테스트하기 위해 Clock을 주입했습니다. 테스트 날짜를 고정하면 실제 실행 날짜가 바뀌어도 경계 조건을 확인할 수 있습니다. 업무 기준일은 LocalDate, 거래·생성·수정 시각은 LocalDateTime으로 구분했습니다.

`Clock.fixed`는 현재 시스템 시간과 독립적인 테스트에 사용할 수 있습니다. [Java 21 Clock 공식 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Clock.html)

현재 적용 범위에는 한계가 있습니다. Clock 빈은 `systemDefaultZone()`이며 스케줄러의 cron 시간대만 `Asia/Seoul`로 명시했습니다. 서버 기본 시간대가 다르면 실행 시점과 계산한 업무일이 어긋날 수 있습니다. 공통 이체는 `LocalDateTime.now()`, Snowflake는 시스템 밀리초를 직접 사용하고, JPA Auditing에도 이 Clock을 연결한 별도 DateTimeProvider가 없습니다.

따라서 '모든 시각을 하나의 서울 시간 Clock으로 통일했습니다'라고 답하지 않습니다. 다른 시간대까지 운영하려면 업무 시간대와 저장 시각의 규약, Instant·OffsetDateTime 등의 사용 여부를 추가로 결정해야 합니다. 기준일 파라미터가 과거 시점의 잔액까지 복원하는 것은 아닙니다.

근거: [TimeConfiguration](../../src/main/java/com/kakaobank/coinbox/common/config/TimeConfiguration.java), [BatchScheduler](../../src/main/java/com/kakaobank/coinbox/common/batch/BatchScheduler.java), [시간 일관성 검토 7.3.5](07_DESIGN_REVIEW_AND_MOCK_INTERVIEW.md).

## 9.10 S09. 왜 open-in-view=false로 설정했습니까?

**답변 초안**

> HTTP 응답을 만들면서 추가로 데이터를 지연 조회하는 동작에 의존하지 않으려는 선택입니다. 필요한 데이터는 서비스와 조회 코드에서 확보하고 응답 DTO로 전달해 DB 접근 경계를 명확하게 하려고 했습니다.

OSIV는 웹 요청 범위에서 EntityManager를 유지해 지연 로딩을 허용하는 기능이며, `spring.jpa.open-in-view=false`로 비활성화할 수 있습니다. [Spring Boot SQL 문서](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.jpa-and-spring-data.open-entity-manager-in-view)

현재는 엔티티 연관관계 없이 ID를 참조하고 projection도 사용하므로 지연 로딩을 위해 OSIV를 켤 필요가 크지 않습니다. 켜 두면 응답 조립은 편할 수 있지만 데이터 접근 위치가 서비스 밖으로 퍼질 수 있습니다. 비활성화가 모든 Controller의 DB 호출을 기술적으로 금지하거나 성능 개선을 증명하는 것은 아닙니다. 모든 엔티티→DTO 변환을 반드시 트랜잭션 내부에서만 수행하도록 구현한 것도 아닙니다.

근거: [실행 설정](../../src/main/resources/application.yaml), [계좌 조회 서비스](../../src/main/java/com/kakaobank/coinbox/account/service/AccountQueryService.java).

## 9.11 S10. 왜 요청·서비스·엔티티에서 검증을 나누었습니까?

**답변 초안**

> 요청 계층은 누락·형식·양수 여부, 서비스는 고객 소유권과 현재 업무 가능 여부, 엔티티는 자신의 상태 변경 조건을 확인합니다. 입력이 올바른 형식이라는 것과 실제로 거래할 수 있다는 것은 다른 문제이기 때문입니다.

예를 들어 양수인 근거계좌 ID라도 다른 고객의 계좌일 수 있고, 처음 조회할 때 ACTIVE였어도 처리 시점에 상태가 달라질 수 있습니다. 외부 요청 검증에 통과했다고 잠금 후 업무 검증을 생략하지 않습니다. 배치처럼 Controller를 거치지 않는 호출에도 상태 변경 규칙이 필요합니다.

모든 검증을 한곳에 두면 코드가 모이지만 HTTP 형식과 금융 규칙이 결합됩니다. 반대로 계층마다 모든 검사를 복사하는 것도 피해야 합니다. 현재 오류 응답은 공통 Handler로 변환하며 자세한 내용은 [J22](06_TECH_TEST_OPERATIONS_QA.md)에 있습니다. 특히 현재 Handler는 모든 IllegalArgumentException을 400으로 매핑하므로 내부 오류가 입력 오류로 분류될 가능성은 추가 검토 대상입니다.

근거: [개설 요청](../../src/main/java/com/kakaobank/coinbox/coinbox/request/OpenCoinBoxRequest.java), [업무 서비스](../../src/main/java/com/kakaobank/coinbox/coinbox/service/CoinBoxService.java), [공통 오류 처리](../../src/main/java/com/kakaobank/coinbox/common/exception/GlobalExceptionHandler.java).

## 9.12 S11. 왜 비우기 요청에서 금액과 입금 계좌를 받지 않습니까?

**답변 초안**

> 비우기는 고객이 임의의 계좌로 원하는 금액을 이체하는 기능이 아니라, 자신의 저금통 잔액 전부를 연결된 근거계좌로 돌려보내는 업무입니다. 따라서 서버가 저장된 연결 관계로 입금 계좌를 결정하고 잠금 후 잔액으로 금액을 결정하게 했습니다.

일반 이체 API처럼 금액과 목적지를 받으면 잘못된 연결 계좌나 오래된 잔액을 요청할 여지가 생겨 추가 검증이 필요합니다. 현재 업무에서 고객에게 선택권이 없는 값은 요청 필드로 받지 않는 구성입니다.

입력 필드를 줄였다고 인증이 구현되는 것은 아닙니다. 고객 헤더의 신뢰 전제는 [J22](06_TECH_TEST_OPERATIONS_QA.md), 잠금 후 JPA 객체 최신성은 [T16](04_TRANSACTION_CONCURRENCY_QA.md)에서 확인합니다.

근거: [CoinBoxController](../../src/main/java/com/kakaobank/coinbox/coinbox/controller/CoinBoxController.java), [전액 이체](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java).

## 9.13 S12. 왜 Swagger와 초기 데이터까지 테스트 대상으로 삼았습니까?

**답변 초안**

> UI가 없는 제출물이라 실행자가 API를 찾고 정상 흐름을 재현할 수 있는지도 중요했습니다. 코드에서 생성되는 OpenAPI의 경로·상태 코드·스키마와 초기 데이터에 연결된 Swagger 예시를 테스트해 문서와 실행 계약이 어긋나는 지점을 확인하려고 했습니다.

Markdown만 수동 관리하면 설명의 자유도는 높지만 실제 API와 달라질 수 있습니다. 코드 기반 OpenAPI도 업무 배경까지 자동 설명하지는 못하므로 ERD·시퀀스·API 문서는 별도로 유지합니다. 테스트가 모든 Markdown 문장을 자동 비교하거나 문서의 완전성을 증명하는 것은 아닙니다.

예시가 최초 실행에서 정상 동작한다고 반복 실행이 항상 같은 성공 응답을 내는 것도 아닙니다. 개설·비우기·해지는 상태를 바꾸므로 시나리오와 데이터 초기 상태를 함께 안내해야 합니다. 기존 테스트 분류와 환경 선택은 [J16~J21](06_TECH_TEST_OPERATIONS_QA.md)을 참고합니다.

근거: [OpenAPI 명세 테스트](../../src/test/java/com/kakaobank/coinbox/common/config/OpenApiSpecificationTest.java), [Swagger 예시 테스트](../../src/test/java/com/kakaobank/coinbox/coinbox/controller/SwaggerExampleIntegrationTest.java), [초기 데이터 테스트](../../src/test/java/com/kakaobank/coinbox/support/InitialDataIntegrationTest.java).

## 9.14 선택을 설명하는 공통 기준

기술 이름보다 `현재 요구 → 선택한 방법 → 대안 → 감수한 비용` 순서로 답합니다. 이미 설명한 선택은 색인의 원문을 사용하고 이 문서의 답변까지 중복해서 외울 필요는 없습니다.

현재 소스에서 확인되는 선택을 설명하는 자료이며, 모든 선택이 성능 실험으로 입증됐다는 의미는 아닙니다. 동작 사실, 설명 가능한 근거, 추가 검증 항목을 구분합니다.
