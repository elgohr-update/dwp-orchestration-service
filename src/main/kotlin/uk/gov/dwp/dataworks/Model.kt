package uk.gov.dwp.dataworks

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.annotation.JsonCreator

data class DeployRequest @JsonCreator constructor(
        val jupyterCpu: Int = 512,
        val jupyterMemory: Int = 512,
        val additionalPermissions: List<String> = emptyList()
)

data class JWTObject(val verifiedJWT: DecodedJWT, val userName: String)
