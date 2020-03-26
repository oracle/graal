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

default: \
	dynLink/shadowing/function/ref.bc \
	dynLink/shadowing/variable/ref.bc

dynLink/shadowing/function/libHello$(SO_EXT): dynLink/shadowing/function/lib.c dynLink/shadowing/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

dynLink/shadowing/function/ref.out: dynLink/shadowing/function/main.c dynLink/shadowing/Makefile dynLink/shadowing/function/libHello$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -rpath $(ORIGIN_TOKEN) -LdynLink/shadowing/function -lHello -o $@ $<

dynLink/shadowing/variable/libHello$(SO_EXT): dynLink/shadowing/variable/lib.c dynLink/shadowing/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

dynLink/shadowing/variable/ref.out: dynLink/shadowing/variable/main.c dynLink/shadowing/Makefile dynLink/shadowing/variable/libHello$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -rpath $(ORIGIN_TOKEN) -LdynLink/shadowing/variable -lHello -o $@ $<

%.bc: %.out
	$(QUIETLY) cp $< $@
