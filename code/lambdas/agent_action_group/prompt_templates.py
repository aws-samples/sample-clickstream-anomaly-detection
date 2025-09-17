SUMMARIZATION_TEMPLATE_PARAGRAPH = """
Race condition detected in clickstream data for User ID: <<userId>>

Issue: add_to_cart event (seq: <<seqNo>>) occurred before product_view event (seq: <<sqlNo>>) despite having a later timestamp.

Root Cause: The frontend code is using a Promise.all pattern that triggers tracking events based on completion order rather than logical flow order. The addToCart API call completes faster than the fetchProductDetails call, causing events to be tracked in the wrong sequence.

Code Area: Product detail page interaction handlers using Promise.all with asynchronous operations.

Recommended Fix:
1. Sequence the async operations to maintain proper event flow:
<<Current Code Block>>


2. Alternative if parallel execution is needed for performance:
<<Improved Code Block>>

Impact: This race condition affects data integrity for user journey analysis and may impact conversion funnel metrics and product performance measurement.

Please implement fix within next 24 hours and verify with A/B testing to ensure proper event sequencing.

"""
