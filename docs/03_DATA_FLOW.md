# 03. CoinBox Process Data Flow

이 문서는 시퀀스 다이어그램의 중요 단계에서 ERD 기준 데이터가 어떻게 조회되고 변경되는지 예시로 설명합니다.
예시의 모든 식별자는 Snowflake `Long` 형식의 가상 값이며, 금액은 원 단위입니다.

---

## 1. 예시 작성 규칙

- 시퀀스 다이어그램의 업무 단계 ID를 그대로 사용합니다.
- 조회나 잠금만 수행한 단계는 데이터가 변경되지 않는다는 사실과 판단 결과를 표시합니다.
- 데이터가 변경되는 단계는 신규 생성 또는 수정된 행을 표시합니다.
- `created_datetime`과 `updated_datetime`은 애플리케이션 감사 컬럼으로 기록합니다.
- 계좌번호는 `varchar`로 관리하되 실제 DB 값에는 하이픈을 저장하지 않습니다.
- 아래 신규가입 예시의 기준 일시는 `2026-08-27 10:15:30`, 개설일은 `2026-08-27`입니다.

---

## 2. 저금통 신규가입

### 2.1 예시 상황

정상 고객이 보유한 입출금계좌를 근거계좌로 선택하여 저금통을 처음 개설합니다.

- 고객은 이용 중인 저금통이 없습니다.
- 선택 계좌는 고객 본인의 정상 입출금계좌입니다.
- 개설일에 적용 가능한 저금통 상품 버전과 최대 한도 정책이 존재합니다.
- 신규 저금통의 최초 잔액은 `0원`, 최대 한도는 `100,000원`입니다.

### 2.2 가입 전 기준 데이터

#### CUSTOMER

| customer_id | customer_status | created_datetime | updated_datetime |
|---:|---|---|---|
| `700000000000000001` | `ACTIVE` | `2025-03-10 09:00:00` | `2025-03-10 09:00:00` |

#### ACCOUNT

| account_id | customer_id | product_type | account_number | parent_account_id | balance | account_status | account_open_date | created_datetime | updated_datetime |
|---:|---:|---|---|---:|---:|---|---|---|---|
| `710000000000000001` | `700000000000000001` | `DEMAND_DEPOSIT` | `3333011234567` | `null` | `1,234,567` | `ACTIVE` | `2025-03-10` | `2025-03-10 09:05:00` | `2026-08-26 18:30:00` |

#### PRODUCT

| product_id | product_type | product_name | created_datetime | updated_datetime |
|---:|---|---|---|---|
| `720000000000000001` | `DEMAND_DEPOSIT` | `입출금통장` | `2025-01-01 00:00:00` | `2025-01-01 00:00:00` |
| `720000000000000002` | `COINBOX` | `저금통` | `2026-01-01 00:00:00` | `2026-01-01 00:00:00` |

#### PRODUCT_VERSION

| product_version_id | product_id | version_number | effective_from | effective_to | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---|---|
| `730000000000000001` | `720000000000000001` | `1` | `2025-01-01` | `9999-12-31` | `2025-01-01 00:00:00` | `2025-01-01 00:00:00` |
| `730000000000000002` | `720000000000000002` | `1` | `2026-01-01` | `9999-12-31` | `2026-01-01 00:00:00` | `2026-01-01 00:00:00` |

#### ACCOUNT_CONTRACT

| account_contract_id | account_id | product_version_id | contract_status | contract_start_date | contract_end_date | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---|---|---|
| `750000000000000001` | `710000000000000001` | `730000000000000001` | `ACTIVE` | `2025-03-10` | `9999-12-31` | `2025-03-10 09:05:00` | `2025-03-10 09:05:00` |

#### COINBOX_POLICY

| coinbox_policy_id | product_version_id | max_amount | created_datetime | updated_datetime |
|---:|---:|---:|---|---|
| `740000000000000001` | `730000000000000002` | `100,000` | `2026-01-01 00:00:00` | `2026-01-01 00:00:00` |

가입 전에는 이 고객의 `product_type = COINBOX`, `account_status != CLOSED`인 `ACCOUNT`와
그 계좌에 연결되는 `COINBOX`가 존재하지 않습니다.

### 2.3 `JOIN-01` — 고객 및 중복 가입 확인

이 단계는 데이터를 변경하지 않습니다.

| 확인 항목 | 조회 결과 | 판단 |
|---|---|---|
| 고객 존재 여부 | `customer_id = 700000000000000001` | 고객 존재 |
| 고객 상태 | `ACTIVE` | 서비스 이용 가능 |
| 이용 중인 저금통 수 | `0` | 신규 가입 가능성 있음 |

`CLOSED` 계좌는 과거 가입 이력이므로 이용 중인 저금통 수에 포함하지 않습니다.

### 2.4 `JOIN-02` — 가입 가능한 근거계좌 조회

이 단계도 데이터를 변경하지 않으며 다음 계좌가 Client에 반환됩니다.

| account_id | account_number | product_type | account_status | balance | 가입 가능 여부 |
|---:|---|---|---|---:|---|
| `710000000000000001` | `3333011234567` | `DEMAND_DEPOSIT` | `ACTIVE` | `1,234,567` | 가능 |

Client는 이 계좌를 선택하고 `selectedAccountId = 710000000000000001`로 개설을 요청합니다.
목록 반환 이후 상태가 변경될 수 있으므로 이 조회 결과만으로 개설을 확정하지 않습니다.

### 2.5 `JOIN-03` — 잠금 획득 및 가입 조건 재검증

개설 트랜잭션에서 다음 순서로 잠금을 획득합니다.

| 잠금 순서 | 대상 행 | 잠금 목적 |
|---:|---|---|
| `1` | `CUSTOMER.customer_id = 700000000000000001` | 같은 고객의 동시 저금통 개설 요청 직렬화 |
| `2` | `ACCOUNT.account_id = 710000000000000001` | 선택 계좌의 상태 변경과 동시 개설 경쟁 방지 |

잠금 후 재검증 결과는 다음과 같습니다. 조회 및 잠금만 수행하므로 아직 데이터 변화는 없습니다.

| 재검증 항목 | 값 | 결과 |
|---|---|---|
| 계좌 소유 고객 | `700000000000000001` | 요청 고객과 일치 |
| 선택 계좌 상품 유형 | `DEMAND_DEPOSIT` | 적합 |
| 선택 계좌 상태 | `ACTIVE` | 적합 |
| 모임통장 여부 | `false` | 적합 |
| 개인사업자통장 여부 | `false` | 적합 |
| 이용 중인 저금통 수 | `0` | 적합 |

### 2.6 `JOIN-04` — 적용 상품 버전 및 정책 확정

개설일 `2026-08-27`이 유효기간에 포함되는 저금통 상품 버전과 연결 정책을 조회합니다.

| 조회 대상 | 확정 값 | 판단 근거 |
|---|---|---|
| `PRODUCT` | `product_id = 720000000000000002`, `product_type = COINBOX` | 저금통 상품군 |
| `PRODUCT_VERSION` | `product_version_id = 730000000000000002`, `version_number = 1` | `2026-01-01 <= 2026-08-27 < 9999-12-31` |
| `COINBOX_POLICY` | `coinbox_policy_id = 740000000000000001`, `max_amount = 100,000` | 확정 버전에 연결된 저금통 정책 |

이 단계도 기준 정보를 조회할 뿐 데이터를 변경하지 않습니다. 확정된
`product_version_id = 730000000000000002`는 다음 단계에서 신규 계약에 저장합니다.

### 2.7 `JOIN-05` — 저금통 데이터 원자적 생성

다음 세 행을 같은 트랜잭션에서 신규 생성합니다.

#### 신규 ACCOUNT

| account_id | customer_id | product_type | account_number | parent_account_id | balance | account_status | account_open_date | created_datetime | updated_datetime |
|---:|---:|---|---|---:|---:|---|---|---|---|
| `710000000000000002` | `700000000000000001` | `COINBOX` | `3310019876543` | `710000000000000001` | `0` | `ACTIVE` | `2026-08-27` | `2026-08-27 10:15:30` | `2026-08-27 10:15:30` |

#### 신규 ACCOUNT_CONTRACT

| account_contract_id | account_id | product_version_id | contract_status | contract_start_date | contract_end_date | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---|---|---|
| `750000000000000002` | `710000000000000002` | `730000000000000002` | `ACTIVE` | `2026-08-27` | `9999-12-31` | `2026-08-27 10:15:30` | `2026-08-27 10:15:30` |

#### 신규 COINBOX

| coinbox_id | account_id | coin_saving_enabled | coin_saving_start_date | created_datetime | updated_datetime |
|---:|---:|---|---|---|---|
| `760000000000000001` | `710000000000000002` | `true` | `2026-08-27` | `2026-08-27 10:15:30` | `2026-08-27 10:15:30` |

세 테이블의 책임과 연결은 다음과 같습니다.

```text
입출금 ACCOUNT(710000000000000001)
        ↑ parent_account_id
저금통 ACCOUNT(710000000000000002)
        ├─ ACCOUNT_CONTRACT → PRODUCT_VERSION(730000000000000002)
        └─ COINBOX → 동전모으기 true, 시작일 2026-08-27
```

### 2.8 `JOIN-06` — Commit 이후 최종 상태

Commit이 완료되면 신규 저금통 관련 행이 모두 함께 조회됩니다.

| 테이블 | 가입 전 관련 행 수 | 가입 후 관련 행 수 | 변화 |
|---|---:|---:|---|
| `ACCOUNT` | 입출금계좌 `1` | 입출금계좌 `1` + 저금통계좌 `1` | 저금통 계좌 생성 |
| `ACCOUNT_CONTRACT` | 입출금계약 `1` | 입출금계약 `1` + 저금통계약 `1` | 저금통 계약 생성 |
| `COINBOX` | `0` | `1` | 저금통 운영 설정 생성 |

`ACCOUNT`, `ACCOUNT_CONTRACT`, `COINBOX` 중 하나라도 저장에 실패하면 세 신규 행을 모두
Rollback합니다. 따라서 실패 후에는 가입 전 상태와 동일하며, 일부 데이터만 남는 중간 상태는 허용하지 않습니다.

---

## 3. 저금통 비우기

### 3.1 예시 상황

앞에서 개설한 저금통에 `48,730원`이 모인 뒤 사용자가 전액 비우기를 요청합니다.

- 요청 일시는 `2026-08-27 14:20:00`입니다.
- 저금통과 연결 입출금계좌는 모두 `ACTIVE` 상태입니다.
- 저금통 잔액 전액을 개설 시 연결한 입출금계좌로 이체합니다.
- 비우기 성공 후에도 저금통 계좌와 계약 및 동전모으기 설정은 유지합니다.

### 3.2 비우기 전 ACCOUNT

| account_id | customer_id | product_type | account_number | parent_account_id | balance | account_status | account_open_date | created_datetime | updated_datetime |
|---:|---:|---|---|---:|---:|---|---|---|---|
| `710000000000000001` | `700000000000000001` | `DEMAND_DEPOSIT` | `3333011234567` | `null` | `1,234,567` | `ACTIVE` | `2025-03-10` | `2025-03-10 09:05:00` | `2026-08-26 18:30:00` |
| `710000000000000002` | `700000000000000001` | `COINBOX` | `3310019876543` | `710000000000000001` | `48,730` | `ACTIVE` | `2026-08-27` | `2026-08-27 10:15:30` | `2026-08-27 14:10:00` |

비우기 직전 두 계좌의 잔액 합계는 `1,283,297원`입니다. `COINBOX` 설정과 저금통 계약은
가입 후 상태를 유지하지만 비우기 로직에서는 조회하거나 변경하지 않습니다.

### 3.3 `EMPTY-01` — 고객 소유 저금통 및 빠른 잔액 확인

Client가 다음 값으로 비우기를 요청합니다.

| 요청값 | 값 |
|---|---|
| `customerId` | `700000000000000001` |
| `accountNumber` | `3310019876543` |

`ACCOUNT` 조회 결과와 1차 판단은 다음과 같으며 데이터는 변경되지 않습니다.

| 확인 항목 | 조회 값 | 결과 |
|---|---|---|
| 소유 고객 | `700000000000000001` | 요청 고객과 일치 |
| 상품 유형 | `COINBOX` | 저금통 계좌 확인 |
| 연결 계좌 | `parent_account_id = 710000000000000001` | 입금 계좌 결정 가능 |
| 잠금 전 잔액 | `48,730` | 0원보다 크므로 이체 서비스 호출 |

잠금 전 잔액은 빠른 검증에만 사용하며 최종 이체 금액을 확정하지 않습니다.

### 3.4 `EMPTY-02` — 당행 이체 요청값 결정

Client 입력이 아니라 조회한 저금통 계좌의 연결 관계와 비우기 업무 규칙으로 다음 값을 결정합니다.

| 구분 | 값 |
|---|---|
| 출금 계좌 ID | `710000000000000002` |
| 입금 계좌 ID | `710000000000000001` |
| 거래 유형 | `TRANSFER` |
| 출금 원장 | `WITHDRAWAL / COINBOX_EMPTY / 비우기` |
| 입금 원장 | `DEPOSIT / COINBOX / 저금통` |
| 이체 금액 | 아직 확정하지 않음 |

### 3.5 `EMPTY-03` — 계좌 잠금 및 최종 검증

출금·입금 방향이 아니라 `account_id`를 기준으로 오름차순 잠금을 획득합니다.

| 잠금 순서 | account_id | 계좌 역할 | 잠금 후 잔액 |
|---:|---:|---|---:|
| `1` | `710000000000000001` | 연결 입출금계좌, 입금 | `1,234,567` |
| `2` | `710000000000000002` | 저금통계좌, 출금 | `48,730` |

| 최종 검증 | 결과 |
|---|---|
| 두 계좌가 모두 존재함 | 통과 |
| 두 계좌가 서로 다른 계좌임 | 통과 |
| 두 계좌의 `account_status = ACTIVE` | 통과 |
| 잠금 후 저금통 잔액이 0원보다 큼 | 통과 |

계약과 상품 정책은 공통 당행 이체의 계좌 유효성 조건이 아니므로 조회하지 않습니다.
최종 이체 금액은 잠금 후 저금통 잔액인 `48,730원`으로 확정합니다.

### 3.6 `EMPTY-04` — 금융거래 및 계좌 원장 생성

#### 신규 FINANCIAL_TRANSACTION

| transaction_id | transaction_type | transaction_amount | transaction_status | execution_datetime | created_datetime | updated_datetime |
|---:|---|---:|---|---|---|---|
| `770000000000000001` | `TRANSFER` | `48,730` | `SUCCESS` | `2026-08-27 14:20:00` | `2026-08-27 14:20:00` | `2026-08-27 14:20:00` |

#### 신규 ACCOUNT_ENTRY

| entry_id | account_id | transaction_id | entry_type | entry_code | amount | balance_before | balance_after | transaction_datetime | entry_description | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---:|---:|---:|---|---|---|---|
| `780000000000000001` | `710000000000000002` | `770000000000000001` | `WITHDRAWAL` | `COINBOX_EMPTY` | `48,730` | `48,730` | `0` | `2026-08-27 14:20:00` | `비우기` | `2026-08-27 14:20:00` | `2026-08-27 14:20:00` |
| `780000000000000002` | `710000000000000001` | `770000000000000001` | `DEPOSIT` | `COINBOX` | `48,730` | `1,234,567` | `1,283,297` | `2026-08-27 14:20:00` | `저금통` | `2026-08-27 14:20:00` | `2026-08-27 14:20:00` |

한 `FINANCIAL_TRANSACTION`에 출금·입금 `ACCOUNT_ENTRY`가 연결됩니다. 두 원장의 `amount`는
양수이며, 자금 방향은 각각 `WITHDRAWAL`과 `DEPOSIT`으로 표현합니다.

### 3.7 `EMPTY-05` — 계좌 잔액 반영 및 Commit

#### Commit 이후 ACCOUNT

| account_id | account_number | balance_before | balance_after | account_status | updated_datetime |
|---:|---|---:|---:|---|---|
| `710000000000000001` | `3333011234567` | `1,234,567` | `1,283,297` | `ACTIVE` | `2026-08-27 14:20:00` |
| `710000000000000002` | `3310019876543` | `48,730` | `0` | `ACTIVE` | `2026-08-27 14:20:00` |

| 정합성 항목 | 값 |
|---|---:|
| 거래 전 두 계좌 잔액 합계 | `1,283,297` |
| 거래 후 두 계좌 잔액 합계 | `1,283,297` |
| 합계 변화 | `0` |

비우기는 자금만 이동하므로 `COINBOX.coin_saving_enabled = true`,
`COINBOX.coin_saving_start_date = 2026-08-27`, `ACCOUNT_CONTRACT.contract_status = ACTIVE`는
그대로 유지됩니다. 처리 중 하나라도 실패하면 금융거래, 두 원장과 두 계좌 잔액 변경을 모두 Rollback합니다.

---

## 4. 저금통 해지

### 4.1 예시 상황

비우기 이후 동전모으기로 다시 `27,410원`이 모인 저금통을 `2026-10-16 16:30:00`에 해지합니다.

- 고객, 저금통 계좌와 연결 입출금계좌는 정상 상태입니다.
- 저금통 계약은 `ACTIVE`이고 동전모으기가 활성화되어 있습니다.
- 남은 원금 전액을 연결 입출금계좌로 이전한 뒤 저금통을 종료합니다.
- 이자 계산과 지급은 수행하지 않습니다.

### 4.2 해지 전 데이터

#### ACCOUNT

| account_id | customer_id | product_type | account_number | parent_account_id | balance | account_status | account_open_date | created_datetime | updated_datetime |
|---:|---:|---|---|---:|---:|---|---|---|---|
| `710000000000000001` | `700000000000000001` | `DEMAND_DEPOSIT` | `3333011234567` | `null` | `1,255,887` | `ACTIVE` | `2025-03-10` | `2025-03-10 09:05:00` | `2026-10-16 10:00:00` |
| `710000000000000002` | `700000000000000001` | `COINBOX` | `3310019876543` | `710000000000000001` | `27,410` | `ACTIVE` | `2026-08-27` | `2026-08-27 10:15:30` | `2026-10-16 10:00:00` |

#### COINBOX

| coinbox_id | account_id | coin_saving_enabled | coin_saving_start_date | created_datetime | updated_datetime |
|---:|---:|---|---|---|---|
| `760000000000000001` | `710000000000000002` | `true` | `2026-08-27` | `2026-08-27 10:15:30` | `2026-08-27 10:15:30` |

#### 저금통 ACCOUNT_CONTRACT

| account_contract_id | account_id | product_version_id | contract_status | contract_start_date | contract_end_date | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---|---|---|
| `750000000000000002` | `710000000000000002` | `730000000000000002` | `ACTIVE` | `2026-08-27` | `9999-12-31` | `2026-08-27 10:15:30` | `2026-08-27 10:15:30` |

해지 직전 두 계좌의 잔액 합계는 `1,283,297원`입니다.

### 4.3 `TERM-01` — 고객 잠금 및 해지 대상 확인

Client가 `customerId = 700000000000000001`, `accountNumber = 3310019876543`으로 해지를 요청합니다.

| 처리 | 대상 또는 결과 |
|---|---|
| 고객 비관적 잠금 | `CUSTOMER.customer_id = 700000000000000001` |
| 고객 상태 | `ACTIVE` |
| 해지 대상 조회 | `ACCOUNT.account_id = 710000000000000002` |
| 소유 관계 | 요청 고객과 일치 |
| 상품 유형 | `COINBOX` |
| 연결 계좌 | `parent_account_id = 710000000000000001` |

아직 계좌 잔액이나 상태는 변경되지 않습니다.

### 4.4 `TERM-02` — 관련 데이터 잠금 및 최종 검증

| 잠금 순서 | 대상 행 | 잠금 목적 |
|---:|---|---|
| `1` | `CUSTOMER(700000000000000001)` | 신규가입과 해지 요청 직렬화 |
| `2` | `ACCOUNT(710000000000000001)` | ID가 작은 연결 입출금계좌 잠금 |
| `3` | `ACCOUNT(710000000000000002)` | ID가 큰 저금통계좌 잠금 |
| `4` | `COINBOX(760000000000000001)` | 동전모으기와 설정 변경 경쟁 방지 |
| `5` | `ACCOUNT_CONTRACT(750000000000000002)` | 계약의 동시 종료 방지 |

| 최종 검증 항목 | 값 | 결과 |
|---|---|---|
| 저금통·연결 계좌 상태 | 모두 `ACTIVE` | 통과 |
| 저금통 상품 유형 | `COINBOX` | 통과 |
| 연결 관계 | `parent_account_id = 710000000000000001` | 통과 |
| 저금통 계약 상태 | `ACTIVE` | 통과 |
| 저금통 설정 | 존재 | 통과 |

`ACCOUNT`가 이미 `CLOSED`이거나 계약이 `TERMINATED`라면 데이터를 변경하지 않고
`이미 해지된 저금통입니다.` 예외를 반환합니다.

### 4.5 `TERM-03` — 잔액 이전 여부 결정

잠금 후 저금통 잔액 `27,410원`이 0원보다 크므로 전액 이전을 선택합니다.

| 구분 | 값 |
|---|---|
| 이체 금액 | `27,410` |
| 출금 계좌 | 저금통 `710000000000000002` |
| 입금 계좌 | 연결 입출금계좌 `710000000000000001` |
| 거래 유형 | `TRANSFER` |
| 출금 원장 | `WITHDRAWAL / COINBOX_TERMINATION / 저금통 해지` |
| 입금 원장 | `DEPOSIT / COINBOX / 저금통` |

### 4.6 `TERM-04` — 남은 잔액 이전

#### 신규 FINANCIAL_TRANSACTION

| transaction_id | transaction_type | transaction_amount | transaction_status | execution_datetime | created_datetime | updated_datetime |
|---:|---|---:|---|---|---|---|
| `770000000000000003` | `TRANSFER` | `27,410` | `SUCCESS` | `2026-10-16 16:30:00` | `2026-10-16 16:30:00` | `2026-10-16 16:30:00` |

#### 신규 ACCOUNT_ENTRY

| entry_id | account_id | transaction_id | entry_type | entry_code | amount | balance_before | balance_after | transaction_datetime | entry_description | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---:|---:|---:|---|---|---|---|
| `780000000000000005` | `710000000000000002` | `770000000000000003` | `WITHDRAWAL` | `COINBOX_TERMINATION` | `27,410` | `27,410` | `0` | `2026-10-16 16:30:00` | `저금통 해지` | `2026-10-16 16:30:00` | `2026-10-16 16:30:00` |
| `780000000000000006` | `710000000000000001` | `770000000000000003` | `DEPOSIT` | `COINBOX` | `27,410` | `1,255,887` | `1,283,297` | `2026-10-16 16:30:00` | `저금통` | `2026-10-16 16:30:00` | `2026-10-16 16:30:00` |

두 계좌의 잔액은 트랜잭션 안에서 각각 `0원`, `1,283,297원`으로 변경됩니다.

### 4.7 `TERM-05` — 저금통 상태 종료

잔액 이전과 같은 트랜잭션에서 다음 상태를 반영합니다.

#### ACCOUNT 변경

| account_id | balance | account_status | updated_datetime |
|---:|---:|---|---|
| `710000000000000001` | `1,283,297` | `ACTIVE` | `2026-10-16 16:30:00` |
| `710000000000000002` | `0` | `CLOSED` | `2026-10-16 16:30:00` |

#### ACCOUNT_CONTRACT 변경

| account_contract_id | contract_status | contract_start_date | contract_end_date | updated_datetime |
|---:|---|---|---|---|
| `750000000000000002` | `TERMINATED` | `2026-08-27` | `2026-10-16` | `2026-10-16 16:30:00` |

#### COINBOX 변경

| coinbox_id | coin_saving_enabled | coin_saving_start_date | updated_datetime |
|---:|---|---|---|
| `760000000000000001` | `false` | `null` | `2026-10-16 16:30:00` |

### 4.8 `TERM-06` — Commit 이후 최종 상태

| 정합성 항목 | 결과 |
|---|---|
| 거래 전·후 두 계좌 잔액 합계 | `1,283,297원`으로 동일 |
| 저금통 잔액 | `0원` |
| 저금통 계좌 상태 | `CLOSED` |
| 저금통 계약 상태 | `TERMINATED`, 종료일 `2026-10-16` |
| 동전모으기 | 비활성, 시작일 `null` |
| 연결 입출금계좌 | 잔액 증가, `ACTIVE` 유지 |

금융거래와 두 원장은 생성 후 변경하지 않습니다. 해지 처리 중 하나라도 실패하면 잔액 이전과
세 종료 상태를 전부 Rollback하여 4.2의 해지 전 상태로 돌아갑니다.

#### 잔액이 이미 0원인 경우

| 대상 | 처리 결과 |
|---|---|
| `FINANCIAL_TRANSACTION` | 생성하지 않음 |
| `ACCOUNT_ENTRY` | 생성하지 않음 |
| 저금통 `ACCOUNT` | `CLOSED`로 변경 |
| `ACCOUNT_CONTRACT` | `TERMINATED`, 실제 해지일 저장 |
| `COINBOX` | 동전모으기 비활성, 시작일 `null` |

잔액 유무와 무관하게 계좌·계약·동전모으기 종료는 동일하게 수행합니다.

---

## 5. 일별 최종 잔액 배치

### 5.1 예시 상황

`DailyBalanceJob`이 `2026-08-28 00:00:00`에 실행되어 전날인 `2026-08-27`의 계좌별
최종 잔액을 저장합니다. 이 예시는 정기 스케줄 실행을 사용하지만 수동 API에
`executionDate=2026-08-28`을 전달해도 같은 업무 데이터 흐름이 발생합니다.

- `ACTIVE` 계좌 2건과 `RESTRICTED` 계좌 1건은 저장 대상입니다.
- `CLOSED` 계좌 1건은 대상에서 제외합니다.
- 조회 SQL 시작 시점의 일관된 잔액을 스냅샷으로 사용합니다.
- `2026-08-27` 기준 스냅샷은 아직 존재하지 않습니다.

### 5.2 조회 시점의 ACCOUNT

Tasklet은 전체 엔티티가 아니라 다음 컬럼만 조회합니다.

| account_id | product_type | account_status | balance | 대상 여부 |
|---:|---|---|---:|---|
| `710000000000000001` | `DEMAND_DEPOSIT` | `ACTIVE` | `1,282,850` | 포함 |
| `710000000000000002` | `COINBOX` | `ACTIVE` | `447` | 포함 |
| `710000000000000003` | `DEMAND_DEPOSIT` | `RESTRICTED` | `50,000` | 포함 |
| `710000000000000004` | `DEMAND_DEPOSIT` | `CLOSED` | `0` | 제외 |

`ACCOUNT`를 조회만 하며 잔액이나 감사 일시는 변경하지 않습니다.

### 5.3 기존 ACCOUNT_DAILY_BALANCE

이전 기준일 스냅샷은 존재하지만 이번 기준일과 복합 UK가 다르므로 함께 보관할 수 있습니다.

| account_daily_balance_id | account_id | balance_date | closing_balance | created_datetime | updated_datetime |
|---:|---:|---|---:|---|---|
| `790000000000000001` | `710000000000000001` | `2026-08-26` | `1,280,000` | `2026-08-27 00:00:02` | `2026-08-27 00:00:02` |

### 5.4 `BAL-01` — 실행일과 기준일 결정

| 파라미터 | 값 |
|---|---|
| 실행 진입점 | `BatchScheduler` 또는 `ManualBatchController` |
| `executionDate` | `2026-08-28` |
| `balanceDate` | `2026-08-27` |
| 실행 시각 | `2026-08-28 00:00:00` |

이 단계에서는 업무 테이블의 데이터가 변경되지 않습니다.

### 5.5 `BAL-02` — 일관된 계좌 잔액 조회

| 조회 순서 | account_id | account_status | 조회된 balance |
|---:|---:|---|---:|
| `1` | `710000000000000001` | `ACTIVE` | `1,282,850` |
| `2` | `710000000000000002` | `ACTIVE` | `447` |
| `3` | `710000000000000003` | `RESTRICTED` | `50,000` |

`ACCOUNT(710000000000000004)`는 `CLOSED`이므로 결과에 포함되지 않습니다. 조회에는 비관적 잠금을
사용하지 않습니다. 첫 조회 SQL 이후 다른 거래가 계좌 잔액을 변경해도 이번 스냅샷 값은 바꾸지 않습니다.

### 5.6 `BAL-03` — Snowflake PK 생성

| account_id | 생성한 account_daily_balance_id | balance_date | closing_balance |
|---:|---:|---|---:|
| `710000000000000001` | `790000000000000002` | `2026-08-27` | `1,282,850` |
| `710000000000000002` | `790000000000000003` | `2026-08-27` | `447` |
| `710000000000000003` | `790000000000000004` | `2026-08-27` | `50,000` |

Snowflake ID를 생성했지만 아직 DB에 신규 행을 Commit하지 않은 상태입니다.

### 5.7 `BAL-04` — ACCOUNT_DAILY_BALANCE 일괄 저장

`JdbcTemplate.batchUpdate`로 다음 세 행을 같은 Step 트랜잭션에 저장합니다.

| account_daily_balance_id | account_id | balance_date | closing_balance | created_datetime | updated_datetime |
|---:|---:|---|---:|---|---|
| `790000000000000002` | `710000000000000001` | `2026-08-27` | `1,282,850` | `2026-08-28 00:00:02` | `2026-08-28 00:00:02` |
| `790000000000000003` | `710000000000000002` | `2026-08-27` | `447` | `2026-08-28 00:00:02` | `2026-08-28 00:00:02` |
| `790000000000000004` | `710000000000000003` | `2026-08-27` | `50,000` | `2026-08-28 00:00:02` | `2026-08-28 00:00:02` |

`(account_id, balance_date)`는 세 행 모두 다르므로 복합 UK를 충족합니다.

### 5.8 `BAL-05` — Commit·Rollback 및 재실행

#### 최초 실행 성공

| 확인 항목 | 결과 |
|---|---|
| 조회 대상 수 | `3` |
| 신규 저장 수 | `3` |
| 제외 수 | `1` (`CLOSED`) |
| Step 결과 | 세 행 모두 Commit |

#### 동일 기준일 재실행

| 확인 항목 | 결과 |
|---|---|
| 기존 `(account_id, balance_date)` 수 | `3` |
| 신규 저장 수 | `0` |
| 기존 `closing_balance`와 감사 일시 | 변경하지 않음 |
| 중복 방지 수단 | `UK(account_id, balance_date)` |

재실행 시점의 현재 계좌 잔액이 달라졌더라도 이미 성공한 `2026-08-27` 스냅샷을 덮어쓰지 않습니다.
최초 실행의 일괄 저장 도중 오류가 발생하면 이번 실행의 신규 행 세 건을 모두 Rollback하고,
기존 `2026-08-26` 스냅샷은 영향을 받지 않습니다.

---

## 6. 동전모으기 배치

### 6.1 예시 상황

`CoinSavingJob`이 금요일인 `2026-08-28 10:00:00`에 실행됩니다. 전일 입출금계좌 최종 잔액은
`1,282,850원`이고, 실행 시점의 입출금계좌 잔액은 `1,282,500원`, 저금통 잔액은 `447원`입니다.
수동 API에 `executionDate=2026-08-28`을 전달하는 경우에도 같은 기준일과 데이터 흐름을 사용합니다.

- 동전모으기 설정과 두 계좌 및 계약은 정상 상태입니다.
- 저금통 최대 한도는 `100,000원`입니다.
- `(coinbox_id, execution_date)` 실행 이력은 아직 없습니다.
- 기본 저축 예정 금액은 전일 잔액의 1,000원 미만 잔돈인 `850원`입니다.

### 6.2 실행 전 주요 데이터

#### ACCOUNT

| account_id | product_type | parent_account_id | balance | account_status | updated_datetime |
|---:|---|---:|---:|---|---|
| `710000000000000001` | `DEMAND_DEPOSIT` | `null` | `1,282,500` | `ACTIVE` | `2026-08-28 09:30:00` |
| `710000000000000002` | `COINBOX` | `710000000000000001` | `447` | `ACTIVE` | `2026-08-28 00:00:00` |

#### COINBOX

| coinbox_id | account_id | coin_saving_enabled | coin_saving_start_date | updated_datetime |
|---:|---:|---|---|---|
| `760000000000000001` | `710000000000000002` | `true` | `2026-08-27` | `2026-08-27 10:15:30` |

#### ACCOUNT_CONTRACT

| account_contract_id | account_id | product_version_id | contract_status | contract_start_date | contract_end_date |
|---:|---:|---:|---|---|---|
| `750000000000000002` | `710000000000000002` | `730000000000000002` | `ACTIVE` | `2026-08-27` | `9999-12-31` |

#### PRODUCT_VERSION·COINBOX_POLICY

| product_version_id | effective_from | effective_to | coinbox_policy_id | max_amount |
|---:|---|---|---:|---:|
| `730000000000000002` | `2026-01-01` | `9999-12-31` | `740000000000000001` | `100,000` |

#### 연결 입출금계좌의 ACCOUNT_DAILY_BALANCE

| account_daily_balance_id | account_id | balance_date | closing_balance |
|---:|---:|---|---:|
| `790000000000000002` | `710000000000000001` | `2026-08-27` | `1,282,850` |

`COIN_SAVING_EXECUTION`에는 `coinbox_id = 760000000000000001`,
`execution_date = 2026-08-28`인 행이 존재하지 않습니다.

### 6.3 `CS-01` — Job 실행 기준일 결정

| 파라미터 | 값 |
|---|---|
| 실행 진입점 | `BatchScheduler` 또는 `ManualBatchController` |
| `executionDate` | `2026-08-28` |
| `previousDate` | `2026-08-27` |
| 실행 시각 | `2026-08-28 10:00:00` |

### 6.4 `CS-02` — 잠금 없는 후보 조회

Reader가 다음 후보를 `coinbox_id` 오름차순 페이지에 포함합니다.

| coinbox_id | coinbox_account_id | parent_account_id | coin_saving_enabled | start_date | previous_closing_balance | 실행 이력 |
|---:|---:|---:|---|---|---:|---|
| `760000000000000001` | `710000000000000002` | `710000000000000001` | `true` | `2026-08-27` | `1,282,850` | 없음 |

후보 데이터는 잠금 없는 조회 결과이므로 ItemProcessor 없이 Writer의 개별 트랜잭션으로 전달합니다.

### 6.5 `CS-03` — 잠금 및 변경 가능 상태 재검증

| 잠금 순서 | 대상 행 | 잠금 후 주요 값 |
|---:|---|---|
| `1` | `ACCOUNT(710000000000000001)` | 연결 계좌, `ACTIVE`, 잔액 `1,282,500` |
| `2` | `ACCOUNT(710000000000000002)` | 저금통, `ACTIVE`, 잔액 `447` |
| `3` | `COINBOX(760000000000000001)` | 활성 `true`, 시작일 `2026-08-27` |
| `4` | `ACCOUNT_CONTRACT(750000000000000002)` | `ACTIVE`, 종료일 `9999-12-31` |

두 계좌의 연결 관계와 상태, 동전모으기 설정 및 계약 상태가 모두 유효합니다.

### 6.6 `CS-04` — 정책 및 실행 이력 재확인

| 확인 항목 | 값 | 결과 |
|---|---|---|
| 계약 상품 버전 | `730000000000000002` | 존재 |
| 저금통 정책 | `740000000000000001` | 존재 |
| 정책 최대 한도 | `100,000` | 유효 |
| 동일 날짜 실행 이력 | `0건` | 실행 가능 |

정책은 잠그지 않으며, `COINBOX` 잠금을 보유한 상태에서 실행 이력을 다시 확인합니다.

### 6.7 `CS-05` — 저축 금액 계산

| 계산 항목 | 계산식 | 결과 |
|---|---|---:|
| 기본 예정 금액 | `1,282,850 % 1,000` | `850` |
| 실행 시점 연결 계좌 잔액 | `1,282,500 > 1,000` | 조건 충족 |
| 저금통 한도 잔여액 | `100,000 - 447` | `99,553` |
| 실제 저축 금액 | `min(850, 99,553)` | `850` |

### 6.8 `CS-06` — 성공 이체와 실행 이력

#### 신규 FINANCIAL_TRANSACTION

| transaction_id | transaction_type | transaction_amount | transaction_status | execution_datetime | created_datetime | updated_datetime |
|---:|---|---:|---|---|---|---|
| `770000000000000002` | `TRANSFER` | `850` | `SUCCESS` | `2026-08-28 10:00:05` | `2026-08-28 10:00:05` | `2026-08-28 10:00:05` |

#### 신규 ACCOUNT_ENTRY

| entry_id | account_id | transaction_id | entry_type | entry_code | amount | balance_before | balance_after | transaction_datetime | entry_description | created_datetime | updated_datetime |
|---:|---:|---:|---|---|---:|---:|---:|---|---|---|---|
| `780000000000000003` | `710000000000000001` | `770000000000000002` | `WITHDRAWAL` | `COINBOX` | `850` | `1,282,500` | `1,281,650` | `2026-08-28 10:00:05` | `저금통` | `2026-08-28 10:00:05` | `2026-08-28 10:00:05` |
| `780000000000000004` | `710000000000000002` | `770000000000000002` | `DEPOSIT` | `COIN_SAVING` | `850` | `447` | `1,297` | `2026-08-28 10:00:05` | `동전 모으기` | `2026-08-28 10:00:05` | `2026-08-28 10:00:05` |

#### Commit 이후 ACCOUNT 잔액

| account_id | balance_before | balance_after | updated_datetime |
|---:|---:|---:|---|
| `710000000000000001` | `1,282,500` | `1,281,650` | `2026-08-28 10:00:05` |
| `710000000000000002` | `447` | `1,297` | `2026-08-28 10:00:05` |

#### 신규 COIN_SAVING_EXECUTION

| execution_id | coinbox_id | transaction_id | execution_date | saving_amount | execution_status | reason_code | created_datetime | updated_datetime |
|---:|---:|---:|---|---:|---|---|---|---|
| `800000000000000001` | `760000000000000001` | `770000000000000002` | `2026-08-28` | `850` | `SUCCESS` | `null` | `2026-08-28 10:00:05` | `2026-08-28 10:00:05` |

거래 전후 두 계좌 잔액 합계는 모두 `1,282,947원`입니다. 금융거래, 두 원장, 두 계좌 잔액과
실행 이력 중 하나라도 저장에 실패하면 이 후보의 변경 전체를 Rollback합니다.

### 6.9 `CS-07` — SKIPPED 및 처리 제외 데이터

아래 행은 서로 다른 저금통의 독립적인 예시이며, 모두 `execution_date = 2026-08-28`,
`saving_amount = 0`, `transaction_id = null`입니다.

| execution_id | coinbox_id | 주요 조건 | execution_status | reason_code |
|---:|---:|---|---|---|
| `800000000000000002` | `760000000000000002` | 전일 잔액 없음 | `SKIPPED` | `DAILY_BALANCE_NOT_FOUND` |
| `800000000000000003` | `760000000000000003` | 전일 잔액 `15,000`, 잔돈 `0` | `SKIPPED` | `NO_SAVING_AMOUNT` |
| `800000000000000004` | `760000000000000004` | 두 계좌 중 하나가 `ACTIVE` 아님 | `SKIPPED` | `ACCOUNT_NOT_ACTIVE` |
| `800000000000000005` | `760000000000000005` | 연결 계좌 현재 잔액 `1,000` | `SKIPPED` | `INSUFFICIENT_BALANCE` |
| `800000000000000006` | `760000000000000006` | 저금통 잔액 `100,000` | `SKIPPED` | `COINBOX_LIMIT_REACHED` |
| `800000000000000007` | `760000000000000007` | 저금통 잔액 `100,100` | `SKIPPED` | `COINBOX_LIMIT_ALREADY_EXCEEDED` |

`SKIPPED` 행은 금융거래와 계좌 원장을 생성하지 않고 계좌 잔액도 변경하지 않습니다.

다음 조건은 `SKIPPED` 이력도 생성하지 않는 처리 제외입니다.

| 잠금 후 확인 결과 | 처리 |
|---|---|
| 동일 날짜 실행 이력이 이미 존재함 | 중복 이체 없이 종료 |
| `coin_saving_enabled = false` | 실행 이력 없이 제외 |
| `coin_saving_start_date >= executionDate` | 실행 이력 없이 제외 |

### 6.10 `CS-08` — 후보별 트랜잭션 결과

| 결과 | 해당 후보의 Commit 데이터 |
|---|---|
| `SUCCESS` | 금융거래 1건, 계좌 원장 2건, 계좌 잔액 2건 변경, 성공 실행 이력 1건 |
| `SKIPPED` | 건너뜀 실행 이력 1건만 생성 |
| 처리 제외 | 업무 데이터 변경 없음 |
| 예상하지 못한 오류 | 해당 후보의 업무 데이터 전체 Rollback 후 Step 실패 |

한 후보의 결과는 다른 후보의 트랜잭션과 분리됩니다. 따라서 특정 저금통의 오류가 앞서 Commit된
다른 저금통의 `SUCCESS` 또는 `SKIPPED` 결과를 되돌리지 않습니다. 현재 Step에는 자동 Retry·Skip과
`FAILED / SYSTEM_ERROR` 실행 이력 저장을 구성하지 않았으며, 이는 운영 확장 범위로 남겨 둡니다.

---

## 7. 고객 ACTIVE 계좌 조회

### 7.1 조회 전 기준 데이터

#### CUSTOMER

| customer_id | customer_status |
|---:|---|
| `700000000000000001` | `ACTIVE` |

#### PRODUCT

| product_id | product_type | product_name |
|---:|---|---|
| `720000000000000001` | `DEMAND_DEPOSIT` | `입출금통장` |
| `720000000000000002` | `COINBOX` | `저금통` |
| `720000000000000003` | `MEETING_ACCOUNT` | `모임통장` |

#### ACCOUNT

| account_id | customer_id | product_type | account_number | parent_account_id | balance | account_status |
|---:|---:|---|---|---:|---:|---|
| `710000000000000001` | `700000000000000001` | `DEMAND_DEPOSIT` | `3333011234567` | `null` | `1,234,567` | `ACTIVE` |
| `710000000000000002` | `700000000000000001` | `COINBOX` | `3310019876543` | `710000000000000001` | `48,730` | `ACTIVE` |
| `710000000000000003` | `700000000000000001` | `MEETING_ACCOUNT` | `7979000012345` | `null` | `350,000` | `ACTIVE` |
| `710000000000000004` | `700000000000000001` | `COINBOX` | `3310000000004` | `710000000000000003` | `0` | `CLOSED` |

### 7.2 `ACCOUNT-LIST-01` — 인증 고객 확인

`customer_id = 700000000000000001`인 고객이 존재하므로 계좌 조회를 계속합니다. 고객이 없다면
`CUSTOMER_NOT_FOUND`를 반환하고, 고객은 있지만 ACTIVE 계좌가 없다면 오류가 아니라 빈 배열을 반환합니다.

### 7.3 `ACCOUNT-LIST-02` — ACTIVE 계좌와 상품명 평면 조회

`account_status = ACTIVE` 조건과 `ACCOUNT.product_type = PRODUCT.product_type` 조인을 적용한 결과입니다.
`CLOSED`인 `account_id = 710000000000000004`는 제외됩니다.

| account_id | product_type | account_number | balance | product_name | parent_account_id |
|---:|---|---|---:|---|---:|
| `710000000000000001` | `DEMAND_DEPOSIT` | `3333011234567` | `1,234,567` | `입출금통장` | `null` |
| `710000000000000002` | `COINBOX` | `3310019876543` | `48,730` | `저금통` | `710000000000000001` |
| `710000000000000003` | `MEETING_ACCOUNT` | `7979000012345` | `350,000` | `모임통장` | `null` |

### 7.4 `ACCOUNT-LIST-03` — 부모·자식 응답 조립

평면 결과를 `parent_account_id`로 그룹핑한 최종 응답입니다. Snowflake `Long` 식별자는 JSON에서
문자열로 반환하고, 자식이 없는 모임통장도 `childAccount: []`를 포함합니다.

```json
[
  {
    "accountId": "710000000000000001",
    "productType": "DEMAND_DEPOSIT",
    "accountNumber": "3333011234567",
    "balance": 1234567,
    "productName": "입출금통장",
    "childAccount": [
      {
        "accountId": "710000000000000002",
        "productType": "COINBOX",
        "accountNumber": "3310019876543",
        "balance": 48730,
        "productName": "저금통"
      }
    ]
  },
  {
    "accountId": "710000000000000003",
    "productType": "MEETING_ACCOUNT",
    "accountNumber": "7979000012345",
    "balance": 350000,
    "productName": "모임통장",
    "childAccount": []
  }
]
```

이 과정은 데이터를 변경하지 않으며 비관적 잠금을 사용하지 않습니다.
