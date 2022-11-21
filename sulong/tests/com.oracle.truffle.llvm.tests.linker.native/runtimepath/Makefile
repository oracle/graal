#
# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
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

default: runtimepath/not-inherited/ref.bc runtimepath/main-override/ref.bc

runtimepath/libFoo$(SO_EXT): runtimepath/FooA.c runtimepath/Makefile runtimepath/lib/libBar$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -rpath $(ORIGIN_TOKEN)/lib -Lruntimepath/lib -lBar -fPIC -shared -o $@ $<

runtimepath/libBar$(SO_EXT): runtimepath/BarB.c runtimepath/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

runtimepath/lib/libBar$(SO_EXT): runtimepath/BarC.c runtimepath/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

runtimepath/not-inherited/ref.out: runtimepath/main.c runtimepath/Makefile runtimepath/libFoo$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -rpath $(ORIGIN_TOKEN)/.. -Lruntimepath -lFoo -o $@ $<

runtimepath/main-override/ref.out: runtimepath/main.c runtimepath/Makefile runtimepath/libFoo$(SO_EXT) runtimepath/libBar$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -rpath $(ORIGIN_TOKEN)/.. -Lruntimepath -lFoo -lBar -o $@ $<

%.bc: %.out
	cp $< $@
