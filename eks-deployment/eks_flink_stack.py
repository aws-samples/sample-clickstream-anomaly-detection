from aws_cdk import (
    Stack,
    aws_ecr as ecr,
    aws_s3 as s3,
    CfnOutput,
    RemovalPolicy,
)
from constructs import Construct


class EksFlinkStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # ECR Repository for Flink application
        ecr_repo = ecr.Repository(
            self,
            "FlinkAnomalyDetectionRepo",
            repository_name="flink-anomaly-detection",
            removal_policy=RemovalPolicy.DESTROY,
            empty_on_delete=True,
        )

        # S3 bucket for Flink checkpoints and savepoints
        checkpoint_bucket = s3.Bucket(
            self,
            "FlinkCheckpointBucket",
            removal_policy=RemovalPolicy.DESTROY,
            auto_delete_objects=True,
        )

        # Outputs
        CfnOutput(self, "EcrRepositoryUri", value=ecr_repo.repository_uri)
        CfnOutput(self, "CheckpointBucketName", value=checkpoint_bucket.bucket_name)
        
        CfnOutput(
            self,
            "NextSteps",
            value="1) Run ./scripts/build-and-push.sh  2) Update k8s/flink-deployment.yaml with outputs  3) kubectl apply -f k8s/",
            description="Manual deployment steps for existing EKS cluster"
        )
