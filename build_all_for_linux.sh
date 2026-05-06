# local Docker images were built for arm64 (Apple Silicon Mac), but ECS Fargate runs on linux/amd64.
# need to rebuild the images for the correct platform.

for SERVICE in auth-service billing-service analytics-service patient-service api-gateway; do
  docker buildx build --platform linux/amd64 -t ${SERVICE}:latest ./${SERVICE} --load
done
