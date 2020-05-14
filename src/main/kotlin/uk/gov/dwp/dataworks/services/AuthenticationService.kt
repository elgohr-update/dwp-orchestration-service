package uk.gov.dwp.dataworks.services

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.JWKKeystoreDataException
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.annotation.PostConstruct

/**
 * Service used to verify and validate JWT tokens included in requests
 */
@Service
class AuthenticationService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AuthenticationService::class.java))
    }

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver;

    internal lateinit var jwkProvider: JwkProvider
    internal lateinit var jwkProviderUrl: URL
    internal lateinit var issuerUrl: String

    @PostConstruct
    fun init() {
        val userPoolId: String = configurationResolver.getStringConfig(ConfigKey.COGNITO_USER_POOL_ID)
        issuerUrl = "https://cognito-idp.${configurationResolver.awsRegion}.amazonaws.com/$userPoolId"
        jwkProviderUrl = URL("$issuerUrl/.well-known/jwks.json")
        jwkProvider = UrlJwkProvider(jwkProviderUrl)
        logger.info("initialised JWK Provider", "user_pool_id" to userPoolId)
    }

    fun validate(jwtToken: String): JWTObject {
        val userJwt = JWT.decode(jwtToken)
        val jwk = jwkProvider.get(userJwt.keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")
        val algorithm = when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey, null)
            else -> throw JwkException("Unsupported JWK algorithm")
        }

        val jwt = JWT.require(algorithm)
                .withIssuer(issuerUrl)
                .build()
                .verify(userJwt)

        return JWTObject(
                jwt,
                userNameFromJwt(userJwt),
                groupsFromJwt(userJwt)
        )
    }

    /**
     * Helper method to extract the Cognito username from a JWT Payload.
     */
    fun userNameFromJwt(jwt: DecodedJWT): String {
        val username = jwt.getClaim("cognito:username").asString()
                ?: jwt.getClaim("username").asString()
                ?: throw IllegalArgumentException("No username found in JWT token")
        return username
    }

    /**
     * Helper method to extract groups from a JWT Payload.
     */
    fun groupsFromJwt(jwt: DecodedJWT): List<String> {
        val groups = jwt.getClaim("cognito:groups").asList(String::class.java)
                ?: throw IllegalArgumentException("No cognito groups found in JWT token")
        return groups
    }

    fun getB64KeyStoreData(): String {
        val httpCon = jwkProviderUrl.openConnection() as HttpURLConnection
        httpCon.requestMethod = "GET"
        httpCon.setRequestProperty("Accept", "application/json")
        httpCon.connect()

        if (httpCon.responseCode != HttpURLConnection.HTTP_OK)
            throw JWKKeystoreDataException("Error in HTTP request while getting keystore data, status code ${httpCon.responseCode} \n" +
                    httpCon.errorStream.bufferedReader().use(BufferedReader::readText))

        val keystoreData = httpCon.inputStream.bufferedReader().use(BufferedReader::readText)
        return Base64.getEncoder().encodeToString(keystoreData.toByteArray())
    }
}
