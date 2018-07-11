#
# Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

# A list of all the libchelper source files.
LIBC_HELPER_SRCS = $(wildcard *.c)

LIBRARY_NAME = libchelper

INCLUDE_DIR = include
HEADER_FILES = $(wildcard $(INCLUDE_DIR)/*.h)

TARGET_LIB = $(TARGET_DIR)/lib$(LIBRARY_NAME).a
TARGET_HEADERS = $(patsubst %,$(TARGET_DIR)/%,$(HEADER_FILES))

OBJ_DIR = $(TARGET_DIR)/$(LIBRARY_NAME)
# The flags and options to be used when compiling C sources.
ifeq (${OS},windows)
# The flags to be used when compiling C sources.
CFLAGS = /I $(INCLUDE_DIR) /Zi /O2 /D _LITTLE_ENDIAN
# A list of all the libchelper object files.
LIBC_HELPER_OBJS = $(LIBC_HELPER_SRCS:%.c=$(OBJ_DIR)/%.obj)
CC := CL
else
# The flags to be used when compiling C sources.
CFLAGS = -I$(INCLUDE_DIR) -g -fPIC -O2 -D _LITTLE_ENDIAN
# A list of all the libchelper object files.
LIBC_HELPER_OBJS = $(LIBC_HELPER_SRCS:%.c=$(OBJ_DIR)/%.o)
endif

all : $(TARGET_LIB) $(TARGET_HEADERS)

$(TARGET_DIR) $(TARGET_DIR)/$(INCLUDE_DIR) $(OBJ_DIR):
	mkdir -p $@

$(TARGET_DIR)/$(INCLUDE_DIR)/%.h : $(INCLUDE_DIR)/%.h | $(TARGET_DIR)/$(INCLUDE_DIR)
	cp $< $@

ifneq (${OS},windows)

$(TARGET_LIB) : $(LIBC_HELPER_OBJS) | $(TARGET_DIR)
	$(AR) -cr $@ $(LIBC_HELPER_OBJS)

$(OBJ_DIR)/%.o : %.c | $(OBJ_DIR)
	$(CC) -c $(CFLAGS) $(CPPFLAGS) $< -o $@

else

# For now just create empty files for Windows
$(TARGET_LIB):  $(TARGET_DIR)
	echo "libchelpers not imlemented on Windows"
	$(shell touch $(TARGET_LIB))

endif

clean :
	$(RM) $(LIBC_HELPER_OBJS)
	$(RM) $(TARGET_LIB) $(TARGET_HEADERS)

.PHONY : all clean
