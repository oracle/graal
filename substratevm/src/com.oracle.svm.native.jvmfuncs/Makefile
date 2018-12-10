#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

# A list of all the source files.
SRC_DIR = src
LIBC_HELPER_SRCS = $(wildcard $(VPATH)/$(SRC_DIR)/*.c)

LIBRARY_NAME = jvm

INCLUDE_DIR = include
HEADER_FILES = $(wildcard $(VPATH)/$(INCLUDE_DIR)/*.h)

TARGET_DIR = ${OS}-${ARCH}
TARGET_LIB = $(TARGET_DIR)/lib$(LIBRARY_NAME).a
TARGET_HEADERS = $(patsubst $(VPATH)/%,$(TARGET_DIR)/%,$(HEADER_FILES))

# The flags and options to be used when compiling C sources.
ifeq (${OS},windows)
TARGET_LIB = $(TARGET_DIR)/$(LIBRARY_NAME).lib
AR = LIB
# The flags to be used when compiling C sources.
CFLAGS = /MD /I $(VPATH)/$(INCLUDE_DIR) /Zi /O2 /D _LITTLE_ENDIAN
# A list of all the object files.
LIBC_HELPER_OBJS = $(LIBC_HELPER_SRCS:$(VPATH)/%.c=$(TARGET_DIR)/%.obj)
CC := CL
else
TARGET_LIB = $(TARGET_DIR)/lib$(LIBRARY_NAME).a
# The flags to be used when compiling C sources.
CFLAGS = -I$(VPATH)/$(INCLUDE_DIR) -g -fPIC -O2 -D _LITTLE_ENDIAN
# A list of all the object files.
LIBC_HELPER_OBJS = $(LIBC_HELPER_SRCS:$(VPATH)/%.c=$(TARGET_DIR)/%.o)
endif

all : $(TARGET_LIB) $(TARGET_HEADERS)

$(TARGET_DIR) $(TARGET_DIR)/$(INCLUDE_DIR) $(TARGET_DIR)/$(SRC_DIR):
	mkdir -p $@

$(TARGET_DIR)/$(INCLUDE_DIR)/%.h : $(INCLUDE_DIR)/%.h | $(TARGET_DIR)/$(INCLUDE_DIR)
	cp $< $@

ifneq (${OS},windows)

$(TARGET_LIB) : $(LIBC_HELPER_OBJS) | $(TARGET_DIR)
	$(AR) -cr $@ $(LIBC_HELPER_OBJS)

$(TARGET_DIR)/%.o : %.c | $(TARGET_DIR)/$(SRC_DIR)
	$(CC) -c $(CFLAGS) $(CPPFLAGS) $< -o $@

else

$(TARGET_LIB) : $(LIBC_HELPER_OBJS) | $(TARGET_DIR)
	$(AR) /OUT:$@ $(LIBC_HELPER_OBJS)

$(TARGET_DIR)/%.obj : %.c | $(TARGET_DIR)/$(SRC_DIR)
	$(CC) -c $(CFLAGS) $(CPPFLAGS) $< /Fo$@

endif

clean :
	$(RM) $(LIBC_HELPER_OBJS)
	$(RM) $(TARGET_LIB) $(TARGET_HEADERS)

.PHONY : all clean
