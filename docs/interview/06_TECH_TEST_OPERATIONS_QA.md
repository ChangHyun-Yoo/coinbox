# 6. 기술·테스트·운영 예상 질문과 답변

[목차](README.md)

## 6.1 J01. Spring을 사용한 이유와 DI의 장점은 무엇입니까?

웹·검증·트랜잭션·JPA·배치 구성을 통합하기 위해 Spring을 사용했습니다. DI는 객체가 의존 객체를 직접 생성하지 않고 외부에서 전달받게 해 결합도를 낮추고, 테스트에서 의존성을 대체하기 쉽게 합니다.

현재 서비스는 final 필드와 @RequiredArgsConstructor로 생성자 주입을 사용합니다.

## 6.2 J02. 클래스의 readOnly=true와 메서드의 @Transactional은 왜 함께 사용합니까?

조회 중심의 기본 설정을 두고, 쓰기 업무만 별도로 표시하기 위해서입니다. 클래스에 @Transactional(readOnly = true)를 두고 쓰기 메서드에는 @Transactional을 붙여 읽기·쓰기 의도를 구분합니다.

readOnly는 트랜잭션 관리자에 전달하는 최적화 힌트이며 쓰기를 완전히 차단하는 제약은 아닙니다. 기존 트랜잭션에 참여할 때는 그 트랜잭션의 속성도 확인해야 합니다. [Spring Transactional API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html)

## 6.3 J03. JPA 엔티티 변경은 언제 DB에 반영됩니까?

관리 중인 엔티티의 변경은 flush 시점에 SQL로 반영되고 commit 때 확정됩니다. flush는 커밋 전이나 필요한 조회 전, 명시적 flush 호출 등에서 발생할 수 있습니다. SQL이 실행됐어도 트랜잭션이 롤백되면 변경은 확정되지 않습니다.

현재 이체에서는 관리 중인 Account의 debit·credit으로 잔액을 바꾸고 변경 감지를 사용합니다.

## 6.4 J04. Native Query를 선택한 이유와 단점은 무엇입니까?

잠금·정렬·존재 확인 등의 SQL을 명시적으로 제어하기 위해 Native Query를 선택했습니다. FOR UPDATE, ORDER BY, EXISTS와 필요한 컬럼 조회를 직접 작성할 수 있습니다. 대신 테이블·컬럼 변경에 민감하고 MySQL 문법을 사용하면 DB 이식성이 낮아집니다.

## 6.5 J05. 연관관계를 안 쓰면 N+1은 없어집니까?

아닙니다. 연관관계의 지연 로딩을 사용하지 않더라도 반복문에서 Repository를 호출하면 반복 조회가 발생합니다. 현재는 정책을 Step별로 캐시하고 계좌 목록과 상품명을 함께 조회해 불필요한 반복 조회를 줄입니다.

## 6.6 J06. DTO와 projection을 사용하는 이유는 무엇입니까?

projection은 필요한 컬럼만 조회하고, DTO는 외부 응답을 엔티티 구조와 분리하기 위해 사용합니다. 현재 조회 projection을 ActiveAccountRow 같은 읽기 모델로 변환하고 응답 DTO를 구성합니다.

## 6.7 J07. JPA와 JdbcTemplate을 섞으면 같은 트랜잭션이 됩니까?

네. 같은 DataSource와 적절한 트랜잭션 관리자를 사용하면 같은 트랜잭션에 참여할 수 있습니다. JpaTransactionManager는 해당 DataSource의 JDBC 접근도 연결할 수 있으므로, JdbcTemplate이 같은 트랜잭션 연결을 사용하도록 구성해야 합니다. [JpaTransactionManager 공식 설명](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html)

## 6.8 J08. Native UPDATE 후 엔티티가 오래된 값을 갖는 문제는 어떻게 봅니까?

Native UPDATE는 영속성 컨텍스트의 객체를 거치지 않고 DB를 변경하므로 기존 객체가 오래된 값을 가질 수 있습니다. 필요한 변경을 먼저 flush하고, UPDATE 후 컨텍스트를 clear한 뒤 다시 조회하는 방식으로 맞출 수 있습니다.

현재 테스트용 잔고 증감 쿼리는 @Modifying(flushAutomatically = true, clearAutomatically = true)를 사용합니다.

## 6.9 J09. Snowflake ID를 미리 넣고 save하면 무조건 INSERT만 합니까?

아닙니다. save는 신규 여부에 따라 persist 또는 merge를 선택합니다. 현재처럼 ID를 선할당하고 @Version이나 Persistable로 신규 여부를 따로 판단하지 않으면 merge 경로를 사용해 INSERT 전 조회가 발생할 수 있습니다. [Spring Data JPA 신규 엔티티 판별](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)

## 6.10 J10. 어떤 인덱스를 먼저 검토하겠습니까?

고객별 계좌 조회와 최신 계약 조회, 배치 후보 조회의 조건·정렬 컬럼을 먼저 검토하겠습니다.

- 계좌 조회: customer_id, product_type, account_status 조건입니다.
- 최신 계약 조회: (account_id, contract_start_date, account_contract_id) 복합 인덱스가 후보입니다.
- 배치 조회: 후보 필터·정렬과 일별 잔액·실행 이력의 조인 조건입니다.

EXPLAIN과 실제 데이터 분포로 스캔·정렬 비용을 확인하고, 쓰기 비용까지 비교해 적용하겠습니다.

## 6.11 J11. 이용 중인 저금통 확인에 EXISTS를 사용하는 이유는 무엇입니까?

필요한 정보가 전체 건수가 아니라 존재 여부이기 때문입니다. EXISTS는 조건에 맞는 행이 있는지만 확인한다는 의도를 표현하고, 일치하는 행을 찾으면 존재 여부를 판단할 수 있습니다.

## 6.12 J12. Map을 이용한 계좌 응답 조립의 복잡도는 어떻습니까?

평균적인 해시 조회를 전제로 시간 O(n), 추가 공간 O(n)입니다. n개 계좌를 Map에 저장하고 부모별 자식 목록을 구성하므로, 매 부모마다 전체 목록을 다시 찾는 중첩 반복을 피합니다. LinkedHashMap은 조회 순서를 유지하기 위해 사용합니다.

## 6.13 J13. long 금액의 오버플로는 고려했습니까?

네. 일반 Account.credit은 Math.addExact로 더하기 오버플로를 검사하고, 범위를 넘으면 ArithmeticException을 발생시킵니다. 출금은 양수 금액과 충분한 잔액을 확인한 뒤 차감합니다. 이 검증은 엔티티 메서드 기준이며 테스트용 직접 SQL 증감은 별도 경로입니다.

## 6.14 J14. Java 21과 JVM에서 어떤 꼬리질문을 준비해야 합니까?

코드에서 사용한 언어 기능과 실제 메모리·스레드 동작에 연결되는 질문을 준비하면 됩니다.

| 꼬리질문 | 핵심 답변 |
|---|---|
| record를 왜 사용했습니까? | 값 전달 객체의 생성자·접근자·equals·hashCode 등을 간결하게 정의하기 위해서입니다. 필드 참조는 고정되지만 내부 가변 객체까지 불변이 되는 것은 아닙니다. [Java 공식 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html) |
| switch 표현식은 무엇입니까? | 분기 결과를 값으로 반환하는 switch입니다. 계좌번호의 상품별 접두어 결정에 사용합니다. |
| long과 Long의 차이는 무엇입니까? | long은 기본형이고 Long은 참조형이라 null이 가능합니다. null인 Long을 언박싱하면 NullPointerException이 발생합니다. |
| Long의 ==와 equals는 무엇이 다릅니까? | 두 Long 객체의 ==는 참조를, equals는 값을 비교합니다. |
| 잔액 Tasklet에서 메모리 문제가 생길 수 있습니까? | 전체 조회 결과와 저장 행 목록을 메모리에 두므로 대상 수에 따라 heap 사용량과 GC 부담이 커질 수 있습니다. |
| DB 대기가 길면 GC 튜닝부터 합니까? | 먼저 SQL·잠금·커넥션 대기인지 확인하고, 메모리·GC 지표가 원인일 때 GC를 검토합니다. |

**왜 Java 21과 Gradle Wrapper를 선택했습니까?**

개발자와 실행자의 빌드 기준을 맞추기 위해 Java 21 toolchain과 Gradle 9.5.1 Wrapper를 사용했습니다. toolchain의 21 지정은 JDK 배포판이나 패치 버전까지 고정하는 설정은 아닙니다.

## 6.15 J15. Snowflake의 synchronized는 무엇을 보호합니까?

같은 Snowflake 객체의 lastTimeMillis와 sequence를 여러 스레드가 동시에 변경하지 못하도록 보호합니다. 한 번에 한 스레드만 ID를 생성하게 해 시간·순번 갱신의 경쟁을 막습니다. 다른 서버의 생성기는 이 잠금으로 보호되지 않습니다.

## 6.16 J16. 어떤 종류의 테스트를 작성했습니까?

계산·서비스·HTTP·DB·배치와 문서 계약을 나누어 테스트했습니다.

| 범위 | 검증 목적 | 코드 예 |
|---|---|---|
| 계산 단위 테스트 | 한도·잔돈·잔액 경계값 | CoinSavingAmountCalculatorTest |
| 서비스 단위 테스트 | 의존성을 대체한 업무 분기 | CoinBoxServiceTest, CoinSavingServiceTest |
| Controller 테스트 | 입력·응답·상태 코드 | CoinBoxControllerTest |
| Repository 테스트 | MySQL SQL·제약·매핑 | AccountRepositoryTest |
| 서비스 통합 테스트 | DB 커밋·롤백 | InternalTransferServiceIntegrationTest |
| HTTP E2E | 실제 HTTP 업무 흐름 | CoinBoxApiEndToEndTest |
| Batch 통합 테스트 | Job 실행·중복·롤백 | BatchLifecycleIntegrationTest |
| 문서·초기 데이터 테스트 | OpenAPI 계약·실행 예시 | OpenApiSpecificationTest, SwaggerExampleIntegrationTest |

## 6.17 J17. Repository·통합 테스트에 MySQL Testcontainers를 사용하는 이유는 무엇입니까?

실제 사용하는 MySQL의 SQL·잠금·제약 동작을 개인 개발 DB와 분리해 검증하기 위해서입니다. Native Query, FOR UPDATE, UK 등은 다른 DB의 동작으로 대체하지 않고 MySQL 컨테이너에서 테스트합니다.

## 6.18 J18. 동시성·롤백에서 어떤 것을 검증했습니까?

중복 가입·초과 출금·중복 배치 반영 방지와 저장 실패 시 전체 롤백을 검증했습니다.

- 동시 가입: 같은 고객의 저금통이 하나만 개설되는지 확인합니다.
- 동시 출금: 같은 계좌에서 잔액을 초과해 출금되지 않는지 확인합니다.
- 동전모으기·잔액 배치: 같은 저금통·일자 또는 계좌·기준일 결과가 중복 저장되지 않는지 확인합니다.
- 저장 실패: 원장 또는 실행 이력 저장에 실패하면 관련 잔액·거래 변경도 롤백되는지 확인합니다.

## 6.19 J19. JaCoCo 수치를 어떻게 설명하겠습니까?

기존 README에 기록된 커버리지는 line 91.06%, branch 70.51%이며, 빌드 통과 기준은 각각 80%, 70%입니다.

Line은 코드 줄의 실행 범위, branch는 조건 분기의 실행 범위를 뜻합니다. 실행 여부를 보여주는 지표이므로 assertion이 올바른지나 모든 요구사항을 검증했는지는 별도로 확인해야 합니다.

## 6.20 J20. Docker Compose와 Testcontainers는 역할이 어떻게 다릅니까?

Compose는 애플리케이션 수동 실행용 DB를, Testcontainers는 자동 테스트용 DB를 제공합니다. 두 구성 모두 MySQL 8.3.0을 사용하지만 데이터와 실행 수명은 분리합니다. 현재 Compose가 실행하는 대상은 MySQL이며 애플리케이션은 Gradle bootRun으로 실행합니다.

## 6.21 J21. 초기 데이터와 고정 설정을 왜 사용했습니까?

채점자가 별도 환경변수나 데이터를 준비하지 않고 같은 시나리오를 실행할 수 있게 하기 위해서입니다. 접속 설정을 고정하고 JPA 테이블 생성 후 data.sql로 초기 데이터를 넣습니다. ddl-auto=create이므로 재시작 시 기존 업무 데이터는 재생성됩니다.

## 6.22 J22. 오류 처리와 보안은 어디까지 구현했습니까?

공통 오류 응답과 계좌 소유권 검증은 구현했고, 실제 인증과 관리자 접근 제어는 구현하지 않았습니다.

- 오류 처리: BusinessException·ErrorCode·GlobalExceptionHandler로 입력 오류, 업무 오류, 서버 오류를 공통 응답으로 변환합니다.
- 소유권 확인: customerId로 요청 고객의 계좌인지 검사합니다.
- 인증·접근 제어: X-Customer-Id를 신뢰하는 전제이며, /internal 경로 자체에는 인증·인가가 없습니다.

## 6.23 J23. Redis나 Kafka를 추가하면 더 좋은 설계입니까?

추가한다고 무조건 좋아지지는 않습니다. 현재 정책 캐시는 한 Step 안에서 같은 버전을 재사용하는 목적이므로 메모리 캐시로 충분합니다. Redis를 도입하면 네트워크와 캐시 무효화 관리가 추가됩니다.

Kafka는 거래 후 알림·외부 연계처럼 비동기 전달 요구가 있을 때 검토할 수 있습니다. 그 경우 DB 커밋과 메시지 발행 사이의 유실, 중복 소비 처리도 함께 설계해야 합니다.

## 6.24 J24. 성능이 느리다는 보고를 받으면 무엇부터 확인합니까?

어느 구간이 느린지부터 나누겠습니다. API 응답인지 배치 완료인지 확인한 뒤 애플리케이션과 DB 지표를 연결합니다.

1. 요청량·오류율·지연 분포로 증상과 발생 구간을 확인합니다.
2. SQL 실행 시간·호출 수·실행 계획과 잠금·커넥션 대기를 확인합니다.
3. CPU·heap·GC·스레드 상태로 애플리케이션 병목을 확인합니다.
4. 확인한 병목을 개선하고 같은 조건에서 다시 측정합니다.

## 6.25 실행·검증 명령

명령은 프로젝트 루트에서 실행합니다. 여기서는 실행 안내만 제공하며 실제 실행하지 않았습니다.

~~~bash
docker compose up -d --wait mysql
./gradlew bootRun
~~~

별도 터미널에서 테스트할 때는 Docker가 실행되어 있어야 합니다. 애플리케이션이나 Compose MySQL이 실행 중일 필요는 없습니다.

~~~bash
./gradlew clean test
./gradlew clean check
~~~

현재 ddl-auto=create이므로 bootRun은 기존 로컬 업무 데이터를 재생성합니다. 보존할 데이터가 있으면 먼저 설정과 백업을 검토합니다.

## 6.26 코드 근거

- [빌드·JaCoCo](../../build.gradle)
- [Testcontainers 설정](../../src/test/java/com/kakaobank/coinbox/support/MySqlTestContainer.java)
- [전체 테스트 디렉터리](../../src/test/java/com/kakaobank/coinbox)
- [계좌 조회·조립](../../src/main/java/com/kakaobank/coinbox/account/service/AccountQueryService.java)
- [공통 오류 처리](../../src/main/java/com/kakaobank/coinbox/common/exception/GlobalExceptionHandler.java)
- [Docker Compose](../../docker-compose.yml)
- [초기 데이터](../../src/main/resources/data.sql)
