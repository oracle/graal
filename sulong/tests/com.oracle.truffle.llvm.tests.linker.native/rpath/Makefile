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
QUIETLY$(MX_VERBOSE)  = @

default: rpath/not-inherited/ref.bc rpath/main-override/ref.bc

rpath/libFoo$(SO_EXT): rpath/FooA.c rpath/Makefile rpath/lib/libBar$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -rpath $(ORIGIN_TOKEN)/lib -Lrpath/lib -lBar -fPIC -shared -o $@ $<

rpath/libBar$(SO_EXT): rpath/BarB.c rpath/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

rpath/lib/libBar$(SO_EXT): rpath/BarC.c rpath/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

rpath/not-inherited/ref.out: rpath/main.c rpath/Makefile rpath/libFoo$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -rpath $(ORIGIN_TOKEN)/.. -Lrpath -lFoo -o $@ $<

rpath/main-override/ref.out: rpath/main.c rpath/Makefile rpath/libFoo$(SO_EXT) rpath/libBar$(SO_EXT)
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -rpath $(ORIGIN_TOKEN)/.. -Lrpath -lFoo -lBar -o $@ $<

%.bc: %.out
	cp $< $@
