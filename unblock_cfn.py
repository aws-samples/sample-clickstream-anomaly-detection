#!/usr/bin/env python3
import boto3
import json

def main():
    # Lambda function details
    function_name = "anomaly-cfn-CustomResourceStartCodeBuild-RkmlslalFGcE"
    region = "us-west-2"
    
    # Create Lambda client
    lambda_client = boto3.client('lambda', region_name=region)
    
    # Construct the DELETE event payload that CloudFormation would send
    event = {
        "RequestType": "Delete",
        "ResponseURL": "https://cloudformation-custom-resource-response-uswest2.s3-us-west-2.amazonaws.com/arn%3Aaws%3Acloudformation%3Aus-west-2%3A956759611117%3Astack/anomaly-cfn/4698d1a0-7920-11f0-9aa3-027b9ed30c9d%7CImageCodeBuild%7C2025/08/14/[$LATEST]eea69f323b0048c19bc63ab20fc6adf1",
        "StackId": "arn:aws:cloudformation:us-west-2:956759611117:stack/anomaly-cfn/4698d1a0-7920-11f0-9aa3-027b9ed30c9d",
        "RequestId": "2025/08/14/[$LATEST]eea69f323b0048c19bc63ab20fc6adf1",
        "LogicalResourceId": "ImageCodeBuild",
        "PhysicalResourceId": "2025/08/14/[$LATEST]eea69f323b0048c19bc63ab20fc6adf1",
        "ResourceType": "Custom::CustomResourceStartCodeBuild",
        "ResourceProperties": {
            "ServiceToken": f"arn:aws:lambda:{region}:956759611117:function:{function_name}",
            "ProjectName": "CodeBuildProject-eBXuRUzvhsmX",
            "Region": region
        }
    }
    
    print(f"Invoking Lambda function: {function_name}")
    print(f"Event payload: {json.dumps(event, indent=2)}")
    
    try:
        # Invoke the Lambda function
        response = lambda_client.invoke(
            FunctionName=function_name,
            InvocationType='RequestResponse',
            Payload=json.dumps(event)
        )
        
        # Parse the response
        payload = json.loads(response['Payload'].read())
        
        print(f"\nLambda invocation successful!")
        print(f"Status Code: {response['StatusCode']}")
        print(f"Response: {json.dumps(payload, indent=2)}")
        
        if response['StatusCode'] == 200:
            print("\n✅ Custom resource DELETE request completed successfully!")
            print("The CloudFormation stack should now be unblocked.")
        else:
            print(f"\n❌ Lambda function returned error: {payload}")
            
    except Exception as e:
        print(f"\n❌ Error invoking Lambda function: {str(e)}")

if __name__ == "__main__":
    main()
