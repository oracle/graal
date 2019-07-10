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

CC_LIBS=-lA -lB
D_LIBS=-lB -lCC -lE
LINKER_MAIN_LIBS=-lCC -lD -lHiddenA -lFileLocalA -lF -lG -lH

default: linker/ref.bc

linker/lib/lib%$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS)
linker/lib/lib%$(SO_EXT): linker/%.c linker/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) $(SO_LDFLAGS) -fPIC -shared -o $@ $<

linker/lib/libCC$(SO_EXT): $(CC_LIBS:-l%=linker/lib/lib%$(SO_EXT))
linker/lib/libCC$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/ -Llinker/lib $(CC_LIBS)
linker/lib/libD$(SO_EXT): $(D_LIBS:-l%=linker/lib/lib%$(SO_EXT))
linker/lib/libD$(SO_EXT): LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/ -Llinker/lib $(D_LIBS)

linker/ref.out: $(LINKER_MAIN_LIBS:-l%=linker/lib/lib%$(SO_EXT))
ifeq ($(OS), darwin)
linker/ref.out: linker/dummy.c linker/Makefile
	# this test does currently not work on darwin [GR-16981]
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $<
else
linker/ref.out: LDFLAGS=$(SUITE_LDFLAGS) -rpath $(ORIGIN_TOKEN)/lib/ -Llinker/lib $(LINKER_MAIN_LIBS)

linker/ref.out: linker/main.c linker/Makefile
	@mkdir -p $(shell dirname $@)
	$(QUIETLY) $(CLANG) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $<
endif

%.bc: %.out
	cp $< $@
