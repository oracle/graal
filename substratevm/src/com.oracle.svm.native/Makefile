#
# Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

# Identify phony targets. This ensures that we always
# recurse into the {dlopen,launch,embedded} submakefiles.
# Since the target names {dlopen,launch,embedded} happen also to be the
# names of directories, they exist without Make's help. Therefore, Make
# will only re-run these rules if the mtime of their named source files
# is changed. Since the source files are the build/.../makefiles,
# it is quite unlikely that they would change. However, the source
# files of the rules they contain (i.e. all the .c files in this project!)
# will change much more often. So, without .PHONY, these makefiles
# never get invoked and the files built from C code are not rebuilt.
# (This suggests a TODO: clean up the needlessly complex makefiles in
# this project. It should not be necessary to copy makefiles around.)

.PHONY: all strictmath libchelper jni clean

ifeq (${OS}, solaris)
all :	nop # Solaris is unsupported
else ifeq (${ARCH}, sparcv9)
all :	nop # SPARC64 is unsupported
else
all :	strictmath libchelper jni
endif

ifeq (${OS}, windows)
export TARGET_DIR = $(shell cmd.exe /c echo %cd%)\${OS}-${ARCH}
else
export TARGET_DIR := $(shell pwd)/${OS}-${ARCH}
endif

strictmath:
	$(MAKE) -C $(VPATH)/strictmath

libchelper:
	$(MAKE) -C $(VPATH)/libchelper

jni:
	$(MAKE) -C $(VPATH)/jni

clean:
	$(MAKE) -C $(VPATH)/strictmath $@
	$(MAKE) -C $(VPATH)/libchelper $@
	$(MAKE) -C $(VPATH)/jni $@

# for unsupported platforms
nop:
