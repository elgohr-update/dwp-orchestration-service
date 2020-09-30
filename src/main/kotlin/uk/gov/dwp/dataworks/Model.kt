package uk.gov.dwp.dataworks

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import kotlin.reflect.full.declaredMemberProperties


data class DeployRequest @JsonCreator constructor(
        val jupyterCpu: Int = 256,
        val jupyterMemory: Int = 896,
        val additionalPermissions: List<String> = emptyList()
)

data class CleanupRequest(
        val activeUsers: List<String>
)

data class JWTObject(val verifiedJWT: DecodedJWT, val userName: String, val cognitoGroup: List<String>)

data class UserTask(val correlationId: String,
                    val userName: String,
                    val targetGroupArn: String?,
                    val albRoutingRuleArn: String?,
                    val ecsClusterName: String?,
                    val ecsServiceName: String?,
                    val iamRoleName: String?,
                    val iamPolicyUserArn: String?,
                    val iamPolicyTaskArn: String?) {
    companion object {
        fun from(map: Map<String, String>) = object {
            val correlationId: String by map
            val userName: String by map
            val targetGroupArn: String by map
            val albRoutingRuleArn: String by map
            val ecsClusterName: String by map
            val ecsServiceName: String by map
            val iamRoleName: String by map
            val iamPolicyUserArn: String by map
            val iamPolicyTaskArn: String by map
            val data = UserTask(correlationId, userName, targetGroupArn, albRoutingRuleArn, ecsClusterName,
                    ecsServiceName, iamRoleName, iamPolicyUserArn, iamPolicyTaskArn)
        }.data

        fun attributes(): List<AttributeDefinition> {
            return UserTask::class.declaredMemberProperties
                    .map { AttributeDefinition.builder().attributeName(it.name).attributeType(ScalarAttributeType.S).build() }
        }
    }
}

data class UserContainerProperties(
        val userName: String,
        val cognitoGroups: List<String>,
        val emrHostname: String,
        val jupyterCpu: Int,
        val jupyterMemory: Int,
        val guacamolePort: Int,
        val userS3Bucket: String,
        val kmsHome: String,
        val kmsShared: String,
        val gitRepo: String,
        val pushHost: String,
        val pushCron: String,
        val s3fsVolumeName: String
)

@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy::class)
data class StatementObject(
        @JsonProperty("Sid") var sid: String,
        @JsonProperty("Effect") var effect: String,
        @JsonProperty("Action") var action: MutableList<String>,
        @JsonProperty("Resource") var resource: MutableList<String>
)

@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy::class)
data class AwsIamPolicyJsonObject(
        @JsonProperty("Version") var version: String,
        @JsonProperty("Statement") var statement: List<StatementObject>
)
