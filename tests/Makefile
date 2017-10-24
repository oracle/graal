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
QUIETLY$(MX_VERBOSE)  = @

ifeq ($(BUILD_REF),1)
REF_TARGETS:=$(TESTS:%=%/ref.out)
else
REF_TARGETS:=
endif

BC_TARGETS:=$(foreach var,$(VARIANTS),$(TESTS:%=%/$(var).bc))

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
	$(QUIETLY) clang $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $^

%/ref.out: %.cpp $(BC_FILE)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) clang++ $(EMBED_BC) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%.bc: %.ll
	$(QUIETLY) $(DRAGONEGG_LLVMAS) -o $@ $<

define OPT_RULES
%/$(1).bc: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) clang -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CFLAGS) -g -o $$@ $$<

%/$(1).bc: %.cpp
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) clang++ -c -emit-llvm $(2) -Wno-everything $(CPPFLAGS) $(CXXFLAGS) -g -o $$@ $$<

%/gcc_$(1).ll: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_GCC) -w -S --std=gnu99 -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) $(CPPFLAGS) $(CFLAGS) -o $$@ $$<
endef

$(eval $(call OPT_RULES,O0,-O0 ${SULONG_MAKE_CLANG_IMPLICIT_ARGS}))
$(eval $(call OPT_RULES,O1,-O1))
$(eval $(call OPT_RULES,O2,-O2))
$(eval $(call OPT_RULES,O3,-O3))

%_MISC_OPTS.bc: %.bc
	$(QUIETLY) opt -o $@ -functionattrs -instcombine -always-inline -jump-threading -simplifycfg -mem2reg $<

%_MEM2REG.bc: %.bc
	$(QUIETLY) opt -o $@ -mem2reg $<
