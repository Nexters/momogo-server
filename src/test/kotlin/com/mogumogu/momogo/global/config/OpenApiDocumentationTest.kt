package com.mogumogu.momogo.global.config

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "momogo.openapi.server-url=https://api.dev.mogumogo.com",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class OpenApiDocumentationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : BehaviorSpec({

    given("Swagger 문서가 활성화된 상태에서") {
        `when`("OpenAPI 문서를 요청하면") {
            val response = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .response
            val document = objectMapper.readTree(response.contentAsString)

            then("기본 정보와 Bearer JWT 인증 방식을 반환한다") {
                response.status shouldBe HttpStatus.OK.value()
                document["info"]["title"].stringValue() shouldBe "Momogo API"
                document["info"]["description"].stringValue() shouldBe "Momogo 서버 API 문서"
                document["info"]["version"].stringValue() shouldBe "0.0.1-SNAPSHOT"
                document["servers"][0]["url"].stringValue() shouldBe "https://api.dev.mogumogo.com"

                val bearerAuth = document["components"]["securitySchemes"][
                    OpenApiConfiguration.BEARER_AUTH
                ]
                bearerAuth["type"].stringValue() shouldBe "http"
                bearerAuth["scheme"].stringValue() shouldBe "bearer"
                bearerAuth["bearerFormat"].stringValue() shouldBe "JWT"
                bearerAuth["description"].stringValue() shouldBe
                    "회원가입 또는 로그인 후 받은 액세스 토큰을 입력합니다."
            }

            then("보호된 사용자와 그룹 API에 Bearer 인증을 표시한다") {
                document.operation("/api/v1/user/me", "get").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/user", "patch").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/user", "delete").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/groups", "get").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/groups", "post").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/groups/{groupId}", "patch")
                    .requiresBearerAuth() shouldBe true
                document.operation("/api/v1/groups/invitations", "get")
                    .requiresBearerAuth() shouldBe true
                document.operation("/api/v1/groups/invitations", "post")
                    .requiresBearerAuth() shouldBe true
                document.operation("/api/v1/groups/{groupId}/members/me", "delete")
                    .requiresBearerAuth() shouldBe true
                document.operation("/api/v1/user/register", "post").requiresBearerAuth() shouldBe false
                document.operation("/api/v1/auth/login", "post").requiresBearerAuth() shouldBe false
                document.operation("/api/v1/auth/reissue", "post").requiresBearerAuth() shouldBe false
                document.operation("/api/v1/auth/logout", "delete").requiresBearerAuth() shouldBe false
                document.operation("/init/versions", "get").requiresBearerAuth() shouldBe false
            }

            then("Bearer 인증 API에 공통 401 응답을 적용한다") {
                val commonResponseRef =
                    "#/components/responses/${OpenApiConfiguration.BEARER_UNAUTHORIZED_RESPONSE}"

                document.operation("/api/v1/user/me", "get")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/user", "patch")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/user", "delete")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/groups", "get")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/groups", "post")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/groups/{groupId}", "patch")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/groups/invitations", "get")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/groups/invitations", "post")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation(
                    "/api/v1/groups/{groupId}/members/me",
                    "delete",
                )["responses"]["401"]["\$ref"].stringValue() shouldBe commonResponseRef

                val commonResponse = document["components"]["responses"][
                    OpenApiConfiguration.BEARER_UNAUTHORIZED_RESPONSE
                ]
                commonResponse["description"].stringValue() shouldBe
                    "액세스 토큰이 없거나 유효하지 않음"
                commonResponse["content"].has("application/problem+json") shouldBe true
            }

            then("서버가 주입하는 userId를 요청 파라미터로 노출하지 않는다") {
                document.operation("/api/v1/user/me", "get").hasParameter("userId") shouldBe false
                document.operation("/api/v1/user", "patch").hasParameter("userId") shouldBe false
                document.operation("/api/v1/user", "delete").hasParameter("userId") shouldBe false
                document.operation("/api/v1/groups", "get").hasParameter("userId") shouldBe false
                document.operation("/api/v1/groups", "post").hasParameter("userId") shouldBe false
                val updateGroupName = document.operation("/api/v1/groups/{groupId}", "patch")
                updateGroupName.hasParameter("userId") shouldBe false
                updateGroupName.hasParameter("groupId") shouldBe true
                document.operation("/api/v1/groups/invitations", "get")
                    .hasParameter("userId") shouldBe false
                document.operation("/api/v1/groups/invitations", "post")
                    .hasParameter("userId") shouldBe false
                document.operation("/api/v1/groups/{groupId}/members/me", "delete")
                    .hasParameter("userId") shouldBe false
            }

            then("user, auth와 group API의 설명과 주요 응답을 제공한다") {
                val register = document.operation("/api/v1/user/register", "post")
                val login = document.operation("/api/v1/auth/login", "post")
                val reissue = document.operation("/api/v1/auth/reissue", "post")
                val logout = document.operation("/api/v1/auth/logout", "delete")
                val getMe = document.operation("/api/v1/user/me", "get")
                val updateNickname = document.operation("/api/v1/user", "patch")
                val withdraw = document.operation("/api/v1/user", "delete")
                val getJoinedGroups = document.operation("/api/v1/groups", "get")
                val createGroup = document.operation("/api/v1/groups", "post")
                val updateGroupName = document.operation("/api/v1/groups/{groupId}", "patch")
                val invitationInfo = document.operation("/api/v1/groups/invitations", "get")
                val joinGroup = document.operation("/api/v1/groups/invitations", "post")
                val leaveGroup = document.operation(
                    "/api/v1/groups/{groupId}/members/me",
                    "delete",
                )
                val checkAppVersion = document.operation("/init/versions", "get")

                register["summary"].stringValue() shouldBe "회원가입"
                register.responseCodes() shouldBe setOf("200", "400", "409")
                register.hasResponseMediaType("200", "application/json") shouldBe true
                register.hasResponseMediaType("400", "application/problem+json") shouldBe true
                login["summary"].stringValue() shouldBe "로그인"
                login.responseCodes() shouldBe setOf("200", "400", "401")
                login.hasResponseMediaType("401", "application/problem+json") shouldBe true
                reissue["summary"].stringValue() shouldBe "토큰 재발급"
                reissue.responseCodes() shouldBe setOf("200", "400", "401")
                logout["summary"].stringValue() shouldBe "로그아웃"
                logout.responseCodes() shouldBe setOf("200", "400")
                getMe["summary"].stringValue() shouldBe "내 정보 조회"
                getMe.responseCodes() shouldBe setOf("200", "401", "404")
                getMe.hasResponseMediaType("200", "application/json") shouldBe true
                getMe.hasResponseMediaType("404", "application/problem+json") shouldBe true
                updateNickname["summary"].stringValue() shouldBe "닉네임 변경"
                updateNickname.responseCodes() shouldBe setOf("200", "400", "401", "404")
                withdraw["summary"].stringValue() shouldBe "회원 탈퇴"
                withdraw.responseCodes() shouldBe setOf("200", "401", "404")
                getJoinedGroups["summary"].stringValue() shouldBe "내가 참여한 그룹 조회"
                getJoinedGroups.responseCodes() shouldBe setOf("200", "401", "404")
                getJoinedGroups.hasResponseMediaType("200", "application/json") shouldBe true
                getJoinedGroups.hasResponseMediaType("404", "application/problem+json") shouldBe true
                createGroup["summary"].stringValue() shouldBe "그룹 생성"
                createGroup.responseCodes() shouldBe setOf("200", "400", "401", "404")
                createGroup.hasResponseMediaType("200", "application/json") shouldBe true
                createGroup.hasResponseMediaType("400", "application/problem+json") shouldBe true
                updateGroupName["summary"].stringValue() shouldBe "그룹명 변경"
                updateGroupName.responseCodes() shouldBe setOf("200", "400", "401", "403", "404")
                updateGroupName.hasResponseMediaType("200", "application/json") shouldBe true
                updateGroupName.hasResponseMediaType("400", "application/problem+json") shouldBe true
                updateGroupName.hasResponseMediaType("403", "application/problem+json") shouldBe true
                updateGroupName.hasResponseMediaType("404", "application/problem+json") shouldBe true
                invitationInfo.responseCodes() shouldBe setOf("200", "401", "404")
                invitationInfo.hasResponseMediaType("200", "application/json") shouldBe true
                invitationInfo.hasResponseMediaType("404", "application/problem+json") shouldBe true
                joinGroup.responseCodes() shouldBe setOf("200", "401", "404", "409")
                joinGroup.hasResponseMediaType("200", "application/json") shouldBe true
                joinGroup.hasResponseMediaType("404", "application/problem+json") shouldBe true
                joinGroup.hasResponseMediaType("409", "application/problem+json") shouldBe true
                leaveGroup["summary"].stringValue() shouldBe "그룹 탈퇴"
                leaveGroup.responseCodes() shouldBe setOf("200", "401", "404")
                leaveGroup.hasResponseMediaType("200", "application/json") shouldBe true
                leaveGroup.hasResponseMediaType("404", "application/problem+json") shouldBe true
                checkAppVersion["summary"].stringValue() shouldBe "앱 버전 체크"
                checkAppVersion.responseCodes() shouldBe setOf("200", "400")
                checkAppVersion.hasResponseMediaType("200", "application/json") shouldBe true
                checkAppVersion.hasResponseMediaType("400", "application/problem+json") shouldBe true
            }

            then("요청 스키마에 현재 지원 범위와 입력 제한을 표시한다") {
                val schemas = document["components"]["schemas"]
                schemas["LoginRequest"]["properties"]["provider"]["enum"]
                    .stringValues() shouldBe listOf("GUEST")
                schemas["RegisterRequest"]["properties"]["provider"]["enum"]
                    .stringValues() shouldBe listOf("GUEST")
                schemas["RegisterRequest"]["properties"]["providerToken"]["maxLength"]
                    .intValue() shouldBe 255
                schemas["RegisterRequest"]["properties"]["providerToken"]["minLength"]
                    .intValue() shouldBe 1
                schemas["RegisterRequest"]["properties"]["nickname"]["maxLength"]
                    .intValue() shouldBe 12
                schemas["RegisterRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("provider", "providerToken", "nickname")
                schemas["LoginRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("provider", "providerToken")
                schemas["AuthResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("userId", "nickname", "accessToken", "refreshToken")
                schemas["ReissueResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("accessToken", "refreshToken")
                schemas["UserResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("userId", "nickname")
                schemas["CreateGroupRequest"]["properties"]["name"]["maxLength"]
                    .intValue() shouldBe 255
                schemas["CreateGroupRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("name")
                schemas["CreateGroupResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("groupId", "groupName", "inviteCode")

                val getJoinedGroups = document.operation("/api/v1/groups", "get")
                val getJoinedGroupsSchema = document.responseSchema(getJoinedGroups, "200")
                getJoinedGroupsSchema["required"].stringValues().toSet() shouldBe
                    setOf("date", "groups")
                getJoinedGroupsSchema["properties"]["date"]["type"].stringValue() shouldBe "string"
                getJoinedGroupsSchema["properties"]["date"]["format"].stringValue() shouldBe "date"
                getJoinedGroupsSchema["properties"]["groups"]["type"].stringValue() shouldBe "array"
                val joinedGroupSchemaRef =
                    getJoinedGroupsSchema["properties"]["groups"]["items"]["\$ref"].stringValue()
                val joinedGroupSchema = schemas[joinedGroupSchemaRef.substringAfterLast('/')]
                joinedGroupSchema["required"].stringValues().toSet() shouldBe setOf(
                    "groupId",
                    "groupName",
                    "totalMemberCount",
                    "todayPhotoUploaderCount",
                )
                joinedGroupSchema["properties"].propertyNames().asSequence().toSet() shouldBe setOf(
                    "groupId",
                    "groupName",
                    "totalMemberCount",
                    "todayPhotoUploaderCount",
                )
                schemas["UpdateGroupRequest"]["properties"]["name"]["maxLength"]
                    .intValue() shouldBe 255
                schemas["UpdateGroupRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("name")

                val updateGroupName = document.operation("/api/v1/groups/{groupId}", "patch")
                val updateGroupNameSchema = document.responseSchema(updateGroupName, "200")
                updateGroupNameSchema["required"].stringValues().toSet() shouldBe
                    setOf("groupId", "groupName")
                updateGroupNameSchema["properties"].propertyNames().asSequence().toSet() shouldBe
                    setOf("groupId", "groupName")
                schemas["AppVersionResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("latestVersion", "minSupportedVersion", "forceUpdate", "updateUrl")

                val invitationInfo = document.operation("/api/v1/groups/invitations", "get")
                val invitationInfoSchema = document.responseSchema(invitationInfo, "200")
                invitationInfoSchema["required"].stringValues().toSet() shouldBe
                    setOf("groupId", "groupName", "totalMemberCount", "participated")
                invitationInfoSchema["properties"].propertyNames().asSequence().toSet() shouldBe
                    setOf("groupId", "groupName", "totalMemberCount", "participated")
                invitationInfoSchema["properties"]["groupId"]["type"].stringValue() shouldBe "integer"
                invitationInfoSchema["properties"]["groupName"]["type"].stringValue() shouldBe "string"
                invitationInfoSchema["properties"]["totalMemberCount"]["type"].stringValue() shouldBe
                    "integer"
                invitationInfoSchema["properties"]["participated"]["type"].stringValue() shouldBe
                    "boolean"

                val joinGroup = document.operation("/api/v1/groups/invitations", "post")
                val joinGroupSchema = document.responseSchema(joinGroup, "200")
                joinGroupSchema["required"].stringValues().toSet() shouldBe
                    setOf("groupId", "code")
                joinGroupSchema["properties"].propertyNames().asSequence().toSet() shouldBe
                    setOf("groupId", "code")
                joinGroupSchema["properties"]["groupId"]["type"].stringValue() shouldBe "integer"
                joinGroupSchema["properties"]["code"]["type"].stringValue() shouldBe "string"
            }
        }

        `when`("Swagger UI 진입 경로를 요청하면") {
            val response = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn()
                .response

            then("Swagger UI 화면으로 이동한다") {
                response.status shouldBe HttpStatus.FOUND.value()
                response.redirectedUrl shouldBe "/swagger-ui/index.html"
            }
        }
    }
})

private fun JsonNode.operation(
    path: String,
    method: String,
): JsonNode = this["paths"][path][method]

private fun JsonNode.requiresBearerAuth(): Boolean =
    this["security"]
        ?.any { requirement -> requirement.has(OpenApiConfiguration.BEARER_AUTH) }
        ?: false

private fun JsonNode.hasParameter(name: String): Boolean =
    this["parameters"]
        ?.any { parameter -> parameter["name"]?.stringValue() == name }
        ?: false

private fun JsonNode.responseCodes(): Set<String> =
    this["responses"].propertyNames().asSequence().toSet()

private fun JsonNode.hasResponseMediaType(
    responseCode: String,
    mediaType: String,
): Boolean =
    this["responses"][responseCode]["content"].has(mediaType)

private fun JsonNode.responseSchema(
    operation: JsonNode,
    responseCode: String,
): JsonNode {
    val schema = operation["responses"][responseCode]["content"]["application/json"]["schema"]
    val schemaName = schema["\$ref"].stringValue().substringAfterLast('/')
    return this["components"]["schemas"][schemaName]
}

private fun JsonNode.stringValues(): List<String> =
    values().map { value -> value.stringValue() }
