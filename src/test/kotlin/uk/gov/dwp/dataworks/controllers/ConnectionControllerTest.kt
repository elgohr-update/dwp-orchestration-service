package uk.gov.dwp.dataworks.controllers

import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.services.ActiveUserTasks
import uk.gov.dwp.dataworks.services.AuthenticationService
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import uk.gov.dwp.dataworks.services.TaskDestroyService
import uk.gov.dwp.dataworks.services.JwtParsingService
import uk.gov.dwp.dataworks.services.UserValidationService

@RunWith(SpringRunner::class)
@WebMvcTest(ConnectionController::class, ConfigurationResolver::class)
class ConnectionControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var taskDestroyService: TaskDestroyService
    @MockBean
    private lateinit var activeUserTasks: ActiveUserTasks
    @MockBean
    private lateinit var jwtParsingService: JwtParsingService
    @MockBean
    private lateinit var userValidationService: UserValidationService

    @Before
    fun setup() {
        System.setProperty(ConfigKey.COGNITO_USER_POOL_ID.key, "test_id")
        System.setProperty(ConfigKey.ECS_CLUSTER_NAME.key, "test_ecs")
        System.setProperty(ConfigKey.USER_CONTAINER_URL.key, "test_url")
        System.setProperty(ConfigKey.DEBUG.key, "true")
        val jwtObject = JWTObject(mock<DecodedJWT>(), "test_user", listOf("group1", "group2"))
        whenever(authService.validate(any())).thenReturn(jwtObject)
    }

    @Test
    fun `Endpoint connect and disconnect return '405 not supported' for GET requests`() {
        mvc.perform(get("/connect"))
                .andExpect(status().isMethodNotAllowed)
        mvc.perform(get("/disconnect"))
                .andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `400 returned when no auth token included connect and disconnect`() {
        mvc.perform(post("/connect"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorisation' for method parameter of type String"))

        mvc.perform(post("/disconnect"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorisation' for method parameter of type String"))
    }

    @Test
    fun `401 returned when bad token connect and disconnect`() {
        whenever(authService.validate(any())).thenThrow(JWTVerificationException(""))
        mvc.perform(post("/connect")
                .content("{\"emrClusterHostName\":\"\"}")
                .header("content-type", "application/json")
                .header("Authorisation", "testBadToken"))
                .andExpect(status().isUnauthorized)
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        mvc.perform(post("/disconnect")
                .header("Authorisation", "testBadToken"))
                .andExpect(status().isUnauthorized)
    }

    @Test
    fun `200 returned with well formed new environment request connect and disconnect`() {
        mvc.perform(post("/connect")
                .content("{\"emrClusterHostName\":\"\"}")
                .header("content-type", "application/json")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
                .andExpect(content().json("{ 'url': 'test_url/test_user/', 'redirect'=false}"))
        mvc.perform(post("/disconnect")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
    }

    @Test
    fun `200 returned with well formed existing environment request connect and disconnect`() {
        whenever(activeUserTasks.contains(any())).thenReturn(true)
        mvc.perform(post("/connect")
                .content("{\"emrClusterHostName\":\"\"}")
                .header("content-type", "application/json")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
                .andExpect(content().json("{ 'url': 'test_url/test_user/', 'redirect'=true}"))
        mvc.perform(post("/disconnect")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
        validateMockitoUsage()
    }

    @Test
    fun `Endpoint debug deploy returns '405 not supported' for GET requests`() {
        mvc.perform(get("/debug/deploy"))
                .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed)
    }

    @Test
    fun `200 returned from debug deploy with well formed request`() {
        mvc.perform(post("/debug/deploy")
                .header("Authorisation", "test_user")
                .header("cognitoGroups", "test_cg1", "test_cg2")
                .header("content-type", "application/json")
                .content("{\"emrClusterHostName\":\"\"}"))
                .andExpect(status().isOk)
    }

    @Test
    fun `400 with missing Authorisation from header debug deploy`() {
        mvc.perform(post("/debug/deploy")
                .content("{}")
                .header("cognitoGroups", "cognito_group")
                .header("content-type", "application/json"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorisation' for method parameter of type String"))
    }

    @Test
    fun `400 with missing cognito groups from header deployusercontainers`() {
        mvc.perform(post("/debug/deploy")
                .content("{}")
                .header("Authorisation", "test_user")
                .header("content-type", "application/json"))
                .andExpect(status().isBadRequest)
    }

    @Test
    fun `200 returned when calling cleanup endpoint`() {
        mvc.perform(post("/cleanup")
                .header("content-type", "application/json")
                .content("{\"activeUsers\": [\"testuser1\", \"testuser2\"]}"))
                .andExpect(status().isOk)
    }

    @Test
    fun `204 returned when calling verify-user endpoint with invalid jwt`(){
        whenever(userValidationService.checkJwtForAttributes(any())).doReturn(false)
        mvc.perform(post("/verify-user")
                .header("content-type", "application/json")
                .header("Authorisation", "testBadToken"))
                .andExpect(status().isNoContent)
    }

    @Test
    fun `200 returned when calling verify-user endpoint with valid jwt`(){
        whenever(userValidationService.checkJwtForAttributes(any())).doReturn(true)
        mvc.perform(post("/verify-user")
                .header("content-type", "application/json")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
    }
}
