#
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

.PHONY: default
default: bin/${LIBSULONG}

# build Sulong's EH library:
# the implementation of this library requires llvm v3.8
# on earlier versions, a fallback stub library is produced

ifeq ($(OS), darwin)
# darwin has at least LLVM 3.8
LLVM_VERSION := 308
LLVM_GE_38 := 1
LDFLAGS=-dynamiclib -g
else
LLVM_VERSION := $(shell clang --version | grep -e 'clang version' | sed -e 's/clang version \([0-9]\+\)\.\([0-9]\+\).*/\1 \2/' | xargs printf "%d%02d")
LLVM_GE_38 := $(shell expr ${LLVM_VERSION} \>= 308)
LDFLAGS=-shared -g
endif

CFLAGS=-g -fPIC -DPIC -O0
CXXFLAGS=${CFLAGS} -DLLVM_VERSION=${LLVM_VERSION}

ifeq (${LLVM_GE_38},1)
CXXFLAGS+=-stdlib=libc++ -std=c++11
LDFLAGS+=-stdlib=libc++ -std=c++11 -lc++abi
endif

bin/${LIBSULONG}: bin/sulongeh.o bin/misc.o bin/posix.o
	clang++ ${LDFLAGS} -o $@ $+

bin/%.o: src/%.c
	@mkdir -p bin
	clang ${CPPFLAGS} ${CFLAGS} -c $< -o $@

bin/%.o: src/%.cpp
	@mkdir -p bin
	clang++ ${CPPFLAGS} ${CXXFLAGS} -c $< -o $@
