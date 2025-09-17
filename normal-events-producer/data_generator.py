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
    def __init__(self):
        self.global_seq = 1000
        self.users = [12345, 67890, 11111, 22222, 33333]
        self.product_types = ['electronics', 'clothing', 'books', 'home', 'sports']
        self.start_time = datetime.now()
        
    def generate_normal_clickstream(self, user_id):
        """Generate normal clickstream events in logical order"""
        events = []
        base_timestamp = int(datetime.now().timestamp() * 1000)
        product_type = random.choice(self.product_types)
        
        # Product view
        events.append({
            'userid': user_id,
            'globalseq': self.global_seq,
            'event_type': 'product_view',
            'product_type': product_type,
            'eventtimestamp': base_timestamp,
            'prevglobalseq': self.global_seq - 1
        })
        self.global_seq += 1
        
        # Add to cart (70% chance)
        if random.random() > 0.3:
            events.append({
                'userid': user_id,
                'globalseq': self.global_seq,
                'event_type': 'add_to_cart',
                'product_type': product_type,
                'eventtimestamp': base_timestamp + random.randint(50, 200),
                'prevglobalseq': self.global_seq - 1
            })
            self.global_seq += 1
            
            # Checkout (50% chance)
            if random.random() > 0.5:
                events.append({
                    'userid': user_id,
                    'globalseq': self.global_seq,
                    'event_type': 'checkout',
                    'product_type': product_type,
                    'eventtimestamp': base_timestamp + random.randint(1000, 5000),
                    'prevglobalseq': self.global_seq - 1
                })
                self.global_seq += 1
        
        return events
    
    def generate_anomaly_clickstream(self, user_id):
        """Generate anomalous clickstream with race conditions"""
        events = []
        base_timestamp = int(datetime.now().timestamp() * 1000)
        product_type = random.choice(self.product_types)
        
        anomaly_type = random.choice(['out_of_order', 'missing_sequence', 'logical_violation'])
        
        if anomaly_type == 'out_of_order':
            # add_to_cart before product_view (race condition)
            events.append({
                'userid': user_id,
                'globalseq': self.global_seq,
                'event_type': 'add_to_cart',
                'product_type': product_type,
                'eventtimestamp': base_timestamp + 50,
                'prevglobalseq': self.global_seq - 1
            })
            self.global_seq += 1
            
            events.append({
                'userid': user_id,
                'globalseq': self.global_seq,
                'event_type': 'product_view',
                'product_type': product_type,
                'eventtimestamp': base_timestamp + 200,
                'prevglobalseq': self.global_seq - 1
            })
            self.global_seq += 1
            
        elif anomaly_type == 'missing_sequence':
            # Skip globalseq numbers
            events.append({
                'userid': user_id,
                'globalseq': self.global_seq,
                'event_type': 'product_view',
                'product_type': product_type,
                'eventtimestamp': base_timestamp,
                'prevglobalseq': self.global_seq - 1
            })
            self.global_seq += 3  # Skip 2 numbers
            
            events.append({
                'userid': user_id,
                'globalseq': self.global_seq,
                'event_type': 'checkout',
                'product_type': product_type,
                'eventtimestamp': base_timestamp + 100,
                'prevglobalseq': self.global_seq - 3
            })
            self.global_seq += 1
            
        else:  # logical_violation
            # Checkout without add_to_cart
            events.append({
                'userid': user_id,
                'globalseq': self.global_seq,
                'event_type': 'checkout',
                'product_type': product_type,
                'eventtimestamp': base_timestamp,
                'prevglobalseq': self.global_seq - 1
            })
            self.global_seq += 1
        
        return events

def main(event):
    """Generate clickstream data with periodic anomalies every 120 seconds"""
    generator = TestDataGenerator()
    
    # Use anomaly flag from event parameter
    should_generate_anomaly = event.get('ANOMALY', False)
    
    messages = []
    
    for _ in range(5):
        user_id = random.choice(generator.users)
        
        try:
            if should_generate_anomaly and random.random() < 0.3:
                events = generator.generate_anomaly_clickstream(user_id)
                logger.info(f"Generated anomaly for user {user_id}")
            else:
                events = generator.generate_normal_clickstream(user_id)
            
            messages.extend(events)
            
        except Exception as e:
            logger.error(f"Error generating events for user {user_id}: {e}")
    
    # for msg in messages:
        # print(json.dumps(msg))
    
    return {"statusCode": 200, "events_generated": len(messages), "events": messages}

if __name__ == "__main__":
    import time
    while True:
        main({})
        time.sleep(2)