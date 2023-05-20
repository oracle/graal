ARG BASE_IMAGE=container-registry.oracle.com/os/oraclelinux:8-slim

FROM ${BASE_IMAGE} as base

RUN microdnf update -y oraclelinux-release-el8 \
    && microdnf --enablerepo ol8_codeready_builder install bzip2-devel ed gcc gcc-c++ gcc-gfortran gzip file fontconfig less libcurl-devel make openssl openssl-devel readline-devel tar glibc-langpack-en \
    vi which xz-devel zlib-devel findutils glibc-static libstdc++ libstdc++-devel libstdc++-static zlib-static \
    && microdnf clean all
RUN fc-cache -f -v

ENV LANG=en_US.UTF-8 \
    JAVA_HOME=/graalvm

WORKDIR /