package uk.gov.dwp.dataworks.services

import com.auth0.jwt.JWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.springframework.test.context.junit4.SpringRunner
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@RunWith(SpringRunner::class)
class UserValidationServiceTest {
    @InjectMocks
    private lateinit var userValidationService: UserValidationService
    @Mock
    private lateinit var jwtParsingService: JwtParsingService
    @Mock
    private lateinit var awsCommunicator: AwsCommunicator

    private var jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJKb2huIERvZSIsImNvZ25pdG86Z3JvdXBzIjpbImdyb3VwMSIsImdyb3VwMiJdLCJpYXQiOjE1MTYyMzkwMjJ9.EvyNmtx5fwQ_4ndrNI9w1J3lrq-hcJ2JEvw6SN9miZogBz93QacM47qBm0nnr3wZeTCZWT5fyxjmjFxLKwxPrx8NH1hQy-jV1MUZugkryOqEZ0t4hBF5ZWROjkA4KUDwJDzJNDE5uqxFyroQcKa_GYv4zyUCJHbUpCzLLIQMIam8RWRMfsiGB-GxW4WDxKO_oxoJ6tTe0YIQM9tJ6f64WgYAHuTXEXD39k-33t1M5j9gESqdkTU_LW1_CEc8L-mGTbID4Ut9E41mlkrYnkuF81Dx7OBFHuX74lrkMKmHY23NJp8Z2sWPqtUzDKY207dI0VWhPxrHBPsFO1UiXRbc3Q"

    @Before
    fun setup() {
        var decodedJWT = JWT.decode(jwt)
        var jwtObject = JWTObject(decodedJWT, "testuser123", listOf("group1", "group2"))
        whenever(jwtParsingService.parseToken(any())).doReturn(jwtObject)
    }

    @Test
    fun `returns false when parsing service doesn't find enabled group or user kms`() {
        whenever(awsCommunicator.checkForExistingEnabledKey("group1-shared")).doReturn(false)
        whenever(awsCommunicator.checkForExistingEnabledKey("testuser123-home")).doReturn(false)
        assertFalse(userValidationService.checkJwtForAttributes("fake_token"))
    }

    @Test
    fun `returns false when parsing service doesn't find enabled group kms and finds enabled user kms`() {
        whenever(awsCommunicator.checkForExistingEnabledKey("testuser123-home")).doReturn(true)
        whenever(awsCommunicator.checkForExistingEnabledKey("group1-shared")).doReturn(false)
        assertFalse(userValidationService.checkJwtForAttributes("fake_token"))
    }

    @Test
    fun `returns false when parsing service doesn't find enabled kms and finds group`() {
        whenever(awsCommunicator.checkForExistingEnabledKey("testuser123-home")).doReturn(false)
        whenever(awsCommunicator.checkForExistingEnabledKey("group1-shared")).doReturn(true)
        assertFalse(userValidationService.checkJwtForAttributes("fake_token"))
    }

    @Test
    fun `returns true when parsing service finds enabled group and user kms`() {
        whenever(awsCommunicator.checkForExistingEnabledKey("testuser123-home")).doReturn(true)
        whenever(awsCommunicator.checkForExistingEnabledKey("group1-shared")).doReturn(true)
        assertTrue(userValidationService.checkJwtForAttributes("fake_token"))
    }
}
