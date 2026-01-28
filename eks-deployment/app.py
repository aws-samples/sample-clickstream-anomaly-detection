#!/usr/bin/env python3
import aws_cdk as cdk
from eks_flink_stack import EksFlinkStack

app = cdk.App()

EksFlinkStack(
    app,
    "EksFlinkStack",
    env=cdk.Environment(
        account=app.node.try_get_context("account") or None,
        region=app.node.try_get_context("region") or "us-east-1"
    ),
)

app.synth()
