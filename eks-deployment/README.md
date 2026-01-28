# EKS Flink Deployment

This folder contains CDK infrastructure and Kubernetes manifests to deploy the Flink anomaly detection application to your existing Amazon EKS cluster.

## What This Deploys

1. **ECR Repository** - For storing the Flink application Docker image
2. **S3 Bucket** - For Flink checkpoints and savepoints

You'll manually deploy to your existing EKS cluster using kubectl.

## Prerequisites

- AWS CDK v2 installed
- Docker installed locally (with buildx support)
- kubectl configured for your EKS cluster
- helm installed
- Existing EKS cluster
- Existing MSK cluster

## Deployment Steps

### 1. Install Dependencies

```bash
cd eks-deployment
pip install -r requirements.txt
```

### 2. Deploy CDK Stack (ECR + S3 only)

```bash
# Bootstrap CDK (first time only)
cdk bootstrap

# Deploy ECR and S3 bucket
cdk deploy EksFlinkStack
```

### 3. Build and Push Docker Image

```bash
# Make scripts executable
chmod +x scripts/*.sh

# Build JAR and Docker image, then push to ECR
./scripts/build-and-push.sh
```

### 4. Install Flink Kubernetes Operator (if not already installed)

```bash
# Add Helm repo
helm repo add flink-operator https://downloads.apache.org/flink/flink-kubernetes-operator-1.13.0/
helm repo update

# Install operator
kubectl create namespace flink
helm install flink-kubernetes-operator flink-operator/flink-kubernetes-operator -n flink
```

### 5. Update Kubernetes Manifests

Edit `k8s/flink-deployment.yaml` and replace:
- `REPLACE_WITH_ECR_REPO_URI` with your ECR URI from CDK output
- `REPLACE_WITH_CHECKPOINT_BUCKET` with your S3 bucket from CDK output

Edit `k8s/configmap.yaml` and replace:
- `REPLACE_WITH_YOUR_MSK_BOOTSTRAP_SERVERS` with your MSK bootstrap servers

### 6. Deploy Flink Application

```bash
# Create namespace if needed
kubectl create namespace flink --dry-run=client -o yaml | kubectl apply -f -

# Apply manifests
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/flink-deployment.yaml
```

### 7. Monitor Application

```bash
# Check Flink deployment status
kubectl get flinkdeployment -n flink

# View logs
kubectl logs -n flink -l app=anomaly-detection -f

# Port-forward to Flink UI
kubectl port-forward -n flink svc/anomaly-detection-rest 8081:8081
# Access at http://localhost:8081
```

## Architecture

- **Local Build** - Maven builds the Flink JAR, Docker buildx creates x86_64 compatible image
- **ECR** - Stores the container image
- **EKS** - Runs the Flink cluster using the Kubernetes Operator
- **Flink Operator** - Manages JobManager and TaskManager pods
- **S3** - Stores checkpoints and savepoints
- Application connects to existing MSK cluster for data processing

## Notes

- Docker images are built for `linux/amd64` platform to ensure compatibility with EKS x86_64 nodes
- The build script uses `docker buildx` which works on both Intel and Apple Silicon Macs
- Make sure Docker Desktop has buildx enabled (it's enabled by default in recent versions)

## Quick Start

```bash
# 1. Deploy infrastructure
cd eks-deployment
pip install -r requirements.txt
cdk deploy

# 2. Build and push image
chmod +x scripts/*.sh
./scripts/build-and-push.sh

# 3. Deploy Flink app
./scripts/deploy.sh
```
