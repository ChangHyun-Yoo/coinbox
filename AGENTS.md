// AGENTS.md
# Engineering Guidelines

## Language
- Java 21

## Framework
- Spring Boot 4.0.8
- Spring Batch (배치 구현 시 사용)

## Persistence
- Spring Data JPA
- JdbcTemplate

## DB
- MySQL

## Build
- Gradle

## 구현 규칙
### 소스 작성
- 주석은 한글로 적극적으로 작성
- 클래스와 주요 함수에는 책임과 업무 목적을 설명하는 주석 작성
- 복잡한 조건 동시성 제어, 트랜잭션 범위에는 처리 방법보다 적용 이유와 주의사항을 설명하는 주석 작성
- 코드를 그대로 반복하는 주석보다 개발자가 업무 규칙과 설계 의도를 이해하는 데 도움이 되는 주석 작성

### Lombok과 로깅
- 반복되는 getter, 보호 생성자와 필수 의존성 생성자는 Lombok을 적극적으로 활용
- 생성자 주입을 사용하는 Controller, Service, Component, Batch 클래스는 `final` 필드와 `@RequiredArgsConstructor` 사용
- Controller, Service, Component, Batch 처리 클래스와 예외 처리 클래스에는 `@Slf4j`를 기본 적용
- 계좌번호 전체, 인증 정보와 같은 민감정보를 로그에 기록하지 않으며 정상 건별 로그는 `debug` 수준을 우선 사용

### Transaction
- Service에는 클래스 수준의 `@Transactional(readOnly = true)`를 기본 적용
- 데이터 생성·수정·삭제 또는 비관적 잠금이 필요한 공개 메서드는 `@Transactional`로 쓰기 트랜잭션을 명시
- 클래스 수준 읽기 전용 설정이 쓰기 메서드에 그대로 적용되지 않도록 메서드 재정의 여부를 확인

### 참고 문서
- Entity 설계 시 docs/01_ERD.md의 테이블, 컬럼, 타입, 제약 조건과 논리 관계를 함께 참고
- Entity와 ERD가 달라지는 경우 코드와 docs/01_ERD.md를 같은 작업에서 동기화
- 데이터 모델과 Enum은 docs/01_ERD.md 참고

### Entity
- JPA를 사용해서 Entity를 설계

## Entity
- PK는 Snowflake Long 사용
- Enum은 EnumType.STRING
- 금액은 Long
- 연관관계는 매핑하지 않고, FK를 Long id 형태로 작성
- nullable, unique, length 등 Entity 특성은 요구사항을 참고하여 작성
- date와 datetime은 LocalDate, LocalDateTime으로 작성
- auditing은 BaseEntity를 상속받아 작성, BaseEntity에는 createdDatetime, updatedDatetime 작성
- Status와 Type은 Enum으로 작성, Enum 내용은 요구사항을 참고하여 작성
- Enum은 영문 저장값과 함께 한글 설명을 관리

## Architecture
- Controller → Service → Repository 구조
- 단순 CRUD와 일반 조회는 Spring Data JPA를 사용
- Spring Data JPA Repository에 직접 선언하는 조회 쿼리는 `@Query(nativeQuery = true)`를 사용
- native query의 SQL은 소문자로 작성하고, text block과 테이블 별칭을 사용해 절마다 줄을 구분
- 온라인 복합 조회는 Spring Data JPA의 `@Query(nativeQuery = true)`와 인터페이스 프로젝션을 사용
- JdbcTemplate은 배치의 대량 데이터 쓰기처럼 JPA보다 일괄 처리가 명확히 유리한 경우에 사용
- 배치 처리는 Spring Batch의 Job과 Step 구조를 사용
- 배치 데이터 쓰기는 JdbcBatchItemWriter를 우선 사용
- package는 domain을 기준으로 하여 entity, repository, service, controller, exception, request, response 등으로 구분
  - account : entity, repository, service, controller 등
  - common -> snowflake, exception, response 등

## Exception Handling
- 비즈니스 예외는 `BusinessException` 하나로 처리
- HTTP 상태, 오류 code, message는 `ErrorCode` Enum에서 관리
- Service에서는 조건에 맞는 `ErrorCode`로 `BusinessException`을 생성
- Controller 예외 응답은 `GlobalExceptionHandler`에서 공통 형식으로 처리
- 임의의 오류 code와 message를 Service 또는 Controller에 직접 작성하지 않음

## Test Code
- Test Code는 Service, Repository, Controller Layer에 대해서만 작성
- Snowflake는 공통 인프라 검증을 위해 Test Code 작성
- Test Code는 given-when-then 구조로 작성
- Test Code의 display name은 한글로 작성
