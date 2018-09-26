#
# Copyright (c) 2018, Oracle and/or its affiliates.
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

BC_TARGETS:=$(foreach var,$(VARIANTS),$(TESTS:%=%/$(var).bc))
CXXFLAGS+=-std=c++11 -stdlib=libc++

.PHONY: default
default:

include $(TOP)/../Makefile.common
include ${TESTFILE}

ifeq ($(OS), darwin)
EMBED_BC=-fembed-bitcode
BC_FILE=
else
EMBED_BC=
BC_FILE=%/O0.bc.o

#%.bc.o: %.bc
#%	$(QUIETLY) objcopy -I binary -O elf64-x86-64 -B i386:x86-64 --rename-section .data=.llvmbc $< $@
endif
BC_FILE=

%.c.dir/ref.out: %.c $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $^

%.cpp.dir/ref.out: %.cpp $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANGXX) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%.cc.dir/ref.out: %.cc $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANGXX) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%.C.dir/ref.out: %.C $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANGXX) $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%.f.dir/ref.out: %.f $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(FC) -w -J$(shell dirname $@) $(CPPFLAGS) $(FFLAGS) $(LDFLAGS) -o $@ $^

%.f90.dir/ref.out: %.f90 $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(FC) -w -J$(shell dirname $@) $(CPPFLAGS) $(FFLAGS) $(LDFLAGS) -o $@ $^

%.f03.dir/ref.out: %.f03 $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(FC) -w -J$(shell dirname $@) $(CPPFLAGS) $(FFLAGS) $(LDFLAGS) -o $@ $^

%.bc: %.dragonegg.ll
	$(QUIETLY) $(DRAGONEGG_LLVMAS) -o $@ $<

define OPT_RULES
%.c.dir/$(1).bc: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(CLANG) -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CFLAGS) -g -o $$@ $$<

%.cpp.dir/$(1).bc: %.cpp
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(CLANGXX) -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) -g -o $$@ $$<

%.cc.dir/$(1).bc: %.cc
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(CLANGXX) -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) -g -o $$@ $$<

%.C.dir/$(1).bc: %.C
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(CLANGXX) -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) -g -o $$@ $$<

%.f.dir/$(1).dragonegg.ll: %.f
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_FC) -w -S -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) -J$$(shell dirname $$@) $(CPPFLAGS) $(FFLAGS) -o $$@ $$<

%.f90.dir/$(1).dragonegg.ll: %.f90
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_FC) -w -S -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) -J$$(shell dirname $$@) $(CPPFLAGS) $(FFLAGS) -o $$@ $$<

%.f03.dir/$(1).dragonegg.ll: %.f03
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_FC) -w -S -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) -J$$(shell dirname $$@) $(CPPFLAGS) $(FFLAGS) -o $$@ $$<

%.c.dir/gcc_$(1).ll: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_GCC) -w -S --std=gnu99 -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) $(CPPFLAGS) $(CFLAGS) -o $$@ $$<

%.ll.dir/$(1).bc: %.ll
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
