package uk.gov.dwp.dataworks.controllers


import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.net.URL
import java.security.interfaces.RSAPublicKey


@RestController
class ConnectionController {

    @GetMapping("/hello")
    fun hello() = "hello"
    @PostMapping("/connect")
    fun connect(@RequestHeader(value = "Authorisation") token: String): DecodedJWT {


        //  fetching the JWKS
        val jwkProvider = UrlJwkProvider(
                URL("https://cognito-idp.{region}.amazonaws.com/{userPool-Id}/.well-known/jwks.json")
        )
        //  decoding token and grab kid from header
        val jwt = JWT.decode(token)
        val jwk = jwkProvider.get(jwt.keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")
        //  grab right algorithm from header and throw error, if different
        val algorithm = when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey)
            else -> throw Exception("Unsupported Algorithm")
        }
        // verify algorithm
        val verifier = JWT.require(algorithm) // signature
                //  verify source
                .withIssuer("http://cognito-idp.{region}.amazonaws.com/{userPool-Id}") // iss
                //   not sure????
                .withAudience("api1") // aud
                .build()
        println("success!")
        return verifier.verify(token)


    }

}
