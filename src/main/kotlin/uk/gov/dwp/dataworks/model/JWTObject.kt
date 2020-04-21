package uk.gov.dwp.dataworks.model

import com.auth0.jwt.interfaces.DecodedJWT

data class JWTObject(val verifiedJWT: DecodedJWT, val userName: String) {
}