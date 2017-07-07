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

REF_TARGETS:=$(TESTS:%=%/ref.out)
BC_TARGETS:=$(foreach var,$(VARIANTS),$(TESTS:%=%/$(var).bc))

.PHONY: default
default: $(REF_TARGETS) $(BC_TARGETS)

%/ref.out: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) clang $(LDFLAGS) -o $@ $<

%/ref.out: %.cpp
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) clang++ $(LDFLAGS) -o $@ $<

%.bc: %.ll
	$(QUIETLY) $(DRAGONEGG_LLVMAS) -o $@ $<

define OPT_RULES
%/$(1).bc: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) clang -c -emit-llvm -$(1) -o $$@ $$<

%/$(1).bc: %.cpp
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) clang++ -c -emit-llvm -$(1) -o $$@ $$<

%/gcc_$(1).ll: %.c
	@mkdir -p $$(shell dirname $$@)
	$(QUIETLY) $(DRAGONEGG_GCC) -S --std=gnu99 -fplugin=$(DRAGONEGG) -fplugin-arg-dragonegg-emit-ir -$(1) -o $$@ $$<
endef

$(eval $(call OPT_RULES,O0))
$(eval $(call OPT_RULES,O1))
$(eval $(call OPT_RULES,O2))
$(eval $(call OPT_RULES,O3))

%_BB_VECTORIZE.bc: %.bc
	$(QUIETLY) opt -o $@ -functionattrs -instcombine -always-inline -jump-threading -simplifycfg -mem2reg -bb-vectorize $<
