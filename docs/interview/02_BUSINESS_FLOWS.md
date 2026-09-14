# 2. 업무 E2E와 데이터 흐름

[면접 준비 목차](README.md) · [원본 시퀀스](../02_SEQUENCE_DIAGRAM.md) · [원본 데이터 예시](../03_DATA_FLOW.md)

## 2.1 설명 원칙

각 업무를 `누가 요청 → 무엇을 확인 → 무엇을 잠금 → 어떤 데이터를 변경 → 언제 확정 → 실패하면 무엇이 남는가` 순서로 설명합니다. 아래 금액 예시는 설명용이며 data.sql의 특정 고객 데이터를 그대로 재현한 것은 아닙니다.

고객 API의 X-Customer-Id는 상위 인증 계층이 검증했다고 가정한 값입니다. 현재 애플리케이션이 헤더만으로 실제 인증을 수행하는 것은 아닙니다.

## 2.2 저금통 신규 가입

### 2.2.1 가입 가능 계좌 조회

1. GET /api/v1/coinboxes/eligible-accounts를 호출합니다.
2. 고객 존재와 ACTIVE 상태를 확인합니다.
3. 고객의 COINBOX 계좌 중 CLOSED가 아닌 계좌가 있는지 확인합니다. RESTRICTED인 저금통도 이용 중인 것으로 취급합니다.
4. 본인 소유, DEMAND_DEPOSIT, ACTIVE, parent_account_id가 없는 계좌를 조회합니다.
5. 후보가 없으면 가입 가능한 입출금계좌가 없다는 업무 예외를 반환합니다.

개인사업자통장과 모임통장은 product_type으로 제외합니다. 계좌번호 접두어는 자격 판단 근거가 아닙니다. 이 조회는 이후 개설 성공을 예약하거나 보장하지 않습니다.

### 2.2.2 실제 개설

1. POST /api/v1/coinboxes에 선택 계좌 ID를 보냅니다.
2. 서비스 트랜잭션을 시작하고 CUSTOMER 행을 FOR UPDATE로 잠급니다.
3. 선택한 ACCOUNT 행을 잠급니다.
4. 이용 중인 저금통 유무, 고객 상태, 계좌 소유·유형·상태·부모 여부를 확인합니다.
5. 개설일에 유효한 상품 버전과 저금통 정책을 조회합니다.
6. 새 계좌번호와 Snowflake ID를 생성합니다.
7. ACCOUNT·ACCOUNT_CONTRACT·COINBOX를 저장하고 커밋합니다.

| 테이블 | 생성되는 데이터의 의미 |
|---|---|
| ACCOUNT | product_type=COINBOX, parent_account_id=선택 계좌, balance=0, status=ACTIVE, 개설일=당일입니다. |
| ACCOUNT_CONTRACT | 새 계좌와 적용 상품 버전을 연결합니다. ACTIVE이며 종료일은 9999-12-31입니다. |
| COINBOX | 새 계좌를 연결하고 coin_saving_enabled=true, coin_saving_start_date=당일로 저장합니다. |

개설 자체에는 돈이 움직이지 않으므로 금융거래·계좌 원장을 만들지 않습니다. 중간 저장이 실패하면 세 데이터의 생성이 함께 롤백됩니다.

### 2.2.3 면접 답변

> 가입 가능 계좌 조회는 사용자 선택을 돕는 조회이며, 개설 때 고객과 선택 계좌를 잠근 상태에서 조건을 확인합니다. 서로 다른 근거계좌로 동시에 신청할 수 있으므로 계좌 잠금만으로는 부족하고, 고객 행을 공통 잠금 대상으로 사용했습니다. 고객당 이용 중인 저금통 하나를 강제하는 별도 DB 유일 제약은 없기 때문에 모든 개설·해지 경로가 이 규칙을 따라야 합니다.

## 2.3 저금통 비우기

### 2.3.1 처리 흐름

1. POST /api/v1/coinboxes/{accountNumber}/empty를 호출합니다.
2. customerId와 계좌번호로 본인 소유 저금통을 확인합니다.
3. 저금통 ACTIVE 여부, 0원이 아닌지, 근거계좌 연결 여부를 확인합니다.
4. 비우기용 원장 코드·적요를 지정해 공통 이체 서비스의 transferAll을 호출합니다.
5. 이체 서비스는 두 ACCOUNT를 ID 오름차순 SQL로 잠그고 ACTIVE와 출금 가능 잔액을 검증합니다.
6. 전액을 근거계좌로 이전하고 거래 한 건과 원장 두 건을 생성합니다.
7. 같은 트랜잭션으로 커밋합니다.

| 대상 | 이전 | 이후 |
|---|---:|---:|
| 저금통 잔액 | 12,000원 | 0원 |
| 근거계좌 잔액 | 30,000원 | 42,000원 |
| FINANCIAL_TRANSACTION | 없음 | TRANSFER, 12,000원 한 건 |
| 저금통 ACCOUNT_ENTRY | 없음 | WITHDRAWAL, 12,000원, 12,000 → 0 |
| 근거계좌 ACCOUNT_ENTRY | 없음 | DEPOSIT, 12,000원, 30,000 → 42,000 |

계좌·계약·동전모으기 설정은 유지합니다. 따라서 비우기는 해지가 아니며 이후 다시 저축할 수 있습니다.

### 2.3.2 중요한 경계

- 금융거래 유형은 TRANSFER입니다. COINBOX_EMPTY는 원장 업무 코드이며 한글 적요는 '비우기'입니다.
- 현재 비우기 경로는 계약 상태를 별도로 조회하지 않습니다. 모든 업무가 양쪽 계약 ACTIVE를 확인한다고 설명하면 코드와 다릅니다.
- 0원은 정상 이체가 아니라 빈 저금통 업무 예외입니다.
- 네트워크 재요청의 최초 성공 응답을 복원하는 Idempotency-Key는 없습니다.
- 잠금 전 조회한 엔티티를 같은 영속성 컨텍스트에서 다시 잠그는 경로의 최신성은 [추가 검증 항목](07_DESIGN_REVIEW_AND_MOCK_INTERVIEW.md)에 따로 정리했습니다. SQL 재실행과 객체 상태 갱신을 동일시하지 않습니다.

## 2.4 자동저축 전 준비: 일별 잔액

### 2.4.1 처리 흐름

1. 서울 시간 매일 00:00에 실행하며 balanceDate는 실행일의 전날입니다.
2. Tasklet에서 ACTIVE·RESTRICTED 계좌의 현재 잔액을 JPA Native Query로 조회합니다.
3. 애플리케이션에서 각 행의 Snowflake ID와 감사 시각을 구성합니다.
4. JdbcTemplate.batchUpdate로 ACCOUNT_DAILY_BALANCE를 저장합니다.
5. 동일 계좌·기준일 행이 이미 있으면 기존 값을 덮어쓰지 않습니다.

| 저장 키 | 저장 값의 예 |
|---|---|
| 근거계좌 A, 2026-09-13 | closing_balance=12,850 |

### 2.4.2 정확하게 표현해야 하는 한계

현재 구현은 조회 시점의 잔액에 기준일을 붙여 보관하는 방식입니다. 스케줄이 늦거나 수동으로 과거 날짜를 지정하더라도 해당 과거 시각의 잔액이 자동 복원되지는 않습니다. 정식 전일 마감을 보장하려면 거래 기준 시각·회계일·마감 경계·원장 집계 정책이 필요합니다.

## 2.5 저금통 자동저축: 동전모으기

### 2.5.1 후보 조회

서울 시간 월요일~금요일 10:00에 실행하며 공휴일은 별도로 제외하지 않습니다. 수동 API는 같은 달력 제한을 강제하지 않습니다.

Reader는 잠금 없이 저금통 ID 오름차순으로 후보를 조회합니다. 조건은 동전모으기 활성, 시작일 < 실행일, 근거계좌 존재, 해당 일자 실행 이력 없음입니다. 전일 근거계좌 잔액을 LEFT JOIN하므로 전일 자료 누락도 처리 사유로 남길 수 있습니다.

페이지 크기는 1,000이며 전체 후보의 상태를 조회 시점에 확정하지 않습니다.

### 2.5.2 후보 한 건 처리

1. 청크 크기 1의 트랜잭션에서 근거계좌·저금통계좌를 ID 오름차순으로 잠급니다.
2. COINBOX와 최신 ACCOUNT_CONTRACT를 잠급니다.
3. 소유 고객·근거계좌 연결·상품 유형을 재검증합니다.
4. 같은 저금통·실행일 이력이 있으면 DUPLICATE로 종료합니다.
5. 설정이 꺼졌거나 시작일 조건을 만족하지 않으면 EXCLUDED로 종료하며 실행 이력은 만들지 않습니다.
6. 계좌가 ACTIVE가 아니면 ACCOUNT_NOT_ACTIVE로 SKIPPED를 저장합니다.
7. 계약 상태와 유효기간을 확인합니다. 계약 누락·비정상은 정상 건너뜀이 아니라 오류로 처리합니다.
8. 계약의 상품 버전 정책을 Step 범위 캐시에서 가져옵니다.
9. 순수 계산기로 저축 금액 또는 건너뜀 사유를 결정합니다.
10. 가능하면 잠긴 계좌를 공통 이체 서비스에 전달해 이체합니다.
11. SUCCESS 실행 이력을 거래 ID와 함께 저장하고, 이체와 함께 커밋합니다.

### 2.5.3 계산 기준과 예시

예정액은 전일 잔액 % 1,000입니다. 현재 근거계좌 잔액이 1,000원 이하이거나, 예정액이 0원인 경우 등에는 저축하지 않습니다. 한도를 넘지 않도록 예정액과 남은 한도 중 작은 금액을 사용합니다.

| 전일 근거 잔액 | 실행 시 근거 잔액 | 실행 시 저금통 잔액 | 한도 | 결과 |
|---:|---:|---:|---:|---|
| 12,850 | 8,000 | 20,000 | 100,000 | 850원 저축 |
| 12,850 | 8,000 | 99,700 | 100,000 | 남은 한도인 300원만 저축 |
| 12,850 | 1,000 | 20,000 | 100,000 | INSUFFICIENT_BALANCE |
| 12,000 | 8,000 | 20,000 | 100,000 | NO_SAVING_AMOUNT |
| 12,850 | 8,000 | 100,000 | 100,000 | COINBOX_LIMIT_REACHED |
| 12,850 | 8,000 | 100,001 | 100,000 | COINBOX_LIMIT_ALREADY_EXCEEDED |
| 자료 없음 | 8,000 | 20,000 | 100,000 | DAILY_BALANCE_NOT_FOUND |

여러 사유가 겹치면 계산기의 검사 순서에 따라 첫 사유가 결정됩니다. 현재 코드는 전일 자료 누락 → 예정액 0 → 근거 잔액 부족 → 한도 초과 → 한도 도달 순서입니다. 최대 한도는 예시 데이터의 10만 원이며 코드가 모든 계약에 10만 원을 하드코딩한 것은 아닙니다.

### 2.5.4 성공과 실패의 데이터 차이

| 결과 | 잔액 변경 | 거래·원장 | COIN_SAVING_EXECUTION |
|---|---|---|---|
| SUCCESS | 두 계좌 변경 | 거래 1건·원장 2건 | 실제 금액·transaction_id |
| SKIPPED | 없음 | 없음 | 금액 0·사유·transaction_id 없음 |
| DUPLICATE | 추가 변경 없음 | 추가 생성 없음 | 기존 행 유지 |
| EXCLUDED | 없음 | 없음 | 새 행 없음 |
| 시스템 오류 | 해당 청크 롤백 | 해당 청크 롤백 | 같은 트랜잭션의 새 행도 롤백 |

FAILED Enum이 있다는 것과 실패 행을 별도 트랜잭션으로 영구 저장한다는 것은 다릅니다. 현재 시스템 오류는 Step 실패로 남고 자동 재시도는 구성하지 않았습니다.

## 2.6 저금통 해지

### 2.6.1 처리 흐름

1. DELETE /api/v1/coinboxes/{accountNumber}를 호출합니다.
2. CUSTOMER를 잠가 개설과 해지의 고객 단위 경쟁을 조정합니다.
3. 본인 소유 저금통과 근거계좌 연결을 조회합니다. 이미 CLOSED이면 예외입니다.
4. 두 ACCOUNT를 ID 오름차순으로 잠급니다.
5. COINBOX와 최신 ACCOUNT_CONTRACT를 잠급니다.
6. 계좌 소유·연결·상품 유형·계좌 상태·저금통 계약 상태를 검증합니다.
7. 잔액이 있으면 transferAllLocked로 근거계좌에 전액 이전합니다.
8. 동전모으기를 끄고 시작일을 null로 바꿉니다.
9. 계약을 TERMINATED로 바꾸고 종료일을 당일로 기록합니다.
10. 잔액 0인 저금통 계좌를 CLOSED로 바꾸고 전체를 커밋합니다.

| 대상 | 해지 후 |
|---|---|
| 저금통 ACCOUNT | balance=0, account_status=CLOSED |
| 근거 ACCOUNT | 잔액이 있었다면 그 금액만큼 증가 |
| ACCOUNT_CONTRACT | contract_status=TERMINATED, contract_end_date=해지일 |
| COINBOX | coin_saving_enabled=false, coin_saving_start_date=null |
| 거래·원장 | 잔액이 있었던 경우에만 생성 |

동전모으기 시작일은 설정을 끌 때 null로 기록합니다. 계약 종료일은 계약의 유효기간을 나타내며 실제 종료 날짜 또는 9999-12-31입니다.

### 2.6.2 비우기와의 차이

> 비우기는 잔액만 회수하고 서비스를 유지합니다. 해지는 잔액 회수뿐 아니라 계좌·계약·설정을 종료합니다. 해지의 자금 이동은 TRANSFER로 기록하고 원장 업무 코드는 COINBOX_TERMINATION을 사용합니다. 이미 해지된 저금통은 업무 예외로 처리하며 이자 정산은 과제 범위에서 제외했습니다.

## 2.7 ACTIVE 계좌 조회

GET /api/v1/accounts는 고객 존재를 확인하고 ACTIVE 계좌와 상품명을 한 조회로 가져옵니다. Map을 이용해 부모별 자식 목록을 만들고 최상위 계좌 아래 한 단계로 반환합니다. 상품 누락, ACTIVE 자식의 부모 누락 또는 두 단계 이상 연결은 현재 구현에서 정합성 오류입니다.

계좌 목록 조회 SQL 한 번 외에 고객 존재 조회가 있으므로 API 전체가 SQL 한 번이라고 말하지 않습니다. 이 구조는 무한 깊이의 일반 트리 API가 아닙니다.

## 2.8 코드 연결

- [온라인 업무](../../src/main/java/com/kakaobank/coinbox/coinbox/service/CoinBoxService.java)
- [공통 이체](../../src/main/java/com/kakaobank/coinbox/financialtransaction/service/InternalTransferService.java)
- [잔액 Tasklet](../../src/main/java/com/kakaobank/coinbox/accountdailybalance/batch/DailyBalanceTasklet.java)
- [동전모으기](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingService.java)
- [금액 계산기](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/service/CoinSavingAmountCalculator.java)
- [계좌 조회](../../src/main/java/com/kakaobank/coinbox/account/service/AccountQueryService.java)
