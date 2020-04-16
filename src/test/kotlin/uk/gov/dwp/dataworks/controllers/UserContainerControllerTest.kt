package uk.gov.dwp.dataworks.controllers

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import uk.gov.dwp.dataworks.services.*

@RunWith(SpringRunner::class)
@WebMvcTest(UserContainerController::class)
class UserContainerControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var configService: ConfigurationService
    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var existingUserServiceCheck: ExistingUserServiceCheck


    @BeforeEach
    fun setup() {
        whenever(authService.validate(any())).thenReturn(mock<DecodedJWT>())
        whenever(existingUserServiceCheck.check(anyString(), anyString())).thenReturn(false)
    }

    @Test
    fun `Endpoints return '405 not supported' for GET requests`() {
        mvc.perform(MockMvcRequestBuilders.get("/deployusercontainers"))
                .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed)
    }

    @Test
    fun `200 returned with well formed request`() {
        mvc.perform(MockMvcRequestBuilders.post("/deployusercontainers")
                .content("{\"ecsClusterName\": \"Test Cluster Name\","
                        + " \"userName\": \"Test User Name\"," +
                        "\"emrClusterHostName\": \"Test EMR Host Name\"," +
                        "\"albName\": \"Test alb Name\"" +
                        "}")
                .header("content-type", "application/json"))
                .andExpect(MockMvcResultMatchers.status().isOk)
    }
}
