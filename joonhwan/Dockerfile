# Built for Vincent development
# From the project root: run docker build -t graal_joon  -f ./joonhwan/Dockerfile .   

FROM ubuntu:20.04

RUN apt-get update && apt-get install -y \
    curl \
    git \
    build-essential \
    python3.8 \
    zlib1g-dev
    
COPY . /workspace/graal_vincent

ENV MX_PATH=/workspace/mx
RUN git clone https://github.com/graalvm/mx.git ${MX_PATH} && chmod +x ${MX_PATH}/mx 

ENV PATH="/workspace/mx:$PATH"

# For this build, we will use: labsjdk-ce-21 | ce-21.0.2+13-jvmci-23.1-b33
RUN echo 10 | mx fetch-jdk && \
    mv /root/.mx/jdks /workspace/jdks

# TODO make this generic
ENV JAVA_HOME=/root/.mx/jdks/labsjdk-ce-21-jvmci-23.1-b33
ENV PATH="$JAVA_HOME/bin:$PATH"

# install pip3 & ninja_syntax. MX can't locate this library for some reason..
RUN yes | apt-get install python3-pip
RUN pip3 install ninja_syntax

# RUN cd /workspace/graal_vincent/compiler && mx build

