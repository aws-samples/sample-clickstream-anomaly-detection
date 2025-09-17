# Agentic AI Enhanced Real-Time Anomaly Detection System

## Description

This repository contains a hackathon project (hack-180) that demonstrates an AI-powered real-time health anomaly detection system. The solution combines streaming data processing with intelligent AI agents to detect health events from wearable devices and automatically route appropriate responses through proactive wellness monitoring or reactive emergency alerts.

### Key Features

- **Real-time Health Monitoring**: Processes streaming health data from wearable IoT devices
- **AI-Powered Decision Making**: Uses Amazon Bedrock agents with Claude 3.5 Sonnet for intelligent event analysis
- **Dual Response Workflows**: 
  - **Proactive**: Wellness advisories and preventive care recommendations
  - **Reactive**: Emergency alerts for critical health events and safety concerns
- **Smart Routing**: Automatically determines response type based on event severity and description content
- **Multi-Channel Notifications**: Sends alerts to emergency services, caregivers, and healthcare providers via SNS
- **Scalable Architecture**: Built on AWS serverless technologies (Lambda, MSK, Bedrock)

## Prerequisites

### AWS Account Setup
1. **Bedrock Model Access**: Enable the following models in your AWS Bedrock console:
   - **Claude 3.5 Sonnet V2** (for agent decision making)
   - **Amazon Nova Micro** (for action group processing)
2. **IAM Permissions**: Ensure your AWS user/role has permissions for:
   - Bedrock (agent creation and model invocation)
   - Lambda, SNS, KMS, CloudFormation
   - MSK cluster creation and access

### Development Environment (for manual setup only)
- Python 3.11+
- AWS CDK v2
- Docker (for CDK lambda layer building)
- Java 11+ (for Flink application)

## Installation

### One-Click Deployment

This solution is designed for **one-click deployment** using AWS CloudFormation. The CloudFormation template automatically handles all infrastructure provisioning and CDK deployment.

1. **Deploy the CloudFormation template**:
   - Navigate to the AWS CloudFormation console in your AWS account
   - Create a new stack using the template: `cfn/anomaly_cfn.yaml`
   - The template will automatically:
     - Create MSK Serverless cluster with required networking (VPC, subnets, security groups)
     - Set up CodeCommit repository with project source code
     - Launch CodeBuild project that deploys the CDK application
     - Deploy all Lambda functions, Bedrock agents, and SNS topics
     - Configure Flink applications for real-time stream processing

2. **Monitor deployment progress**:
   - CloudFormation stack creation takes ~15-20 minutes
   - CodeBuild automatically runs CDK deployment as part of the stack creation
   - All resources are provisioned and configured automatically

### Manual Development Setup (Optional)

For development purposes only:

```bash
# Clone repository
git clone <repository-url>
cd hack-180-agentic-ai-enhanced-real-time-anomaly-detection-system

# Install dependencies
pip install -r requirements.txt

# Deploy with existing MSK cluster
cdk deploy --parameters mskClusterArn=<your-msk-cluster-arn>
```

## Usage

### Architecture Overview

1. **Data Ingestion**: Health data flows into MSK topic `data-ingest`
2. **Stream Processing**: Flink application processes data and outputs anomalies to `data-output` topic
3. **AI Analysis**: Lambda function invokes Bedrock agent to analyze health events
4. **Intelligent Routing**: Agent routes to appropriate action group based on event content:
   - Location-based events (safe zone violations) → Reactive workflow
   - High severity health events → Reactive workflow  
   - Low-medium severity trends → Proactive workflow
5. **Notifications**: SNS topics deliver alerts to relevant stakeholders

### Example Health Event Processing

**Input** (Flink anomaly detection):
```json
{
  "patient_id": "P12345",
  "severity": "low", 
  "description": "Patient detected outside designated safe zone",
  "last_known_location": {"lat": 34.042283, "lng": -118.250064}
}
```

**Output** (Reactive alert):
```
LOCATION ALERT: Patient Outside Safe Zone

Our monitoring system has detected that you've been outside your designated safe zone for an extended period. Your current location is in Los Angeles (34.042283, -118.250064).

This requires immediate caregiver notification for safety purposes...
```

## Project Structure

- **`app.py`**: CDK application entry point
- **`code/code_stack.py`**: Main CDK stack with all AWS resources
- **`code/lambdas/`**: Lambda function implementations
  - `invoke_agent/`: Processes MSK events and invokes Bedrock agent
  - `reactive_health_action_group/`: Emergency response workflows
  - `proactive_health_action_group/`: Wellness monitoring workflows
- **`flink-app/`**: Java Flink application for stream processing
- **`cfn/`**: CloudFormation templates for additional resources

## Technical Implementation Details

### 1. CDK Infrastructure (`app.py`)
The main CDK application deploys:
- **Bedrock Agent**: AI decision-making engine with dual action groups
- **Lambda Functions**: Event processing and notification handlers
- **SNS Topics**: Encrypted notification channels for different stakeholder groups
- **MSK Integration**: Event source mapping for real-time data processing
- **IAM Roles**: Least-privilege access for all components

### 2. Flink Stream Processing (`flink-app/`)
Java-based Flink application that:
- Consumes health data from MSK `data-ingest` topic
- Applies anomaly detection algorithms
- Outputs structured anomaly events to `data-output` topic
- Supports real-time processing with configurable windowing

### 3. AI Agent Workflows (`code/lambdas/`)
**Invoke Agent Lambda**: 
- Triggered by MSK events
- Formats health data for agent analysis
- Handles Bedrock agent invocation with retry logic

**Reactive Health Action Group**:
- Processes emergency and safety-critical events
- Generates urgent medical alerts
- Notifies emergency services, caregivers, and healthcare providers

**Proactive Health Action Group**:
- Handles wellness monitoring and trend analysis
- Creates preventive health advisories
- Sends supportive notifications to patients and care teams

### 4. Notification Architecture
Three encrypted SNS topics with KMS key rotation:
- **Emergency Topic**: Critical alerts requiring immediate response
- **Caregiver Topic**: Family/caregiver notifications
- **Primary Care Topic**: Healthcare provider updates

### 5. Agent Intelligence
The Bedrock agent uses sophisticated routing logic:
- Analyzes both severity level AND event description content
- Routes location-based events (safe zone violations) to reactive workflow regardless of severity
- Applies clinical decision-making for health event classification
- Maintains context-aware response generation

## Support

For questions or issues, please contact any of the repository owners.

## Contributing

This is a hackathon project. Contributions are welcome through pull requests.

## Project Status

Active development for hackathon submission. This is a proof-of-concept implementation demonstrating AI-enhanced health monitoring capabilities.