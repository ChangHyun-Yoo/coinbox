package com.kakaobank.coinbox.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI와 OpenAPI 명세에 공통으로 노출할 서비스 정보와 업무 영역을 정의한다.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "카카오뱅크 저금통 API",
                version = "v1",
                description = """
                        저금통 과제의 온라인 API와 로컬 검증용 수동 실행 API 명세입니다.
                        X-Customer-Id는 실제 인증 수단이 아니라 인증·게이트웨이 계층이 검증 후 전달한다고
                        가정한 고객 식별 헤더입니다.
                        """,
                contact = @Contact(name = "CoinBox Project")
        ),
        tags = {
                @Tag(name = "계좌", description = "고객의 현재 ACTIVE 계좌 조회 API"),
                @Tag(name = "저금통", description = "저금통 가입 가능 조회·개설·비우기·해지 API"),
                @Tag(name = "배치 수동 실행", description = "지정 업무일의 배치 Job을 즉시 시작하는 내부 API"),
                @Tag(name = "테스트 잔고 충전", description = "로컬 테스트에서 지정 계좌의 잔고만 증가시키는 내부 API"),
                @Tag(name = "테스트 잔고 출금", description = "로컬 테스트에서 입출금계좌의 잔고만 감소시키는 내부 API")
        }
)
public class OpenApiConfiguration {
}
