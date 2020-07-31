package uk.gov.dwp.dataworks.integration

import com.auth0.jwt.JWT
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockitoAnnotations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Bean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.TaskDefinition
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.GetPolicyRequest
import software.amazon.awssdk.services.iam.model.GetRoleRequest
import software.amazon.awssdk.services.iam.model.Policy
import software.amazon.awssdk.services.iam.model.Role
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.AlreadyExistsException
import software.amazon.awssdk.services.kms.model.CreateAliasRequest
import software.amazon.awssdk.services.kms.model.CreateKeyRequest
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsClients
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing
import uk.gov.dwp.dataworks.controllers.ConnectionController
import uk.gov.dwp.dataworks.services.*

import java.lang.Exception
import java.net.URI

@RunWith(SpringRunner::class)
@WebMvcTest(
        properties = [
            "orchestrationService.aws_region=us-east-1",
            "orchestrationService.user_container_port=1234",
            "orchestrationService.user_container_url=www.com",
            "orchestrationService.user_task_execution_role_arn=arn:aws:iam::000000000000:policy/analytical-testusername123-taskRolePolicyArn",
            "orchestrationService.user_task_subnets=testSubnets",
            "orchestrationService.user_task_security_groups=testSg",
            "orchestrationService.load_balancer_port=1234",
            "orchestrationService.load_balancer_name=testLb",
            "orchestrationService.user_container_url=www.com",
            "orchestrationService.emr_cluster_hostname=test_hostname",
            "orchestrationService.ecs_cluster_name=test_cluster",
            "orchestrationService.container_log_group=testLog",
            "orchestrationService.aws_account_number=000000000000",
            "orchestrationService.ecr_endpoint=endpoint",
            "orchestrationService.debug=false",
            "orchestrationService.jupyterhub_bucket_arn=arn:aws:s3:::bucketTest",
            "TAGS={\"Name\":\"TaskName\", \"Fruit\": \"Cherry\", \"Colour\":\"Red\"}",
            "orchestrationService.data_science_git_repo=codecommit_repo"
        ],
        controllers = [
            ConnectionController::class,
            ConfigurationResolver::class,
            TaskDeploymentService::class,
            AwsParsing::class
        ]

)
class OrchestrationServiceSpec {
    private val localStackClients: LocalStackClients = LocalStackClients()
    private val localIamClient = localStackClients.localIamClient()
    private val localKmsClient = localStackClients.localKmsClient()
    private val localDynamoClient = localStackClients.localDynamoDbClient()

    @Autowired
    private lateinit var mvc: MockMvc
    @SpyBean
    private lateinit var awsCommunicator: AwsCommunicator
    @MockBean
    private lateinit var activeUserTasks: ActiveUserTasks
    @SpyBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var taskDestroyService: TaskDestroyService
    @MockBean
    private lateinit var awsClients: AwsClients
    @MockBean
    private lateinit var authenticationService: AuthenticationService

    private val newUserTestJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6InRlc3R1c2VybmFtZSIsInVzZXJuYW1lIjoidXNlck5hbWUiLCJpc3MiOiJUZXN0SXNzdWVyIiwic3ViIjoiMTIzNDU2Nzg5MCJ9.lXKrCqpkHBUKR1yN7H85QXH9Yyq-aFWWcLa2VDxkP8SbqEnPttW7DGRL0jj2Pm8JimSvc0WFGZvvyT7cCZllEyjCHjCRIXgXbIv5pg9kFzRNgp2D7W-MujZAul6-TJrJ3h9Dv0RRKklrZvKr6PXCnwpFGqrwlzUg-2zMh9x2QEK4Hjr7-EZWJtorJAtSYKUWwKh_wLrFb9PBwSDIrbO0i1snJHIM1_ti6S7_qf4Mmf29Zzn_HeakLnLM06YPCxqkV-KM4ABsax9BQirQF67KI9o7p5SgNjqlDscb6gn5XmV6eGG193rtMiiPxhgioP4eMQFzpA_ZuNbB1om7qsEdWA"
    private val existingUserTestJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6ImV4aXN0aW5ndGVzdHVzZXJuYW1lIiwidXNlcm5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIiLCJzdWIiOiIxMjM0NTY3ODkwIn0.nt7d_o7R8aTvNEa9saVjDm_-kwmDwsly1dfqB5bFUZfapb2W_tVEOaPuH78lKgUAifOjao4EOrfkp-5SNKIzecmdHtSoRNymrl7f95S-kgLayJsA2-qSPCSOvoPa_LKOmmMWH9-3TtZb-fd5D-pWJGx40dqErFkUx2sYt9MRmIV5HA0-oCsLjpwYHh5dD6rhI-G40ABqGlqSWHCJBIBYeKfy1TBt8Sz8ldrNVPQqy6-TRCTKsosXV4hbONTEcAijgBSLblZ8lfjn7YfACpMLfS3PhZreGRUVGsEiXGIZT-eDlgKsDeRzW23AF_b5_Wu2pxS_n2XwEUKTfgYmUY_FLg"
    private val disconnectUserTestJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6ImRpc2Nvbm5lY3R0ZXN0dXNlcm5hbWUiLCJ1c2VybmFtZSI6InVzZXJOYW1lIiwiaXNzIjoiVGVzdElzc3VlciIsInN1YiI6IjEyMzQ1Njc4OTAifQ.dHYSqA4GCAoUrnDriCkf_cchwZWuMOjBVBbZEgYqDpvoHGgB1ChtIWhasIISBbYBjYStsNLa7zeb00dSP6cRYQ82XE1OHWcmpnReHAha27ub9kaWKEYuAcdtL_e9FBbO3FT2rm3mIfh_3jQ538wKSnDfEJgl_lF4oo26Xlk5htBznM5O7byk9ne2rQKxI182szp6xtMAEmwx0hzeAQOOLyLyxHZVBSg2TgnDeJYOUEdtVGFTvI3TtZbA5_-7RVIHf_AftvkVJSY6wNVBPxwvzq4VAzRKmyH6oe1dMQANI1Jj7LiN-nk82FmUsx4El812CBLIkudytJBvX2GXTrEobg"

    private fun createTable() {
        val tables = localDynamoClient.listTables()
        if(!tables.tableNames().contains(ActiveUserTasks.dynamoTableName)){
            val tableRequest = CreateTableRequest.builder()
                    .tableName(ActiveUserTasks.dynamoTableName)
                    .keySchema(KeySchemaElement.builder().attributeName("userName").keyType("HASH").build())
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("userName").attributeType(ScalarAttributeType.S).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10).writeCapacityUnits(10).build())
                    .build()
            val table = localDynamoClient.createTable(tableRequest)
            println(table)
        } else println("Table already exists")
    }

    private fun addKmsKeys() {
        try {
            val sharedKmsKey1 = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/cg1-shared").targetKeyId(sharedKmsKey1.keyId()).build())
            val sharedKmsKey2 = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/cg2-shared").targetKeyId(sharedKmsKey2.keyId()).build())
            val userKmsKey = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/testusername123-home").targetKeyId(userKmsKey.keyId()).build())
            val existingUserKmsKey = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/existingtestusername123-home").targetKeyId(existingUserKmsKey.keyId()).build())
            val disconnectUserKmsKey = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/disconnecttestusername123-home").targetKeyId(disconnectUserKmsKey.keyId()).build())
        } catch (e: AlreadyExistsException){
            println("KMS keys already exist")
        }
    }

    private fun getDynamoEntry(userName: String): MutableMap<String, AttributeValue> {
        val keyToGet = HashMap<String, AttributeValue>()
        keyToGet["userName"] = AttributeValue.builder()
                .s(userName).build()
        val request = GetItemRequest.builder().key(keyToGet).tableName(ActiveUserTasks.dynamoTableName).build()
        return localDynamoClient.getItem(request).item();
    }

    private fun setupUser (username: String, jwtToken: String) {
        try {
            mvc.perform(MockMvcRequestBuilders.post("/connect")
                    .content("{\"emrClusterHostName\":\"\"}")
                    .header("content-type", "application/json")
                    .header("Authorisation", jwtToken)).andExpect(MockMvcResultMatchers.status().isOk)
            if (!awsCommunicator.getDynamoDeploymentEntry(username).hasItem()) throw Exception()
        } catch (e : Exception){
            println("Failed setting up existing $username")
            e.printStackTrace()
        }
    }

    @Before
    fun setup() {
        println("Inside setup")
        MockitoAnnotations.initMocks(this)
        whenever(awsClients.kmsClient).thenReturn(localKmsClient)
        doReturn(localDynamoClient).whenever(awsClients).dynamoDbClient
        doReturn(localIamClient).whenever(awsClients).iamClient
        doReturn(LoadBalancer.builder().loadBalancerArn("abc123").vpcId("12345").build())
                .whenever(awsCommunicator).getLoadBalancerByName(anyString())
        doReturn(Listener.builder().listenerArn("abc123").build())
                .whenever(awsCommunicator).getAlbListenerByPort(anyString(), any())
        doReturn(TargetGroup.builder().targetGroupArn("1234abcd").build())
                .whenever(awsCommunicator).createTargetGroup(anyString(), any(), any(),any(),any())
        doReturn(Rule.builder().build())
                .whenever(awsCommunicator).createAlbRoutingRule(any(), any(),any(),any())
        doReturn(TaskDefinition.builder().build())
                .whenever(awsCommunicator).registerTaskDefinition(any(), anyString(), anyString(), anyString(), any(), any(), any())
        doReturn(Service.builder().build())
                .whenever(awsCommunicator).createEcsService(anyString(),anyString(),anyString(), any(), any(), any(), any())
        createTable()
        addKmsKeys()
    }

    @Test
    fun `IAM Roles, policies and DynamoDB entry correctly created, when not present, on authenticated call to connect API`() {
        whenever(authenticationService.validate(newUserTestJwt)).thenReturn(JWTObject(JWT.decode(newUserTestJwt),
                "testusername123", listOf("cg1", "cg2")))
        assertDoesNotThrow{
            setupUser("testusername123", newUserTestJwt)

            val role = localIamClient.getRole(GetRoleRequest.builder().roleName("orchestration-service-user-testusername123-role").build()).role()
            assertThat(role).isNotNull

            val returnedItem = getDynamoEntry("testusername123")
            assertThat(returnedItem.contains("analytical-testusername123-iamPolicyTaskArn")
                    && returnedItem.contains("analytical-testusername123-iamPolicyUserArn")
                    && returnedItem.contains("orchestration-service-user-testusername123-role")
            )
        }
    }

    @Test
    fun `No duplicate DynamoDB entries are attempted when user exists` () {
        whenever(authenticationService.validate(existingUserTestJwt)).thenReturn(JWTObject(JWT.decode(existingUserTestJwt),
                "existingtestusername123", listOf("cg1", "cg2")))
        setupUser("existingtestusername123", existingUserTestJwt)
        assertThat(awsCommunicator.getDynamoDeploymentEntry("existingtestusername123").hasItem())
        doReturn(true).whenever(activeUserTasks).contains("existingtestusername123")
        setupUser("existingtestusername123", existingUserTestJwt)
        verify(taskDeploymentService, times(1)).runContainers("existingtestusername123", listOf("cg1", "cg2"), 256, 512, emptyList())
    }

    @Test
    fun `IAM Roles and DynamoDB entries deleted on disconnect` () {
        whenever(authenticationService.validate(disconnectUserTestJwt)).thenReturn(JWTObject(JWT.decode(existingUserTestJwt),
                "disconnecttestusername123", listOf("cg1", "cg2")))
        setupUser("disconnecttestusername123", disconnectUserTestJwt)
        fun getRole(): Role? {return  localIamClient.getRole(GetRoleRequest.builder().roleName("orchestration-service-user-disconnecttestusername123-role").build()).role()}
        fun getUserPolicy(): Policy? {return localIamClient.getPolicy(GetPolicyRequest.builder().policyArn("arn:aws:iam::000000000000:policy/analytical-disconnecttestusername123-iamPolicyUserArn").build()).policy()}
        fun getTaskPolicy(): Policy? {return localIamClient.getPolicy(GetPolicyRequest.builder().policyArn("arn:aws:iam::000000000000:policy/analytical-disconnecttestusername123-iamPolicyTaskArn").build()).policy()}
        assertThat(getRole()).isNotNull
        assertThat(getUserPolicy()).isNotNull
        assertThat(getTaskPolicy()).isNotNull
        assertThat(awsCommunicator.getDynamoDeploymentEntry("disconnecttestusername123").hasItem())
        mvc.perform(MockMvcRequestBuilders.post("/disconnect")
                .content("{\"emrClusterHostName\":\"\"}")
                .header("content-type", "application/json")
                .header("Authorisation", disconnectUserTestJwt)).andExpect(MockMvcResultMatchers.status().isOk)
        assertThat(!awsCommunicator.getDynamoDeploymentEntry("disconnecttestusername123").hasItem())
    }
}

class LocalStackClients {
    @Bean
    fun localDynamoDbClient(): DynamoDbClient {
        return DynamoDbClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Bean
    fun localKmsClient(): KmsClient {
        return KmsClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Bean
    fun localIamClient(): IamClient {
        return IamClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Test
    fun testConnectionDb() {
        val localDb = localDynamoDbClient()
        assertThat(localDb.listTables()).isNotNull
    }

    @Test
    fun testConnectionKms() {
        val localKmsClient = localKmsClient()
        assertThat(localKmsClient.listKeys()).isNotNull
    }

    @Test
    fun testConnectionIam() {
        val localIamClient = localIamClient()
        assertThat(localIamClient.listUsers()).isNotNull
    }
}
