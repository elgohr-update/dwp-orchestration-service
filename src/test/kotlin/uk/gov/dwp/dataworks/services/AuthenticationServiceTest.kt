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

    private val cognitoUserNameOnly= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6ImNvZ25pdG9Vc2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIiLCJzdWIiOiIxMjM0NTY3ODkwIn0.m21vtIWQXK65MiICii6uoktGaRtFfupKj4f1-NVnbKcBWcEoPauK1caAs36f1vGQsz8j8LoONRbmWDMvF2J5L8hza7-Vml83qlASCpYXsSvHD-mJ4uVcGfJ3RWWZdt3XNdTnaMPSL3S-B-8ZiM7ShSxvKgEFpojo-eIIEkFlwWb1G-GKjmkFUJ-EBGUcUbrMmb-JadIDTVZSQSM6QufetpFfvgjTVYUc4G7nL68cT_6i9pqjXzpDjx6VT6Z1oIO_McOnbHGkxh3w27XQmgEumMAAUqRia0AcGlWUazWGBRb0tYsgcyLpeSa4XTa02Ze5j67GUP_vNFFEIhyzsNBjmA"
    private val cognitoAndNoneCognito= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6ImNvZ25pdG9Vc2VyTmFtZSIsInVzZXJuYW1lIjoidXNlck5hbWUiLCJpc3MiOiJUZXN0SXNzdWVyIiwic3ViIjoiMTIzNDU2Nzg5MCJ9.UDU_SpaoVyGT7NyQnBHCI2w-G5j5pUId4LaQHfXYjiFM7etuCsIoNkhmqR8msBki6YDxXl59Ut9el_f3AioT0sCtDhyKcYH7Lr0YSGVU3CIo1rgqVa14K7Nm4Z_aWcj8RffsXZF4WXt6satQhrOYpD-i9b3sdB7FqBWQrau3PowEfwn5njoxTd_ghvpPU34E4G8AaSMza6NII7TfKlZlD2-HCGJNL_1a72WgoPCqABYYS0KdQReoZZidTdOJq355iM4rJRHKEJA1L8yNe2DlZhXOo0PHuBtV2OyFjL4jJuL49c-e_FA3IXornat1OvVCFSRzAe9ypJuaijgTkQb-2A"
    private val noneCognitoOnly= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwidXNlcm5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIiLCJzdWIiOiIxMjM0NTY3ODkwIn0.lYmwmI8or4iwitQAgWOatlcSRaKbQXxU4THERZdtSzBgZ_B4bhydmgNEagfU0nYue81uhoJI96pU5_sS99YfVVrNoHjMXOoT8J_y98N1sjg-qdITTN5ukMTUOvtOzE93OhB6lfWxuFPxf1SDOI83V9BPdFtal32828Nuz48ZGWzmm1fb3lvGtiaWX8O-aOtSyxZOUH4JnbcMKD6YTldTKsyn6-qwN3ZrTHXB0qG0sEJYbuJD2vY7YYZbQ7SyyCXPjS0zJmSYzqiwfwyHD2J6E6IHQpCaFz2Iy4iiGJnkOTsX7JYr58wsd08-yjeTVkbxADf2_TLY2erz8Y5SUjnvJQ"
    private val noUserName= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwibm9Vc2VyTmFtZSI6Im5vTmFtZSJ9.ELIjgAc31MS4ul32KKmTxYSumL1hOnQmfh0z6BEvMdH0Jwi0Ocm79raWqcjQwARmw77EgijqBhU7WYkYh9rQ7yLJfd2CBe8Exuu-cFIyw-xPCS3xlYfUavTIgWHQz_J690-WcFWS1boxMDGp247ciS1tLdHcQ1GpGgTc4XnED14NeN2OvEWvdmnF8o7ZJbq34EjPK_FndXwsT2ldOHAk_gBIFcux0FMOb8dWQCtjimFU9jEgZCRWFLAFQR4lamZzqDvs6xtfSQX_I6aQ5tgcFPzTVs8OEkUPWZNMduyXsRUHbbUoIL7z6dJOnFI60d14vEmHW8vl9iySXg8XDuJ-4A"
    private val noGroups= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJub0NvZ25pdG86Z3JvdXBzIjpbImNnMSIsImNnMiJdLCJ1c2VybmFtZSI6IlRlc3RVc2VyTmFtZSJ9.m-9P5Ci1V8kacH8_lEKiJ8Ddeo9yowf51KsuEqjqmMOoaMuv-XML1pHpJTgaYnRoDiKNzEAoKIz7Bd5ozemS4vGRr_2Iis94qa_nmM1KVnodkLDOelot5cneMLrSqGy65SBwXYiEjM-RXr13yA5gDRtVrK9hrGVtdnvKTwaSoCpFNHC7vznkTTPXXkI0oRPQ35pwrOfE0ClSfO7GLp3xZfq5S-RBILBHvf4Gd2aE_13SqzIwGJqCW4_9Guzvjnf2f7B1zNcVHdSAYQ78XQPn38WxhKI14_Aus58GHWtL9OyK2r-3_3mZc2rnEN_2cAPpOFyVUd09GiUYwb-YLFygUA"
    private val adfsUser= "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6IkRXX0FERlMuVEVTVEBEV1AuR0lTLkdPVi5VSyIsImlzcyI6IlRlc3RJc3N1ZXIiLCJzdWIiOiIxMjM0NTY3ODkwIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiODc2NTQzMjEiLCJqdGkiOiJhNDdlNjFkMy00YmE3LTQ2YWQtYWZlZS1jYTkwYzAwZGZiZWYiLCJpYXQiOjE1OTY3OTY0MzQsImV4cCI6MTU5NjgwMDAzNH0.Ve2qip1p_uo2ODTzk5OsbN_orjemPP0ygXHAxJA0-84"

    @BeforeEach
    fun setup() {
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
        whenever(configurationResolver.getStringConfig(any())).thenReturn("")
    }

    @Test
    fun `Returns cognito username when present`() {
        val decodedJWT = JWT.decode(cognitoUserNameOnly)
        val cognitoUserName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName123")
    }
    @Test
    fun `Returns cognito username when cognito and none cognito present`() {
        val decodedJWT = JWT.decode(cognitoAndNoneCognito)
        val cognitoUserName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName123")
    }
    @Test
    fun `Returns non-cognito username when cognito username isnt present`() {
        val decodedJWT = JWT.decode(noneCognitoOnly)
        val cognitoUserName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("userName123")
    }

    @Test
    fun `Throws correct error, when no user name present`() {
        val decodedJWT = JWT.decode(noUserName)
        Assertions.assertThatCode { authenticationService.userNameFromJwt(decodedJWT) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("No username found in JWT token")
    }

    @Test
    fun `Throws correct error, when no cognito groups present`() {
        val decodedJWT = JWT.decode(noGroups)
        Assertions.assertThatCode { authenticationService.groupsFromJwt(decodedJWT)}
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("No cognito groups found in JWT token")
    }

    @Test
    fun `Uses the preferred_username rather than cognito_username`() {
        val decodedJWT = JWT.decode(adfsUser)
        val userName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(userName).isEqualTo("87654321123")
    }
}
