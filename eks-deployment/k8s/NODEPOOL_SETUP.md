# Flink NodePool Configuration for EKS Auto Mode

## What are NodePools?

In EKS Auto Mode, **NodePools** replace traditional node groups. They work with Karpenter to automatically provision the right EC2 instances for your workloads based on pod requirements.

## How it Works

1. **NodePool**: Defines what types of nodes can be created (instance types, capacity type, limits)
2. **NodeClass**: Defines AWS-specific configuration (IAM role, subnets, security groups, storage)
3. **Taints & Tolerations**: Ensure Flink pods only run on appropriate nodes
4. **Node Selectors**: Direct pods to specific NodePools

## Your Flink Setup

### Three NodePools Created:

1. **flink-jobmanager**: For Flink JobManager pods (1 CPU, 2GB RAM)
   - Instance types: t3.medium, t3.large
   - 50GB ephemeral storage

2. **flink-taskmanager**: For Flink TaskManager pods (2 CPU, 4GB RAM each)
   - Instance types: m5.xlarge, m5.2xlarge
   - 100GB ephemeral storage for RocksDB state backend

3. **flink-operator**: For Flink Kubernetes Operator
   - Instance types: t3.small, t3.medium
   - 30GB ephemeral storage

## Setup Steps

### 1. Get Your Auto Mode Node Role ARN

```bash
# Find your EKS cluster's Auto Mode node role
aws eks describe-cluster --name YOUR_CLUSTER_NAME --query 'cluster.autoMode.nodeRole' --output text
```

### 2. Update NodePool Configuration

Edit `flink-nodepools.yaml` and replace `YOUR_AUTO_MODE_NODE_ROLE_ARN` with the actual role ARN from step 1.

Also verify the subnet and security group tags match your cluster:
- Subnet tags: `Name: "*Private*"` and `kubernetes.io/role/internal-elb: "1"`
- Security group tags: `Name: "*cluster-sg*"`

### 3. Apply NodePools

```bash
kubectl apply -f flink-nodepools.yaml
```

### 4. Verify NodePools

```bash
kubectl get nodepools
kubectl get nodeclasses
```

### 5. Deploy Flink

```bash
kubectl apply -f configmap.yaml
kubectl apply -f flink-deployment.yaml
```

## How Pods Get Scheduled

When you deploy Flink:

1. **JobManager pod** requests 1 CPU + 2GB RAM with:
   - `nodeSelector: workload=flink-jobmanager`
   - `toleration: workload=flink-jobmanager`

2. Karpenter sees no suitable nodes exist

3. Karpenter provisions a node from the **flink-jobmanager** NodePool (e.g., t3.medium)

4. Pod gets scheduled on the new node

5. Same process happens for **TaskManager pods** using the flink-taskmanager NodePool

## Key Concepts Explained

### Taints & Tolerations
- **Taint** on node: "Don't schedule pods here unless they tolerate this taint"
- **Toleration** on pod: "I can run on nodes with this taint"
- This prevents random pods from landing on your Flink nodes

### Node Selectors
- Directs pods to nodes with specific labels
- Works with taints to ensure proper placement

### Limits
- Prevents runaway scaling
- `cpu: "32"` means max 32 CPUs across all nodes in this NodePool

### Disruption Policy
- `WhenEmpty`: Only remove nodes when they have no pods
- `consolidateAfter: 30s`: Wait 30 seconds before removing empty nodes

## Troubleshooting

### Pods stuck in Pending state?

```bash
kubectl describe pod <pod-name> -n flink
```

Look for:
- "0/X nodes available: X node(s) didn't match Pod's node affinity/selector"
  → Check nodeSelector labels match NodePool labels
- "0/X nodes available: X node(s) had untolerated taint"
  → Check tolerations match NodePool taints

### Check Karpenter logs:

```bash
kubectl logs -n kube-system -l app.kubernetes.io/name=karpenter
```

### Verify NodePool status:

```bash
kubectl describe nodepool flink-jobmanager
kubectl describe nodepool flink-taskmanager
```

## Cost Optimization Tips

1. Use Spot instances for TaskManagers (change `capacity-type` to `["spot", "on-demand"]`)
2. Adjust instance types based on actual resource usage
3. Increase `consolidateAfter` duration if pods restart frequently
4. Monitor with: `kubectl top nodes`
