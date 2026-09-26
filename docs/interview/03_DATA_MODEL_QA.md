# 3. 데이터 모델 예상 질문과 답변

[목차](README.md) · [원본 ERD](../01_ERD.md)

## 3.1 D01. 왜 저금통을 별도 계좌로 만들었습니까?

저금통도 독립적인 잔액·상태·계좌번호·입출금 기록을 가지기 때문에 별도 계좌로 만들었습니다. ACCOUNT에는 공통 계좌 정보를, COINBOX에는 동전모으기 활성 여부와 시작일 같은 저금통 전용 설정을 두었습니다. 근거 입출금계좌는 ACCOUNT.parent_account_id로 연결합니다.

## 3.2 D02. ACCOUNT와 ACCOUNT_CONTRACT를 왜 분리했습니까?

계좌의 현재 운영 정보와 상품 가입 계약의 책임을 분리하기 위해서입니다. ACCOUNT는 잔액과 거래 가능 상태를, ACCOUNT_CONTRACT는 가입한 상품 버전과 계약 상태·기간을 관리합니다.

## 3.3 D03. PRODUCT·PRODUCT_VERSION·COINBOX_POLICY가 모두 필요합니까?

가입한 상품 버전과 그 버전의 정책을 구분해 관리하려고 세 테이블로 나눴습니다. PRODUCT는 상품군과 이름, PRODUCT_VERSION은 상품 버전과 적용 기간, COINBOX_POLICY는 버전별 저금통 한도를 담당합니다. 정책이 항상 하나로 고정된다면 더 단순한 구조도 가능합니다.

## 3.4 D04. ACCOUNT.product_type은 왜 중복 저장했습니까?

계좌의 현재 상품 유형을 계약·상품 버전 조인 없이 확인하기 위해 중복 저장했습니다. 조회가 단순해지는 대신, 계약이 가리키는 상품 유형과 ACCOUNT.product_type이 일치하도록 관리해야 합니다.

## 3.5 D05. 계약의 상품 유형은 어떻게 결정됩니까?

계약이 참조하는 상품 버전의 PRODUCT.product_type으로 결정됩니다. 조회 경로는 ACCOUNT_CONTRACT.product_version_id → PRODUCT_VERSION.product_id → PRODUCT.product_type입니다.

## 3.6 D06. COINBOX.account_id는 어떤 관계를 나타냅니까?

COINBOX와 ACCOUNT를 연결하는 일대일 관계입니다. COINBOX.account_id에 UK를 두어 한 계좌에 저금통 설정이 하나만 연결되도록 했습니다. 각 COINBOX는 하나의 계좌를 참조하고, 일반 계좌에는 COINBOX가 없을 수 있습니다.

## 3.7 D07. COINBOX_POLICY에서 max_amount를 관리하는 이유는 무엇입니까?

최대 한도는 고객별 설정이 아니라 상품 버전의 공통 정책이기 때문입니다. COINBOX_POLICY에 한 번 저장하면 같은 버전에 가입한 저금통들이 동일한 한도를 사용합니다.

## 3.8 D08. 정책을 변경하면 기존 가입자에게도 바로 적용됩니까?

새 버전을 추가하는 것만으로는 기존 가입자에게 적용되지 않습니다. 기존 계약은 자신이 보관한 product_version_id의 정책을 계속 사용하고, 신규 가입은 개설일에 유효한 버전을 선택합니다.

기존 버전의 정책 행 자체를 수정하면 해당 버전을 사용하는 기존 가입자도 다음 정책 조회부터 변경된 값을 사용합니다. 이미 캐시한 배치 Step은 그 실행 동안 기존 값을 유지합니다.

## 3.9 D09. FINANCIAL_TRANSACTION과 ACCOUNT_ENTRY의 차이는 무엇입니까?

FINANCIAL_TRANSACTION은 자금 이동 한 건을, ACCOUNT_ENTRY는 그 거래가 각 계좌에 반영된 결과를 나타냅니다. 850원을 이체하면 금융거래 한 건과 출금·입금 원장 각각 한 건이 생성됩니다.

## 3.10 D10. 원장이 있는데 ACCOUNT.balance도 저장해야 합니까?

현재 잔액을 매번 전체 원장 합산 없이 조회하고 출금 가능 여부를 판단하기 위해 저장했습니다. 잔액과 원장이 어긋나지 않도록 일반 이체에서는 계좌 잔액 변경과 원장 생성을 같은 트랜잭션으로 처리합니다.

## 3.11 D11. 원장의 before·after와 적요를 왜 저장합니까?

거래 당시의 잔액 변화와 통장 표시 문구를 보존하기 위해서입니다. balance_before·balance_after는 거래 전후 잔액을 보여주고, 적요는 Enum 설명이 나중에 바뀌더라도 당시 표시한 문구를 유지합니다.

## 3.12 D12. 어떤 유일 제약이 중요합니까?

계좌번호 중복과 계좌·일자별 중복 처리를 막는 UK가 핵심입니다. 주요 유일 제약은 다음과 같습니다.

| 제약 | 역할 |
|---|---|
| ACCOUNT.account_number | 계좌번호 중복을 방지합니다. |
| PRODUCT.product_type | 상품군 업무 유형의 중복을 방지합니다. |
| PRODUCT_VERSION(product_id, version_number) | 상품 내 버전 번호 중복을 방지합니다. |
| COINBOX.account_id | 계좌별 저금통 설정 한 개를 보장합니다. |
| COINBOX_POLICY.product_version_id | 상품 버전별 저금통 정책 한 개를 보장합니다. |
| ACCOUNT_DAILY_BALANCE(account_id, balance_date) | 계좌·기준일별 잔액 행 중복을 방지합니다. |
| COIN_SAVING_EXECUTION(coinbox_id, execution_date) | 저금통·실행일별 처리 행 중복을 방지합니다. |
| COIN_SAVING_EXECUTION.transaction_id | 같은 금융거래의 중복 연결을 방지합니다. |

## 3.13 D13. 실행 이력의 거래 ID는 왜 nullable입니까?

저축을 건너뛴 SKIPPED 결과에는 연결할 금융거래가 없기 때문입니다. SUCCESS에는 실제 이체의 transaction_id를 저장하고, SKIPPED에는 null을 저장합니다.

## 3.14 D14. JPA 연관관계를 사용하지 않으면 FK도 없어야 합니까?

아닙니다. JPA의 객체 연관관계와 DB의 물리 외래 키는 별개입니다. Java에서 상대 엔티티의 ID만 보관하더라도 DB에는 외래 키 제약을 설정할 수 있습니다.

**꼬리질문: 그렇다면 이 과제에서 물리 외래 키를 설정하지 않은 이유는 무엇입니까?**

현재는 ID 참조와 서비스의 관계 검증으로 구현했고, Hibernate 자동 DDL 외에 물리 외래 키를 추가하는 DDL은 작성하지 않았습니다. 따라서 직접 SQL 등 서비스 밖의 쓰기에는 참조 무결성이 보장되지 않습니다. 보완한다면 ID 참조 방식은 유지하면서 DB 외래 키를 추가할 수 있습니다.

## 3.15 D15. 금액은 왜 Long이고 계좌번호는 왜 String입니까?

금액은 정수 원 단위의 계산 값이므로 Long, 계좌번호는 계산하지 않는 식별 값이므로 String을 사용했습니다. String은 앞자리 0과 고정 길이도 보존할 수 있습니다. 소수 단위 금액이나 이자를 계산한다면 BigDecimal과 반올림 규칙이 필요합니다.

## 3.16 D16. Snowflake의 장단점과 JSON 문자열 반환 이유는 무엇입니까?

Snowflake는 DB 저장 전에 Long ID를 생성할 수 있지만 노드 ID와 시스템 시계를 관리해야 합니다. JSON에서는 JavaScript Number의 안전한 정수 범위를 넘는 ID의 정밀도 손실을 막으려고 문자열로 반환합니다.

- 장점: DB 채번 없이 ID를 선발급해 연관 데이터를 구성할 수 있고, 시간·노드·순번을 Long 하나로 표현합니다.
- 단점: 노드 ID 중복과 시계 역행을 관리해야 합니다. 현재 노드 ID는 0이며, 시계가 역행하면 예외를 발생시킵니다.

**왜 AUTO_INCREMENT나 UUID 대신 선택했습니까?**

저장 전에 참조 ID를 확보하면서 Long 대리 키를 일관되게 사용하려고 선택했습니다.

| 방식 | 선택 시 고려할 점 |
|---|---|
| AUTO_INCREMENT | DB가 채번하므로 애플리케이션의 생성기 관리가 단순하지만, ID 확보가 INSERT와 연결됩니다. |
| UUID | DB 저장 전 생성할 수 있지만 128비트 값의 저장 방식과 사용할 UUID 버전을 결정해야 합니다. |
| Snowflake | 저장 전 Long ID를 확보할 수 있지만 노드·시계 관리가 필요합니다. |

## 3.17 D17. Enum 문자열과 9999-12-31의 트레이드오프는 무엇입니까?

EnumType.STRING은 가독성과 선언 순서 변경의 안전성을, 9999-12-31은 기간 조회의 단순함을 얻는 대신 각각 이름 변경과 특수 날짜 규약을 관리해야 합니다.

| 선택 | 장점 | 비용 |
|---|---|---|
| EnumType.STRING | DB 값을 읽기 쉽고 Enum 선언 순서가 바뀌어도 의미가 유지됩니다. | Enum 이름 변경 시 기존 데이터·API 호환성을 검토해야 하고 문자열 공간이 필요합니다. |
| 종료일 9999-12-31 | 무기한 계약도 null 분기 없이 기간 조건으로 조회할 수 있습니다. | 무기한을 뜻하는 특수 값이라는 규약을 연동 시스템과 공유해야 합니다. |

유효기간은 시작일 포함·종료일 제외로 판단합니다.

## 3.18 D18. 자연 키와 대리 키의 차이는 무엇이며, 이 과제에서는 왜 대리 키를 선택했습니까?

자연 키는 업무 값 자체로 식별하고, 대리 키는 업무 값과 별도의 ID로 식별합니다. 이 과제는 업무 정책과 행 식별자를 분리하고 참조 구조를 단순하게 하려고 Snowflake 대리 키를 선택했습니다.

| 구분 | 예시 | 장점 | 단점 |
|---|---|---|---|
| 자연 키 | 계좌번호를 PK로 사용 | 별도 식별자가 필요하지 않습니다. | 값이나 업무 규칙이 바뀌면 PK·참조 관계에 영향을 줄 수 있습니다. |
| 대리 키 | account_id를 PK로 사용 | 업무 값 변경과 독립적이고 단일 ID로 참조할 수 있습니다. | 업무 중복을 막으려면 별도 UK가 필요합니다. |

따라서 ACCOUNT는 account_id를 PK로, account_number를 UK로 사용합니다. 변경 가능성이 낮고 단순한 업무 값이라면 자연 키도 선택할 수 있습니다.

## 3.19 확인할 코드

- [상품 모델](../../src/main/java/com/kakaobank/coinbox/product/entity)
- [계약 모델](../../src/main/java/com/kakaobank/coinbox/accountcontract/entity/AccountContract.java)
- [금융거래](../../src/main/java/com/kakaobank/coinbox/financialtransaction/entity/FinancialTransaction.java)
- [계좌 원장](../../src/main/java/com/kakaobank/coinbox/accountentry/entity/AccountEntry.java)
- [실행 이력](../../src/main/java/com/kakaobank/coinbox/coinsavingexecution/entity/CoinSavingExecution.java)
- [채번](../../src/main/java/com/kakaobank/coinbox/account/service/AccountNumberGenerator.java)
- [Snowflake](../../src/main/java/com/kakaobank/coinbox/common/snowflake/Snowflake.java)
