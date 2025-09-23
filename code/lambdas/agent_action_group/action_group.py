import json
import boto3
import os
from prompt_templates import SUMMARIZATION_TEMPLATE_PARAGRAPH

TOPIC_ARN = os.environ.get("TOPIC_ARN", "")
REGION_NAME = os.environ.get("REGION_NAME", "us-west-2")

sns_client = boto3.client("sns", region_name=REGION_NAME)
bedrock_runtime = boto3.client("bedrock-runtime", region_name=REGION_NAME)

def lambda_handler(event, context):
    try:
        print(f"Received event: {json.dumps(event)}")
        
        agent = event.get('agent', 'unknown')
        actionGroup = event.get('actionGroup', 'unknown')
        function = event.get('function', 'unknown')
        parameters = event.get('parameters', [])
        
        params = {p['name']: p['value'] for p in parameters}
        print(f"Function: {function}, Params: {params}")
        
        if function == 'generateTemplate':
            print(f"Processing generateTemplate function")
            
            response_body = {
                'TEXT': {
                    'body': json.dumps({
                        'template': SUMMARIZATION_TEMPLATE_PARAGRAPH
                    })
                }
            }
            
        elif function == 'generateCodeExample':
            print(f"Processing generateCodeExample function")
            
            # Return the problematic frontend code example
            code_example = '''// Frontend code with race condition
async function handleProductClick(productId) {
    // Two async operations start simultaneously
    const [productDetails, cartResponse] = await Promise.all([
        fetchProductDetails(productId),  // Takes 200ms
        addToCart(productId)            // Takes 50ms - completes first!
    ]);
    
    // Events fire in completion order, not logical order
    trackEvent('product_view', productDetails);
    trackEvent('add_to_cart', cartResponse);
}'''
            
            response_body = {
                'TEXT': {
                    'body': json.dumps({
                        'code_example': code_example,
                        'issue_type': 'race_condition',
                        'description': 'Async operations completing out of order causing event sequence violations'
                    })
                }
            }
            
        elif function == 'sendNotification':
            severity = params.get('severity', '1')
            if severity == '2':
                if TOPIC_ARN:
                    message_body = params.get('message', 'Clickstream anomaly detected - User journey violation requires investigation')
                    
                    subject = params.get('subject', 'E-commerce Alert - Clickstream Anomaly Detected')
                    
                    sns_client.publish(
                        TopicArn=TOPIC_ARN,
                        Message=message_body,
                        Subject=subject
                    )
                status = 'notification sent'
            else:
                status = 'notification skipped - severity below threshold'


                
            response_body = {
                'TEXT': {
                    'body': json.dumps({
                        'status': status,
                        'severity': severity
                    })
                }
            }
        else:
            response_body = {
                'TEXT': {
                    'body': json.dumps({'error': f'Unknown function: {function}'})
                }
            }
        
        return {
            'messageVersion': '1.0',
            'response': {
                'actionGroup': actionGroup,
                'function': function,
                'functionResponse': {
                    'responseBody': response_body
                }
            }
        }
    
    except Exception as e:
        print(f"Error: {str(e)}")
        return {
            'messageVersion': '1.0',
            'response': {
                'actionGroup': event.get('actionGroup', 'unknown'),
                'function': event.get('function', 'unknown'),
                'functionResponse': {
                    'responseBody': {
                        'TEXT': {
                            'body': json.dumps({'error': str(e)})
                        }
                    }
                }
            }
        }
