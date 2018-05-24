#
# Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
# ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

# Binary directory
SRC_DIR := $(VPATH)/src
SRC_FILE := $(SRC_DIR)/polyglot-nativeapi/polyglot-nativeapi.c
RESOURCE_DIR := $(VPATH)/../org.graalvm.polyglot.nativeapi/resources
OUT_DIR := ${OS}-${ARCH}
OUT_FILE := $(OUT_DIR)/polyglot-nativeapi

.DEFAULT_GOAL := all

ifneq (${OS},windows)
POLYGLOT_NATIVE_CFLAGS := -c -fPIC -g -I $(RESOURCE_DIR)
$(OUT_FILE).o: $(SRC_FILE)
	mkdir -p $(OUT_DIR)
	$(CC) $(POLYGLOT_NATIVE_CFLAGS) $(SRC_FILE) -o $(OUT_FILE).o

all: $(OUT_FILE).o
else
$(OUT_FILE).obj: $(SRC_FILE)
	mkdir -p $(OUT_DIR)
	cl /c /Zi /I $(RESOURCE_DIR) /Fo$(OUT_FILE).obj $(SRC_FILE)

all: $(OUT_FILE).obj
endif

clean :
	$(RM) -r $(OUT_DIR)

.PHONY: all clean
