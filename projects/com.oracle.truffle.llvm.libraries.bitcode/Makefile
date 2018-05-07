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

.PHONY: default
default: bin/libsulong.bc bin/libsulong-src.tar.gz

C_SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)
CXX_SOURCE_FILES=$(wildcard ${VPATH}/libcxxabi/*.cpp)
BC_FILES=${C_SOURCE_FILES:${VPATH}/src/%.c=bin/%.bc} ${CXX_SOURCE_FILES:${VPATH}/libcxxabi/%.cpp=bin/%.bc}

OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -instcombine -dse -loop-simplify -reassociate -licm -gvn

CXXFLAGS=-std=c++11 -stdlib=libc++ -DLIBCXXABI_SILENT_TERMINATE

DEPFLAGS = -MT $@ -MMD -MP -MF deps/$*.Td
POSTCOMPILE = @mv -f deps/$*.Td deps/$*.d && touch $@
$(shell mkdir -p deps bin >/dev/null)

bin/libsulong.bc: ${BC_FILES}
	llvm-link -o $@ $+

bin/%.bc: bin/%.noopt.bc
	opt -o $@ $+ ${OPT_FLAGS}

bin/%.noopt.bc: src/%.c deps/%.d
	clang -c -emit-llvm -o $@ -I${VPATH}/include ${CFLAGS} ${DEPFLAGS} $<
	${POSTCOMPILE}

bin/%.noopt.bc: libcxxabi/%.cpp deps/%.d
	clang++ -c -emit-llvm -I${VPATH}/include -I${VPATH}/libcxxabi -I${VPATH}/libcxx -o $@ ${CFLAGS} ${CXXFLAGS} ${DEPFLAGS} $<
	${POSTCOMPILE}

bin/libsulong-src.tar.gz: ${C_SOURCE_FILES} ${CXX_SOURCE_FILES} include/polyglot.h
	tar -czf $@ -C ${VPATH} .

deps/%.d: ;
.PRECIOUS: deps/%.d

include $(wildcard $(patsubst %,deps/%.d,$(basename $(BC_FILES))))
