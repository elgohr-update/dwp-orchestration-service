package uk.gov.dwp.dataworks.aws

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import software.amazon.awssdk.services.ecs.model.*
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupAttributesRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.PathPatternConditionConfig
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RuleCondition
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RuleNotFoundException
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.GetRoleRequest
import software.amazon.awssdk.services.iam.model.IamException
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesRequest
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse
import software.amazon.awssdk.services.iam.model.ListRolesRequest
import software.amazon.awssdk.services.iam.model.ListRolesResponse
import software.amazon.awssdk.services.iam.model.NoSuchEntityException
import software.amazon.awssdk.services.iam.model.Policy
import software.amazon.awssdk.services.iam.model.Role
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest
import software.amazon.awssdk.services.kms.model.NotFoundException
import software.amazon.awssdk.services.lambda.model.InvokeRequest
import software.amazon.awssdk.services.rdsdata.model.ExecuteStatementRequest
import software.amazon.awssdk.services.rdsdata.model.ExecuteStatementResponse
import software.amazon.awssdk.services.rdsdata.model.RdsDataException
import software.amazon.awssdk.services.rdsdata.model.SqlParameter
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.model.LambdaException
import uk.gov.dwp.dataworks.MultipleListenersMatchedException
import uk.gov.dwp.dataworks.MultipleLoadBalancersMatchedException
import uk.gov.dwp.dataworks.NetworkConfigurationMissingException
import uk.gov.dwp.dataworks.SystemArgumentException
import uk.gov.dwp.dataworks.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.services.ActiveUserTasks
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import software.amazon.awssdk.services.ecs.model.LoadBalancer as EcsLoadBalancer

/**
 * Component Class to encapsulate any and all communications with AWS via the SDK API. This class allows separation of
 * duties, where any AWS communications are stored and handled.
 *
 * Ideally this will leave the remaining code in the repo to be clean and concise, as much processing is outsorced to
 * this component.
 *
 * Unit testing also becomes easier, as this component can be mocked and all AWS calls can be synthetically manipulated.
 */
@Component
class AwsCommunicator {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AwsCommunicator::class.java))
    }

    @Autowired
    private lateinit var awsClients: AwsClients

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    /**
     * Retrieve a [LoadBalancer] from AWS given it's name. If multiple LoadBalancers are found with the same
     * name, an exception will be thrown.
     * @throws MultipleLoadBalancersMatchedException when more than one Load balancer is located with name [albName]
     */
    fun getLoadBalancerByName(albName: String): LoadBalancer {
        val albs = awsClients.albClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(albName).build())
                .loadBalancers()
        if (albs.size == 1) return albs[0]
        else throw MultipleLoadBalancersMatchedException("Expected to find 1 Load Balancer with name $albName, actually found ${albs.size}")
    }

    /**
     * Retrieves a [Listener] from a [LoadBalancer] based on the name of the ALB and the port of the listener.
     * Note that, for simplicity, this method expects only a single binding on the listener and will always
     * return the first one found.
     * @throws MultipleListenersMatchedException when more than one Listener is located matching port [listenerPort]
     */
    fun getAlbListenerByPort(loadBalancerArn: String, listenerPort: Int): Listener {
        val albListeners = awsClients.albClient.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build())
        val matchedListeners = albListeners.listeners().filter { it.port() == listenerPort }
        return matchedListeners
                .elementAtOrElse(0) { throw MultipleListenersMatchedException("Expected to find 1 Listener with port $listenerPort, actually found ${matchedListeners.size}") }
    }

    /**
     * Creates and returns a [TargetGroup] in the given VPC. This target group can later be assigned to
     * a [LoadBalancer] using it's ARN or [ElasticLoadBalancingV2Client.registerTargets].
     */
    fun createTargetGroup(correlationId: String, userName: String, vpcId: String, targetPort: Int, targetType: TargetTypeEnum): TargetGroup {
        val targetGroupName = "os-user-$userName-tg"
        // Create HTTPS target group in VPC to port containerPort
        val targetGroupResponse = awsClients.albClient.createTargetGroup(
                CreateTargetGroupRequest.builder()
                        .name(targetGroupName)
                        .protocol("HTTPS")
                        .vpcId(vpcId)
                        .port(targetPort)
                        .targetType(targetType)
                        .build())
        val targetGroup = targetGroupResponse.targetGroups().first { it.port() == targetPort }
        logger.info("Created target group",
                "correlation_id" to correlationId,
                "vpc_id" to vpcId,
                "target_group_arn" to targetGroup.targetGroupArn(),
                "protocol" to targetGroup.protocolAsString(),
                "load_balancer_arns" to targetGroup.loadBalancerArns().joinToString(),
                "target_group_name" to targetGroupName,
                "target_port" to targetPort.toString())
        updateDynamoDeploymentEntry(userName, "targetGroupArn" to targetGroup.targetGroupArn())
        return targetGroup
    }

    /**
     * Deletes the [TargetGroup] with the specified [targetGroupArn]. Before deleting, a
     * [DescribeTargetGroupAttributesRequest] is sent to establish whether the target group
     * exists. If it does not exist then the deletion is not attempted
     */
    fun deleteTargetGroup(correlationId: String, targetGroupArn: String) {
        try {
            awsClients.albClient.describeTargetGroupAttributes(
                    DescribeTargetGroupAttributesRequest.builder().targetGroupArn(targetGroupArn).build())
        } catch (e: TargetGroupNotFoundException) {
            logger.debug ("Not deleting target group as it does not exist",
                    "correlation_id" to correlationId,
                    "target_group_arn" to targetGroupArn)
            return
        }

        val deleteRequest = DeleteTargetGroupRequest.builder().targetGroupArn(targetGroupArn).build()
        awsClients.albClient.deleteTargetGroup(deleteRequest)
        logger.info("Deleted target group","correlation_id" to correlationId, "target_group_arn" to targetGroupArn)
    }

    /**
     * Creates a [LoadBalancer] routing rule for the [Listener] with given [listenerArn] and [TargetGroup]
     * of given [targetGroupArn].
     * The rule created will be a path-pattern forwarder for all traffic with path prefix /[userName]/.
     *
     * **See Also:** [AWS docs](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html)
     */
    fun createAlbRoutingRule(correlationId: String, userName: String, listenerArn: String, targetGroupArn: String): Rule {
        // Build path pattern condition
        val pathPattern = "/$userName/*"
        val albRuleCondition = RuleCondition.builder()
                .field("path-pattern")
                .pathPatternConfig(PathPatternConditionConfig.builder().values(pathPattern).build())
                .build()

        //Set up forwarding rule
        val albRuleAction = Action.builder().type("forward").targetGroupArn(targetGroupArn).build()

        //Get rules on listener & calculate vacant priority.
        val albRules = awsClients.albClient.describeRules(DescribeRulesRequest.builder().listenerArn(listenerArn).build())
        val rulePriority = calculateVacantPriorityValue(albRules.rules())

        //Create the complete rule
        val rule = awsClients.albClient.createRule(CreateRuleRequest.builder()
                .listenerArn(listenerArn)
                .priority(rulePriority)
                .conditions(albRuleCondition)
                .actions(albRuleAction)
                .build()).rules()[0]
        logger.info("Created alb routing rule",
                "correlation_id" to correlationId,
                "actions" to rule.actions().joinToString(),
                "priority" to rule.priority(),
                "rule_arn" to rule.ruleArn(),
                "conditions" to rule.conditions().joinToString())
        updateDynamoDeploymentEntry(userName, "albRoutingRuleArn" to rule.ruleArn())
        return rule
    }

    /**
     * Deleted the [Rule] with the specified [ruleArn]. If the rule does not exist, this function
     * will take no action other than logging this fact.
     */
    fun deleteAlbRoutingRule(correlationId: String, ruleArn: String) {
        try {
            awsClients.albClient.deleteRule(DeleteRuleRequest.builder().ruleArn(ruleArn).build())
            logger.info("Deleted alb routing rule", "correlation_id" to correlationId, "rule_arn" to ruleArn)
        } catch (e: RuleNotFoundException) {
            logger.info("Not deleting alb rule as it does not exist",
                    "correlation_id" to correlationId,
                    "rule_arn" to ruleArn)
        }
    }

    /**
     * Helper method to calculate the lowest non-used priority in a given set of [Rules][Rule]. This excludes
     * the `default` rule and assumes that all rules have come from the same listener.
     */
    fun calculateVacantPriorityValue(rules: Iterable<Rule>): Int {
        val rulePriorities = rules.map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        for (priority in 1..999) {
            if (!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException("The upper limit of 1000 rules has been reached on this listener.")
    }


    /**
     * Creates an ECS service with the name [clusterName], friendly service name of "[userName]-analytical-workspace" and sits
     * it behind the load balancer [loadBalancer].
     *
     * For ease of use, the task definition is retrieved from the [task definition env var][ConfigKey.USER_CONTAINER_TASK_DEFINITION]
     */
    fun createEcsService(correlationId: String, userName: String, clusterName: String, taskDefinition: TaskDefinition, loadBalancer: EcsLoadBalancer,
                         subnets: Collection<String>? = null, securityGroups: Collection<String>? = null): Service {

        val serviceName = "$userName-analytical-workspace"

        // Create ECS service request
        val serviceBuilder = CreateServiceRequest.builder()
                .cluster(clusterName)
                .loadBalancers(loadBalancer)
                .serviceName(serviceName)
                .taskDefinition(taskDefinition.taskDefinitionArn())
                .desiredCount(1)

        // Configure networking if AWSVPC networking mode is used
        if (taskDefinition.networkMode() == NetworkMode.AWSVPC) {
            if(subnets == null || securityGroups == null)
                throw NetworkConfigurationMissingException("Arguments subnets and securityGroups are required when using AWSVPC networking mode")

            val awsVpcConfiguration = AwsVpcConfiguration.builder()
                    .subnets(subnets)
                    .securityGroups(securityGroups)
                    .build()

            serviceBuilder.networkConfiguration(NetworkConfiguration.builder()
                    .awsvpcConfiguration(awsVpcConfiguration)
                    .build())
        }

        //Create the service
        val ecsService = awsClients.ecsClient.createService(serviceBuilder.build()).service()
        logger.info("Created ECS Service",
                "correlation_id" to correlationId,
                "cluster_name" to clusterName,
                "service_name" to serviceName,
                "cluster_arn" to ecsService.clusterArn(),
                "task_definition" to ecsService.taskDefinition())
        updateDynamoDeploymentEntry(userName, "ecsClusterName" to clusterName)
        updateDynamoDeploymentEntry(userName, "ecsServiceName" to serviceName)
        return ecsService
    }

    /**
     * Deletes the [Service] from the cluster [clusterName] with the name [serviceName]. If
     * the ECS service does not exist, this function will take no action other than logging this fact.
     *
     * Before deleting the service, we have to wait until all tasks are stopped within it. To
     * achieve this, we issue a scale to 0 request and poll until the service has 0 running tasks.
     */
    fun deleteEcsService(correlationId: String, clusterName: String, serviceName: String) {
        try {
            awsClients.ecsClient.updateService(UpdateServiceRequest.builder()
                    .cluster(clusterName)
                    .service(serviceName)
                    .desiredCount(0)
                    .build()).service()

            while (true) {
                Thread.sleep(5000)
                val service = awsClients.ecsClient.describeServices(DescribeServicesRequest.builder()
                        .cluster(clusterName)
                        .services(serviceName).build()).services()[0]
                if(service.runningCount() == 0) {
                    break
                }
            }

            awsClients.ecsClient.deleteService(
                    DeleteServiceRequest.builder()
                            .cluster(clusterName)
                            .service(serviceName).build())
            logger.info("Deleted ECS Service",
                    "correlation_id" to correlationId,
                    "cluster_name" to clusterName,
                    "service_name" to serviceName)
        } catch(e: ServiceNotFoundException) {
            logger.info("Not deleting service as it does not exist",
                    "correlation_id" to correlationId,
                    "clusterName" to clusterName,
                    "serviceName" to serviceName)
        } catch(e: ServiceNotActiveException) {
            logger.info("Not deleting service as it is not active",
                    "correlation_id" to correlationId,
                    "clusterName" to clusterName,
                    "serviceName" to serviceName)
        }
    }

    /**
     * Registers a new [TaskDefinition]
     */
    fun registerTaskDefinition(correlationId: String, taskDefinition: TaskDefinition, tags: Collection<Tag>): TaskDefinition {
        TaskDefinition.builder().build()
        val registerTaskDefinitionRequest = RegisterTaskDefinitionRequest.builder()
                .family(taskDefinition.family())
                .executionRoleArn(taskDefinition.executionRoleArn())
                .taskRoleArn(taskDefinition.taskRoleArn())
                .networkMode(taskDefinition.networkMode())
                .volumes(taskDefinition.volumes())
                .containerDefinitions(taskDefinition.containerDefinitions())
                .tags(tags)
                .build()

        logger.info("Registering task definition",
                "correlation_id" to correlationId,
                "family" to taskDefinition.family(),
                "execution_role_arn" to taskDefinition.executionRoleArn(),
                "task_role_arn" to taskDefinition.taskRoleArn(),
                "network_mode" to taskDefinition.networkModeAsString(),
                "tags" to tags.joinToString { tag -> "${tag.key()}:${tag.value()}" },
                "container_definitions" to taskDefinition.containerDefinitions().joinToString("; ", transform = { def ->
                    val env = def.environment().joinToString { "${it.name()}:${it.value()}" }
                    "${def.name()}, image=${def.image()}, cpu = ${def.cpu()}, memory= ${def.memory()} env = [${env}]"
                }))

        return awsClients.ecsClient.registerTaskDefinition(registerTaskDefinitionRequest).taskDefinition()
    }

    /**
     * Creates an IAM [Policy] from the name and document provided. [policyDocument] should be in JSON format
     * as per the AWS standards for documents.
     */
    fun createIamPolicy(correlationId: String, userName: String, policyDocument: String, dynamoAttribute: String): Policy {
        val policyName = "analytical-$userName-$dynamoAttribute"
        val policy = awsClients.iamClient.createPolicy(
                CreatePolicyRequest.builder()
                        .policyDocument(policyDocument)
                        .policyName(policyName).build())
                .policy()
        logger.info("Created iam policy",
                "correlation_id" to correlationId,
                "policy_arn" to policy.arn(),
                "policy_path" to policy.path(),
                "policy_name" to policy.policyName(),
                "policy_id" to policy.policyId(),
                "created_date" to policy.createDate().toString())
        updateDynamoDeploymentEntry(userName, dynamoAttribute to policy.arn())
        return policy
    }

    /**
     * Deletes the [Policy] with the specified [policyArn]. If the IAM Policy does not exist,
     * this function will take no action other than logging this fact.
     */
    fun deleteIamPolicy(correlationId: String, policyArn: String) {
        try {
            awsClients.iamClient.deletePolicy(DeletePolicyRequest.builder().policyArn(policyArn).build())
            logger.info("Deleted iam policy", "correlation_id" to correlationId, "policy_arn" to policyArn)
        } catch (e: NoSuchEntityException) {
            logger.info("Not deleting iam policy as it does not exist",
                    "correlation_id" to correlationId,
                    "role_arn" to policyArn)
        }
    }

    /**
     * Creates an IAM [Role] from the name and role assumption document provided. [assumeRolePolicy] should be
     * in JSON format as per the AWS standards for documents.
     */
    fun createIamRole(correlationId: String, userName: String, assumeRolePolicy: String): Role {
        val roleName = "orchestration-service-user-$userName-role"
        val role = awsClients.iamClient.createRole(
            CreateRoleRequest.builder()
                .assumeRolePolicyDocument(assumeRolePolicy)
                .roleName(roleName)
                .path("/app/os/").build()
        )
            .role()

        logger.info(
            "Created iam role",
            "correlation_id" to correlationId,
            "role_arn" to role.arn(),
            "role_path" to role.path(),
            "role_name" to role.roleName(),
            "role_id" to role.roleId(),
            "created_date" to role.createDate().toString()
        )
        updateDynamoDeploymentEntry(userName, "iamRoleName" to role.roleName())
        return role
    }

    /**
     * Deletes the [Role] with the specified [roleName]. If the IAM Role does not exist,
     * this function will take no action other than logging this fact.
     */
    fun deleteIamRole(correlationId: String, roleName: String) {
        try {
            awsClients.iamClient.deleteRole(DeleteRoleRequest.builder().roleName(roleName).build())
            logger.info("Deleted iam role", "correlation_id" to correlationId, "role_name" to roleName)
        } catch (e: NoSuchEntityException) {
            logger.info("Not deleting iam role as it does not exist",
            "correlation_id" to correlationId,
            "role_name" to roleName)
        }
    }

    /**
     * Attaches [policy] to [role] using the [Policy] ARN and [Role] name.
     */
    fun attachIamPolicyToRole(correlationId: String, policy: Policy, role: Role) {
        awsClients.iamClient.attachRolePolicy(AttachRolePolicyRequest.builder()
                .policyArn(policy.arn())
                .roleName(role.roleName()).build())
        logger.info("Attached policy to role", "correlation_id" to correlationId, "policy_arn" to policy.arn(), "role_name" to role.roleName())
    }

    /**
     * Detaches the Policy [policyArn] from the role [roleName]. If the either the role or policy
     * cannot be found, this method takes no action other than logging this fact.
     */
    fun detachIamPolicyFromRole(correlationId: String, roleName: String, policyArn: String) {
        try {
            awsClients.iamClient.detachRolePolicy(DetachRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyArn(policyArn).build())
            logger.info("Detached policy from role", "correlation_id" to correlationId, "role_name" to roleName, "policy_arn" to policyArn)
        } catch (e: NoSuchEntityException) {
            logger.info("Not detaching iam policy at least one entitity does not exist",
            "correlation_id" to correlationId,
            "role_name" to roleName,
            "policy_arn" to policyArn)
        }
    }

    /**
     * Retrieves role details for the user [userName]. If the role does not exist
     * this method returns null.
     */
    fun getIamRole(correlationId: String, userName: String): Role? {
        val roleName = "orchestration-service-user-$userName-role"

        return try {
            val role = awsClients.iamClient.getRole(GetRoleRequest.builder()
                .roleName(roleName)
                .build()).role()
            logger.info("Role already exists for $userName",
                "correlation_id" to correlationId,
                "role_arn" to role.arn(),
                "role_path" to role.path(),
                "role_name" to role.roleName(),
                "role_id" to role.roleId(),
                "created_date" to role.createDate().toString())
            updateDynamoDeploymentEntry(userName, "iamRoleName" to role.roleName())
            role
        } catch (e: IamException) {
            logger.info("Role does not exist for $userName",
                "correlation_id" to correlationId,
                        "user" to userName,
                        "exception" to e.localizedMessage)
            null
        }

    }

    fun listRolesWithPrefix(pathPrefix: String): ListRolesResponse{
        return awsClients.iamClient.listRoles(
            ListRolesRequest.builder()
                .pathPrefix(pathPrefix).build())
    }

    fun listRolePolicies(roleName: String): ListAttachedRolePoliciesResponse {
        return awsClients.iamClient.listAttachedRolePolicies(
            ListAttachedRolePoliciesRequest.builder().roleName(roleName).build()
        )
    }

    /**
     * Detaches policies from and destroys IAM Roles that have not
     * been used for longer than 1 month.
     */
    fun deleteUnusedIamRoles(correlationId: String) {
        val response = listRolesWithPrefix("/app/os/")

        if (response.hasRoles()) {
            logger.info(
                "Found orchestration-service user roles",
                "roles" to response.roles().joinToString(",") {
                    "<role:${it.roleName()},last_used(days ago):${
                        if (it.roleLastUsed() != null)
                            Duration.between(
                                Instant.now(),
                                it.roleLastUsed().lastUsedDate()
                            ).toDays()
                        else "never"
                    }>"
                })
        } else {
            logger.warn("No orchestration-service user roles found")
            return
        }

        response.roles().filter {
            it.roleLastUsed() == null || it.roleLastUsed().lastUsedDate().atZone(ZoneId.systemDefault())
                .toLocalDate() < LocalDate.now()
                .minusMonths(1)
        }.forEach { role ->

            val existingPolicies = listRolePolicies(role.roleName()).attachedPolicies()

            existingPolicies.forEach { policy ->
                detachIamPolicyFromRole(correlationId, role.roleName(), policy.policyArn())
            }

            logger.info("Deleting role ${role.roleName()}")
            deleteIamRole(correlationId, role.roleName())

        }

    }

    /**
     * Creates a DynamoDB table named [tableName] with the attributes in [attributes] and primary key
     * of [keyName]
     */
    fun createDynamoDbTable(tableName: String, attributes: List<AttributeDefinition>, keyName: String) {
        val tables = awsClients.dynamoDbClient.listTables(ListTablesRequest.builder().build())
        if(!tables.tableNames().contains(tableName)) {
            awsClients.dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName(keyName).attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName(keyName).keyType(KeyType.HASH).build())
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L).build())
                    .build())
            logger.info("Created dynamodb table", "table_name" to tableName)
        }
    }

    /**
     * Creates an entry in the DynamoDB table that holds the deployment information for each user. The entry
     * only contains [correlationId] and [userName] at this point and is updated as resources are deployed for
     * the user.
     */
    fun createDynamoDeploymentEntry(correlationId: String, userName: String) {
        val items = mapOf(
                ActiveUserTasks.dynamoPrimaryKey to AttributeValue.builder().s(userName).build(),
                "correlationId" to AttributeValue.builder().s(correlationId).build())
        awsClients.dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(ActiveUserTasks.dynamoTableName)
                .item(items).build())
        logger.info("Created dynamodb entry", "correlation_id" to correlationId, "items" to items.toString())
    }

    /**
     * Updates the DynamoDB deployment table's entry for [userName]. This should be called many times
     * in a deployment to allow for changes to be incrementally added to the table as resources are
     * created for the user.
     */
    fun updateDynamoDeploymentEntry(userName: String, attribute: Pair<String, String>) {
        val updateExpression = "SET ${attribute.first} = :value"
        awsClients.dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(ActiveUserTasks.dynamoTableName)
                .key(mapOf(ActiveUserTasks.dynamoPrimaryKey to AttributeValue.builder().s(userName).build()))
                .updateExpression(updateExpression)
                .expressionAttributeValues(mapOf(":value" to AttributeValue.builder().s(attribute.second).build()))
                .build())
        logger.debug("Updated dynamodb item", "primary_key" to userName,
                "attribute_name" to attribute.first,
                "attribute_value" to attribute.second)
    }

    /**
     * Retrieves an entry from the DynamoDB table that holds the deployment information for each user.
     */
    fun getDynamoDeploymentEntry(primaryKeyValue: String): GetItemResponse {
        val retrievalKey = mapOf(ActiveUserTasks.dynamoPrimaryKey to AttributeValue.builder().s(primaryKeyValue).build())
        return awsClients.dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(ActiveUserTasks.dynamoTableName)
                        .key(retrievalKey)
                        .build())
    }

    /**
     * Removes an entry from the deployment DynamoDB table. This should only be done after all resources
     * in the entry have been destroyed.
     */
    fun removeDynamoDeploymentEntry(correlationId: String, primaryKeyValue: String) {
        awsClients.dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(ActiveUserTasks.dynamoTableName)
                .key(mapOf(ActiveUserTasks.dynamoPrimaryKey to AttributeValue.builder().s(primaryKeyValue).build()))
                .build())
        logger.info("User tasks deregistered in dynamodb",
                "correlation_id" to correlationId,
                "user_name" to primaryKeyValue)
    }

    /**
     * Gets the ARN of the key aliased by [keyAlias]
     */
    fun getKmsKeyArn(keyAlias: String): String {
        return awsClients.kmsClient
                .describeKey(DescribeKeyRequest.builder().keyId(keyAlias).build())
                .keyMetadata().arn()
    }

    /**
     * Checks if the key aliased by [keyAlias] exists and is enabled - returns true or false
     */
    fun checkForExistingEnabledKey(keyAlias: String): Boolean {
        try {
            return awsClients.kmsClient
                    .describeKey(DescribeKeyRequest.builder().keyId("alias/${keyAlias}").build())
                    .keyMetadata().enabled()
        } catch (e: NotFoundException){
            logger.error("KMS key not found: ${keyAlias}")
            return false
        }
    }


    fun rdsExecuteStatement(sql: String, parameters: Collection<SqlParameter>? = null): ExecuteStatementResponse {
        try {
            return awsClients.rdsDataClient.executeStatement(
                ExecuteStatementRequest.builder()
                    .secretArn(configurationResolver.getStringConfig(ConfigKey.RDS_CREDENTIALS_SECRET_ARN))
                    .resourceArn(configurationResolver.getStringConfig(ConfigKey.RDS_CLUSTER_ARN))
                    .database(configurationResolver.getStringConfig(ConfigKey.RDS_DATABASE_NAME))
                    .parameters(parameters)
                    .sql(sql)
                    .build()
            )
        } catch (e: SystemArgumentException) {
            logger.error("RDS database not configured")
            throw RdsDataException.builder().message("Not configured").cause(e).build()
        }

    }

    /**
     * Invokes [functionName] AWS lambda synchronously with [jsonPayload]
     */
    fun invokeLambda(functionName : String, jsonPayload : String): String? {
        return try {
            val payload = SdkBytes.fromUtf8String(jsonPayload)
            val request = InvokeRequest.builder().functionName(functionName).payload(payload).build()
            awsClients.lambdaClient.invoke(request).payload().asUtf8String()
        } catch (e: LambdaException) {
            logger.error("Lambda invocation failed: ${e.message}")
            null
        }
    }
}
