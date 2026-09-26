# 5. 배치 예상 질문과 답변

[목차](README.md) · [동전모으기 E2E](02_BUSINESS_FLOWS.md)

## 5.1 B01. Spring Batch 없이 스케줄러와 반복문만 써도 되지 않습니까?

가능하지만, 실행 상태·진행 정보·트랜잭션 경계를 직접 관리해야 합니다. 이 과제에서는 이를 Job·Step·실행 메타데이터로 관리하고, 저금통별 커밋을 Chunk로 구성하기 위해 Spring Batch를 사용했습니다.

## 5.2 B02. 스케줄러와 Job의 책임은 어떻게 다릅니까?

스케줄러는 언제 실행할지, Job은 무엇을 처리할지를 담당합니다. @Scheduled가 서울 시간 매일 00:00에 잔액 배치, 월~금 10:00에 동전모으기를 요청하면 BatchExecutionService가 기준일 파라미터를 만들어 Job을 시작합니다.

## 5.3 B03. Tasklet과 Chunk 중 무엇을 어떻게 사용했습니까?

일별 잔액 저장에는 단순 작업을 직접 실행하는 Tasklet을, 동전모으기에는 항목을 읽고 일정 단위로 처리·커밋하는 Chunk를 사용했습니다.

| 구분 | Tasklet | Chunk |
|---|---|---|
| 처리 방식 | execute에 작업을 작성하고 완료 또는 반복 여부를 반환합니다. | Reader로 읽고 선택적으로 Processor를 거쳐 Writer로 처리합니다. |
| 트랜잭션 단위 | execute 호출 단위입니다. | 지정한 청크 단위입니다. |
| 과제 적용 | 잔액 전체 조회 → 저장 행 구성 → JDBC 일괄 저장입니다. | 후보 조회 → 저금통별 검증·이체·결과 기록이며 청크 크기는 1입니다. |
| 선택 이유 | 조회한 값을 일괄 저장하는 단순 흐름을 표현하기 쉽습니다. | 저금통별 처리와 커밋 경계를 구분하기 쉽습니다. |

현재 잔액 Tasklet은 전체 대상을 메모리에 올리므로 대상이 커지면 분할 처리가 필요합니다. [TaskletStep](https://docs.spring.io/spring-batch/reference/step/tasklet.html) · [청크 처리](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html)

## 5.4 B04. JdbcTemplate과 Spring Batch는 대체 관계입니까?

아닙니다. Spring Batch는 작업의 실행·상태·트랜잭션 흐름을 관리하고, JdbcTemplate은 SQL 실행을 담당하므로 함께 사용할 수 있습니다. 현재 잔액 배치는 Tasklet 안에서 JPA Native Query로 읽고 JdbcTemplate.batchUpdate로 저장합니다.

**왜 잔액 저장은 JPA saveAll이 아니라 JDBC입니까?**

스냅샷을 일괄 INSERT하면서 같은 계좌·기준일이 이미 있으면 기존 값을 유지하는 SQL을 직접 표현하기 위해서입니다. 저장할 엔티티를 관리 상태로 만들 필요는 없지만, ID와 감사 시각은 직접 채워야 합니다.

## 5.5 B05. Reader·Processor·Writer는 각각 무엇을 합니까?

Reader는 후보 조회, Processor는 선택적인 변환·필터링, Writer는 결과 반영을 담당합니다. 현재 구현은 다음과 같습니다.

- Reader: 저금통 후보와 전일 근거계좌 잔액을 페이징 조회합니다.
- Processor: 별도로 구성하지 않았습니다.
- Writer: CoinSavingService를 호출해 잠금·재검증·계산·이체·실행 이력 저장을 수행합니다.

별도의 변환 단계보다 잠금 후 판단과 금융 반영을 한 서비스 호출에 모으는 구성이 적합해 Processor를 생략했습니다. [Spring Batch 청크 처리](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html)

## 5.6 B06. 재검증할 것인데 후보 조회는 왜 필요합니까?

후보 조회는 처리 대상을 줄이고, 재검증은 실제 처리 시점의 유효성을 확인하기 위해 필요합니다. 처음부터 모든 저금통을 잠그는 대신 활성 설정과 실행 이력 등으로 후보를 추린 뒤, 처리할 저금통의 상태·잔액·설정을 잠금 후 다시 확인합니다.

## 5.7 B07. 조회에 조인이 많으면 전체 대상이 잠기지 않습니까?

아닙니다. 현재 후보 SELECT에는 FOR UPDATE가 없으므로 조인이 많다는 이유로 후보 전체에 변경용 행 잠금을 걸지는 않습니다. 실제 금융 변경에 필요한 행은 저금통별 처리 트랜잭션에서 잠급니다.

## 5.8 B08. 페이지 크기 1,000과 청크 크기 1의 차이는 무엇입니까?

페이지 크기는 한 번에 조회하는 수이고, 청크 크기는 한 번에 처리·커밋하는 수입니다. 현재는 후보를 1,000개씩 가져오고 한 저금통씩 커밋합니다. 조회 왕복 횟수와 트랜잭션 크기를 각각 조절하는 설정입니다.

## 5.9 B09. 처리한 후보가 조회 결과에서 빠지면 페이징이 누락되지 않습니까?

현재는 유일한 coinbox_id를 정렬 키로 이어 읽으므로, 앞에서 처리한 행이 결과에서 빠져도 OFFSET 이동에 따른 누락을 피할 수 있습니다. JdbcPagingItemReader와 MySqlPagingQueryProvider가 이후 페이지를 마지막 정렬 키 기준으로 조회합니다.

다만 실행 도중 이미 지나간 ID가 새로 후보 조건을 만족하는 경우까지 포착하는 구조는 아닙니다. [Spring Batch DB Reader](https://docs.spring.io/spring-batch/reference/readers-and-writers/database.html)

## 5.10 B10. 왜 청크 크기를 1로 했습니까?

실패 시 롤백 범위를 한 저금통으로 제한하기 위해서입니다. 이체·원장·실행 이력을 한 청크로 확정하므로 뒤의 저금통이 실패해도 앞서 커밋한 결과는 유지됩니다. 대신 건별 커밋 비용이 늘어나며, 현재는 오류가 나면 해당 청크 롤백 후 Step이 중단됩니다.

## 5.11 B11. 정책 캐시는 정확히 언제 비워집니까?

새 Step 실행이 시작될 때 새 캐시가 만들어집니다. CoinSavingPolicyResolver가 @StepScope이므로 한 실행에서 조회한 정책만 재사용하고, 같은 날짜에 수동 재실행해도 다시 조회합니다. [Spring Batch Step Scope](https://docs.spring.io/spring-batch/reference/step/late-binding.html)

## 5.12 B12. 캐시 키가 저금통 ID가 아니라 상품 버전 ID인 이유는 무엇입니까?

정책을 공유하는 단위가 저금통이 아니라 상품 버전이기 때문입니다. 같은 버전에 가입한 저금통들은 동일한 한도를 사용하므로 product_version_id를 키로 두면 한 Step에서 버전별로 한 번만 조회할 수 있습니다.

## 5.13 B13. 배치 중 정책이 바뀌면 어떻게 됩니까?

이미 캐시한 버전은 현재 Step에서 기존 값을 사용하고, 아직 조회하지 않은 버전은 최초 조회 시점의 값을 사용합니다. 따라서 모든 버전의 정책을 한 시점에 고정하는 전역 스냅샷은 아닙니다. 새 Step에서는 정책을 다시 조회합니다.

## 5.14 B14. 멀티스레드로 바꾸면 지금 캐시도 안전합니까?

아니요. 현재 HashMap 캐시는 단일 스레드 처리를 전제로 하므로 같은 인스턴스를 여러 스레드가 사용하면 안전하지 않습니다. 멀티스레드로 바꾸려면 스레드 안전한 캐시와 중복 로딩 방지, 또는 파티션별 Step 캐시 분리를 검토해야 합니다.

## 5.15 B15. 저축 금액의 결정식을 설명해 보십시오.

저축 금액은 전일 근거계좌 잔액의 1,000원 미만 잔돈과 남은 저금통 한도 중 작은 금액입니다.

- 예정액 = 전일 근거계좌 잔액 % 1,000
- 남은 한도 = 정책 한도 − 현재 저금통 잔액
- 실제 저축액 = min(예정액, 남은 한도)

현재 근거계좌 잔액이 1,000원 이하이거나 전일 잔액 누락·잔돈 없음·한도 도달 상태이면 저축하지 않습니다. 전일 잔액이 12,850원이고 저금통 잔액이 99,700원, 한도가 100,000원이면 300원만 저축합니다.

## 5.16 B16. 시작일을 오늘로 저장했는데 왜 당일 저축하지 않습니까?

대상 조건이 '시작일 < 실행일'이기 때문입니다. 가입 당일에는 설정이 켜져 있어도 이 조건을 만족하지 않고, 다음 날부터 월~금 10:00 자동 실행의 대상이 됩니다.

## 5.17 B17. 일별 중복 저축은 어떻게 방지합니까?

사전 조회와 잠금 후 중복 확인, 복합 UK를 함께 사용합니다.

1. Reader에서 해당 날짜의 실행 이력이 있는 저금통을 제외합니다.
2. 처리 트랜잭션에서도 잠금 후 실행 이력을 확인합니다.
3. COIN_SAVING_EXECUTION(coinbox_id, execution_date)의 UK로 중복 저장을 막습니다.

이체와 실행 이력을 같은 트랜잭션으로 저장하므로 이력 저장에 실패하면 이체도 롤백됩니다.

## 5.18 B18. Batch 메타데이터가 있는데 업무 실행 테이블은 왜 필요합니까?

Batch 메타데이터는 Job·Step의 실행을, 업무 실행 테이블은 저금통·일자별 처리 결과를 관리하기 때문입니다. 새 JobInstance로 같은 날짜를 다시 실행해도 중복 저축을 막으려면 별도의 업무 키와 결과 이력이 필요합니다.

## 5.19 B19. 지금 수동 실행은 실패 지점부터 Restart합니까?

아닙니다. 매번 새로운 JobInstance를 시작하고, 업무 실행 이력으로 이미 처리한 저금통을 제외합니다. executionDate 외에 launchedAt과 launchSequence를 식별 파라미터로 추가하므로 기존 ExecutionContext를 이어 쓰는 Restart가 아니라 미처리 대상 재실행입니다.

## 5.20 B20. 100번째 저금통 처리에서 오류가 나면 무엇이 남습니까?

앞선 99개의 커밋 결과는 남고, 100번째 저금통의 이체·원장·실행 이력은 롤백됩니다. 현재는 오류를 건너뛰는 설정이 없으므로 Step이 실패하고 이후 후보는 처리하지 않습니다. 원인을 해결한 뒤 같은 날짜로 재실행하면 저장된 결과가 있는 대상을 제외합니다.

## 5.21 B21. SKIPPED와 EXCLUDED는 왜 다릅니까?

SKIPPED는 그날의 업무상 건너뜀 결과를 저장하고, EXCLUDED는 처리 대상 조건에서 벗어난 후보라 새 이력을 저장하지 않습니다.

- SKIPPED: 전일 잔액 없음, 잔돈 없음, 잔액 부족, 한도 도달 등입니다. 같은 날짜에 재실행해도 다시 처리하지 않습니다.
- EXCLUDED: 잠금 후 설정이 꺼져 있거나 시작일 조건을 만족하지 않는 경우입니다. 이력이 없으므로 이후 조건을 만족하면 다시 후보가 될 수 있습니다.

## 5.22 B22. FAILED와 SYSTEM_ERROR는 언제 저장합니까?

현재는 저장하지 않습니다. FAILED와 SYSTEM_ERROR는 Enum에만 정의돼 있고, 시스템 오류가 발생하면 해당 청크를 롤백하고 Batch Step을 실패 처리합니다. 업무 실행 테이블에 저장하는 결과는 SUCCESS와 SKIPPED입니다.

## 5.23 B23. 잔액 배치를 재실행하면 기존 금액도 갱신합니까?

아닙니다. 같은 계좌·기준일 데이터가 있으면 기존 금액을 유지하고 없는 행만 추가합니다. ON DUPLICATE KEY UPDATE에서 ID를 자기 자신으로 설정해 기존 값을 덮어쓰지 않도록 했습니다.

## 5.24 B24. 배치 운영에서 가장 먼저 보완할 것은 무엇입니까?

정확한 잔액 기준 시점과 실패 복구 절차부터 보완하겠습니다. 현재 잔액 배치는 조회 시점 잔액을 지정 기준일로 저장하고, 동전모으기는 시스템 오류 시 Step이 중단되기 때문입니다.

이후 재시도 가능한 오류 분류·보정 대상 처리·다중 서버 중복 스케줄 조정·대용량 분할 처리를 정하겠습니다.

## 5.25 구현 근거

- [Job·Reader 설정](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/batch/CoinSavingJobConfig.java)
- [Writer](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/batch/CoinSavingItemWriter.java)
- [후보 처리 서비스](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingService.java)
- [정책 캐시](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingPolicyResolver.java)
- [수동·자동 실행 서비스](../../src/main/java/com/kakaobank/coinbox/common/batch/service/BatchExecutionService.java)
- [잔액 JDBC 저장](../../src/main/java/com/kakaobank/coinbox/accountdailybalance/repository/DailyBalanceJdbcRepository.java)
- [배치 통합 테스트](../../src/test/java/com/kakaobank/coinbox/coinsavingexecution/service/BatchLifecycleIntegrationTest.java)
