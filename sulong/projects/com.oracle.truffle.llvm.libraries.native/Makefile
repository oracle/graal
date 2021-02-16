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
QUIETLY$(MX_VERBOSE) = @

.PHONY: default
default: bin/${LIBSULONG}

CLANG?=clang

ifeq ($(OS), darwin)
CLANG_PREFIX=/usr/bin/xcrun
LDFLAGS=-dynamiclib -g
bin/${LIBSULONG}: LDFLAGS+=-install_name ${LIBSULONG}
else
CLANG_PREFIX=
LDFLAGS=-shared -g
endif

LDFLAGS+= ${EXTRA_LDFLAGS}
CFLAGS=-g -fPIC -DPIC -O3 ${EXTRA_CFLAGS}

bin/${LIBSULONG}: bin/misc.o bin/posix.o bin/fp80.o bin/globals.o
	$(QUIETLY) $(CLANG_PREFIX) $(CLANG) ${LDFLAGS} -lm -o $@ $+

bin/%.o: src/%.c
	@mkdir -p bin
	$(QUIETLY) $(CLANG_PREFIX) $(CLANG) ${CPPFLAGS} ${CFLAGS} -c $< -o $@
