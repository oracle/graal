#
# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
TOP := $(dir $(lastword $(MAKEFILE_LIST)))

ifeq ($(BUILD_REF),1)
REF_TARGETS:=$(TESTS:%=%/ref.out)
else
REF_TARGETS:=
endif

ifeq ($(BUILD_SO),1)
# SO_EXT is set by SulongTestSuite
BC_TARGETS:=$(foreach var,$(VARIANTS),$(TESTS:%=%/$(var)$(SO_EXT)))
CFLAGS+= -fPIC
CXXFLAGS+= -fPIC
else
BC_TARGETS:=$(foreach var,$(VARIANTS),$(TESTS:%=%/$(var).bc))
endif

CXXFLAGS+=-DLIBCXXABI_SILENT_TERMINATE

.PHONY: default
ifeq ($(strip $(REF_TARGETS) $(BC_TARGETS)),)
$(error No test files to compile!)
endif
default: $(REF_TARGETS) $(BC_TARGETS)

include $(TOP)/Makefile.common

ifeq ($(OS), darwin)
# NOTE: the darwin linker refuses bundle bitcode if any of the dependencies do not have a bundle section.
#   However, it does include the bundle if linked with -flto, although the warning still says otherwise.
%$(SO_EXT): %.bc
	$(QUIETLY) $(CLANG) -flto -Wl,-bitcode_bundle -dynamiclib -Wno-everything $(LDFLAGS) -g -o $@ $<
else

# Rules for shared objects
# NOTE: this relies on the fact that there is only a single source file:
#   We produce an object file with embedded bitcode section.
#   The linker will just copy this section into the final shared object.
#   Since there is only one bitcode file there is no need for llvm-link.
# NOTE2: -fembed-bitcode will not work with clang before version 4
%_BC_EMBED_PIC.o: %.bc
	$(QUIETLY) $(CLANG) -c -fembed-bitcode -O0 ${SULONG_MAKE_CLANG_IMPLICIT_ARGS} -Wno-everything -fPIC $(CPPFLAGS) $(CFLAGS) -g -o $@ $<

%$(SO_EXT): %_BC_EMBED_PIC.o
	$(QUIETLY) $(CLANG) -shared -Wno-everything $(LDFLAGS) -g -o $@ $<
endif
