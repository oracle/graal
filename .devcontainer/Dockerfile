# LICENSE UPL 1.0
#
# Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
#

FROM container-registry.oracle.com/os/oraclelinux:7-slim

RUN yum update -y oraclelinux-release-el7 \
    && yum install -y oraclelinux-developer-release-el7 oracle-softwarecollection-release-el7 \
    && yum-config-manager --enable ol7_developer \
    && yum-config-manager --enable ol7_developer_EPEL \
    && yum-config-manager --enable ol7_optional_latest \
    && yum install -y bzip2-devel ed gcc gcc-c++ gcc-gfortran gzip file fontconfig less libcurl-devel make openssl openssl-devel readline-devel tar vi which xz-devel zlib-devel \
    && yum install -y glibc-static libcxx libcxx-devel libstdc++-static zlib-static git \
    && rm -rf /var/cache/yum

ENV LANG=en_US.UTF-8 \
    PYENV_ROOT=/data/.pyenv/ \
    MX_PATH=/data/mx/ \
    JAVA_HOME=/data/jdk \
    PATH="/data/mx/:/data/.pyenv/shims/:/data/.pyenv/bin:$PATH"

RUN git clone https://github.com/pyenv/pyenv.git ${PYENV_ROOT} \
    && pyenv install 3.8.0 \
    && pyenv global 3.8.0 \
    && git clone https://github.com/graalvm/mx.git ${MX_PATH} \
    && cd /data \
    && mx --java-home= fetch-jdk --jdk-id labsjdk-ce-11 --to jdk-dl --alias ${JAVA_HOME}

CMD mx --version
