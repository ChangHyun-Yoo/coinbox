package com.kakaobank.coinbox.common.config;

import com.jayway.jsonpath.JsonPath;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 코드에서 생성된 OpenAPI 명세가 05_API.md의 공개 경로·상태 코드·공통 계약과 일치하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainer.class)
@DisplayName("OpenAPI 명세 통합 테스트")
class OpenApiSpecificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("05_API.md의 경로와 상태 코드를 OpenAPI 명세로 노출한다")
    void exposesDocumentedPathsAndResponses() throws Exception {
        // given & when: 실행 중인 애플리케이션이 생성한 OpenAPI JSON을 조회한다.
        String specification = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("카카오뱅크 저금통 API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: 배치·내부 서비스는 제외하고 05_API.md의 온라인 경로 다섯 개만 공개한다.
        Map<String, Object> paths = readMap(specification, "$.paths");
        assertThat(paths.keySet()).containsExactlyInAnyOrder(
                "/api/v1/accounts",
                "/api/v1/coinboxes/eligible-accounts",
                "/api/v1/coinboxes",
                "/api/v1/coinboxes/{accountNumber}/empty",
                "/api/v1/coinboxes/{accountNumber}"
        );

        // and: 05_API.md에 정의된 정상·입력·업무·시스템 응답 상태를 빠짐없이 명시한다.
        assertResponseCodes(specification, "/api/v1/accounts", "get", "200", "400", "404", "500");
        assertResponseCodes(
                specification,
                "/api/v1/coinboxes/eligible-accounts",
                "get",
                "200", "400", "404", "409", "500"
        );
        assertResponseCodes(
                specification,
                "/api/v1/coinboxes",
                "post",
                "201", "400", "404", "409", "500", "503"
        );
        assertResponseCodes(
                specification,
                "/api/v1/coinboxes/{accountNumber}/empty",
                "post",
                "200", "400", "404", "409", "500"
        );
        assertResponseCodes(
                specification,
                "/api/v1/coinboxes/{accountNumber}",
                "delete",
                "200", "400", "404", "409", "500"
        );
    }

    @Test
    @DisplayName("고객 식별 헤더와 요청·응답·공통 오류 스키마를 노출한다")
    void exposesHeaderAndSchemas() throws Exception {
        // given & when: 생성된 OpenAPI JSON을 조회한다.
        String specification = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: 모든 온라인 Operation이 인증 계층에서 전달받는 고객 ID를 필수 헤더로 설명한다.
        assertCustomerHeader(specification, "/api/v1/accounts", "get");
        assertCustomerHeader(specification, "/api/v1/coinboxes/eligible-accounts", "get");
        assertCustomerHeader(specification, "/api/v1/coinboxes", "post");
        assertCustomerHeader(specification, "/api/v1/coinboxes/{accountNumber}/empty", "post");
        assertCustomerHeader(specification, "/api/v1/coinboxes/{accountNumber}", "delete");

        // and: 저금통 개설 요청의 ID는 JavaScript 정밀도 손실을 피하는 문자열 계약으로 표시한다.
        assertThat(JsonPath.<String>read(
                specification,
                "$.paths['/api/v1/coinboxes'].post.requestBody.content['application/json'].schema['$ref']"
        )).isEqualTo("#/components/schemas/OpenCoinBoxRequest");
        assertThat(JsonPath.<String>read(
                specification,
                "$.components.schemas.OpenCoinBoxRequest.properties.parentAccountId.type"
        )).isEqualTo("string");

        // and: 성공 응답과 모든 오류 응답이 재사용 가능한 DTO 스키마를 참조한다.
        assertThat(JsonPath.<String>read(
                specification,
                "$.paths['/api/v1/coinboxes'].post.responses['201'].content['application/json'].schema['$ref']"
        )).isEqualTo("#/components/schemas/OpenCoinBoxResponse");
        assertThat(JsonPath.<String>read(
                specification,
                "$.paths['/api/v1/coinboxes'].post.responses['409'].content['application/json'].schema['$ref']"
        )).isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(JsonPath.<String>read(
                specification,
                "$.components.schemas.ErrorResponse.properties.code.example"
        )).isEqualTo("COINBOX_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("Swagger UI 진입 경로를 제공한다")
    void servesSwaggerUi() throws Exception {
        // when & then: 사람이 확인할 수 있는 Swagger UI의 고정 진입 경로로 이동한다.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }

    private void assertResponseCodes(
            String specification,
            String path,
            String method,
            String... expectedCodes
    ) {
        Map<String, Object> responses = readMap(
                specification,
                "$.paths['" + path + "']." + method + ".responses"
        );
        assertThat(responses.keySet()).containsExactlyInAnyOrder(expectedCodes);
    }

    private void assertCustomerHeader(String specification, String path, String method) {
        List<Map<String, Object>> parameters = JsonPath.read(
                specification,
                "$.paths['" + path + "']." + method + ".parameters"
        );
        assertThat(parameters).anySatisfy(parameter -> assertThat(parameter)
                .containsEntry("name", "X-Customer-Id")
                .containsEntry("in", "header")
                .containsEntry("required", true));
    }

    private Map<String, Object> readMap(String specification, String path) {
        return JsonPath.read(specification, path);
    }
}
