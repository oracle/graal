#
# Copyright (c) 2017, 2019, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
QUIETLY$(MX_VERBOSE) = @

.PHONY: default
default: bin/libsulong.bc bin/libsulong++.bc bin/libsulong-src.tar.gz bin/$(LIB_POLYGLOT)

CLANG?=clang
CLANGXX?=clang++
OPT?=opt
LLVM_LINK?=llvm-link

C_SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)
C_BC_FILES=${C_SOURCE_FILES:${VPATH}/src/%.c=bin/%.bc}

CXX_SOURCE_FILES=$(wildcard ${VPATH}/libcxxabi/*.cpp)
CXX_BC_FILES=${CXX_SOURCE_FILES:${VPATH}/libcxxabi/%.cpp=bin/%.bc}

OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -instcombine -dse -loop-simplify -reassociate -licm -gvn

CFLAGS+=-g -fdebug-prefix-map=${VPATH}=com.oracle.truffle.llvm.libraries.bitcode
CXXFLAGS+=-std=c++11 -stdlib=libc++ -DLIBCXXABI_SILENT_TERMINATE

DEPFLAGS = -MT $@ -MMD -MP -MF deps/$*.Td
POSTCOMPILE = @mv -f deps/$*.Td deps/$*.d && touch $@
$(shell mkdir -p deps bin >/dev/null)

bin/libsulong.bc: ${C_BC_FILES}
	$(QUIETLY) $(LLVM_LINK) -o $@ $+

bin/libsulong++.bc: ${CXX_BC_FILES}
	$(QUIETLY) $(LLVM_LINK) -o $@ $+

bin/%.bc: bin/%.noopt.bc
	$(QUIETLY) $(OPT) -o $@ $+ ${OPT_FLAGS}

bin/%.noopt.bc: src/%.c deps/%.d
	$(QUIETLY) $(CLANG) -c -emit-llvm -o $@ -I${VPATH}/include ${CFLAGS} ${DEPFLAGS} $<
	${POSTCOMPILE}

bin/%.noopt.bc: libcxxabi/%.cpp deps/%.d
	$(QUIETLY) $(CLANGXX) -c -emit-llvm -I${VPATH}/include -I${VPATH}/libcxxabi -I${VPATH}/libcxx -o $@ ${CFLAGS} ${CXXFLAGS} ${DEPFLAGS} $<
	${POSTCOMPILE}

bin/libsulong-src.tar.gz: ${C_SOURCE_FILES} ${CXX_SOURCE_FILES} include/polyglot.h
	$(QUIETLY) tar -czf $@ -C ${VPATH} .

deps/%.d: ;
.PRECIOUS: deps/%.d

include $(wildcard $(patsubst %,deps/%.d,$(basename $(BC_FILES))))
