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

    private val cognitoUserNameOnly= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6ImNvZ25pdG9Vc2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.CpUehrX6RehLxHWm6BiIfFvE6dXATYmXYMuFpRqDevwGVPLS-jsWrafxGYsdcX-KLQxhLsj7i2Ni8dZnoOkNGthkPruiqnWGes93kH4PhKE-ZtZ4VQ8cqve6eVWmxUFr1vLIXQ2gWAEhirQYNLqlxyccFulnFphkPlgLucZ3Zgxcm31MWzSGG7uzyRvVCrklLXEv4aaU7xBAWZSmBReKf1Je3UigCmHbMH4B-jGNA-RWao_pV0PxlA1Swv6jM4V4dPCWJn9nnBSluzfjt7A9McAQktgmpPfMsVgXQksC2AACvZKgt-MFP1bAZLipgzZ54Vmvz65LYkqtIlD2tFNAIg"
    private val cognitoAndNoneCognito= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6ImNvZ25pdG9Vc2VyTmFtZSIsInVzZXJuYW1lIjoidXNlck5hbWUiLCJpc3MiOiJUZXN0SXNzdWVyIn0.ScjcTwBPNbpk1eeC_P5OPU-wieucIiTWvyN60B42qU5VpsVUuzBWsyop9OfAcoiPbb7guIetlQUNNdlW9aKtI5sLf_TJMTO9j99FL8TOS6K-REK1jjAjjWvWjyZl-5BvgQ-HuhQwhMoER1F1PfjeXJQSgMPyggnoOLzCm8ijEQlRx-x-FJ9pIFgPu2ySuGeNKHqbymh_YH35FPBm-94-9s4IiuWOHPtxpEHb3RXGjxnZ-CCbqHHtWFiJbTHxeiUOb9ziANP4RMAAmsWRHZ-_5uO5ngHPTapivZsxFqgJDYPzczC5GJ8jU2vUeDfrzYiGO_Ms0S1tciUc2AU9W1k6rQ"
    private val noneCognitoOnly= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwidXNlcm5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.KeP6Wk7Bnt1BAG5Drzit2PsWvIUpIMAl7SCdwvTBoH295kN_K9OaY1pM4An_4b5McnpwHYYrfmr_SCHM_7PNe3VNpv7hhodTHNEzqAQ8PnXxEvaQAQhIpExkpCU_yNqHYoXzbT9ftGnh5uLVKgOjnUXKANMcAxBPjaZ0w2M-SjWJSqSLsI9c04HfF410JK-pyjV6TK1phG3Vfbk0BJ8byXvxSXJITeBlR34RIsXwhZAO3LveU1Kzicrv0M43ZE0d_d1edMv6qkaVt5LsxQTCjsXStj5JFcdtLgb6xpBVN0THOaW2NT9sdxYkdqngI6ZlXdFPHzRO1zAqF15QuvdvSw"
    private val noUserName= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwibm9Vc2VyTmFtZSI6Im5vTmFtZSJ9.ELIjgAc31MS4ul32KKmTxYSumL1hOnQmfh0z6BEvMdH0Jwi0Ocm79raWqcjQwARmw77EgijqBhU7WYkYh9rQ7yLJfd2CBe8Exuu-cFIyw-xPCS3xlYfUavTIgWHQz_J690-WcFWS1boxMDGp247ciS1tLdHcQ1GpGgTc4XnED14NeN2OvEWvdmnF8o7ZJbq34EjPK_FndXwsT2ldOHAk_gBIFcux0FMOb8dWQCtjimFU9jEgZCRWFLAFQR4lamZzqDvs6xtfSQX_I6aQ5tgcFPzTVs8OEkUPWZNMduyXsRUHbbUoIL7z6dJOnFI60d14vEmHW8vl9iySXg8XDuJ-4A"
    private val noGroups= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJub0NvZ25pdG86Z3JvdXBzIjpbImNnMSIsImNnMiJdLCJ1c2VybmFtZSI6IlRlc3RVc2VyTmFtZSJ9.m-9P5Ci1V8kacH8_lEKiJ8Ddeo9yowf51KsuEqjqmMOoaMuv-XML1pHpJTgaYnRoDiKNzEAoKIz7Bd5ozemS4vGRr_2Iis94qa_nmM1KVnodkLDOelot5cneMLrSqGy65SBwXYiEjM-RXr13yA5gDRtVrK9hrGVtdnvKTwaSoCpFNHC7vznkTTPXXkI0oRPQ35pwrOfE0ClSfO7GLp3xZfq5S-RBILBHvf4Gd2aE_13SqzIwGJqCW4_9Guzvjnf2f7B1zNcVHdSAYQ78XQPn38WxhKI14_Aus58GHWtL9OyK2r-3_3mZc2rnEN_2cAPpOFyVUd09GiUYwb-YLFygUA"

    @BeforeEach
    fun setup() {
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
        whenever(configurationResolver.getStringConfig(any())).thenReturn("")
    }

    @Test
    fun `Returns cognito username when present`() {
        val decodedJWT = JWT.decode(cognitoUserNameOnly)
        val cognitoUserName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns cognito username when cognito and none cognito present`() {
        val decodedJWT = JWT.decode(cognitoAndNoneCognito)
        val cognitoUserName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns non-cognito username when cognito username isnt present`() {
        val decodedJWT = JWT.decode(noneCognitoOnly)
        val cognitoUserName = authenticationService.userNameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("userName")
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
}
