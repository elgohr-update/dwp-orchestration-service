package uk.gov.dwp.dataworks.pacts

import au.com.dius.pact.provider.junit.Provider
import au.com.dius.pact.provider.junit.RestPactRunner
import au.com.dius.pact.provider.junit.State
import au.com.dius.pact.provider.junit.loader.PactUrl
import au.com.dius.pact.provider.junit.target.TestTarget
import au.com.dius.pact.provider.spring.target.MockMvcTarget
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.controllers.ConnectionController
import uk.gov.dwp.dataworks.services.*

@RunWith(RestPactRunner::class)
@Provider("OrchestrationService")
@PactUrl(urls = ["https://github.com/dwp/dataworks-analytical-frontend-service/releases/latest/download/frontendservice-orchestrationservice.json"])

class FrontEndServicePactProviderTest {

    @Mock
    lateinit var authServiceMock: AuthenticationService
    @Mock
    lateinit var activeUserTasks : ActiveUserTasks;
    @Mock
    lateinit var taskDeploymentService: TaskDeploymentService
    @Mock
    lateinit var taskDestroyService: TaskDestroyService
    @Mock()
    lateinit var configurationResolver: ConfigurationResolver

    @InjectMocks()
    lateinit var connectionController: ConnectionController

    @JvmField
    @TestTarget
    val target = MockMvcTarget()

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        target.setControllers(connectionController)
        whenever(configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_URL)).thenReturn("userContainerUrl")
    }

    @State("I am awaiting a connection")
    fun `a request for a tooling environment with valid token`() {
        val decodedJWT : DecodedJWT = mock()
        val goodJWT = JWTObject(userName = "validUser", cognitoGroup = emptyList(), verifiedJWT = decodedJWT )
        whenever(authServiceMock.validate(anyString())).thenReturn(goodJWT)
    }

    @State("I am awaiting an invalid connection")
    fun `a request for a tooling environment with invalid token`() {
        whenever(authServiceMock.validate(anyString())).thenThrow(JWTVerificationException(""))
    }
}
