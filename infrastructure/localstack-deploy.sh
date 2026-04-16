#!/bin/zsh

set -e  # Stops the script if any command fails

# ###############################################################
# localstack requires to delete the stack before deploying a new one -- real AWS deployments don't require that, but
# localstack recommends this
#
# If that doesn't work (sometimes the stack gets stuck in a "DELETE_IN_PROGRESS" state), you can also try
# deleting the stack from the localstack web UI. If that doesn't work, you can delete the container from the
# localstack desktop UI. You'll have to recreate the localstack container after that, including api-key, etc.
aws --endpoint-url=http://localhost:4566 cloudformation delete-stack \
    --stack-name patient-management
#
# ###############################################################

# Without specifying the url, it will try to connect to AWS and fail.
aws --endpoint-url=http://localhost:4566 cloudformation deploy \
    --stack-name patient-management \
    --template-file ./cdk.out/localstack.template.json


# Not required, but nice to print out load balancer address
# that we use to access the api gateway instead of having to run
# manually every time:
#
# Will likely change every time we deploy a fresh stack:
aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
    --query "LoadBalancers[0].DNSName" --output text>lb_url.txt

# Visit codejackal.com to join discord






