#!/bin/bash
set -e

echo "=== Building and Pushing Flink Docker Image ==="

# Get ECR repository URI from CDK outputs
ECR_REPO_URI=$(aws cloudformation describe-stacks --stack-name EksFlinkStack --query "Stacks[0].Outputs[?OutputKey=='EcrRepositoryUri'].OutputValue" --output text)
AWS_REGION=${AWS_REGION:-us-east-1}
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
IMAGE_TAG=$(date +%Y%m%d-%H%M%S)

echo "ECR Repository: $ECR_REPO_URI"
echo "Image Tag: $IMAGE_TAG"

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build Flink JAR
echo "Building Flink application JAR..."
cd ../flink-app/anomaly-detection
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean package -DskipTests

# Build Docker image for x86_64 (linux/amd64) platform
# This ensures compatibility with EKS nodes even when building on Mac ARM
echo "Building Docker image for linux/amd64 platform..."
docker buildx build \
  --platform linux/amd64 \
  -f Dockerfile \
  -t $ECR_REPO_URI:latest \
  -t $ECR_REPO_URI:$IMAGE_TAG \
  --load \
  .

cd ../..

# Push to ECR
echo "Pushing to ECR..."
docker push $ECR_REPO_URI:latest
docker push $ECR_REPO_URI:$IMAGE_TAG

echo ""
echo "=== Build and Push Complete ==="
echo "Image: $ECR_REPO_URI:latest"
echo "Image: $ECR_REPO_URI:$IMAGE_TAG"


echo kubectl delete flinkdeployment anomaly-detection -n flink
echo kubectl apply -f k8s/flink-deployment.yaml
echo kubectl get pods -n flink
echo kubectl logs -n flink -f anomaly-detection-7c4d4d5d6-hbqcd
echo kubectl port-forward -n flink svc/anomaly-detection-rest 8081:8081
echo Look at http://localhost:8081 to see Flink UI

