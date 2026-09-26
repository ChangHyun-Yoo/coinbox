# 4. 트랜잭션·동시성 예상 질문과 답변

[목차](README.md) · [추가 검증과 한계](07_DESIGN_REVIEW_AND_MOCK_INTERVIEW.md)

## 4.1 T01. @Transactional만 있으면 동시성 문제가 해결됩니까?

아닙니다. @Transactional은 여러 변경의 커밋·롤백 범위를 정하지만, 동시에 읽은 잔액으로 두 요청이 출금을 판단하는 경쟁까지 자동으로 해결하지는 않습니다. 이 과제에서는 계좌를 비관적으로 잠근 뒤 잔액과 출금 조건을 확인합니다.

## 4.2 T02. 이 과제에서 ACID를 설명해 보십시오.

이체의 원자성, 업무 규칙의 일관성, 동시 실행의 격리성, 커밋 결과의 지속성으로 설명할 수 있습니다.

- 원자성(Atomicity): 두 계좌 잔액과 거래·원장이 모두 반영되거나 함께 롤백됩니다.
- 일관성(Consistency): 양수 금액·잔액 부족 검증과 PK·UK 같은 제약으로 정해진 규칙을 유지합니다.
- 격리성(Isolation): 같은 계좌를 변경하는 트랜잭션을 계좌 잠금으로 조정합니다.
- 지속성(Durability): 커밋한 변경은 DB의 로그·스토리지 내구성 설정에 따라 보존됩니다.

## 4.3 T03. 신규 가입 때 고객을 잠그는 이유는 무엇입니까?

동일 고객의 중복 가입을 고객 단위로 직렬화하기 위해서입니다. 서로 다른 근거계좌로 동시에 신청하면 계좌 잠금만으로는 두 요청이 모두 가입할 수 있습니다. 공통 대상인 고객 행을 먼저 잠근 뒤 이용 중인 저금통이 있는지 확인합니다.

## 4.4 T04. 애플리케이션 서버가 두 대여도 고객 잠금이 유효합니까?

네. 같은 DB의 동일 고객 행을 잠그면 서버가 달라도 요청을 조정할 수 있습니다. DB 잠금이므로 한 JVM 안에서만 동작하는 synchronized와 다릅니다. 모든 가입 경로가 고객 잠금 후 중복을 확인하는 규약을 따라야 합니다.

## 4.5 T05. 근거계좌도 잠그는 이유는 무엇입니까?

가입 조건을 확인하는 동안 선택한 근거계좌의 상태가 바뀌지 않도록 하기 위해서입니다. 고객 잠금은 중복 가입을, 근거계좌 잠금은 해당 계좌의 상태 변경과의 경쟁을 제어합니다.

## 4.6 T06. 비관적 잠금 대신 낙관적 잠금은 어떻습니까?

낙관적 잠금도 가능하지만, 이 과제에서는 잔액 확인부터 두 계좌 변경까지 잠금으로 직렬화하려고 비관적 잠금을 선택했습니다.

비관적 잠금은 먼저 잠그고 다른 요청을 대기시킵니다. 낙관적 잠금은 version으로 변경 충돌을 감지하고 실패한 트랜잭션을 재시도하는 방식입니다. 충돌이 드물고 재시도 비용이 작다면 낙관적 잠금이 적합할 수 있습니다.

## 4.7 T07. 조건부 UPDATE로 출금하면 잠금 조회가 불필요하지 않습니까?

한 계좌의 초과 출금 방지만 필요하다면 별도 잠금 SELECT 없이 조건부 UPDATE로 처리할 수 있습니다. 잔액이 출금액 이상일 때만 차감하고 영향 행 수가 1인지 확인하면 됩니다. UPDATE 자체는 DB의 쓰기 잠금을 사용합니다.

다만 이체에서는 입금 계좌 반영, 두 계좌의 상태 검증, 원장 기록까지 같은 트랜잭션으로 처리해야 합니다. 현재는 이 값을 확보하고 검증하기 위해 잠금 조회 후 엔티티를 변경합니다.

## 4.8 T08. 두 계좌를 오름차순으로 잠그는 이유는 무엇입니까?

반대 방향 이체에서 잠금 순서가 뒤집혀 데드락이 발생할 가능성을 줄이기 위해서입니다. A→B와 B→A 모두 같은 계좌 ID 순서로 잠금 조회를 요청합니다. 다만 실제 잠금 범위와 다른 쿼리의 영향이 있으므로 데드락이 완전히 없어지는 것은 아닙니다. [MySQL 공식 문서](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html)

## 4.9 T09. 실제 잠금 순서를 설명해 보십시오.

고객 잠금이 필요한 업무는 고객을 먼저 잠그고, 두 계좌는 ID 오름차순으로 잠금 조회합니다.

| 업무 | 잠금 조회 순서 |
|---|---|
| 개설 | CUSTOMER → 선택 ACCOUNT |
| 비우기 | 두 ACCOUNT의 ID 오름차순 조회 |
| 동전모으기 | 두 ACCOUNT의 ID 오름차순 조회 → COINBOX → 최신 ACCOUNT_CONTRACT |
| 해지 | CUSTOMER → 두 ACCOUNT의 ID 오름차순 조회 → COINBOX → 최신 ACCOUNT_CONTRACT |

## 4.10 T10. 조인 쿼리에 FOR UPDATE를 붙이면 여러 행이 잠깁니까?

네. 조인에 참여하는 여러 테이블의 여러 행에 잠금이 걸릴 수 있습니다. 실제 범위는 DBMS·인덱스·실행 계획·조회 조건·격리 수준에 따라 달라지므로, 반환된 결과 한 행이 잠금 한 개를 의미하지는 않습니다. [MySQL 잠금 설명](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

## 4.11 T11. 공통 이체에서 상품과 양쪽 계약까지 확인합니까?

아닙니다. 공통 이체는 두 계좌의 존재·ACTIVE 상태, 서로 다른 계좌인지, 양수 금액과 잔액 충분 여부를 확인합니다. 상품별 계약 규칙은 상위 업무 서비스에서 검증합니다.

현재 비우기는 저금통 유형을 확인하지만 계약은 조회하지 않습니다. 동전모으기와 해지는 저금통 계약을 확인합니다.

## 4.12 T12. 계약이 해지됐는데 계좌가 ACTIVE이면 어떻게 됩니까?

현재 공통 이체는 계약 상태를 조회하지 않으므로, 계좌 조건을 만족하면 이체를 허용할 수 있습니다. 이를 막기 위해 정상 해지 경로에서는 계좌 CLOSED와 계약 TERMINATED를 같은 트랜잭션으로 변경합니다.

## 4.13 T13. MANDATORY가 잠금을 보장합니까?

아닙니다. MANDATORY는 기존 트랜잭션 참여만 강제하며, 트랜잭션이 없으면 예외를 발생시킵니다. 잠금 획득 여부는 확인하지 않습니다.

transferLocked와 transferAllLocked는 호출자가 같은 트랜잭션에서 두 계좌를 잠갔다는 전제로 전달받은 객체를 재사용합니다. [Spring Propagation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html)

## 4.14 T14. REQUIRED와 REQUIRES_NEW를 여기서 어떻게 구분합니까?

REQUIRED는 기존 트랜잭션에 참여하고 없으면 새로 만들며, REQUIRES_NEW는 기존 트랜잭션을 일시 보류하고 독립적인 새 트랜잭션을 만듭니다.

이 과제에서는 이체와 실행 이력을 함께 커밋·롤백해야 하므로 같은 트랜잭션에 참여시킵니다. 이체만 REQUIRES_NEW로 분리하면 이체가 커밋된 뒤 실행 이력 저장이 실패해도 이체를 되돌릴 수 없습니다. [Spring 전파 옵션](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html)

## 4.15 T15. 예외가 발생하면 항상 롤백됩니까?

아닙니다. Spring 선언적 트랜잭션의 기본 설정은 RuntimeException과 Error를 롤백하고, checked exception은 rollbackFor 등으로 지정해야 합니다. 현재 BusinessException은 RuntimeException 계열입니다.

예외를 잡고 정상 반환하면 트랜잭션 경계에 실패가 전달되지 않을 수 있습니다. 다만 이미 rollback-only로 표시된 트랜잭션은 예외를 잡더라도 커밋할 수 없습니다. [Spring @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

## 4.16 T16. FOR UPDATE로 재조회하면 Java 객체도 최신입니까?

항상 최신이라고 보장할 수는 없습니다. FOR UPDATE로 DB 행을 잠그더라도 영속성 컨텍스트가 이미 관리 중인 같은 ID의 객체를 재사용하면 필드가 자동으로 갱신되지 않을 수 있습니다. 잠금 조회에서 처음 엔티티를 읽거나 refresh로 상태를 갱신하는 방식을 검토해야 합니다. [Jakarta Persistence EntityManager·refresh](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager)

## 4.17 T17. REPEATABLE READ와 잠금 읽기의 차이는 무엇입니까?

InnoDB의 REPEATABLE READ에서 일반 SELECT는 스냅샷을 읽고, SELECT FOR UPDATE는 읽을 행에 잠금을 거는 현재 읽기를 수행합니다.

- 일반 일관 읽기: 보통 해당 트랜잭션의 첫 일관 읽기에서 만든 스냅샷을 이후에도 사용하므로, 다른 트랜잭션의 이후 커밋은 보이지 않습니다.
- 잠금 읽기: 같은 스냅샷에 고정되지 않고 현재 행을 읽으며, 경쟁하는 변경이 있으면 잠금 해제를 기다릴 수 있습니다.

격리 수준은 트랜잭션의 읽기 규칙이고, FOR UPDATE는 특정 조회의 잠금 방식이므로 같은 층위의 개념은 아닙니다. [InnoDB 일관 읽기](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html)

## 4.18 T18. 온라인 요청도 멱등성을 보장합니까?

온라인 요청 전체에 대해 요청 단위 멱등성을 보장하지는 않습니다. 비우기에는 요청 ID와 처리 결과를 저장해 재사용하는 기능이 없습니다. 첫 비우기 후 새 돈이 들어오면 같은 요청의 재전송으로 새 잔액도 비울 수 있습니다.

요청 단위 멱등성을 보장하려면 요청 키와 처리 결과를 저장하고 동일 요청에는 기존 결과를 반환해야 합니다.

## 4.19 T19. UK 충돌을 잡고 같은 트랜잭션에서 계속하면 됩니까?

안전하다고 가정하면 안 됩니다. UK 위반으로 트랜잭션이 rollback-only가 되거나 영속성 컨텍스트를 계속 쓰기 어려워질 수 있습니다. 관련 변경을 롤백한 뒤 새 트랜잭션에서 기존 처리 결과를 확인하는 방식으로 대응해야 합니다.

## 4.20 T20. 동전모으기와 해지가 동시에 실행되면 어떻게 설명합니까?

같은 계좌·저금통·계약을 잠그므로 먼저 잠금을 확보한 업무를 기준으로 순차 처리되도록 설계했습니다.

- 동전모으기가 먼저 완료되면 해지는 저축된 금액까지 포함해 잔액을 회수합니다.
- 해지가 먼저 완료되면 배치는 종료 상태나 비활성 설정을 확인해 자금을 이동하지 않습니다.

이 결과를 보장하려면 잠금 후 객체가 최신 상태여야 하므로, 해당 교차 실행은 추가 동시성 검증 대상입니다.

## 4.21 코드 근거

- [계좌 Native 잠금 쿼리](../../src/main/java/com/kakaobank/coinbox/account/repository/AccountRepository.java)
- [가입·비우기·해지 트랜잭션](../../src/main/java/com/kakaobank/coinbox/coinbox/service/CoinBoxService.java)
- [공통 이체와 MANDATORY](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java)
- [이체 동시성 테스트](../../src/test/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferServiceIntegrationTest.java)
- [원장 실패 롤백 테스트](../../src/test/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferServiceRollbackIntegrationTest.java)
