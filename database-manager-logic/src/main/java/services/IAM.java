package services;

import java.util.List;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.iam.waiters.IamWaiter;

/**
 * With AWS Identity and Access Management (IAM), you can specify who or what can access services and
 * resources in AWS, centrally manage fine-grained permissions, and analyze access to refine permissions across AWS.
 */
public class IAM {

    /**
     * Authenticate to the IAM client using the AWS user's credentials.
     * @param awsBasicCredentials The AWS Access Key ID and Secret Access Key are credentials that are used to securely sign requests to AWS services.
     * @param iamRegion The AWS Region where the service will be hosted.
     * @return Service client for accessing IAM.
     */
    public static IamClient authenticateIAM(AwsBasicCredentials awsBasicCredentials, Region iamRegion) {
        return IamClient
                .builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
                .region(iamRegion)
                .build();
    }

    /**
     * Creates an IAM role that will be used across the entire application in AWS.
     * @param iamClient Service client for accessing IAM.
     * @param roleName IAM role name.
     * @param roleDescription IAM role description.
     * @return Role ARN.
     */
    public static String createRole(IamClient iamClient, String roleName, String roleDescription) {
        try {
            IamWaiter iamWaiter = iamClient.waiter();
            JSONObject trustPolicy = (JSONObject) readJsonFile("policies", "trust-policy");
            CreateRoleRequest createRoleRequest = CreateRoleRequest
                    .builder()
                    .roleName(roleName)
                    .description(roleDescription)
                    .assumeRolePolicyDocument(trustPolicy.toJSONString())
                    .build();

            CreateRoleResponse createRoleResponse = iamClient.createRole(createRoleRequest);

            GetRoleRequest roleRequest = GetRoleRequest
                    .builder()
                    .roleName(createRoleResponse.role().roleName())
                    .build();

            // Wait until the role is created
            WaiterResponse<GetRoleResponse> waitUntilRoleExists = iamWaiter.waitUntilRoleExists(roleRequest);
            waitUntilRoleExists.matched().response().ifPresent(System.out::println);

            return createRoleResponse.role().arn();
        } catch (IamException | IOException | ParseException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
        return "";
    }

    /**
     * Creates a service-linked role for AWS Lex V2.
     * @param iamClient Service client for accessing IAM.
     * @param awsServiceName The service principal for the AWS service to which this role is attached.
     * @param customSuffix A string that you provide, which is combined with the service-provided prefix to form the complete role name.
     * @param description The description of the role.
     * @return Service-linked role ARN.
     */
    public static String createServiceLinkedRole(IamClient iamClient, String awsServiceName, String customSuffix, String description) {
        try {
            IamWaiter iamWaiter = iamClient.waiter();
            CreateServiceLinkedRoleRequest createServiceLinkedRoleRequest = CreateServiceLinkedRoleRequest
                    .builder()
                    .awsServiceName(awsServiceName)
                    .customSuffix(customSuffix)
                    .description(description)
                    .build();

            CreateServiceLinkedRoleResponse createServiceLinkedRoleResponse = iamClient.createServiceLinkedRole(createServiceLinkedRoleRequest);

            GetRoleRequest getRoleRequest = GetRoleRequest
                    .builder()
                    .roleName(createServiceLinkedRoleResponse.role().roleName())
                    .build();

            // Wait until the service-linked role is created
            WaiterResponse<GetRoleResponse> waitUntilRoleExists = iamWaiter.waitUntilRoleExists(getRoleRequest);
            waitUntilRoleExists.matched().response().ifPresent(System.out::println);

            return createServiceLinkedRoleResponse.role().arn();
        } catch (IamException error) {
                System.err.println(error.getMessage());
                System.exit(1);
        }
        return "";
    }

    /**
     * Creates permissions policy.
     * @param iamClient Service client for accessing IAM.
     * @param policyName Policy name.
     * @return Policy ARN.
     */
    public static String createPermissionsPolicy(IamClient iamClient, String policyName) {
        try {
            IamWaiter iamWaiter = iamClient.waiter();
            JSONObject permissionsPolicy = (JSONObject) readJsonFile("policies", "permissions-policy");
            CreatePolicyRequest request = CreatePolicyRequest
                    .builder()
                    .policyName(policyName)
                    .policyDocument(permissionsPolicy.toJSONString())
                    .build();

            CreatePolicyResponse createPolicyResponse = iamClient.createPolicy(request);

            GetPolicyRequest getPolicyRequest = GetPolicyRequest
                    .builder()
                    .policyArn(createPolicyResponse.policy().arn())
                    .build();

            // Wait until the permissions policy is created
            WaiterResponse<GetPolicyResponse> waitUntilPolicyExists = iamWaiter.waitUntilPolicyExists(getPolicyRequest);
            waitUntilPolicyExists.matched().response().ifPresent(System.out::println);

            return createPolicyResponse.policy().arn();
        } catch (IamException | IOException | ParseException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
        return "";
    }

    /**
     * Attaches the specified managed policy to the specified IAM role. When you attach a managed policy to a role,
     * the managed policy becomes part of the role's permission (access) policy.
     * @param iamClient Service client for accessing IAM.
     * @param roleName Name of the role to which permissions policy will be attached.
     * @param permissionsPolicyArn Permissions policy ARN.
     */
    public static void attachRolePermissionsPolicy(IamClient iamClient, String roleName, String permissionsPolicyArn) {
        try {
            ListAttachedRolePoliciesRequest request = ListAttachedRolePoliciesRequest
                    .builder()
                    .roleName(roleName)
                    .build();

            ListAttachedRolePoliciesResponse response = iamClient.listAttachedRolePolicies(request);
            List<AttachedPolicy> attachedPolicies = response.attachedPolicies();

            // Ensure that the policy is not attached to this role
            String policyArn;
            for (AttachedPolicy policy : attachedPolicies) {
                policyArn = policy.policyArn();
                if (policyArn.compareTo(permissionsPolicyArn) == 0) {
                    System.out.println(roleName + " policy is already attached to this role.");
                    return;
                }
            }

            AttachRolePolicyRequest attachPolicyRequest = AttachRolePolicyRequest
                    .builder()
                    .roleName(roleName)
                    .policyArn(permissionsPolicyArn)
                    .build();

            iamClient.attachRolePolicy(attachPolicyRequest);
        } catch (IamException error) {
            System.err.println(error.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    /**
     * Reads JSON file content then converts it to the InputStream and parses to JSONObject.
     * @param filename The name of the JSON file to read.
     * @return Returns the parsed contents of the JSON file as a JSONObject.
     * @throws IOException Signals that an I/O exception has occurred. This class is the general class of exceptions produced by failed or interrupted I/O operations.
     * @throws ParseException Signals that an error has been reached unexpectedly while parsing.
     */
    private static Object readJsonFile(String folder, String filename) throws IOException, ParseException {
        InputStream inputStream = IAM.class.getResourceAsStream("/" + folder + "/" + filename + ".json");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        JSONParser jsonParser = new JSONParser();
        return jsonParser.parse(bufferedReader);
    }
}