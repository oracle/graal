#
# Copyright (c) 2019, Oracle and/or its affiliates.
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

RESULT_DIR=bitcodeformat
TARGETS:=hello-linux-emit-llvm.bc \
         hello-linux-compile-fembed-bitcode.o \
         hello-linux-link-fembed-bitcode \
         hello-linux-link-fembed-bitcode.so \
         hello-darwin-emit-llvm.bc \
         hello-darwin-compile-fembed-bitcode.o \
         hello-darwin-link-fembed-bitcode \
         hello-darwin-link-fembed-bitcode.dylib \
         hello-darwin-link.bundle

CLANG?=clang

# disable default rules
.SUFFIXES:

.PHONY: default
default: $(addprefix $(RESULT_DIR)/, $(TARGETS))

ifdef rebuild
ifeq ($(OS), darwin)
$(RESULT_DIR)/%-darwin-emit-llvm.bc: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -c -emit-llvm -o $@ $<

$(RESULT_DIR)/%-darwin-compile-fembed-bitcode.o: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -c -fembed-bitcode -o $@ $<

$(RESULT_DIR)/%-darwin-link-fembed-bitcode: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -fembed-bitcode -flto=full -o $@ $<

$(RESULT_DIR)/%-darwin-link-fembed-bitcode.dylib: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -fembed-bitcode -flto=full -Wl,-dylib -o $@ $<

$(RESULT_DIR)/%-darwin-link.bundle: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(TOOLCHAIN_CLANG) -dynamic -bundle -o $@ $<
else
$(RESULT_DIR)/%-linux-emit-llvm.bc: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -c -emit-llvm -o $@ $<

$(RESULT_DIR)/%-linux-compile-fembed-bitcode.o: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -c -fembed-bitcode -o $@ $<

$(RESULT_DIR)/%-linux-link-fembed-bitcode: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -flto=full -Wl,--plugin-opt=embed-bitcode -fuse-ld=lld -o $@ $<

$(RESULT_DIR)/%-linux-link-fembed-bitcode.so: %.c
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) -flto=full -Wl,--plugin-opt=embed-bitcode -fuse-ld=lld -shared -fPIC -o $@ $<
endif
else

# We just copy prebuilt files since generating them is not easy in a target independent way.
# Current binaries have been built with llvm 7.0.1.
$(RESULT_DIR)/%: binaries/%
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) cp $< $@

endif
