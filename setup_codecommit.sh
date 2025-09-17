#!/bin/bash

# Upload SSH public key to IAM (you'll need to run this with your IAM user credentials)
echo "Uploading SSH public key to IAM..."
SSH_KEY_ID=$(aws iam upload-ssh-public-key --user-name alialem --ssh-public-key-body file://~/.ssh/codecommit_rsa.pub --query 'SSHPublicKey.SSHPublicKeyId' --output text)

if [ $? -eq 0 ]; then
    echo "SSH Key uploaded successfully. SSH Key ID: $SSH_KEY_ID"
    
    # Update SSH config with the actual SSH key ID
    sed -i.bak "s/APKAEIBAERJR2EXAMPLE/$SSH_KEY_ID/g" ~/.ssh/config
    
    echo "SSH config updated with SSH Key ID: $SSH_KEY_ID"
    echo "Testing connection..."
    ssh -T git-codecommit.us-west-2.amazonaws.com
else
    echo "Failed to upload SSH key. Make sure you have the correct IAM permissions."
    echo "You may need to run this with your IAM user credentials instead of assumed role."
fi
