#
# Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
LIBRARY_NAME = strictmath

# Some directory names.
SRC_DIR = src
LIBM_SRC_DIR = $(SRC_DIR)/fdlibm/src
LIBM_INCLUDE = $(SRC_DIR)/fdlibm/include

# How to make object files from source files.
ifeq (${OS},windows)
CFLAGS := /I $(VPATH)/$(LIBM_INCLUDE) /O1 /D _LITTLE_ENDIAN
OBJ_EXT := obj
CC := CL
else
# The flags to be used when compiling C sources.
CFLAGS := -I$(VPATH)/$(LIBM_INCLUDE) -fPIC -O1 -D _LITTLE_ENDIAN
OBJ_EXT := o
endif

TARGET_DIR = ${OS}-${ARCH}
OBJ_DIR = $(TARGET_DIR)/$(LIBM_SRC_DIR)

# A list of all the libm function source files.
LIBM_SRCS = $(wildcard $(VPATH)/$(LIBM_SRC_DIR)/*.c)
LIBM_OBJS = $(LIBM_SRCS:$(VPATH)/%.c=$(TARGET_DIR)/%.$(OBJ_EXT))

TARGET = $(TARGET_DIR)/lib$(LIBRARY_NAME).a

# The first target is what gets made by default.
$(TARGET) : $(TARGET_DIR)/$(SRC_DIR)/StrictMath.$(OBJ_EXT) $(LIBM_OBJS) | $(TARGET_DIR)
	$(AR) -cr $@ $^

ifeq (${OS},windows)
$(TARGET_DIR)/%.$(OBJ_EXT) : %.c $(LIBM_INCLUDE)/fdlibm.h $(LIBM_INCLUDE)/jfdlibm.h | $(OBJ_DIR)
	$(CC) -c $(CFLAGS) $< /Fo$@
else
$(TARGET_DIR)/%.$(OBJ_EXT) : %.c $(LIBM_INCLUDE)/fdlibm.h $(LIBM_INCLUDE)/jfdlibm.h | $(OBJ_DIR)
	$(CC) -c $(CFLAGS) $< -o $@
endif

$(TARGET_DIR) $(OBJ_DIR):
	mkdir -p $@

# How to clean up.
clean :
	$(RM) $(LIBM_OBJS)
	$(RM) $(TARGET_DIR)/$(SRC_DIR)/StrictMath.$(OBJ_EXT)
	$(RM) $(TARGET)

# Use "make dependencies" to discover all the dependencies.
dependencies :
	@ $(CC) -MM $(CFLAGS) $(LIBM_SRCS) StrictMath.c

# The declaration of which targets do not result in files.
.PHONY : clean dependencies
