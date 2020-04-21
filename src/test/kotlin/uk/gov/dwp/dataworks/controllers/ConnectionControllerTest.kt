package uk.gov.dwp.dataworks.controllers

import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import uk.gov.dwp.dataworks.model.JWTObject
import uk.gov.dwp.dataworks.services.ConfigurationService
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.AuthenticationService
import uk.gov.dwp.dataworks.services.ExistingUserServiceCheck
import uk.gov.dwp.dataworks.services.TaskDeploymentService

@RunWith(SpringRunner::class)
@WebMvcTest(ConnectionController::class, ConfigurationService::class)
class ConnectionControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var existingUserServiceCheck: ExistingUserServiceCheck
    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService

    @Before
    fun setup() {
        System.setProperty(ConfigKey.COGNITO_USER_POOL_ID.key, "test_id")
        System.setProperty(ConfigKey.ECS_CLUSTER_NAME.key, "test_ecs")
        System.setProperty(ConfigKey.USER_CONTAINER_URL.key, "test_url")
        val jwtObject = JWTObject(mock<DecodedJWT>(), "test_user")
        whenever(authService.validate(any())).thenReturn(jwtObject)
        whenever(existingUserServiceCheck.check(any(), anyString())).thenReturn(false)
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
                .content("{}")
                .header("content-type", "application/json")
                .header("Authorisation", "testBadToken"))
                .andExpect(status().isUnauthorized)
        mvc.perform(post("/disconnect")
                .header("Authorisation", "testBadToken"))
                .andExpect(status().isUnauthorized)
    }

    @Test
    fun `200 returned with well formed request connect and disconnect`() {
        mvc.perform(post("/connect")
                .content("{}")
                .header("content-type", "application/json")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
        mvc.perform(post("/disconnect")
                .header("Authorisation", "testGoodToken"))
                .andExpect(status().isOk)
    }
    @Test
    fun `Endpoint deployusercontainers returns '405 not supported' for GET requests`() {
        mvc.perform(get("/deployusercontainers"))
                .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed)
    }

    @Test
    fun `200 returned from deployusercontainers with well formed request`() {
        mvc.perform(post("/deployusercontainers")
                .header("Authorisation", "test_user")
                .header("content-type", "application/json")
                .content("{}"))
                .andExpect(status().isOk)
    }

    @Test
    fun `400 with missing Authorisation from header deployusercontainers`() {
        mvc.perform(post("/deployusercontainers")
                .content("{}")
                .header("content-type", "application/json"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorisation' for method parameter of type String"))
    }
}
