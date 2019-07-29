#
# Copyright (c) 2019, 2019, Oracle and/or its affiliates.
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
SHELL=/bin/bash -o pipefail

QUIETLY$(MX_VERBOSE)  = @

TARGETS= \
run-polyglot_biginteger.out-lli \
run-polyglot_biginteger.comp-and-link.out-lli \
run-polyglot_biginteger_cxx.out-lli \
run-polyglot_biginteger_cxx.comp-and-link.out-lli \
run-main.comp-and-link.out-lli \
run-main.out-lli \
print-help \
print-graalvm-help \
print-graalvm-cmd \
fembed-bitcode

.SECONDARY:
.PHONY: default clean print-help print-graalvm-help
default: $(TARGETS)

CFLAGS=
CXXFLAGS=
LDFLAGS=

define CHECK_ENV
ifndef $(1)
  $$(error Environment variable "$(1)" is undefined)
endif
endef

$(eval $(call CHECK_ENV,CLANG))
$(eval $(call CHECK_ENV,CLANGXX))
$(eval $(call CHECK_ENV,SULONG_EXE))

ifeq ($(OS), darwin)
BC_MAGIC=0b17c0de
BC_SECTION=__bitcode\|__bundle
else
BC_MAGIC=dec04342
BC_SECTION=\.llvmbc
endif

# checks
define CHECK_BC_SECTION
	( objdump -h $(1) | grep "$(BC_SECTION)" ) > /dev/null || ( echo No bitcode section in $(1) ; false )
endef
define CHECK_BC_FILE
	( od -t x4 -N 4 $(1) | grep "$(BC_MAGIC)" ) > /dev/null || ( echo Not a bitcode file: $(1) ; false )
endef

ifeq ($(OS), darwin)
else
main.out: LDFLAGS+=-Wl,--gc-sections
endif

# randomly compile one file with -v
obj/main.o: CFLAGS+=-v
# require libployglot
polyglot_biginteger.out: LDFLAGS+=-lpolyglot-mock
polyglot_biginteger.comp-and-link.out: LDFLAGS+=-lpolyglot-mock
polyglot_biginteger_cxx.out: LDFLAGS+=-lpolyglot-mock
polyglot_biginteger_cxx.comp-and-link.out: LDFLAGS+=-lpolyglot-mock

# normal compilation (with -fLTO, i.e., result is bitcode file)
obj/%.o: src/%.c
	$(QUIETLY) mkdir -p $(dir $@)
	$(QUIETLY) $(CLANG) $(CFLAGS) -c $< -o $@
	$(QUIETLY) $(call CHECK_BC_FILE,$@)

obj/%.opp: src/%.cpp
	$(QUIETLY) mkdir -p $(dir $@)
	$(QUIETLY) $(CLANGXX) $(CXXFLAGS) -c $< -o $@
	$(QUIETLY) $(call CHECK_BC_FILE,$@)

# linking (executable with embedded bitcode)
%.out: obj/%.o
	$(QUIETLY) $(CLANG) $(LDFLAGS) $+ -o $@
	$(QUIETLY) $(call CHECK_BC_SECTION,$@)

%.out: obj/%.opp
	$(QUIETLY) $(CLANGXX) $(LDFLAGS) $+ -o $@
	$(QUIETLY) $(call CHECK_BC_SECTION,$@)

# compile and link (executable with embedded bitcode)
%.comp-and-link.out: src/%.c
	$(QUIETLY) $(CLANG) $(LDFLAGS) $+ -o $@
	$(QUIETLY) $(call CHECK_BC_SECTION,$@)

%.comp-and-link.out: src/%.cpp
	$(QUIETLY) $(CLANGXX) $(LDFLAGS) $+ -o $@
	$(QUIETLY) $(call CHECK_BC_SECTION,$@)

# run with sulong
run-%-lli: %
	$(QUIETLY) $(SULONG_EXE) $<

print-help:
	$(QUIETLY) $(CLANG) --help

print-graalvm-help:
	$(QUIETLY) $(CLANG) --graalvm-help

print-graalvm-cmd:
	$(QUIETLY) $(CLANG) --graalvm-print-cmd bla.c

# not supported
fembed-bitcode:
	$(QUIETLY) $(CLANG) -fembed-bitcode bla.c ; test $$? -eq 1

clean:
	$(QUIETLY) rm -rf *.out obj
