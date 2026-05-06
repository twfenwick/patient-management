#!/bin/zsh

for SERVICE in auth-service billing-service analytics-service patient-service api-gateway; do
  aws ecs update-service \
    --cluster PatientManagementCluster \
    --service $SERVICE \
    --force-new-deployment
done
