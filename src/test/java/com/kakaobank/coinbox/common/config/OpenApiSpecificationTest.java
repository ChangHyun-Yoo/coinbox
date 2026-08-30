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

        // then: 온라인 다섯 개와 로컬 테스트용 수동 실행 네 개 경로만 공개합니다.
        Map<String, Object> paths = readMap(specification, "$.paths");
        assertThat(paths.keySet()).containsExactlyInAnyOrder(
                "/api/v1/accounts",
                "/api/v1/coinboxes/eligible-accounts",
                "/api/v1/coinboxes",
                "/api/v1/coinboxes/{accountNumber}/empty",
                "/api/v1/coinboxes/{accountNumber}",
                "/internal/v1/batches/daily-balance",
                "/internal/v1/batches/coin-saving",
                "/internal/v1/test-account-deposits",
                "/internal/v1/test-account-withdrawals"
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
        assertResponseCodes(
                specification,
                "/internal/v1/batches/daily-balance",
                "post",
                "200", "400", "500"
        );
        assertResponseCodes(
                specification,
                "/internal/v1/batches/coin-saving",
                "post",
                "200", "400", "500"
        );
        assertResponseCodes(
                specification,
                "/internal/v1/test-account-deposits",
                "post",
                "200", "400", "404", "409", "500"
        );
        assertResponseCodes(
                specification,
                "/internal/v1/test-account-withdrawals",
                "post",
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

        // then: 고객 ID를 JavaScript 정밀도 손실이 없는 필수 문자열 헤더로 설명한다.
        assertCustomerHeader(specification, "/api/v1/accounts", "get");
        assertCustomerHeader(specification, "/api/v1/coinboxes/eligible-accounts", "get");
        assertCustomerHeader(specification, "/api/v1/coinboxes", "post");
        assertCustomerHeader(specification, "/api/v1/coinboxes/{accountNumber}/empty", "post");
        assertCustomerHeader(specification, "/api/v1/coinboxes/{accountNumber}", "delete");

        // and: Swagger 기본 요청값은 data.sql에서 서로 독립된 성공 시나리오를 가리킵니다.
        assertParameterExample(
                specification, "/api/v1/accounts", "get",
                "X-Customer-Id", "700000000000000001"
        );
        assertParameterExample(
                specification, "/api/v1/coinboxes/eligible-accounts", "get",
                "X-Customer-Id", "700000000000000002"
        );
        assertParameterExample(
                specification, "/api/v1/coinboxes", "post",
                "X-Customer-Id", "700000000000000003"
        );
        assertParameterExample(
                specification, "/api/v1/coinboxes/{accountNumber}/empty", "post",
                "X-Customer-Id", "700000000000000001"
        );
        assertParameterExample(
                specification, "/api/v1/coinboxes/{accountNumber}/empty", "post",
                "accountNumber", "3310000000001"
        );
        assertParameterExample(
                specification, "/api/v1/coinboxes/{accountNumber}", "delete",
                "X-Customer-Id", "700000000000000004"
        );
        assertParameterExample(
                specification, "/api/v1/coinboxes/{accountNumber}", "delete",
                "accountNumber", "3310000000002"
        );

        // and: 내부 배치 실행 API는 고객 업무 요청이 아니며 성공 검증용 실행일을 제공합니다.
        assertExecutionDateParameter(specification, "/internal/v1/batches/daily-balance");
        assertExecutionDateParameter(specification, "/internal/v1/batches/coin-saving");
        assertParameterExample(
                specification, "/internal/v1/batches/daily-balance", "post",
                "executionDate", "2026-08-30"
        );
        assertParameterExample(
                specification, "/internal/v1/batches/coin-saving", "post",
                "executionDate", "2026-08-30"
        );

        // and: 저금통 개설 요청의 ID는 JavaScript 정밀도 손실을 피하는 문자열 계약으로 표시한다.
        assertThat(JsonPath.<String>read(
                specification,
                "$.paths['/api/v1/coinboxes'].post.requestBody.content['application/json'].schema['$ref']"
        )).isEqualTo("#/components/schemas/OpenCoinBoxRequest");
        assertThat(JsonPath.<String>read(
                specification,
                "$.components.schemas.OpenCoinBoxRequest.properties.parentAccountId.type"
        )).isEqualTo("string");
        assertThat(JsonPath.<String>read(
                specification,
                "$.components.schemas.OpenCoinBoxRequest.properties.parentAccountId.example"
        )).isEqualTo("710000000000000005");

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
        assertThat(parameters).anySatisfy(parameter -> {
            assertThat(parameter)
                    .containsEntry("name", "X-Customer-Id")
                    .containsEntry("in", "header")
                    .containsEntry("required", true);
            assertThat(readParameterSchema(parameter))
                    .containsEntry("type", "string")
                    .containsEntry("pattern", "^[1-9][0-9]*$");
            assertThat(readParameterSchema(parameter).get("example")).isInstanceOf(String.class);
        });
    }

    private void assertExecutionDateParameter(String specification, String path) {
        List<Map<String, Object>> parameters = JsonPath.read(
                specification,
                "$.paths['" + path + "'].post.parameters"
        );
        assertThat(parameters).singleElement().satisfies(parameter -> assertThat(parameter)
                .containsEntry("name", "executionDate")
                .containsEntry("in", "query")
                .containsEntry("required", true));
    }

    private void assertParameterExample(
            String specification,
            String path,
            String method,
            String parameterName,
            String expectedExample
    ) {
        List<Map<String, Object>> parameters = JsonPath.read(
                specification,
                "$.paths['" + path + "']." + method + ".parameters"
        );
        assertThat(parameters).anySatisfy(parameter -> {
            assertThat(parameter).containsEntry("name", parameterName);
            Object example = parameter.containsKey("example")
                    ? parameter.get("example")
                    : readParameterSchema(parameter).get("example");
            assertThat(String.valueOf(example)).isEqualTo(expectedExample);
        });
    }

    private Map<String, Object> readMap(String specification, String path) {
        return JsonPath.read(specification, path);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readParameterSchema(Map<String, Object> parameter) {
        return (Map<String, Object>) parameter.get("schema");
    }
}
