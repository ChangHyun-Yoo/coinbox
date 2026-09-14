# 5. 배치 예상 질문과 답변

[목차](README.md) · [동전모으기 E2E](02_BUSINESS_FLOWS.md)

## 5.1 B01. Spring Batch 없이 스케줄러와 반복문만 써도 되지 않습니까?

가능합니다. 다만 실행 단위, 처리 상태, 트랜잭션 경계와 실행 메타데이터를 직접 관리해야 합니다. 이 과제는 스케줄러는 실행 시점을 정하고 Spring Batch는 Job·Step·청크 처리를 담당하도록 분리했습니다.

프레임워크가 제공하는 확장 가능성과 실제 구현 범위는 다릅니다. 현재 자동 Retry·Skip이나 실패 Job 재시작 API는 구현하지 않았습니다.

## 5.2 B02. 스케줄러와 Job의 책임은 어떻게 다릅니까?

@Scheduled는 서울 시간 기준 잔액 배치를 매일 00:00, 동전모으기를 평일 10:00에 호출합니다. BatchExecutionService는 업무 날짜와 실행 식별 파라미터를 만들고 Job을 시작합니다. Job과 Step은 실제 처리와 트랜잭션을 관리합니다.

spring.batch.job.enabled=false는 애플리케이션 시작 시 Job 자동 실행을 끄는 설정입니다. 실행 중인 @Scheduled까지 비활성화하는 설정이 아닙니다.

## 5.3 B03. Tasklet과 Chunk 중 무엇을 어떻게 사용했습니까?

일별 잔액은 대상 조회와 일괄 저장이라는 단순 작업이라 Tasklet으로 구성했습니다. 동전모으기는 후보별 검증·자금 이동·결과 기록이 필요해 Reader와 Writer를 사용하는 Chunk Step으로 구성했습니다.

Tasklet이 반드시 트랜잭션이 없거나 모든 Tasklet이 대용량 처리에 유리한 것은 아닙니다. 현재 잔액 Tasklet은 대상 전체를 메모리에 적재하고 하나의 Step 작업으로 저장합니다.

## 5.4 B04. JdbcTemplate과 Spring Batch는 대체 관계입니까?

아닙니다. Spring Batch는 실행 흐름과 처리 단위를 관리하고 JdbcTemplate은 SQL 실행을 돕습니다. 현재 잔액 배치는 Spring Batch Tasklet 안에서 JPA Native Query로 읽고 JdbcTemplate.batchUpdate로 저장합니다.

Spring Batch의 실행 관리와 JdbcTemplate의 SQL 실행을 함께 사용한 구조입니다. 두 도구의 책임을 구분해서 설명합니다.

## 5.5 B05. Reader·Processor·Writer는 각각 무엇을 합니까?

Reader는 처리 후보와 전일 잔액을 조회합니다. 현재 Processor는 구성하지 않았습니다. Writer가 CoinSavingService를 호출하며, 서비스가 잠금·재검증·금액 계산·이체·실행 이력 저장을 수행합니다.

Processor는 선택 사항입니다. 계산기를 별도 클래스로 분리한 것과 Batch ItemProcessor를 등록한 것은 다릅니다. [Spring Batch 청크 처리](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html)

## 5.6 B06. 재검증할 것인데 후보 조회는 왜 필요합니까?

후보 조회는 대상 수를 줄여 불필요한 잠금과 금융 검증을 줄이는 역할입니다. 재검증은 후보 조회 후 바뀔 수 있는 현재 설정·상태·잔액을 처리 시점에 판단하는 역할입니다.

전자는 효율, 후자는 정확성이라는 목적이 다르므로 중복으로만 보지 않았습니다. 현재 Reader는 계약과 정책까지 모두 조인하지 않습니다.

## 5.7 B07. 조회에 조인이 많으면 전체 대상이 잠기지 않습니까?

일반 후보 SELECT에는 FOR UPDATE가 없으므로 금융 변경용 행 잠금을 페이지 전체에 걸어두지 않습니다. 실제 변경은 후보별 트랜잭션에서 필요한 행을 잠급니다.

그렇다고 일반 조회가 DB 자원을 쓰지 않거나 어떠한 잠금도 없다는 뜻은 아닙니다. SQL 비용·메타데이터 잠금·MVCC 비용 등은 별도로 관찰해야 합니다.

## 5.8 B08. 페이지 크기 1,000과 청크 크기 1의 차이는 무엇입니까?

페이지 크기는 Reader가 후보를 가져오는 단위이고 청크 크기는 Writer 처리 후 커밋하는 단위입니다. 현재 후보는 1,000개씩 읽되 한 저금통씩 커밋합니다.

DB 읽기 왕복과 실패 격리 범위를 별도로 조절하려는 설정입니다. 청크 1은 커밋 횟수와 메타데이터 갱신 비용을 늘리므로 무조건 최적의 처리량 설정은 아닙니다. [Spring Batch 청크 처리](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html)

## 5.9 B09. 처리한 후보가 조회 결과에서 빠지면 페이징이 누락되지 않습니까?

현재 JdbcPagingItemReader와 MySqlPagingQueryProvider는 유일한 coinbox_id를 정렬 키로 사용합니다. 이후 페이지를 정렬 키 기준으로 이어가는 방식이므로 매번 줄어드는 결과 집합에 OFFSET만 적용하는 방식과 구분합니다.

정렬 키의 유일성과 안정성이 중요합니다. 실행 중 새로 후보 조건을 만족한 과거 ID까지 모두 포착하는 고정 대상 집합이나 실시간 큐가 되는 것은 아닙니다. [Spring Batch DB Reader](https://docs.spring.io/spring-batch/reference/readers-and-writers/database.html)

## 5.10 B10. 왜 청크 크기를 1로 했습니까?

한 저금통의 이체·원장·실행 결과를 함께 확정하고 실패 시 그 단위만 롤백하려고 선택했습니다. 앞서 커밋한 저금통은 이후 후보 실패 때문에 되돌리지 않습니다.

단, 실패한 후보 다음의 나머지를 계속 처리한다는 뜻은 아닙니다. 현재는 Skip 설정이 없으므로 예상하지 못한 오류가 발생하면 해당 Step이 중단됩니다.

## 5.11 B11. 정책 캐시는 정확히 언제 비워집니까?

CoinSavingPolicyResolver는 @StepScope이며 내부 Map을 사용합니다. 한 Step 실행에서 상품 버전별 최초 접근 때 정책을 조회하고 재사용합니다. 새 Step 실행에서는 새 캐시가 만들어집니다.

하루 동안 유지되는 TTL 캐시가 아닙니다. 같은 날짜에 수동으로 다시 실행해도 새 Step에서 정책을 다시 조회합니다. 후보가 없어 해당 버전이 사용되지 않으면 선행 조회도 하지 않습니다. [Spring Batch Step Scope](https://docs.spring.io/spring-batch/reference/step/late-binding.html)

## 5.12 B12. 캐시 키가 저금통 ID가 아니라 상품 버전 ID인 이유는 무엇입니까?

같은 버전에 가입한 여러 저금통이 같은 정책을 사용하므로 중복 조회 제거 효과가 버전 단위에서 발생합니다. 대상 저금통 N개가 V개 버전을 사용하면 성공적으로 로드되는 정책은 한 Step에서 버전별 한 번, 즉 V회로 제한하는 구조입니다.

계좌·설정·계약 등 현재 업무 상태까지 캐시한 것은 아닙니다. 그 값들은 경쟁 변경을 고려해 처리 시점에 검증합니다.

## 5.13 B13. 배치 중 정책이 바뀌면 어떻게 됩니까?

이미 읽은 버전은 그 Step에서 최초로 읽은 정책 스냅샷을 사용합니다. 아직 읽지 않은 다른 버전은 최초 접근 시점에 조회하므로 모든 버전의 전역 동시 스냅샷을 보장하는 것은 아닙니다.

현재 설계는 버전별 정책을 불변으로 운영한다는 전제를 둡니다. 전체 Job에 하나의 기준 시점이 필요하다면 시작 시 정책 일괄 고정이나 별도 정책 revision 기록을 검토해야 합니다.

## 5.14 B14. 멀티스레드로 바꾸면 지금 캐시도 안전합니까?

현재 HashMap과 단일 Step 흐름을 전제로 작성했습니다. 단순히 작업 스레드만 늘리면 동시 접근과 중복 정책 로딩을 검토해야 합니다.

파티션별 Step 인스턴스, 스레드 안전한 캐시, 트랜잭션 경계와 DB 커넥션 수를 함께 설계해야 합니다. @StepScope가 붙었다는 이유만으로 공유되는 내부 자료구조가 자동으로 스레드 안전해지는 것은 아닙니다.

## 5.15 B15. 저축 금액의 결정식을 설명해 보십시오.

전일 근거계좌 잔액의 1,000원 미만 잔돈을 구하고, 실제 저축 금액은 그 금액과 남은 저금통 한도 중 작은 값입니다. 실행 시점 근거 잔액이 1,000원 이하이면 저축하지 않습니다.

예를 들어 전일 잔액이 12,850원이고 현재 저금통이 99,700원이면 850원이 아닌 300원만 저축합니다. 이전 잔액은 예정액 결정에, 현재 잔액·정책은 실제 실행 가능 여부에 사용됩니다.

## 5.16 B16. 시작일을 오늘로 저장했는데 왜 당일 저축하지 않습니까?

활성 시작일이 실행일보다 이전이어야 한다는 별도 실행 조건이 있기 때문입니다. 가입 당일 설정은 켜져 있지만 그날은 대상이 아니며, 시작일 다음 날 이후의 스케줄에서 처리 가능합니다.

주말·공휴일을 모두 영업일로 계산하는 것은 아닙니다. 자동 스케줄은 월~금, 공휴일은 별도로 제외하지 않는 과제 규칙입니다.

## 5.17 B17. 일별 중복 저축은 어떻게 방지합니까?

Reader에서 이미 실행 이력이 있는 후보를 제외하고, 처리 트랜잭션에서도 잠금 후 실행 이력을 다시 확인합니다. 최종적으로 COIN_SAVING_EXECUTION(coinbox_id, execution_date)의 UK가 중복 행을 방지합니다.

이체와 실행 이력을 같은 트랜잭션으로 묶었으므로 이력 저장 실패 시 이체도 롤백되는 것이 중요합니다. UK가 이체와 분리된 별도 트랜잭션에 있다면 같은 방어 효과를 주장할 수 없습니다.

## 5.18 B18. Batch 메타데이터가 있는데 업무 실행 테이블은 왜 필요합니까?

Job·Step 메타데이터는 배치 실행 상태와 진행 정보를 관리합니다. 업무 실행 테이블은 특정 저금통의 특정 날짜 결과·금액·사유·금융거래 연결을 관리합니다.

새 JobInstance로 같은 업무 날짜를 다시 실행해도 업무 중복을 막아야 하므로 비즈니스 키가 필요합니다. 프레임워크 실행 식별자와 업무 멱등성 키는 목적이 다릅니다.

## 5.19 B19. 지금 수동 실행은 실패 지점부터 Restart합니까?

아닙니다. 현재 실행 서비스는 executionDate 외에 launchedAt과 launchSequence를 식별 파라미터로 추가해 새로운 실행을 시작합니다. 재실행 시 업무 실행 이력으로 이미 처리한 저금통을 제외합니다.

실패한 JobInstance의 저장된 ExecutionContext를 이용하는 메타데이터 기반 Restart API는 구현하지 않았습니다. '미처리 업무 재실행'과 '기존 Job 재시작'을 구분합니다.

## 5.20 B20. 100번째 저금통 처리에서 오류가 나면 무엇이 남습니까?

앞서 커밋된 99개 결과는 유지되고 100번째 청크의 이체·원장·실행 이력은 롤백됩니다. 이후 후보는 현재 실행에서 처리되지 않으며 Step은 실패합니다.

원인을 해결한 뒤 같은 업무 날짜로 수동 실행하면 저장된 결과가 있는 후보를 제외해 남은 대상을 처리할 수 있습니다. 동일한 원인이 남아 있으면 다시 실패하므로 재실행만으로 복구가 끝난 것은 아닙니다.

## 5.21 B21. SKIPPED와 EXCLUDED는 왜 다릅니까?

SKIPPED는 전일 잔액 없음, 잔돈 없음, 잔액 부족, 한도 도달 등 그날의 업무상 처리 결과를 저장합니다. EXCLUDED는 잠금 후 설정이 꺼졌거나 시작일 조건을 만족하지 않는 후보이며 새 이력을 만들지 않습니다.

따라서 SKIPPED는 같은 날짜 재실행에서도 다시 저축하지 않습니다. 설정이나 자료가 나중에 바뀌었다고 무조건 재처리할 수 있는 구조가 아니므로 보정 정책은 별도로 필요합니다.

## 5.22 B22. FAILED와 SYSTEM_ERROR는 언제 저장합니까?

현재 Enum에는 존재하지만 일반 시스템 실패를 별도 복구 트랜잭션에서 FAILED/SYSTEM_ERROR 행으로 저장하는 흐름은 없습니다. 정상 결과는 SUCCESS·SKIPPED이며 예상하지 못한 오류는 청크 롤백과 Batch Step 실패로 처리합니다.

실패 기록을 추가할 때도 '실패 행이 있으니 Reader가 영원히 제외한다'는 문제가 생기지 않도록 재처리 가능 상태, 업무 키, 시도 이력을 함께 설계해야 합니다.

## 5.23 B23. 잔액 배치를 재실행하면 기존 금액도 갱신합니까?

아닙니다. ON DUPLICATE KEY UPDATE에서 기존 ID를 자기 자신으로 설정하는 방식으로 기존 계좌·기준일 값을 유지합니다. 새로 없는 행만 추가합니다.

중복 방지와 기준 시점 정확성은 별개입니다. 잘못된 기준 시점 값이 먼저 저장되었으면 UK가 그 값을 올바른 전일 최종 잔액으로 교정해 주지 않습니다. 과거 날짜 수동 실행은 현재 잔액을 과거 기준일에 기록할 수 있습니다.

## 5.24 B24. 배치 운영에서 가장 먼저 보완할 것은 무엇입니까?

금융 기준일·마감 정확성과 실패 복구 정책을 먼저 명확히 하겠습니다. 이후 재시도 가능 오류 분류, 제한된 재시도, 실패 사유와 복구 절차, 다중 인스턴스 스케줄 조정, 대용량 성능 측정을 추가하겠습니다.

잔액 Tasklet은 전체 결과를 메모리에 적재하므로 대상 규모에 따라 범위 분할·청크 저장·집합 기반 SQL을 비교해야 합니다. 동전모으기는 단순히 병렬도를 높이기 전에 온라인 요청과의 계좌 잠금 경합, 커넥션 풀, 정책 캐시를 함께 측정해야 합니다.

## 5.25 구현 근거

- [Job·Reader 설정](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/batch/CoinSavingJobConfig.java)
- [Writer](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/batch/CoinSavingItemWriter.java)
- [후보 처리 서비스](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingService.java)
- [정책 캐시](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingPolicyResolver.java)
- [수동·자동 실행 서비스](../../src/main/java/com/kakaobank/coinbox/common/batch/service/BatchExecutionService.java)
- [잔액 JDBC 저장](../../src/main/java/com/kakaobank/coinbox/accountdailybalance/repository/DailyBalanceJdbcRepository.java)
- [배치 통합 테스트](../../src/test/java/com/kakaobank/coinbox/coinsavingexecution/service/BatchLifecycleIntegrationTest.java)

외부 문서는 일반 동작의 참고 자료입니다. 현재 프로젝트의 실제 옵션·처리 순서는 연결한 소스를 우선 확인합니다.
