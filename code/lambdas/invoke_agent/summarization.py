import boto3
import os
import base64
import json
import time
from datetime import datetime
from connections import tracer, logger, metrics
from aws_lambda_powertools.metrics import MetricUnit
from botocore.exceptions import ClientError

AGENT_ID = os.environ["AGENT_ID"]
AGENT_ALIAS_ID = os.environ["AGENT_ALIAS_ID"]
REGION_NAME = os.environ["REGION_NAME"]

bedrock_agent_runtime = boto3.client("bedrock-agent-runtime", region_name=REGION_NAME)

def format_clickstream_anomaly(event_data):
    """Format clickstream anomaly event data for agent analysis"""
    user_id = event_data.get('user_id', 'Unknown')
    severity = event_data.get('severity', 'Unknown')
    description = event_data.get('description', 'No description')
    anomaly_type = event_data.get('anomaly_type', 'Unknown')
    detection_timestamp = event_data.get('detection_timestamp', 'Unknown')
    
    # Format affected events
    affected_events = event_data.get('affected_events', [])
    events_summary = ""
    for event in affected_events:
        events_summary += f"\n  - Seq {event.get('globalseq', 'N/A')}: {event.get('event_type', 'unknown')} at {event.get('timestamp', 'N/A')}"
    
    return f"""Clickstream Anomaly Detection Alert:
- User ID: {user_id}
- Severity: {severity}
- Anomaly Type: {anomaly_type}
- Detection Time: {detection_timestamp}
- Description: {description}
- Affected Events:{events_summary}

This anomaly requires immediate analysis to determine if it indicates a technical issue, security concern, or user experience problem that needs resolution."""

@logger.inject_lambda_context(log_event=True, clear_state=True)
@tracer.capture_lambda_handler
@metrics.log_metrics(capture_cold_start_metric=True)
def lambda_handler(event, context):
    
    metrics.add_metric(name="TotalInvocations", unit=MetricUnit.Count, value=1)
    
    records = event.get("records")
    responses = []
    
    for topic_key in records.keys():
        messages = records.get(topic_key)
        
        for msg in messages:
            jsg_msg = json.loads(
                base64.b64decode(msg["value"]).decode("utf-8"), strict=False
            )
            
            timestamp = jsg_msg.get('timestamp', 'Unknown')
            logger.info(f"Processing clickstream anomaly for user: {jsg_msg.get('user_id', 'Unknown')} at {timestamp}")
            event_data = format_clickstream_anomaly(jsg_msg)
            logger.info(f"Event data: {event_data}")
            
            # Retry logic for throttling
            max_retries = 3
            for attempt in range(max_retries):
                try:
                    logger.info(f"Calling agent {AGENT_ID}/{AGENT_ALIAS_ID} - Attempt {attempt + 1}")
                    response = bedrock_agent_runtime.invoke_agent(
                        agentId=AGENT_ID,
                        agentAliasId=AGENT_ALIAS_ID,
                        sessionId=f'clickstream-{int(time.time())}-{attempt}', 
                        inputText=f"Analyze this clickstream anomaly and recommend appropriate actions: {event_data}"
                    )
                    
                    # Process streaming response
                    agent_response = ""
                    chunk_count = 0
                    try:
                        for event_chunk in response['completion']:
                            chunk_count += 1
                            if 'chunk' in event_chunk:
                                chunk = event_chunk['chunk']
                                if 'bytes' in chunk:
                                    agent_response += chunk['bytes'].decode('utf-8')
                    except Exception as stream_error:
                        logger.error(f"Stream failed after {chunk_count} chunks: {str(stream_error)}")
                        pass
                    
                    logger.info(f"SUCCESS: Agent completed, {chunk_count} chunks")
                    responses.append({"anomaly_event": jsg_msg, "agent_response": agent_response})
                    break  # Success, exit retry loop
                    
                except ClientError as e:
                    if e.response['Error']['Code'] == 'throttlingException':
                        if attempt < max_retries - 1:
                            wait_time = (2 ** attempt) + 1  # Exponential backoff
                            logger.warning(f"Throttled, retrying in {wait_time}s (attempt {attempt + 1})")
                            time.sleep(wait_time)
                        else:
                            logger.error(f"Max retries exceeded for throttling")
                            metrics.add_metric(name="ThrottlingErrors", unit=MetricUnit.Count, value=1)
                            responses.append({"anomaly_event": jsg_msg, "error": "throttled"})
                    else:
                        raise e
                        
                except Exception as agent_error:
                    logger.error(f"Agent error (attempt {attempt + 1}): {str(agent_error)}")
                    if attempt < max_retries - 1:
                        time.sleep(2 ** attempt)
                    else:
                        responses.append({"anomaly_event": jsg_msg, "error": str(agent_error)})
                        break
    
    return responses
