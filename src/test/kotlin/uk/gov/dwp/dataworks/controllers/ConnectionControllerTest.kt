package uk.gov.dwp.dataworks.controllers

import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import uk.gov.dwp.dataworks.services.AuthenticationService
import uk.gov.dwp.dataworks.services.ConfigurationService

@RunWith(SpringRunner::class)
@WebMvcTest(ConnectionController::class)
class ClusterCreationControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var configService: ConfigurationService

    @BeforeEach
    fun setup() {
        whenever(authService.validate(any())).thenReturn(mock<DecodedJWT>())
    }

    @Test
    fun `Endpoints return '405 not supported' for GET requests`() {
        mvc.perform(get("/connect"))
                .andExpect(status().isMethodNotAllowed)
        mvc.perform(get("/disconnect"))
                .andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `400 returned when no auth token included`() {
        mvc.perform(post("/connect"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorization' for method parameter of type String"))

        mvc.perform(post("/disconnect"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorization' for method parameter of type String"))
    }

    @Test
    fun `401 returned when bad token`() {
        whenever(authService.validate(any())).thenThrow(JWTVerificationException(""))
        mvc.perform(post("/connect")
                .header("Authorization", "testBadToken"))
                .andExpect(status().isUnauthorized)
        mvc.perform(post("/disconnect")
                .header("Authorization", "testBadToken"))
                .andExpect(status().isUnauthorized)
    }

    @Test
    fun `200 returned with well formed request`() {
        whenever(authService.validate(any())).thenReturn(mock<DecodedJWT>())
        mvc.perform(post("/connect")
                .header("Authorization", "testGoodToken"))
                .andExpect(status().isOk)
        mvc.perform(post("/disconnect")
                .header("Authorization", "testGoodToken"))
                .andExpect(status().isOk)
    }
}
