#!/bin/bash
set -e

echo "=== Building and Pushing Clickstream Producer Docker Image ==="

# Configuration
AWS_REGION=${AWS_REGION:-us-east-1}
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO_NAME="clickstream-producer"
ECR_REPO_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO_NAME"
IMAGE_TAG=$(date +%Y%m%d-%H%M%S)

echo "ECR Repository: $ECR_REPO_URI"
echo "Image Tag: $IMAGE_TAG"

# Create ECR repository if it doesn't exist
echo "Ensuring ECR repository exists..."
aws ecr describe-repositories --repository-names $ECR_REPO_NAME --region $AWS_REGION 2>/dev/null || \
  aws ecr create-repository --repository-name $ECR_REPO_NAME --region $AWS_REGION

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build Docker image for x86_64 (linux/amd64) platform
echo "Building Docker image for linux/amd64 platform..."
cd ../normal-events-producer
docker buildx build \
  --platform linux/amd64 \
  -f Dockerfile \
  -t $ECR_REPO_URI:latest \
  -t $ECR_REPO_URI:$IMAGE_TAG \
  --load \
  .

cd ../eks-deployment

# Push to ECR
echo "Pushing to ECR..."
docker push $ECR_REPO_URI:latest
docker push $ECR_REPO_URI:$IMAGE_TAG

echo ""
echo "=== Build and Push Complete ==="
echo "Image: $ECR_REPO_URI:latest"
echo "Image: $ECR_REPO_URI:$IMAGE_TAG"
echo ""
echo "To deploy the producer:"
echo "  kubectl apply -f k8s/producer-configmap.yaml"
echo "  kubectl apply -f k8s/producer-deployment.yaml"
echo ""
echo "To check status:"
echo "  kubectl get pods -n flink -l app=clickstream-producer"
echo "  kubectl logs -n flink -l app=clickstream-producer -f"
