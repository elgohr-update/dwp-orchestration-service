package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doCallRealMethod
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.iam.model.AttachedPolicy
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse
import software.amazon.awssdk.services.iam.model.ListRolesResponse
import software.amazon.awssdk.services.iam.model.Role
import software.amazon.awssdk.services.iam.model.RoleLastUsed
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import java.time.LocalDate
import java.time.ZoneId

@RunWith(SpringRunner::class)
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDestroyServiceTest {
    @Autowired
    private lateinit var taskDestroyService: TaskDestroyService
    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator
    @SpyBean
    private lateinit var activeUserTasks: ActiveUserTasks

    private val successfulFunction = {  }
    private val exceptionFunction = { throw Exception("fail") }

    @Before
    fun setup() {
        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doNothing().whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())
        doNothing().whenever(awsCommunicator).deleteEcsService(any(), any(), any())
        doNothing().whenever(awsCommunicator).detachIamPolicyFromRole(any(), any(), any())
        doNothing().whenever(awsCommunicator).deleteIamRole(any(), any())
        doNothing().whenever(awsCommunicator).deleteIamPolicy(any(), any())
    }

    @Test
    fun `Does not remove entry from DynamoDB when one deletion fails`() {
        val testUserTask = UserTask("correlation","username", "tgArn", "albArn", "ecsCN", "ecsSN", "iamRN", "iamPArn", "iamTArn")
        doReturn(testUserTask).whenever(activeUserTasks).get(any())
        doAnswer { throw Exception("fail") }.whenever(awsCommunicator).deleteTargetGroup(any(), any())

        taskDestroyService.destroyServices("username")
        verify(activeUserTasks, never()).remove("correlation", "username")

        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
    }

    @Test
    fun `Does not remove entry from DynamoDB when multiple deletions fail`() {
        val testUserTask = UserTask("correlation","username", "tgArn", null, "ecsCN", "ecsSN", "iamRN", "iamPArn", "iamTArn")
        doReturn(testUserTask).whenever(activeUserTasks).get(any())
        doAnswer { throw Exception("fail") }.whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doAnswer { throw Exception("fail") }.whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())

        taskDestroyService.destroyServices("username")
        verify(activeUserTasks, never()).remove("correlation", "username")

        doNothing().whenever(awsCommunicator).deleteTargetGroup(any(), any())
        doNothing().whenever(awsCommunicator).deleteAlbRoutingRule(any(), any())
    }

    @Test
    fun `Removes entry from DynamoDB when no deletions fail`() {
        val testUserTask = UserTask("correlation","username", "tgArn", "albArn", "ecsCN", "ecsSN", "iamRN", "iamPArn", "iamTArn")
        doReturn(testUserTask).whenever(activeUserTasks).get(any())

        taskDestroyService.destroyServices("username")
        verify(activeUserTasks).remove("correlation", "username")
    }

    @Test
    fun `Try delete returns true when one requisite is null`() {
        val actual = taskDestroyService.tryDeleteResource("resource", null, function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete returns true when one requisite is Blank`() {
        val actual = taskDestroyService.tryDeleteResource("resource", "", function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete returns true when multiple requisites are null`() {
        val actual = taskDestroyService.tryDeleteResource("resource", null, "abc", null, function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete continues when no requisites sent`() {
        val actual = taskDestroyService.tryDeleteResource("resource", function = exceptionFunction)
        assertThat(actual).isFalse()
    }

    @Test
    fun `Try delete returns true when function does not throw exception`() {
        val actual = taskDestroyService.tryDeleteResource("resource", "not-null", function = successfulFunction)
        assertThat(actual).isTrue()
    }

    @Test
    fun `Try delete returns false when function throws exception`() {
        val actual = taskDestroyService.tryDeleteResource("resource", "not-null", function = exceptionFunction)
        assertThat(actual).isFalse()
    }

    @Test
    fun `Deletes unused IAM roles with no policies`() {

        doReturn(
            ListRolesResponse.builder()
                .roles(
                    Role.builder()
                        .roleName("testRoleName")
                        .build()
                )
                .build()
        ).whenever(awsCommunicator).listRolesWithPrefix(any())
        doReturn(ListAttachedRolePoliciesResponse.builder().build()).whenever(awsCommunicator).listRolePolicies(any())
        doCallRealMethod().whenever(awsCommunicator).deleteUnusedIamRoles(any())

        taskDestroyService.cleanupUnusedIamRoles()
        verify(awsCommunicator).deleteUnusedIamRoles(any())
        verify(awsCommunicator).deleteIamRole(any(), eq("testRoleName"))
    }

    @Test
    fun `Detaches policies from and deletes unused IAM roles`() {

        doReturn(
            ListRolesResponse.builder()
                .roles(
                    Role.builder()
                        .roleName("testRoleName")
                        .build()
                )
                .build()
        ).whenever(awsCommunicator).listRolesWithPrefix(any())
        doReturn(
            ListAttachedRolePoliciesResponse.builder()
                .attachedPolicies(
                    listOf(
                        AttachedPolicy.builder().policyName("testPolicyName").policyArn("testPolicyArn").build()
                    )
                )
                .build()
        ).whenever(awsCommunicator).listRolePolicies(any())
        doCallRealMethod().whenever(awsCommunicator).deleteUnusedIamRoles(any())

        taskDestroyService.cleanupUnusedIamRoles()
        verify(awsCommunicator).deleteUnusedIamRoles(any())
        verify(awsCommunicator).detachIamPolicyFromRole(any(), eq("testRoleName"), eq("testPolicyArn"))
        verify(awsCommunicator).deleteIamRole(any(), eq("testRoleName"))
    }

    @Test
    fun `Deletes IAM roles unused for longer than 1 month`() {
        doReturn(
            ListRolesResponse.builder()
                .roles(
                    Role.builder()
                        .roleName("testRoleName")
                        .roleLastUsed(
                            RoleLastUsed.builder().lastUsedDate(
                                LocalDate.now().minusMonths(2).atStartOfDay(
                                    ZoneId.systemDefault()
                                ).toInstant()
                            ).build()
                        )
                        .build()
                )
                .build()
        ).whenever(awsCommunicator).listRolesWithPrefix(any())
        doReturn(ListAttachedRolePoliciesResponse.builder().build()).whenever(awsCommunicator).listRolePolicies(any())
        doCallRealMethod().whenever(awsCommunicator).deleteUnusedIamRoles(any())

        taskDestroyService.cleanupUnusedIamRoles()
        verify(awsCommunicator).deleteUnusedIamRoles(any())
        verify(awsCommunicator).deleteIamRole(any(), eq("testRoleName"))
    }

    @Test
    fun `Does not delete IAM roles unused for less than 1 month`() {
        doReturn(
            ListRolesResponse.builder()
                .roles(
                    Role.builder()
                        .roleName("testRoleName")
                        .roleLastUsed(
                            RoleLastUsed.builder().lastUsedDate(
                                LocalDate.now().minusDays(1).atStartOfDay(
                                    ZoneId.systemDefault()
                                ).toInstant()
                            ).build()
                        )
                        .build()
                )
                .build()
        ).whenever(awsCommunicator).listRolesWithPrefix(any())
        doReturn(ListAttachedRolePoliciesResponse.builder().build()).whenever(awsCommunicator).listRolePolicies(any())
        doCallRealMethod().whenever(awsCommunicator).deleteUnusedIamRoles(any())

        taskDestroyService.cleanupUnusedIamRoles()
        verify(awsCommunicator).deleteUnusedIamRoles(any())
        verify(awsCommunicator, never()).deleteIamRole(any(), eq("testRoleName"))
    }
}
