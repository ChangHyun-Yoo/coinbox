-- 애플리케이션을 처음 실행한 직후 API와 배치를 확인할 수 있는 최소 샘플 데이터다.
-- Hibernate가 ddl-auto=create로 업무 테이블을 생성한 다음 이 파일을 실행한다.

-- 상품군은 계좌 조회와 가입 정책 확인에 사용한다.
insert into product (
    product_id,
    product_type,
    product_name,
    created_datetime,
    updated_datetime
) values
    (730000000000000001, 'DEMAND_DEPOSIT', '입출금통장', current_timestamp, current_timestamp),
    (730000000000000002, 'COINBOX', '저금통', current_timestamp, current_timestamp),
    (730000000000000003, 'MEETING_ACCOUNT', '모임통장', current_timestamp, current_timestamp),
    (730000000000000004, 'BUSINESS_DEMAND_DEPOSIT', '개인사업자통장', current_timestamp, current_timestamp);

-- 9999-12-31은 현재 적용 중인 상품 버전의 논리적인 미종료일이다.
insert into product_version (
    product_version_id,
    product_id,
    version_number,
    effective_from,
    effective_to,
    created_datetime,
    updated_datetime
) values
    (731000000000000001, 730000000000000001, 1, '2026-01-01', '9999-12-31', current_timestamp, current_timestamp),
    (731000000000000002, 730000000000000002, 1, '2026-01-01', '9999-12-31', current_timestamp, current_timestamp),
    (731000000000000003, 730000000000000003, 1, '2026-01-01', '9999-12-31', current_timestamp, current_timestamp),
    (731000000000000004, 730000000000000004, 1, '2026-01-01', '9999-12-31', current_timestamp, current_timestamp);

-- 저금통 V1은 최대 10만 원까지 보유할 수 있다.
insert into coinbox_policy (
    coinbox_policy_id,
    product_version_id,
    max_amount,
    created_datetime,
    updated_datetime
) values (
    740000000000000001,
    731000000000000002,
    100000,
    current_timestamp,
    current_timestamp
);

-- Swagger 예시를 실행 순서와 무관하게 검증할 수 있도록 조회·개설·비우기·해지 고객을 분리한다.
insert into customer (
    customer_id,
    customer_status,
    created_datetime,
    updated_datetime
) values
    (700000000000000001, 'ACTIVE', current_timestamp, current_timestamp),
    (700000000000000002, 'ACTIVE', current_timestamp, current_timestamp),
    (700000000000000003, 'ACTIVE', current_timestamp, current_timestamp),
    (700000000000000004, 'ACTIVE', current_timestamp, current_timestamp);

-- 계좌번호는 하이픈 없는 13자리이며 상품별 접두어 규칙을 따른다.
insert into account (
    account_id,
    customer_id,
    product_type,
    account_number,
    parent_account_id,
    balance,
    account_status,
    account_open_date,
    created_datetime,
    updated_datetime
) values
    (710000000000000001, 700000000000000001, 'DEMAND_DEPOSIT', '3333000000001', null, 253400, 'ACTIVE', '2026-01-02', current_timestamp, current_timestamp),
    (710000000000000002, 700000000000000001, 'COINBOX', '3310000000001', 710000000000000001, 48730, 'ACTIVE', '2026-08-27', current_timestamp, current_timestamp),
    (710000000000000003, 700000000000000001, 'DEMAND_DEPOSIT', '3333000000002', null, 30000, 'RESTRICTED', '2026-02-03', current_timestamp, current_timestamp),
    (710000000000000004, 700000000000000002, 'DEMAND_DEPOSIT', '3333000000003', null, 125670, 'ACTIVE', '2026-03-04', current_timestamp, current_timestamp),
    (710000000000000005, 700000000000000003, 'DEMAND_DEPOSIT', '3333000000004', null, 186420, 'ACTIVE', '2026-04-05', current_timestamp, current_timestamp),
    (710000000000000006, 700000000000000004, 'DEMAND_DEPOSIT', '3333000000005', null, 410000, 'ACTIVE', '2026-05-06', current_timestamp, current_timestamp),
    (710000000000000007, 700000000000000004, 'COINBOX', '3310000000002', 710000000000000006, 35270, 'ACTIVE', '2026-08-28', current_timestamp, current_timestamp),
    (710000000000000008, 700000000000000002, 'BUSINESS_DEMAND_DEPOSIT', '3333000000006', null, 500000, 'ACTIVE', '2026-06-07', current_timestamp, current_timestamp);

-- 현재 계약은 9999-12-31까지 열어 두고 가입 당시의 상품 버전을 고정한다.
insert into account_contract (
    account_contract_id,
    account_id,
    product_version_id,
    contract_status,
    contract_start_date,
    contract_end_date,
    created_datetime,
    updated_datetime
) values
    (720000000000000001, 710000000000000001, 731000000000000001, 'ACTIVE', '2026-01-02', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000002, 710000000000000002, 731000000000000002, 'ACTIVE', '2026-08-27', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000003, 710000000000000003, 731000000000000001, 'ACTIVE', '2026-02-03', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000004, 710000000000000004, 731000000000000001, 'ACTIVE', '2026-03-04', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000005, 710000000000000005, 731000000000000001, 'ACTIVE', '2026-04-05', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000006, 710000000000000006, 731000000000000001, 'ACTIVE', '2026-05-06', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000007, 710000000000000007, 731000000000000002, 'ACTIVE', '2026-08-28', '9999-12-31', current_timestamp, current_timestamp),
    (720000000000000008, 710000000000000008, 731000000000000004, 'ACTIVE', '2026-06-07', '9999-12-31', current_timestamp, current_timestamp);

-- 1번 저금통은 비우기·배치용이고 4번 고객의 저금통은 해지 전용으로 분리한다.
insert into coinbox (
    coinbox_id,
    account_id,
    coin_saving_enabled,
    coin_saving_start_date,
    created_datetime,
    updated_datetime
) values
    (
        750000000000000001,
        710000000000000002,
        true,
        '2026-08-27',
        current_timestamp,
        current_timestamp
    ),
    (
        750000000000000002,
        710000000000000007,
        false,
        null,
        current_timestamp,
        current_timestamp
    );

-- 전일 잔액은 실행일을 기준으로 계산해 샘플 데이터가 날짜와 무관하게 배치 후보가 되도록 한다.
insert into account_daily_balance (
    account_daily_balance_id,
    account_id,
    balance_date,
    closing_balance,
    created_datetime,
    updated_datetime
) values (
    760000000000000001,
    710000000000000001,
    date_sub(current_date, interval 1 day),
    253400,
    current_timestamp,
    current_timestamp
);
