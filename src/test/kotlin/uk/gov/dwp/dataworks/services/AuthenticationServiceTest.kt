package uk.gov.dwp.dataworks.services

import com.auth0.jwt.JWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.aws.AwsClients
import java.lang.IllegalArgumentException

@RunWith(SpringRunner::class)
class AuthenticationServiceTest {
    @InjectMocks
    private lateinit var authenticationService: AuthenticationService

    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var awsClients: AwsClients
    @MockBean
    private lateinit var configurationResolver: ConfigurationResolver

    private val cognitoUserNameOnly= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJjb2duaXRvOnVzZXJuYW1lIjoiY29nbml0b1VzZXJOYW1lIiwiaXNzIjoiVGVzdElzc3VlciJ9.atgHhs2UIhHq4pngA3q5yZSnTckSfan2LFixG85bnC1KJlZdacTTdJlYlowy63fRru7iyqJkRW1ALFJ8YownLpQn6NW4vLGrwz33PNIyxl0_r-DMQDlN1AENO-Hb46d8bu9S9x9Py6ujgVjhuoXC8_cgJFeMhXQUePhDOVa2nGfPQ85JUuCV4zu8XApDNITmWhfjFMBquJFYvIj51t2h8NlZyDsq3P2H0rjPxWDa3H21a5am_Mkh0qc5bCK8K41mzv77vv1ZPKtqWz1m5rfw65y4mtMDOHWpXczreJsnIaytWdPkgPOREPCVe8AaDHkFyKWyHEQ_-su3qQXmmnUorg"
    private val cognitoAndNoneCognito= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOnVzZXJuYW1lIjoiY29nbml0b1VzZXJOYW1lIiwidXNlcm5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.Yk5KiToUCyJyIZh8V3hi8BuXceq55niEGxaEOfZfqncClDFO8-s_4-d__1wzyMyccr6ONafgKqbLR8qavtNsoy-0Z7MsltaN100FOnQC8JMYXbdTbhCLSmeq41LW9-mTym3jnDtc1dyUOdejY1F7HHmQdvP7TvrqPEhJjfhf1d_0yXLhkKSAdhcO8nRN4HlvB3SKY4DmTR7ZnqVq9ZfJaTKujaBhqsOu59dt_srtZOkN56INVHT69_LfMHOWJwSWtOBI9R4WVgGI3dM8ZvKmfM1DyhsRj3ndPdAYMCKhsHpmTFeRNx2Le20bEH41YW5O0lkP59Yu98DOvRddYnsnFQ"
    private val noneCognitoOnly= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6InVzZXJOYW1lIiwiaXNzIjoiVGVzdElzc3VlciJ9.YyzwQsAN0dy0F8_YjMZ7Bpupf66R-IGMH0tFwUpIJbsK9i_yDOcmizBmGHq79TFhbuQyJ_-aJ4avuZ4dChVoaqnHLUNazv_dzGuSUEbwjfJIfAHowpv9ppXlyeJLFy4IpngAUMcg0b7E62VTQ7zVYitqAd8-APmtM2wBjq0r1K9ejzTM_658LIsNadG8iYqEy0UKouZqnuhYvWXJZCJF8OShBF_OXELMFZ_roqK7FNbxjA2h9J40QItZZEGEy4FezCZj7H7l1tgm244CyIGhx1h4CD91PXrXe7O3CvKi8y75SdW6qfEzBY5uCR08-Z510R5_nvx-iVhnBi_ydj5kLA"
    private val noUserName= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJub1VzZXJOYW1lIjoibm9OYW1lIn0.OqJBmVXDYr_TpfagfsfpVL_cK64QEG6K92CLk4GH36RxAGgrpqro5w1EOicGqXCOu6PUZViV5-Xu4QUIuu0lcqPpD-4tybe54z_zHuebKGIcWHifUm2tT5Cfbwvj98Vc_BKtGuKCXiPZQ8tW-T6ELXXJifIfxHB_sypQ5nnmo_GaBUAdP7JkIS-6Dy8OzcwD04pF_O5TOUfwpcnV5aEhfSxPY9TAmfUWVw-xMaGRxtqiolsOKa2iWsg805U2k8R01TkYtTzhfjMD3CvbJpCxoz8JpL9VXcmSP84S1D33kbyygYkyYXrrESnrgDJvGexgtj22PjQbRcXlSkzS7Z1_jw"

    @BeforeEach
    fun setup() {
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
        whenever(configurationResolver.getStringConfig(any())).thenReturn("")
    }

    @Test
    fun `Returns cognito username when present`() {
        val decodedJWT = JWT.decode(cognitoUserNameOnly)
        val cognitoUserName = authenticationService.cognitoUsernameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns cognito username when cognito and none cognito present`() {
        val decodedJWT = JWT.decode(cognitoAndNoneCognito)
        val cognitoUserName = authenticationService.cognitoUsernameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns non-cognito username when cognito username isnt present`() {
        val decodedJWT = JWT.decode(noneCognitoOnly)
        val cognitoUserName = authenticationService.cognitoUsernameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("userName")
    }

    @Test
    fun `Throws correct error, when no user name present`() {
        val decodedJWT = JWT.decode(noUserName)
        Assertions.assertThatCode { authenticationService.cognitoUsernameFromJwt(decodedJWT) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("No username found in JWT token")
    }
}
