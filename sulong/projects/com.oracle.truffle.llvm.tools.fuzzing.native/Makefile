#
# Copyright (c) 2020, Oracle and/or its affiliates.
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

QUIETLY$(MX_VERBOSE)  = @
QUIET_REDIRECT$(MX_VERBOSE)  = > /dev/null
.PHONY: default

default: ${LLVM_STRESS} ${LLVM_REDUCE}

ifeq ($(OS), darwin)
CLANG_PREFIX=/usr/bin/xcrun
LDFLAGS+=-lz
else
CLANG_PREFIX=
endif

LIBS+=$(shell ${LLVM_CONFIG} --libs)
LDFLAGS+=$(shell ${LLVM_CONFIG} --ldflags) -rpath $(shell ${LLVM_CONFIG} --libdir)
CXXFLAGS+=$(shell ${LLVM_CONFIG} --cxxflags)
CPPFLAGS+=$(shell ${LLVM_CONFIG} --cppflags)

src/llvm-stress.cpp: ${LLVM_ORG_SRC}/llvm/tools/llvm-stress/llvm-stress.cpp
src/llvm-reduce.cpp: ${LLVM_ORG_SRC}/llvm/tools/llvm-isel-fuzzer/llvm-isel-fuzzer.cpp

src/%.cpp: %.diff
	$(eval tmp_dir=$(dir $@)/tmp)
	@mkdir -p ${tmp_dir}
	@# copy source file to temporary location
	$(QUIETLY) cp $(word 2,$^) ${tmp_dir}/$(notdir $@)
	@# patch source file
	$(QUIETLY) patch -p1 -i $< -d ${tmp_dir} ${QUIET_REDIRECT}
	@# copy patched file to destination
	$(QUIETLY) cp ${tmp_dir}/$(notdir $@) $@

bin/%.o: src/%.cpp
	@mkdir -p $(dir $@)
	$(QUIETLY) $(CLANG_PREFIX) $(CXX) -c ${CXXFLAGS} ${CPPFLAGS} -o $@ $<

${LLVM_REDUCE}: bin/llvm-reduce.o
	@mkdir -p $(dir $@)
	$(QUIETLY) $(CLANG_PREFIX) $(CXX) ${LDFLAGS} ${CXXFLAGS} -o $@ $< $(LIBS)

${LLVM_STRESS}: bin/llvm-stress.o
	@mkdir -p $(dir $@)
	$(QUIETLY) $(CLANG_PREFIX) $(CXX) ${LDFLAGS} ${CXXFLAGS} -o $@ $< $(LIBS)
