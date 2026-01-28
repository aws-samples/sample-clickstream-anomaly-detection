# Flink on EKS Auto Mode - Deployment Summary

This document summarizes all the work done to get the Flink job running on EKS Auto Mode with NodePools.

## 1. NodePool Setup

Created three dedicated NodePools for Flink workloads:

### flink-jobmanager NodePool
- Instance types: t3.medium, t3.large
- Resources: 1 CPU, 2GB RAM
- Taint: `workload=flink-jobmanager:NoSchedule`
- Label: `workload: flink-jobmanager`

### flink-taskmanager NodePool
- Instance types: m5.xlarge, m5.2xlarge
- Resources: 2 CPU, 4GB RAM per TaskManager
- Taint: `workload=flink-taskmanager:NoSchedule`
- Label: `workload: flink-taskmanager`
- 100GB ephemeral storage for RocksDB state backend

### flink-operator NodePool
- Instance types: t3.small, t3.medium
- No taint (allows operator and system components to schedule)
- Label: `workload: flink-operator`

### Key Fixes
- Fixed `role` field in NodeClass to use role name instead of full ARN (64-byte limit)
- Added `template.metadata.labels` to NodePools so pods can match via nodeSelector
- Used proper IAM role name: `eksctl-streaming-bench-cluster-clu-AutoModeNodeRole-yWAQRZOis0Dp`

## 2. Flink Kubernetes Operator

### Installation
- Installed via Helm without cert-manager dependency
- Disabled webhook to avoid cert-manager requirement
- Added tolerations and nodeSelector to operator deployment

### Configuration
```yaml
tolerations:
  - key: workload
    value: flink-operator
    effect: NoSchedule
nodeSelector:
  workload: flink-operator
webhook:
  create: false
```

## 3. Flink Application Code Fixes

### Flink 2.x Configuration Updates
Updated deprecated config keys for Flink 2.x compatibility:
- `state.backend: rocksdb` → `state.backend.type: rocksdb`
- `state.backend.incremental` → `state.backend.rocksdb.incremental`
- `restart-strategy: fixed-delay` → `restart-strategy.type: fixed-delay`

### Java Model Classes
Added default constructors to all model classes to fix POJO serialization:
- `ConversionMetrics.java`
- `HealthMetrics.java`
- `ProductMetrics.java`
- `ClickstreamAnomaly.java`

Added annotations:
```java
@NoArgsConstructor
@AllArgsConstructor
```

### JsonSerializationSchema Fix
Fixed type erasure issue by:
1. Storing the `recordClazz` field
2. Implementing `ResultTypeQueryable<T>` interface
3. Adding `getProducedType()` method:
```java
public TypeInformation<T> getProducedType() {
    return TypeInformation.of(recordClazz);
}
```

### Job Arguments
Added job arguments to pass config file path:
```yaml
args:
  - "--config-file"
  - "/opt/flink/conf/flink-config/application.properties"
```

### Volume Mount Fix
Changed from file mount to directory mount:
```yaml
volumeMounts:
  - name: flink-config
    mountPath: /opt/flink/conf/flink-config
```

## 4. Docker Image

### S3 Plugin Installation
Added S3 filesystem plugin to Dockerfile:
```dockerfile
RUN mkdir -p /opt/flink/plugins/s3-fs-hadoop && \
    cd /opt/flink/plugins/s3-fs-hadoop && \
    wget https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/2.2.0/flink-s3-fs-hadoop-2.2.0.jar
```

### Platform
Built for ARM64 architecture to match EKS node architecture:
```bash
docker buildx build --platform linux/arm64 -t IMAGE:latest --push .
```

## 5. AWS IAM and IRSA Setup

### OIDC Provider
- Cluster OIDC ID: `69475B417B1455CF613474BFA815BB1E`
- Created IAM OIDC provider for the cluster

### IAM Role: FlinkPodRole
Created with trust policy for IRSA:
```json
{
  "Principal": {
    "Federated": "arn:aws:iam::668876353122:oidc-provider/oidc.eks.us-east-1.amazonaws.com/id/69475B417B1455CF613474BFA815BB1E"
  },
  "Condition": {
    "StringEquals": {
      "oidc.eks.us-east-1.amazonaws.com/id/69475B417B1455CF613474BFA815BB1E:sub": "system:serviceaccount:flink:flink"
    }
  }
}
```

### Permissions Attached
1. **S3 Access**: `AmazonS3FullAccess` policy
2. **MSK Access**: Inline policy with:
   - `kafka-cluster:Connect`
   - `kafka-cluster:*Topic*`
   - `kafka-cluster:WriteData`
   - `kafka-cluster:ReadData`
   - `kafka-cluster:AlterGroup`
   - `kafka-cluster:DescribeGroup`

### Service Account Annotation
```bash
kubectl annotate serviceaccount flink -n flink \
  eks.amazonaws.com/role-arn=arn:aws:iam::668876353122:role/FlinkPodRole
```

### Flink S3 Configuration
Added to FlinkDeployment:
```yaml
flinkConfiguration:
  s3.access.key: ""
  s3.secret.key: ""
  fs.s3a.aws.credentials.provider: com.amazonaws.auth.WebIdentityTokenCredentialsProvider
```

## 6. Final Architecture

### Pod Placement
- **Flink Operator**: Runs on `flink-operator` NodePool
- **JobManager**: Runs on `flink-jobmanager` NodePool (t3.medium/large)
- **TaskManagers**: Run on `flink-taskmanager` NodePool (m5.xlarge/2xlarge)

### Resource Flow
1. Pods request specific node labels via `nodeSelector`
2. Karpenter provisions nodes from matching NodePool
3. Pods tolerate NodePool taints to schedule
4. IRSA injects AWS credentials via service account annotation
5. Flink uses WebIdentityToken for S3 and MSK access

## Key Files Modified

- `eks-deployment/k8s/flink-nodepools.yaml` - NodePool definitions
- `eks-deployment/k8s/flink-deployment.yaml` - FlinkDeployment with node selectors and S3 config
- `eks-deployment/k8s/flink-operator-values.yaml` - Operator Helm values
- `flink-app/anomaly-detection/Dockerfile` - S3 plugin installation
- `flink-app/anomaly-detection/src/main/java/com/amazonaws/proserve/workshop/serde/JsonSerializationSchema.java` - Type information fix
- All model classes in `src/main/java/com/amazonaws/proserve/workshop/process/model/` - Added constructors

## Verification Commands

```bash
# Check NodePools
kubectl get nodepools

# Check Flink deployment
kubectl get flinkdeployment -n flink

# Check pods
kubectl get pods -n flink

# Check pod placement
kubectl get pods -n flink -o wide

# Check service account annotation
kubectl get sa flink -n flink -o yaml

# Check pod AWS credentials
kubectl exec -n flink <jobmanager-pod> -- env | grep AWS

# View logs
kubectl logs -n flink -l component=jobmanager -f
kubectl logs -n flink -l component=taskmanager -f
```

## Troubleshooting Tips

1. **Pods stuck in Pending**: Check node selectors match NodePool labels
2. **S3 access denied**: Verify IAM role has S3 permissions and service account annotation
3. **MSK connection issues**: Ensure IAM role has MSK permissions and security groups allow traffic
4. **Type erasure errors**: Ensure all model classes have default constructors
5. **S3 plugin missing**: Verify jar exists in `/opt/flink/plugins/s3-fs-hadoop/` in container
