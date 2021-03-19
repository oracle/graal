#
# Copyright (c) 2021, Oracle and/or its affiliates.
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

LIBS=dlopenLocator/libone$(SO_EXT) dlopenLocator/libtwo$(SO_EXT) dlopenLocator/libthree$(SO_EXT) dlopenLocator/libfour$(SO_EXT)
default: dlopenLocator/ref.bc dlopenLocator/ref.out $(LIBS)

dlopenLocator/lib%$(SO_EXT): src/%.c dlopenLocator/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG_SO) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

dlopenLocator/ref.out: LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/ -ldl
dlopenLocator/ref.out: CPPFLAGS+=-DDLOPEN_TEST_NO_ABSOLUTE=1
dlopenLocator/ref.out: src/dlopentest.c dlopenLocator/Makefile $(LIBS)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG_REF) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $<

dlopenLocator/ref.bc: LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/ -ldl
dlopenLocator/ref.bc: CPPFLAGS+=-DDLOPEN_TEST_NO_ABSOLUTE=1
dlopenLocator/ref.bc: src/dlopentest.c dlopenLocator/Makefile $(LIBS)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG_BC) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $<

dlopenLocator/libone$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/ -LdlopenLocator -ltwo
dlopenLocator/libone$(SO_EXT): dlopenLocator/libtwo$(SO_EXT)

dlopenLocator/libtwo$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS)

dlopenLocator/libthree$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS)

dlopenLocator/libfour$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/ -LdlopenLocator -lone
dlopenLocator/libfour$(SO_EXT): dlopenLocator/libone$(SO_EXT)
