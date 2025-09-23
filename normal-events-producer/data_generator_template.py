# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Import dependencies
import json
import random
import logging
import math
from typing import List, Dict

from datetime import datetime


# Constants
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TestDataGenerator:
    """
    AI PROMPT FOR PATIENT MONITORING DATA GENERATOR:
    
    Generate a Python class that creates realistic patient monitoring data for healthcare IoT systems.
    The class should generate synthetic data for:
    
    1. PATIENT PROFILES:
       - Patient demographics (age, medical conditions, risk factors)
       - Baseline health metrics (heart rate, blood pressure, activity levels)
       - Care settings (home, assisted living, hospital)
    
    2. WEARABLE DEVICE DATA:
       - Vital signs: heart rate, blood oxygen, temperature, respiratory rate
       - Activity metrics: steps, sleep patterns, movement, calories
       - Device status: battery, connectivity, wearing compliance
    
    3. LOCATION & SAFETY DATA:
       - GPS coordinates with geofencing (safe zones)
       - Indoor positioning (room-level tracking)
       - Emergency situations (falls, wandering, medical alerts)
    
    4. HEALTH RISK INDICATORS:
       - Fall risk assessment (gait stability, medication effects)
       - UTI risk factors (hydration, bathroom patterns)
       - Cognitive health (confusion levels, behavioral changes)
       - Nutrition indicators (meal completion, weight trends)
    
    5. ANOMALY GENERATION:
       - Normal vs abnormal data patterns
       - Configurable severity levels (low, medium, high risk)
       - Time-based variations (circadian rhythms, medication schedules)
    
    EXAMPLE USAGE:
    ```python
    generator = TestDataGenerator()
    
    # Generate normal patient data
    normal_data = generator.generate_patient_data(patient_id="P001", anomaly=False)
    
    # Generate anomalous data for testing
    anomaly_data = generator.generate_patient_data(patient_id="P002", anomaly=True)
    
    # Batch generate for multiple patients
    batch_data = generator.generate_batch_data(patient_count=10, anomaly_rate=0.1)
    ```
    
    REQUIREMENTS:
    - Use realistic medical ranges and patterns
    - Include timestamp and patient identification
    - Support both streaming and batch data generation
    - Provide configurable anomaly injection
    - Generate JSON-serializable output for IoT systems
    - Include data quality indicators and device metadata
    
    The generated data should be suitable for:
    - Real-time anomaly detection systems
    - Machine learning model training
    - Healthcare dashboard visualization
    - Emergency alert system testing
    - Clinical decision support validation
    """

"""
AI PROMPT FOR PATIENT MONITORING DATA PROCESSING MAIN FUNCTION:

Generate a main function that orchestrates patient monitoring data generation and processing.
The function should:

1. DATA GENERATION WORKFLOW:
   - Initialize TestDataGenerator instance
   - Configure message count and batch size
   - Generate patient data with anomaly control
   - Support both normal and anomalous data patterns

2. PATIENT SELECTION LOGIC:
   - Random patient selection from available profiles
   - Conditional anomaly injection based on patient ID
   - Support for targeted testing scenarios

3. BATCH PROCESSING:
   - Generate configurable number of messages
   - Collect messages in batches for efficient processing
   - Handle data serialization and formatting

4. ERROR HANDLING:
   - Robust exception handling for data generation
   - Logging for debugging and monitoring
   - Graceful degradation on failures

5. EVENT-DRIVEN ARCHITECTURE:
   - Accept event parameters for configuration
   - Support anomaly flags and processing modes
   - Return appropriate status codes

EXAMPLE IMPLEMENTATION:
```python
def main(event):
    generator = TestDataGenerator()
    msg_count = event.get('message_count', 20)
    anomaly_enabled = event.get('ANOMALY', False)
    
    messages = []
    for i in range(msg_count):
        patient_id = select_patient(generator.patients)
        anomaly = should_generate_anomaly(patient_id, anomaly_enabled)
        data = generator.generate_patient_data(patient_id, anomaly)
        messages.append(data)
    
    return process_messages(messages)
```

REQUIREMENTS:
- Support configurable message counts
- Handle both streaming and batch modes
- Provide anomaly injection control
- Include comprehensive error handling
- Return AWS Lambda compatible responses
- Support testing and production scenarios
"""

def main(event):
    """Generic main function interface for data generation"""
    # Implementation should:
    # 1. Initialize TestDataGenerator
    # 2. Use event.get('ANOMALY', False) for anomaly control
    # 3. Generate appropriate data based on use case
    # 4. Return {"statusCode": 200, "events": [...], "events_generated": count}
    
    return {"statusCode": 200, "events": [], "events_generated": 0}

if __name__ == "__main__":
    main({"ANOMALY": False})