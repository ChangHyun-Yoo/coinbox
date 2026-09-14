# 6. 기술·테스트·운영 예상 질문과 답변

[목차](README.md)

이 문서는 일반 CS 전체 요약이 아니라 과제 코드에서 이어질 수 있는 질문을 다룹니다. 미사용 기술은 도입 조건을 설명하는 수준으로 준비합니다.

## 6.1 J01. Spring을 사용한 이유와 DI의 장점은 무엇입니까?

웹 요청, 트랜잭션, 검증, JPA, 배치 구성에 필요한 기반 기능을 통합하려고 사용했습니다. 서비스는 필요한 Repository·계산기·Clock 등을 생성자 주입받도록 해 의존성을 드러냈습니다. 단위 테스트에서는 해당 의존성을 대체해 업무 판단을 독립적으로 확인할 수 있습니다.

@RequiredArgsConstructor는 final 등 필요한 필드의 생성자를 생성합니다. Lombok이 의존성 주입이나 트랜잭션을 직접 수행하는 것은 아닙니다.

## 6.2 J02. 클래스의 readOnly=true와 메서드의 @Transactional은 왜 함께 사용합니까?

서비스의 기본 의도를 조회로 두고 쓰기 업무 메서드에 트랜잭션 속성을 명시했습니다. 실제 동작은 프록시 호출과 기존 트랜잭션 참여 여부까지 확인해야 합니다.

readOnly는 트랜잭션 관리자에 전달하는 힌트이며 모든 쓰기를 보안적으로 차단하는 보장은 아닙니다. 실제 최적화·차단 동작은 JPA 공급자와 DB 설정에 따라 확인해야 합니다. [Spring Transactional API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html)

## 6.3 J03. JPA 엔티티 변경은 언제 DB에 반영됩니까?

관리 중인 엔티티의 잔액을 변경하면 영속성 컨텍스트가 변경을 감지하고 flush 시점에 SQL을 실행할 수 있습니다. flush와 commit은 다르며 SQL이 실행됐어도 트랜잭션이 롤백되면 확정되지 않습니다.

공통 이체 서비스는 관리 중인 두 Account의 debit·credit을 호출하고 거래·원장을 저장합니다. 따라서 테스트도 메서드 호출 성공뿐 아니라 최종 커밋 결과와 롤백 결과를 확인해야 합니다.

## 6.4 J04. Native Query를 선택한 이유와 단점은 무엇입니까?

FOR UPDATE, 계좌 정렬, EXISTS와 조회 전용 projection 등 필요한 SQL을 명확히 표현하려고 사용했습니다. 그 대신 테이블·컬럼·DB 문법 변경에 더 민감하고 JPA의 DB 독립성은 줄어듭니다.

조회 성능은 SQL, 왕복 횟수, 반환 데이터 크기와 실행 계획으로 확인해야 합니다. Native Query를 사용한다는 사실만으로 더 빠르다고 판단하지 않습니다.

## 6.5 J05. 연관관계를 안 쓰면 N+1은 없어집니까?

지연 로딩 때문에 숨겨진 추가 조회가 생기는 경로는 줄지만, 반복문에서 Repository를 호출하면 여전히 N번 조회가 발생할 수 있습니다. 현재 동전모으기 정책은 Step별 상품 버전 캐시에서 재사용하므로 같은 버전의 정책을 후보마다 조회하지 않습니다.

계좌 목록은 상품명을 함께 조회하고 메모리에서 부모·자식으로 조립합니다. 조회 수는 SQL 로그로 확인해야 하며 객체 연관관계 유무만으로 효율을 판단하지 않습니다.

## 6.6 J06. DTO와 projection을 사용하는 이유는 무엇입니까?

조회 화면에 필요한 컬럼만 가져오고 DB 조회 결과를 서비스용 불변 읽기 모델로 변환합니다. 외부 응답을 엔티티에 직접 묶지 않아 API 필드와 내부 모델의 변경을 분리할 수 있습니다.

현재 ActiveAccountRow 등 record를 사용하지만 record가 내부 참조 객체까지 깊은 불변성을 자동 보장하는 것은 아닙니다. 정책 snapshot처럼 값 중심 객체에 적합하게 사용합니다.

## 6.7 J07. JPA와 JdbcTemplate을 섞으면 같은 트랜잭션이 됩니까?

같은 DataSource와 호환되는 트랜잭션 관리 경로를 사용해야 합니다. JpaTransactionManager는 조건이 맞으면 같은 DataSource에 대한 JDBC 접근을 같은 트랜잭션에 참여시킬 수 있습니다.

따라서 서로 다른 DB 연결이나 별도 트랜잭션 관리자를 임의로 사용하면 안 됩니다. 현재 잔액 Step의 JPA 조회와 JDBC 저장도 설정과 통합 테스트를 함께 확인할 지점입니다. [JpaTransactionManager 공식 설명](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html)

## 6.8 J08. Native UPDATE 후 엔티티가 오래된 값을 갖는 문제는 어떻게 봅니까?

벌크 SQL은 영속성 컨텍스트의 개별 객체 변경과 별개로 DB를 수정할 수 있습니다. 이미 관리 중인 엔티티와 DB 상태가 어긋나지 않도록 flush·clear·재조회 정책을 확인해야 합니다.

현재 테스트용 잔고 증감 쿼리에 @Modifying(clearAutomatically=true, flushAutomatically=true)가 있습니다. 모든 상황에서 clear를 무조건 호출하기보다 아직 저장되지 않은 변경이 있는지도 고려해야 합니다.

## 6.9 J09. Snowflake ID를 미리 넣고 save하면 무조건 INSERT만 합니까?

아닙니다. Spring Data JPA는 엔티티 신규 여부 판단에 따라 persist 또는 merge를 선택합니다. 현재 엔티티는 ID를 미리 할당하고 별도의 @Version이나 Persistable 신규 상태 판별을 사용하지 않으므로 merge 경로와 추가 조회 가능성을 점검해야 합니다.

새 엔티티의 SQL 수를 측정한 뒤 Persistable 구현이나 명시적 persist 등 대안을 검토할 수 있습니다. 이 문서에서는 실제 추가 SQL 수를 측정하거나 수정하지 않았습니다. [Spring Data JPA 신규 엔티티 판별](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)

## 6.10 J10. 어떤 인덱스를 먼저 검토하겠습니까?

계좌의 고객·상품·상태 조건, 최신 계약 조회의 account_id와 정렬 컬럼, 배치 후보 조건을 먼저 확인하겠습니다. 일별 잔액과 실행 이력의 복합 UK는 같은 키의 조회에도 활용 가능한 구조입니다.

예를 들어 최신 계약 조회에는 (account_id, contract_start_date, account_contract_id)가 검토 후보입니다. 아래는 구현 완료 목록이 아니라 검토 순서입니다.

1. 실제 SQL·데이터 건수·분포·선택도를 확인합니다.
2. EXPLAIN으로 접근 인덱스, 예상 행 수와 정렬 여부를 봅니다.
3. 통제된 테스트 데이터에서 실제 지연·스캔·잠금 대기를 측정합니다.
4. 쓰기 비용과 저장 공간까지 비교합니다.

상태 값처럼 선택도가 낮은 컬럼을 단독 인덱스로 추가하는 것이 항상 유리하지는 않습니다.

## 6.11 J11. 이용 중인 저금통 확인에 EXISTS를 사용하는 이유는 무엇입니까?

전체 건수가 아니라 존재 여부만 필요할 때 의도를 분명하게 하고 첫 일치로 판단할 수 있는 실행을 유도할 수 있습니다. 현재 이용 중인 저금통 확인은 SELECT EXISTS를 사용합니다.

계좌번호 후보 확인은 account_number 유일 키를 조건으로 COUNT 쿼리를 사용합니다. 각 쿼리의 목적과 실행 계획을 기준으로 적절성을 판단합니다.

## 6.12 J12. Map을 이용한 계좌 응답 조립의 복잡도는 어떻습니까?

조회된 n개 계좌를 Map에 넣고 부모별 자식을 모으므로 평균적인 해시 조회를 전제로 시간 O(n), 추가 공간 O(n) 수준입니다. 매 부모마다 전체 목록을 다시 탐색하는 중첩 반복을 피했습니다.

LinkedHashMap은 입력 순서를 유지하기 위한 선택입니다. 현재는 한 단계 자식 구조만 지원하며 부모가 조회되지 않거나 더 깊게 연결되면 정합성 오류로 처리합니다.

## 6.13 J13. long 금액의 오버플로는 고려했습니까?

일반 Account.credit은 Math.addExact로 입금 시 오버플로를 검사합니다. 출금은 양수 금액·ACTIVE·잔액 충분 여부를 확인합니다.

모든 숫자 연산과 모든 경로에 같은 방어가 자동 적용되는 것은 아닙니다. 테스트용 직접 SQL 증감은 별도 구현이고, 소수 단위 금융 계산은 BigDecimal·정밀도·반올림 정책을 추가로 검토해야 합니다.

## 6.14 J14. Java 21과 JVM에서 어떤 꼬리질문을 준비해야 합니까?

프로젝트에서 사용한 record, switch 표현식, Long과 long의 null·언박싱 차이, equals와 ==를 우선 준비합니다. Java 21을 쓴다고 가상 스레드를 활성화하거나 성능 최적화를 구현한 것은 아닙니다.

JVM 질문은 실제 병목에 연결합니다. 잔액 Tasklet의 전체 리스트는 heap 사용과 GC 압박을 만들 수 있고, DB 대기 중에는 CPU보다 커넥션과 잠금 대기가 병목일 수 있습니다. GC 옵션부터 바꾸기보다 메모리·스레드·SQL 지표를 먼저 확인하겠다고 설명합니다.

## 6.15 J15. Snowflake의 synchronized는 무엇을 보호합니까?

한 Snowflake 객체의 lastTimeMillis와 sequence 갱신을 직렬화합니다. DB 계좌 잔액이나 다른 서버 인스턴스의 ID 생성기를 보호하지 않습니다.

한 밀리초의 12비트 순번을 모두 쓰면 다음 밀리초를 기다리고, 시계 역행은 예외로 처리합니다. 비트 구성상 순번 공간과 실제 초당 성능을 동일시하지 않습니다.

## 6.16 J16. 어떤 종류의 테스트를 작성했습니까?

| 범위 | 검증 목적 | 코드 예 |
|---|---|---|
| 순수 계산 단위 테스트 | 한도·잔돈·잔액 경계값 | CoinSavingAmountCalculatorTest |
| 서비스 단위 테스트 | 의존성을 대체한 업무 분기 | CoinBoxServiceTest, CoinSavingServiceTest |
| Controller 테스트 | 입력·응답·상태 코드 계약 | CoinBoxControllerTest |
| Repository 테스트 | MySQL SQL·제약·매핑 | AccountRepositoryTest |
| 서비스 통합 테스트 | DB 커밋과 롤백 | InternalTransferServiceIntegrationTest |
| HTTP E2E | 가입부터 비우기·해지까지 실제 경로 | CoinBoxApiEndToEndTest |
| Batch 통합 테스트 | 실제 Job 실행·중복·이력 실패 롤백 | BatchLifecycleIntegrationTest |
| 문서·초기 데이터 테스트 | OpenAPI 계약과 실행 예시 | OpenApiSpecificationTest, SwaggerExampleIntegrationTest |

테스트 이름과 개수보다 어떤 장애를 실제 DB로 재현하고 무엇을 assertion했는지 설명하는 것이 중요합니다.

## 6.17 J17. Repository·통합 테스트에 MySQL Testcontainers를 사용하는 이유는 무엇입니까?

Native Query, FOR UPDATE, UK, JDBC 드라이버 결과 등 MySQL 동작을 확인하기 위해 같은 엔진을 사용했습니다. Compose로 띄운 개인 개발 DB와 분리해 데이터 상태 의존성을 줄였습니다.

컨테이너 시작 시간과 Docker 의존성이 생기는 비용이 있습니다. 순수 계산 테스트까지 DB를 사용하지는 않습니다. 또한 모든 테스트 메서드마다 새 컨테이너가 생기는 구조라고 단정하지 않고 Spring 테스트 컨텍스트 재사용과 DB 정리 방식을 함께 봅니다.

## 6.18 J18. 동시성·롤백에서 어떤 것을 검증했습니까?

현재 테스트 코드에는 동시 가입의 단일 개설, 같은 계좌의 동시 출금과 초과 출금 방지, 동일 저금통의 동시 동전모으기, 동일 기준일 잔액 중복 방지가 있습니다. 원장 또는 실행 이력 저장에 실패를 유도해 관련 금융 변경이 롤백되는 테스트도 있습니다.

반면 모든 교차 업무 경쟁이나 장애 시점이 검증된 것은 아닙니다. 추가로 잠금 전 조회가 있는 비우기와 동전모으기의 경쟁, 정책 캐시 실행 경계, DB 커밋 직후 응답 유실 등을 검증할 수 있습니다.

## 6.19 J19. JaCoCo 수치를 어떻게 설명하겠습니까?

기존 README에는 테스트 126개 통과, line 91.06%, branch 70.51%가 기록되어 있습니다. 빌드 기준은 line 80%, branch 70%입니다. 이번 면접 자료 작성에서는 재실행하지 않았습니다.

Line은 실행된 코드 줄의 범위, branch는 분기 실행 범위를 보여줍니다. assertion의 적절성, 실제 운영 부하, 경쟁 시나리오와 요구사항 완전성을 수치만으로 증명하지는 않습니다.

## 6.20 J20. Docker Compose와 Testcontainers는 역할이 어떻게 다릅니까?

Compose는 채점자와 개발자가 애플리케이션을 직접 실행할 때 사용할 MySQL을 제공합니다. Testcontainers는 테스트 컨텍스트에 연결할 MySQL을 관리합니다. 현재 Compose는 MySQL만 실행하며 애플리케이션 전체가 Docker 이미지로 패키징된 것은 아닙니다.

MySQL 버전은 두 구성 모두 8.3.0입니다. 로컬 실행은 Docker 준비 → MySQL healthcheck → Gradle bootRun 순서입니다. 운영용 이미지·배포 파이프라인을 구현했다고 말하지 않습니다.

## 6.21 J21. 초기 데이터와 고정 설정을 왜 사용했습니까?

채점자가 환경변수 값을 준비하지 않고 같은 예시로 실행할 수 있게 로컬 설정을 고정하고, JPA 테이블 생성 후 data.sql을 실행하도록 했습니다. 대신 ddl-auto=create 때문에 앱 재시작 시 업무 데이터가 재생성됩니다.

Compose의 environment 블록에는 MySQL 컨테이너 초기화 값을 고정해서 전달합니다. 로컬 실행의 편의성을 위한 설정이며, 운영에서는 비밀값 분리와 스키마 마이그레이션이 필요합니다.

## 6.22 J22. 오류 처리와 보안은 어디까지 구현했습니까?

BusinessException·ErrorCode·GlobalExceptionHandler로 업무 오류와 잘못된 입력, 예상하지 못한 서버 오류를 공통 응답으로 변환했습니다. 계좌 업무에서 customerId로 소유 관계를 확인합니다.

인증 서버는 없고 X-Customer-Id를 신뢰하는 전제입니다. /internal이라는 경로명만으로 접근이 제한되지 않으며 현재 관리자 인증·인가도 없습니다. 테스트용 잔고 변경과 배치 실행은 운영 노출을 차단해야 합니다.

## 6.23 J23. Redis나 Kafka를 추가하면 더 좋은 설계입니까?

요구가 먼저입니다. 현재 동전모으기 정책은 실행 내부의 버전별 재사용이 목적이라 프로세스 내 Step 캐시로 충분하며 Redis를 추가하면 캐시 무효화·네트워크·운영 비용이 생깁니다.

거래 후 알림·외부 연계가 요구된다면 Kafka 등 이벤트 전달을 검토할 수 있습니다. 그 경우 DB 커밋과 발행 사이의 유실, 중복 소비와 순서 문제를 별도로 설계해야 합니다. 현재 Kafka·Redis·Outbox를 구현했다고 말하지 않습니다.

## 6.24 J24. 성능이 느리다는 보고를 받으면 무엇부터 확인합니까?

API 응답 지연인지 배치 완료 지연인지 범위를 구분하고, 요청량·오류율·지연 분포·DB 연결 대기·쿼리 시간·잠금 대기·CPU·heap·GC를 확인하겠습니다. 실행 계획과 호출별 SQL 수로 반복 조회나 넓은 스캔을 찾습니다.

현재 조회 구조는 정책의 Step별 재사용, 잠긴 계좌 객체 전달, 존재 확인의 EXISTS, 계좌 목록 일괄 조회·조립으로 구성되어 있습니다. 이 구조의 실제 처리량과 지연은 별도 측정이 필요합니다.

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
