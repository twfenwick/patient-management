#!/bin/zsh

docker buildx build --platform linux/amd64 -t analytics-service:latest . --load
