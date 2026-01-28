#!/bin/bash
set -e

echo "=== Flink on EKS Deployment Script ==="

# Check required tools
command -v aws >/dev/null 2>&1 || { echo "AWS CLI is required but not installed. Aborting." >&2; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required but not installed. Aborting." >&2; exit 1; }

# Get stack outputs
echo "Fetching CDK stack outputs..."
ECR_REPO_URI=$(aws cloudformation describe-stacks --stack-name EksFlinkStack --query "Stacks[0].Outputs[?OutputKey=='EcrRepositoryUri'].OutputValue" --output text)
CHECKPOINT_BUCKET=$(aws cloudformation describe-stacks --stack-name EksFlinkStack --query "Stacks[0].Outputs[?OutputKey=='CheckpointBucketName'].OutputValue" --output text)
EKS_CLUSTER=$(aws cloudformation describe-stacks --stack-name EksFlinkStack --query "Stacks[0].Outputs[?OutputKey=='EksClusterName'].OutputValue" --output text)

echo "ECR Repository: $ECR_REPO_URI"
echo "Checkpoint Bucket: $CHECKPOINT_BUCKET"
echo "EKS Cluster: $EKS_CLUSTER"

# Update kubeconfig
echo "Updating kubeconfig..."
aws eks update-kubeconfig --name $EKS_CLUSTER --region ${AWS_REGION:-us-east-1}

# Prompt for MSK bootstrap servers
read -p "Enter MSK Bootstrap Servers: " MSK_BOOTSTRAP

# Update ConfigMap
echo "Creating ConfigMap..."
sed "s|REPLACE_WITH_YOUR_MSK_BOOTSTRAP_SERVERS|$MSK_BOOTSTRAP|g" k8s/configmap.yaml | kubectl apply -f -

# Update FlinkDeployment manifest
echo "Creating FlinkDeployment..."
sed -e "s|REPLACE_WITH_ECR_REPO_URI|$ECR_REPO_URI|g" \
    -e "s|REPLACE_WITH_CHECKPOINT_BUCKET|$CHECKPOINT_BUCKET|g" \
    k8s/flink-deployment.yaml | kubectl apply -f -

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "Check status with:"
echo "  kubectl get flinkdeployment -n flink"
echo ""
echo "View logs with:"
echo "  kubectl logs -n flink -l app=anomaly-detection -f"
echo ""
echo "Access Flink UI with:"
echo "  kubectl port-forward -n flink svc/anomaly-detection-rest 8081:8081"
echo "  Then open http://localhost:8081"
