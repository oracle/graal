FROM ubuntu:20.04

RUN apt-get update && apt-get install -y \
    curl \
    git \
    build-essential \
    python3.8 \
    zlib1g-dev

# Set the environment variable in the Dockerfile
ENV PATH=/mx:$PATH
