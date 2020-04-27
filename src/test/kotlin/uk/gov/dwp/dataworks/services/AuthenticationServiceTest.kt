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
    private val cognitoAndNoneCognito= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJjb2duaXRvOnVzZXJuYW1lIjoiY29nbml0b1VzZXJOYW1lIiwidXNlck5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.g2qv6-6c32j7Qggk5pZ7NEU6FyCaaOdZU92vXle4Ff3YAn1qyFVcd5oaaAuul-pkJFs7njnUpMlF7ijJlDqIWYwiOcbUI0G9oAwp482AYbZR67hkQXj7v6xUd1iRJ3SuCVh9KfJA799qxG0RxhtX6A9xlT87SEJwjg8AjKwgr3ttQdEz1ZF13xriIA1R2-TmmgOGfXrjUHQNCKDQGf-3gBLcZMtNB2NmX_-qS7FkWflzaWl8-zfdqdI00ztNQYzHLYDUv5grsKM9Bwdys_9SYq-N2ZuHA61pHlDPe_DyYiwgn5TPuqTMH_OSJcX_S0PJRAQMhakWPqPhvH992SXdnQ"
    private val noneCognitoOnly= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1c2VyTmFtZSI6InVzZXJOYW1lIiwiaXNzIjoiVGVzdElzc3VlciJ9.PS37nzlPzI-Yk-K4tlD9meXwOyG04gksmMIcw9KJ_waGyH0sXQidF_jdWBvwzmqKuMmY5pFEFbLCXj_V3I0IoYeUx4ccSQJTPGmg3sok_eeYZrAkxRWvLyTF4BAP3IcWwK8-DD9Ren7uB14kZcKEvDY73ieS85vZUdSTCiG32aibYxzULeV1c31KkZ7uvMo4yOp8q3QZ9x-5gnI4ALsGE4r0thelyRZyka6_JytvQCKHt8GLvEOCWCV3xJ4o_g2bbp7anaY4M6dBnM8P2wuYw_XBXLQTvgBHiZz7zLrVWjhYwZUVVC3f_wWAtMUclfot6qrzTt7rjofwxKAfH4H1JQ"
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
