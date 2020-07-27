#
# Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
.PHONY: default

OBJECTS=capture.o

default: bin/${LIBPIPE}

OBJECTFILES=${OBJECTS:%.o=bin/%.o}
CPPFLAGS+=-I${VPATH}/include -Isrc_gen "-I${JAVA_HOME}/include" "-I${JAVA_HOME}/include/${OS}"
CFLAGS=-g -fPIC
LDFLAGS=-g

ifeq (${OS},solaris)
CFLAGS+=-m64
LDFLAGS+=-m64
endif

ifeq ($(OS), darwin)
CXX_PREFIX=/usr/bin/xcrun
else
CXX_PREFIX=
endif

bin/${LIBPIPE}: ${OBJECTFILES}
	@echo LINK $@
	@$(CXX_PREFIX) $(CXX) -std=gnu++0x -shared ${LDFLAGS} $+ -o $@

bin/%.d: src/%.cpp
	@mkdir -p $(@D)
	@$(CXX_PREFIX) $(CXX) -MM ${CPPFLAGS} $< | sed 's|\($*\)\.o[ :]*|bin/\1.o $@ : |g' > $@

bin/%.o: src/%.cpp
	@echo CXX $@
	@$(CXX_PREFIX) $(CXX) -c ${CFLAGS} ${CPPFLAGS} -o $@ $<

-include ${OBJECTS:%.o=bin/%.d}
