package uk.gov.dwp.dataworks.services

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.URL
import java.security.interfaces.RSAPublicKey
import javax.annotation.PostConstruct

/**
 * Service used to verify and validate JWT tokens included in requests
 */
@Service
class AuthenticationService {

    @Autowired
    private lateinit var configService: ConfigurationService;

    private lateinit var jwkProvider: JwkProvider
    private lateinit var issuerUrl: String

    @PostConstruct
    fun init() {
        val userPoolId: String = configService.getStringConfig(ConfigKey.COGNITO_USER_POOL_ID)
        issuerUrl = "https://cognito-idp.${configService.awsRegion}.amazonaws.com/$userPoolId"
        jwkProvider = UrlJwkProvider(URL("$issuerUrl/.well-known/jwks.json"))
    }

    fun validate(jwtToken: String): DecodedJWT {
        val userJwt = JWT.decode(jwtToken)
        val jwk = jwkProvider.get(userJwt.keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")
        val algorithm = when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey, null)
            else -> throw JwkException("Unsupported JWK algorithm")
        }

        return JWT.require(algorithm)
                .withIssuer(issuerUrl)
                .build()
                .verify(userJwt)
    }
}