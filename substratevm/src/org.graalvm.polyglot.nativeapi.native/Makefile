#
# Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
# ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

# This makefile builds the svmffi, javaframeanchor and svmjni libraries.
TARGETOS ?= $(shell uname -s)
ifeq ($(TARGETOS),Linux)
    OS:=linux
else
ifeq ($(TARGETOS),Darwin)
    OS:=darwin
else
    $(error Unsupported operating system $(TARGETOS). Currently supported: Linux and Darwin)
endif
endif

# Binary directory
SRC_DIR := src/
OUT_DIR = ../../mxbuild/org.graalvm.polyglot.nativeapi.native/$(OS)-amd64
POLYGLOT_NATIVE_CFLAGS := -c -fPIC -g -I ../org.graalvm.polyglot.nativeapi/resources

.DEFAULT_GOAL := all

$(OUT_DIR)/polyglot-nativeapi.o: $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c
	mkdir -p $(OUT_DIR)
	$(CC) $(POLYGLOT_NATIVE_CFLAGS) $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c -o $(OUT_DIR)/polyglot-nativeapi.o

clean :
	$(RM) -r $(OUT_DIR)

all: $(OUT_DIR)/polyglot-nativeapi.o

.PHONY: all clean
