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
ifeq ($(TARGETOS),CYGWIN_NT-10.0)
    OS:=windows
else
    $(error Unsupported operating system $(TARGETOS). Currently supported: Linux, Darwin and Windows)
endif
endif
endif

# Binary directory
SRC_DIR := src/
OUT_DIR = ../../mxbuild/org.graalvm.polyglot.nativeapi.native/$(OS)-amd64

.DEFAULT_GOAL := all

ifneq ($(OS),windows)
POLYGLOT_NATIVE_CFLAGS := -c -fPIC -g -I ../org.graalvm.polyglot.nativeapi/resources
$(OUT_DIR)/polyglot-nativeapi.o: $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c
	mkdir -p $(OUT_DIR)
	$(CC) $(POLYGLOT_NATIVE_CFLAGS) $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c -o $(OUT_DIR)/polyglot-nativeapi.o

all: $(OUT_DIR)/polyglot-nativeapi.o

else
$(OUT_DIR)/polyglot-nativeapi.obj: $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c
	mkdir -p $(OUT_DIR)
	cl /c /Zi /I ../org.graalvm.polyglot.nativeapi/resources /Fo$(OUT_DIR)/polyglot-nativeapi.obj  $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c

all: $(OUT_DIR)/polyglot-nativeapi.obj
endif

clean :
	$(RM) -r $(OUT_DIR)

.PHONY: all clean
