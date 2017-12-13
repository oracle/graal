#
# Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

INCLUDE_DIR = include
HEADER_FILES = $(wildcard $(INCLUDE_DIR)/*.h)

TARGETOS ?= $(shell uname -s)
ifeq ($(TARGETOS),Linux)
    OS:=linux
endif
ifeq ($(TARGETOS),Darwin)
    OS:=darwin
endif
TARGET_DIR = ../../../clibraries/$(OS)-amd64
TARGET_HEADERS = $(patsubst %,$(TARGET_DIR)/%,$(HEADER_FILES))

all : $(TARGET_HEADERS)

$(TARGET_DIR) $(TARGET_DIR)/$(INCLUDE_DIR):
	mkdir -p $@

$(TARGET_DIR)/$(INCLUDE_DIR)/%.h : $(INCLUDE_DIR)/%.h | $(TARGET_DIR)/$(INCLUDE_DIR)
	cp $< $@

clean :
	$(RM) $(TARGET_HEADERS)

.PHONY : all clean
