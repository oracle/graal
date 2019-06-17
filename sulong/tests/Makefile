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
QUIETLY$(MX_VERBOSE)  = @

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

CLANG?=clang
CLANGXX?=clang++
LLVM_AS?=llvm-as
OPT?=opt
FC?=gfortran

CFLAGS+= $(SUITE_CFLAGS)
CXXFLAGS+= $(SUITE_CXXFLAGS) -std=c++11 -stdlib=libc++ -DLIBCXXABI_SILENT_TERMINATE
CPPFLAGS+= $(SUITE_CPPFLAGS)
LDFLAGS+= $(SUITE_LDFLAGS)

.PHONY: default
default: $(REF_TARGETS) $(BC_TARGETS)

ifeq ($(OS), darwin)
EMBED_BC=-fembed-bitcode
BC_FILE=
else
EMBED_BC=
BC_FILE=%/O0.bc.o

%.bc.o: %.bc
	$(QUIETLY) objcopy -I binary -O elf64-x86-64 -B i386:x86-64 --rename-section .data=.llvmbc $< $@
endif

%/ref.out: %.c $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $^

%/ref.out: %.cpp $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANGXX) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%/ref.out: %.ll $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $^

%/gcc_O0.bc: %/gcc_O0.ll
	$(QUIETLY) $(DRAGONEGG_LLVMAS) -o $@ $<

define OPT_RULES
%/$(1).bc: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(CLANG) -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CFLAGS) -g -o $$@ $$<

%/$(1).bc: %.cpp
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(CLANGXX) -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) -g -o $$@ $$<

%/gcc_$(1).ll: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_GCC) -w -S --std=gnu99 -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) $(CPPFLAGS) $(CFLAGS) -o $$@ $$<

%/$(1).bc: %.ll
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(LLVM_AS) -o $$@ $$<

endef

$(eval $(call OPT_RULES,O0,-O0 ${SULONG_MAKE_CLANG_IMPLICIT_ARGS}))
$(eval $(call OPT_RULES,O1,-O1))
$(eval $(call OPT_RULES,O2,-O2))
$(eval $(call OPT_RULES,O3,-O3))

%_MISC_OPTS.bc: %.bc
	$(QUIETLY) $(OPT) -o $@ -functionattrs -instcombine -always-inline -jump-threading -simplifycfg -mem2reg $<

%_MEM2REG.bc: %.bc
	$(QUIETLY) $(OPT) -o $@ -mem2reg $<

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
ifndef NO_FEMBED_BITCODE
%_BC_EMBED_PIC.o: %.bc
	$(QUIETLY) $(CLANG) -c -fembed-bitcode -O0 ${SULONG_MAKE_CLANG_IMPLICIT_ARGS} -Wno-everything -fPIC $(CPPFLAGS) $(CFLAGS) -g -o $@ $<
else
%_BC_NO_EMBED_PIC.o: %.bc
	$(QUIETLY) $(CLANG) -c -O0 -Wno-everything -fPIC $(CPPFLAGS) $(CFLAGS) -g -o $@ $<

%_BC_EMBED_PIC.o: %_BC_NO_EMBED_PIC.o %.bc
	$(QUIETLY) objcopy --add-section .llvmbc=$(word 2, $^) --set-section-flags .llvmbc=alloc,contents,load,readonly $< $@
endif

%$(SO_EXT): %_BC_EMBED_PIC.o
	$(QUIETLY) $(CLANG) -shared -Wno-everything $(LDFLAGS) -g -o $@ $<
endif
