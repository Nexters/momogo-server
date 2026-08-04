package com.mogumogu.momogo.global.config

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.OpenApiExample
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
                document.operation("/api/v1/photos/upload-urls", "post")
                    .requiresBearerAuth() shouldBe true
                document.operation("/api/v1/photos", "post").requiresBearerAuth() shouldBe true
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
                document.operation(
                    "/api/v1/photos/upload-urls",
                    "post",
                )["responses"]["401"]["\$ref"].stringValue() shouldBe commonResponseRef
                document.operation(
                    "/api/v1/photos",
                    "post",
                )["responses"]["401"]["\$ref"].stringValue() shouldBe commonResponseRef

                val commonResponse = document["components"]["responses"][
                    OpenApiConfiguration.BEARER_UNAUTHORIZED_RESPONSE
                ]
                commonResponse["description"].stringValue() shouldBe
                    "액세스 토큰이 없거나 유효하지 않음"
                commonResponse["content"].has("application/problem+json") shouldBe true
                val unauthorizedContent = commonResponse["content"]["application/problem+json"]
                unauthorizedContent["schema"]["\$ref"].stringValue() shouldBe
                    "#/components/schemas/ProblemDetail"
                unauthorizedContent["examples"].propertyNames().asSequence().toSet() shouldBe
                    setOf(ErrorCode.INVALID_AUTH_CREDENTIALS.name)
                unauthorizedContent["examples"][ErrorCode.INVALID_AUTH_CREDENTIALS.name]
                    .shouldBeProblemExample(
                        errorCode = ErrorCode.INVALID_AUTH_CREDENTIALS,
                        status = HttpStatus.UNAUTHORIZED,
                    )
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
                document.operation("/api/v1/photos/upload-urls", "post")
                    .hasParameter("userId") shouldBe false
                document.operation("/api/v1/photos", "post")
                    .hasParameter("userId") shouldBe false
            }

            then("user, auth, group과 photo API의 설명과 주요 응답을 제공한다") {
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
                val issuePhotoUploadUrl = document.operation("/api/v1/photos/upload-urls", "post")
                val createPhoto = document.operation("/api/v1/photos", "post")
                val checkAppVersion = document.operation("/init/versions", "get")

                register["summary"].stringValue() shouldBe "회원가입"
                register.responseCodes() shouldBe setOf("200", "400", "409")
                register.hasResponseMediaType("200", "application/json") shouldBe true
                register.hasResponseMediaType("400", "application/problem+json") shouldBe true
                login["summary"].stringValue() shouldBe "로그인"
                login.responseCodes() shouldBe setOf("200", "400", "404")
                login.hasResponseMediaType("404", "application/problem+json") shouldBe true
                reissue["summary"].stringValue() shouldBe "토큰 재발급"
                reissue.responseCodes() shouldBe setOf("200", "400", "404")
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
                issuePhotoUploadUrl["summary"].stringValue() shouldBe "사진 업로드 URL 발급"
                issuePhotoUploadUrl.responseCodes() shouldBe setOf("200", "400", "401", "404")
                issuePhotoUploadUrl.hasResponseMediaType("200", "application/json") shouldBe true
                issuePhotoUploadUrl.hasResponseMediaType("400", "application/problem+json") shouldBe true
                issuePhotoUploadUrl.hasResponseMediaType("404", "application/problem+json") shouldBe true
                createPhoto["summary"].stringValue() shouldBe "사진 등록"
                createPhoto.responseCodes() shouldBe
                    setOf("200", "400", "401", "403", "404", "409", "422")
                createPhoto.hasResponseMediaType("200", "application/json") shouldBe true
                createPhoto.hasResponseMediaType("400", "application/problem+json") shouldBe true
                createPhoto.hasResponseMediaType("403", "application/problem+json") shouldBe true
                createPhoto.hasResponseMediaType("404", "application/problem+json") shouldBe true
                createPhoto.hasResponseMediaType("409", "application/problem+json") shouldBe true
                createPhoto.hasResponseMediaType("422", "application/problem+json") shouldBe true
                checkAppVersion["summary"].stringValue() shouldBe "앱 버전 체크"
                checkAppVersion.responseCodes() shouldBe setOf("200", "400")
                checkAppVersion.hasResponseMediaType("200", "application/json") shouldBe true
                checkAppVersion.hasResponseMediaType("400", "application/problem+json") shouldBe true
            }

            then("재사용 가능한 요청과 성공 응답 예시를 components에 제공한다") {
                val examples = document["components"]["examples"]

                OpenApiExample.entries
                    .filterNot { example -> example == OpenApiExample.NONE }
                    .forEach { example ->
                        val componentExample = examples[example.componentName]
                        componentExample["summary"].stringValue() shouldBe example.summary
                        componentExample["value"] shouldBe
                            objectMapper.valueToTree<JsonNode>(example.value)
                    }
            }

            then("각 API에 구체적인 요청과 성공 응답 예시를 연결한다") {
                operationExampleExpectations.forEach { expectation ->
                    val operation = document.operation(expectation.path, expectation.method)

                    if (expectation.request != OpenApiExample.NONE) {
                        operation.requestExampleRef(expectation.request) shouldBe
                            expectation.request.componentRef()
                        operation.requestSchemaRef() shouldBe
                            expectation.requestSchema.componentSchemaRef()
                    }
                    operation.successExampleRef(expectation.success) shouldBe
                        expectation.success.componentRef()
                    if (expectation.successSchema == null) {
                        operation.successSchema()["type"].stringValue() shouldBe "object"
                    } else {
                        operation.successSchemaRef() shouldBe
                            expectation.successSchema.componentSchemaRef()
                    }
                }

                val invitationInfo = document.operation("/api/v1/groups/invitations", "get")
                invitationInfo.parameter("code")["example"].stringValue() shouldBe "A1B2C3"

                val appVersion = document.operation("/init/versions", "get")
                appVersion.parameter("platform")["example"].stringValue() shouldBe "IOS"
                appVersion.parameter("appVersion")["example"].stringValue() shouldBe "1.0.0"
            }

            then("실제 발생 가능한 오류를 상태별 named example로 제공한다") {
                document["components"]["schemas"].has("ProblemDetail") shouldBe true

                errorResponseExpectations.forEach { expectation ->
                    val operation = document.operation(expectation.path, expectation.method)
                    val content =
                        operation["responses"][expectation.responseCode]["content"]
                            .get("application/problem+json")

                    content["schema"]["\$ref"].stringValue() shouldBe
                        "#/components/schemas/ProblemDetail"
                    content["examples"].propertyNames().asSequence().toSet() shouldBe
                        expectation.errors.map { errorCode -> errorCode.name }.toSet()

                    expectation.errors.forEach { errorCode ->
                        content["examples"][errorCode.name].shouldBeProblemExample(
                            errorCode = errorCode,
                            status = HttpStatus.valueOf(expectation.responseCode.toInt()),
                        )
                    }
                }
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

                schemas["PhotoUploadUrlRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("contentType")
                val issuePhotoUploadUrl = document.operation("/api/v1/photos/upload-urls", "post")
                val photoUploadUrlSchema = document.responseSchema(issuePhotoUploadUrl, "200")
                photoUploadUrlSchema["required"].stringValues().toSet() shouldBe
                    setOf("uploadUrl", "objectKey", "contentType", "expiresAt")
                photoUploadUrlSchema["properties"].propertyNames().asSequence().toSet() shouldBe
                    setOf("uploadUrl", "objectKey", "contentType", "expiresAt")
                photoUploadUrlSchema["properties"]["expiresAt"]["type"].stringValue() shouldBe "string"
                photoUploadUrlSchema["properties"]["expiresAt"]["format"].stringValue() shouldBe
                    "date-time"

                schemas["PhotoCreateRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("objectKey", "groupIds")
                schemas["PhotoCreateRequest"]["properties"]["objectKey"]["maxLength"]
                    .intValue() shouldBe 512
                schemas["PhotoCreateRequest"]["properties"]["groupIds"]["type"]
                    .stringValue() shouldBe "array"
                schemas["PhotoCreateRequest"]["properties"]["groupIds"]["maxItems"]
                    .intValue() shouldBe 20
                val createPhoto = document.operation("/api/v1/photos", "post")
                val photoCreateSchema = document.responseSchema(createPhoto, "200")
                photoCreateSchema["required"].stringValues().toSet() shouldBe
                    setOf("photoId", "objectKey", "createdAt")
                photoCreateSchema["properties"].propertyNames().asSequence().toSet() shouldBe
                    setOf("photoId", "objectKey", "createdAt")
                photoCreateSchema["properties"]["createdAt"]["type"].stringValue() shouldBe
                    "string"
                photoCreateSchema["properties"]["createdAt"]["format"].stringValue() shouldBe
                    "date-time"

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

private data class OperationExampleExpectation(
    val path: String,
    val method: String,
    val success: OpenApiExample,
    val successSchema: String?,
    val request: OpenApiExample = OpenApiExample.NONE,
    val requestSchema: String? = null,
)

private val operationExampleExpectations = listOf(
    OperationExampleExpectation(
        path = "/api/v1/user/register",
        method = "post",
        request = OpenApiExample.REGISTER_REQUEST,
        requestSchema = "RegisterRequest",
        success = OpenApiExample.AUTH_RESPONSE,
        successSchema = "AuthResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/auth/login",
        method = "post",
        request = OpenApiExample.LOGIN_REQUEST,
        requestSchema = "LoginRequest",
        success = OpenApiExample.AUTH_RESPONSE,
        successSchema = "AuthResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/auth/reissue",
        method = "post",
        request = OpenApiExample.REFRESH_TOKEN_REQUEST,
        requestSchema = "RefreshTokenRequest",
        success = OpenApiExample.REISSUE_RESPONSE,
        successSchema = "ReissueResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/auth/logout",
        method = "delete",
        request = OpenApiExample.REFRESH_TOKEN_REQUEST,
        requestSchema = "RefreshTokenRequest",
        success = OpenApiExample.EMPTY_OBJECT_RESPONSE,
        successSchema = null,
    ),
    OperationExampleExpectation(
        path = "/api/v1/user/me",
        method = "get",
        success = OpenApiExample.USER_RESPONSE,
        successSchema = "UserResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/user",
        method = "patch",
        request = OpenApiExample.UPDATE_NICKNAME_REQUEST,
        requestSchema = "UpdateNicknameRequest",
        success = OpenApiExample.UPDATED_USER_RESPONSE,
        successSchema = "UserResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/user",
        method = "delete",
        success = OpenApiExample.EMPTY_OBJECT_RESPONSE,
        successSchema = null,
    ),
    OperationExampleExpectation(
        path = "/api/v1/groups",
        method = "get",
        success = OpenApiExample.JOINED_GROUPS_RESPONSE,
        successSchema = "JoinedGroupsResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/groups",
        method = "post",
        request = OpenApiExample.CREATE_GROUP_REQUEST,
        requestSchema = "CreateGroupRequest",
        success = OpenApiExample.CREATE_GROUP_RESPONSE,
        successSchema = "CreateGroupResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/groups/{groupId}",
        method = "patch",
        request = OpenApiExample.UPDATE_GROUP_REQUEST,
        requestSchema = "UpdateGroupRequest",
        success = OpenApiExample.UPDATE_GROUP_RESPONSE,
        successSchema = "UpdateGroupResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/groups/invitations",
        method = "get",
        success = OpenApiExample.GROUP_INVITATION_RESPONSE,
        successSchema = "GroupInvitationResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/groups/invitations",
        method = "post",
        request = OpenApiExample.JOIN_GROUP_REQUEST,
        requestSchema = "JoinGroupRequest",
        success = OpenApiExample.JOIN_GROUP_RESPONSE,
        successSchema = "JoinGroupResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/groups/{groupId}/members/me",
        method = "delete",
        success = OpenApiExample.EMPTY_OBJECT_RESPONSE,
        successSchema = null,
    ),
    OperationExampleExpectation(
        path = "/api/v1/photos/upload-urls",
        method = "post",
        request = OpenApiExample.PHOTO_UPLOAD_URL_REQUEST,
        requestSchema = "PhotoUploadUrlRequest",
        success = OpenApiExample.PHOTO_UPLOAD_URL_RESPONSE,
        successSchema = "PhotoUploadUrlResponse",
    ),
    OperationExampleExpectation(
        path = "/api/v1/photos",
        method = "post",
        request = OpenApiExample.PHOTO_CREATE_REQUEST,
        requestSchema = "PhotoCreateRequest",
        success = OpenApiExample.PHOTO_CREATE_RESPONSE,
        successSchema = "PhotoCreateResponse",
    ),
    OperationExampleExpectation(
        path = "/init/versions",
        method = "get",
        success = OpenApiExample.APP_VERSION_RESPONSE,
        successSchema = "AppVersionResponse",
    ),
)

private data class ErrorResponseExpectation(
    val path: String,
    val method: String,
    val responseCode: String,
    val errors: Set<ErrorCode>,
)

private val errorResponseExpectations = listOf(
    ErrorResponseExpectation(
        "/api/v1/user/register",
        "post",
        "400",
        setOf(ErrorCode.INVALID_REQUEST, ErrorCode.UNSUPPORTED_PROVIDER),
    ),
    ErrorResponseExpectation(
        "/api/v1/user/register",
        "post",
        "409",
        setOf(ErrorCode.DUPLICATE_LOGIN_ACCOUNT),
    ),
    ErrorResponseExpectation(
        "/api/v1/auth/login",
        "post",
        "400",
        setOf(ErrorCode.INVALID_REQUEST, ErrorCode.UNSUPPORTED_PROVIDER),
    ),
    ErrorResponseExpectation(
        "/api/v1/auth/login",
        "post",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/auth/reissue",
        "post",
        "400",
        setOf(ErrorCode.INVALID_REQUEST),
    ),
    ErrorResponseExpectation(
        "/api/v1/auth/reissue",
        "post",
        "404",
        setOf(ErrorCode.INVALID_REFRESH_TOKEN),
    ),
    ErrorResponseExpectation(
        "/api/v1/auth/logout",
        "delete",
        "400",
        setOf(ErrorCode.INVALID_REQUEST),
    ),
    ErrorResponseExpectation(
        "/api/v1/user/me",
        "get",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/user",
        "patch",
        "400",
        setOf(ErrorCode.INVALID_REQUEST),
    ),
    ErrorResponseExpectation(
        "/api/v1/user",
        "patch",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/user",
        "delete",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups",
        "get",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups",
        "post",
        "400",
        setOf(ErrorCode.INVALID_REQUEST),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups",
        "post",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/{groupId}",
        "patch",
        "400",
        setOf(ErrorCode.INVALID_REQUEST),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/{groupId}",
        "patch",
        "403",
        setOf(ErrorCode.NOT_GROUP_MEMBER),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/{groupId}",
        "patch",
        "404",
        setOf(ErrorCode.GROUP_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/invitations",
        "get",
        "404",
        setOf(ErrorCode.INVALID_INVITATION_CODE),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/invitations",
        "post",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND, ErrorCode.INVALID_INVITATION_CODE),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/invitations",
        "post",
        "409",
        setOf(ErrorCode.ALREADY_JOINED, ErrorCode.GROUP_FULL),
    ),
    ErrorResponseExpectation(
        "/api/v1/groups/{groupId}/members/me",
        "delete",
        "404",
        setOf(ErrorCode.GROUP_NOT_FOUND, ErrorCode.MEMBER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos/upload-urls",
        "post",
        "400",
        setOf(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_CONTENT_TYPE),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos/upload-urls",
        "post",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos",
        "post",
        "400",
        setOf(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_OBJECT_KEY),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos",
        "post",
        "403",
        setOf(ErrorCode.NOT_GROUP_MEMBER),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos",
        "post",
        "404",
        setOf(ErrorCode.USER_NOT_FOUND),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos",
        "post",
        "409",
        setOf(
            ErrorCode.PHOTO_ALREADY_REGISTERED,
            ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED,
        ),
    ),
    ErrorResponseExpectation(
        "/api/v1/photos",
        "post",
        "422",
        setOf(ErrorCode.OBJECT_NOT_UPLOADED),
    ),
    ErrorResponseExpectation(
        "/init/versions",
        "get",
        "400",
        setOf(ErrorCode.INVALID_PLATFORM, ErrorCode.INVALID_REQUEST),
    ),
)

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

private fun JsonNode.parameter(name: String): JsonNode =
    this["parameters"].first { parameter -> parameter["name"].stringValue() == name }

private fun JsonNode.requestExampleRef(example: OpenApiExample): String =
    this["requestBody"]["content"]["application/json"]["examples"]
        .get(example.componentName)["\$ref"].stringValue()

private fun JsonNode.successExampleRef(example: OpenApiExample): String =
    this["responses"]["200"]["content"]["application/json"]["examples"]
        .get(example.componentName)["\$ref"].stringValue()

private fun JsonNode.requestSchemaRef(): String =
    this["requestBody"]["content"]["application/json"]["schema"]["\$ref"].stringValue()

private fun JsonNode.successSchema(): JsonNode =
    this["responses"]["200"]["content"]["application/json"]["schema"]

private fun JsonNode.successSchemaRef(): String =
    successSchema()["\$ref"].stringValue()

private fun OpenApiExample.componentRef(): String =
    "#/components/examples/$componentName"

private fun String?.componentSchemaRef(): String {
    checkNotNull(this)
    return "#/components/schemas/$this"
}

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

private fun JsonNode.shouldBeProblemExample(
    errorCode: ErrorCode,
    status: HttpStatus,
) {
    this["summary"].stringValue() shouldBe errorCode.message
    this["value"]["type"].stringValue() shouldBe "about:blank"
    this["value"]["title"].stringValue() shouldBe status.reasonPhrase
    this["value"]["status"].intValue() shouldBe status.value()
    this["value"]["detail"].stringValue() shouldBe errorCode.message
    if (errorCode == ErrorCode.INVALID_PLATFORM) {
        this["value"]["code"].stringValue() shouldBe errorCode.name
    } else {
        this["value"].has("code") shouldBe false
    }
}
