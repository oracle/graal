#
# Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
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
