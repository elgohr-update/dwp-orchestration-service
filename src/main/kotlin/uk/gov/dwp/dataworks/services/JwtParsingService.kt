package uk.gov.dwp.dataworks.services

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class JwtParsingService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(JwtParsingService::class.java))
    }

    fun parseToken(jwt: String): JWTObject{
        val decodedJwt = JWT.decode(jwt)
        val cognitoGroups = getCognitoGroupsFromJwt(decodedJwt)
        val userName = getUsernameFromJwt(decodedJwt)

        return JWTObject(
                decodedJwt,
                userName,
                cognitoGroups
        )
    }

    fun getCognitoGroupsFromJwt(decodedJwt: DecodedJWT): List<String> {
        return decodedJwt.getClaim("cognito:groups").asList(String::class.java)
                ?: throw IllegalArgumentException("No cognito groups found in JWT token")
    }

    fun getUsernameFromJwt(decodedJwt: DecodedJWT): String {
        val userName = decodedJwt.getClaim("preferred_username").asString()
                ?: decodedJwt.getClaim("cognito:username").asString()
                ?: decodedJwt.getClaim("username").asString()
                ?: throw IllegalArgumentException("No username found in JWT token")
        val sub = decodedJwt.getClaim("sub").asString()
                ?: throw IllegalArgumentException("No sub found in JWT token")
        val subPrefix = sub.take(3)
        // Try and differentiate same usernames by adding
        // parts of the sub. (can't use full sub since
        // TargetGroupNames, IAMRoleNames etc are limited to
        // 32 chars (which is the size of sub!).

        return userName + subPrefix
    }
}
