# 9. 개발 선택 색인과 보충 질문

[전체 목차](README.md)

최종 구현을 기준으로 '왜 이 방법을 선택했습니까?'라는 질문을 준비하는 자료입니다. 기존 답변을 다시 싣지 않고 찾아갈 위치를 정리했으며, 기존 자료에 부족했던 선택만 S01~S12로 보충했습니다. Snowflake와 Tasklet·Chunk의 대안 비교는 각각 기존 D16, B03~B05에 보충했습니다.

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

두 계좌 잔액과 거래·원장을 하나의 DB 트랜잭션으로 처리하고 실행 구성을 단순하게 유지하기 위해서입니다. 현재 과제 범위에서는 서비스별 독립 배포보다 자금 이동의 원자성을 직접 보장하는 것이 중요하다고 판단했습니다.

근거: [빌드 구성](../../build.gradle), [공통 이체 서비스](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java).

## 9.3 S02. 왜 업무별 패키지 안에 Controller·Service·Repository를 두었습니까?

업무별로 코드를 쉽게 찾으면서 각 계층의 책임을 구분하기 위해서입니다. 계좌·저금통·동전모으기 패키지 안에서 Controller는 HTTP 입출력, Service는 업무 순서와 트랜잭션, Repository는 DB 접근을 담당합니다.

근거: [업무 패키지](../../src/main/java/com/kakaobank/coinbox), [클래스 다이어그램](../04_CLASS_DIAGRAM.md).

## 9.4 S03. 왜 엔티티에 공개 Setter 대신 create·debit·credit·close를 두었습니까?

상태 변경을 업무 행위로 표현하고, 변경할 때 필요한 규칙을 엔티티 안에서 확인하기 위해서입니다. create는 생성 조건, debit·credit은 금액과 거래 가능 상태, close는 잔액 0원 조건을 검증합니다. 공개 Setter로 잔액·상태를 임의로 덮어쓰는 경로를 줄였습니다.

근거: [Account](../../src/main/java/com/kakaobank/coinbox/account/entity/Account.java).

## 9.5 S04. 왜 저축 금액 계산을 별도 클래스로 분리했습니까?

DB·잠금과 무관한 계산 규칙을 독립적으로 테스트하기 위해서입니다. 계산기는 전일 잔돈·현재 잔액·한도로 저축 금액이나 건너뜀 사유를 반환하고, 서비스는 조회·잠금·이체·이력 저장을 담당합니다. 이를 통해 1,000원 경계와 한도 직전·도달·초과를 작은 단위 테스트로 확인합니다.

근거: [계산기](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingAmountCalculator.java), [경계값 테스트](../../src/test/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingAmountCalculatorTest.java).

## 9.6 S05. 왜 공통 이체에 TransferLedgerSpec을 전달합니까?

공통 이체 로직은 유지하면서 업무별 원장 코드와 적요만 다르게 전달하기 위해서입니다. TransferLedgerSpec이 출금·입금 양쪽의 코드와 적요를 묶으므로, 이체 서비스 안에 동전모으기·비우기·해지별 분기문을 둘 필요가 없습니다. 생성 시 코드와 적요 길이도 함께 검증합니다.

근거: [TransferLedgerSpec](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/TransferLedgerSpec.java).

## 9.7 S06. 왜 계좌번호는 난수 후보와 중복 확인으로 생성했습니까?

과제에 필요한 13자리 형식과 중복 저장 방지를 간단하게 구현하기 위해서입니다. 상품 접두어와 9자리 난수로 후보를 만들고 기존 번호와 겹치면 최대 100회까지 다시 생성합니다. 최종 중복 저장은 DB UK로 막습니다.

조회 직후 다른 요청이 같은 번호를 저장하면 UK 충돌로 실패할 수 있습니다. 현재 100회 재시도는 후보 생성에만 적용되며 저장 실패까지 재시도하는 것은 아닙니다.

근거: [AccountNumberGenerator](../../src/main/java/com/kakaobank/coinbox/account/service/AccountNumberGenerator.java).

## 9.8 S07. 왜 BaseEntity와 JPA Auditing을 사용했습니까?

엔티티마다 반복되는 생성·수정 시각 매핑과 갱신 코드를 공통화하기 위해서입니다. BaseEntity는 @MappedSuperclass로 컬럼 매핑을 재사용하고, JPA Auditing은 @CreatedDate·@LastModifiedDate 값을 채웁니다. 별도의 BaseEntity 테이블은 생성하지 않습니다.

근거: [BaseEntity](../../src/main/java/com/kakaobank/coinbox/common/entity/BaseEntity.java), [감사 설정](../../src/main/java/com/kakaobank/coinbox/common/config/JpaAuditingConfiguration.java).

## 9.9 S08. 왜 Clock을 주입하고 날짜와 시각을 나누었습니까?

Clock은 날짜에 의존하는 로직을 테스트에서 고정하기 위해, 날짜와 시각의 구분은 업무 기준일과 실제 발생 시각을 다르게 표현하기 위해 사용했습니다.

가입일·시작일·배치 기준일은 LocalDate, 거래·생성·수정 시각은 LocalDateTime으로 관리합니다. Clock을 주입한 로직은 테스트에서 Clock.fixed로 시간을 고정해 날짜 경계를 검증할 수 있습니다. [Java 21 Clock 공식 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Clock.html)

근거: [TimeConfiguration](../../src/main/java/com/kakaobank/coinbox/common/config/TimeConfiguration.java).

## 9.10 S09. 왜 open-in-view=false로 설정했습니까?

서비스 밖의 응답 처리에서 지연 로딩으로 추가 조회하는 방식에 의존하지 않기 위해서입니다. 필요한 데이터를 조회 코드에서 확보해 DTO로 반환하도록 DB 접근 경계를 명확히 했습니다. 현재는 ID 참조와 projection을 사용하므로 응답 조립을 위해 OSIV를 켤 필요가 크지 않습니다.

근거: [실행 설정](../../src/main/resources/application.yaml), [Spring Boot OSIV 설명](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.jpa-and-spring-data.open-entity-manager-in-view).

## 9.11 S10. 왜 요청·서비스·엔티티에서 검증을 나누었습니까?

각 계층이 확인할 수 있는 조건이 다르기 때문입니다.

- 요청 계층: 누락·형식·양수 여부를 확인합니다.
- 서비스: 고객 소유권, 계좌 간 관계, 현재 업무 가능 여부를 확인합니다.
- 엔티티: 출금 가능 잔액이나 해지 전 잔액 0원처럼 자신의 상태 변경 조건을 확인합니다.

양수인 계좌 ID라도 다른 고객의 계좌일 수 있으므로 요청 형식 검증만으로 업무 검증을 대신할 수 없습니다.

근거: [개설 요청](../../src/main/java/com/kakaobank/coinbox/coinbox/request/OpenCoinBoxRequest.java), [업무 서비스](../../src/main/java/com/kakaobank/coinbox/coinbox/service/CoinBoxService.java).

## 9.12 S11. 왜 비우기 요청에서 금액과 입금 계좌를 받지 않습니까?

비우기는 고객이 금액과 목적지를 선택하는 일반 이체가 아니라, 저금통 전액을 연결된 근거계좌로 돌려보내는 업무이기 때문입니다. 서버가 저장된 연결 관계로 입금 계좌를 정하고, 잠금 후 잔액을 기준으로 이체 금액을 결정합니다.

근거: [CoinBoxController](../../src/main/java/com/kakaobank/coinbox/coinbox/controller/CoinBoxController.java), [전액 이체](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java).

## 9.13 S12. 왜 Swagger와 초기 데이터까지 테스트 대상으로 삼았습니까?

채점자가 문서의 예시로 실제 API를 실행할 수 있는지 검증하기 위해서입니다. OpenAPI 테스트는 경로·상태 코드·스키마를, Swagger 예시와 초기 데이터 테스트는 예시 요청이 준비된 데이터와 연결되는지 확인합니다.

근거: [OpenAPI 명세 테스트](../../src/test/java/com/kakaobank/coinbox/common/config/OpenApiSpecificationTest.java), [Swagger 예시 테스트](../../src/test/java/com/kakaobank/coinbox/coinbox/controller/SwaggerExampleIntegrationTest.java), [초기 데이터 테스트](../../src/test/java/com/kakaobank/coinbox/support/InitialDataIntegrationTest.java).

## 9.14 답변 원칙

질문이 묻는 결론부터 답하고, 필요한 이유와 구현 근거만 덧붙입니다. 대안·한계·개선안은 질문에 포함되거나 답변의 정확성에 필요한 경우에만 설명합니다.
