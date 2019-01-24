#
# Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

# 'make MX_VERBOSE=y' will report all lines executed.  The actual value doesn't
# matter as long as it's not empty.
QUIETLY$(MX_VERBOSE)  = @

.PHONY: default

OBJECTS=api.o \
	closure.o \
	intrinsics.o \
	jni.o \
	lookup.o \
	signature.o

ifneq (${OS},windows)

default: bin/${LIBTRUFFLENFI}

OBJECTFILES=${OBJECTS:%.o=bin/%.o}
CPPFLAGS+=-I${VPATH}/include "-I${JAVA_HOME}/include" "-I${JAVA_HOME}/include/${OS}" -I${LIBFFI_DIST}/include -DOS_${OS}
CFLAGS=-g -fPIC
LDFLAGS=-g -ldl
LIBFFI_LIB=${LIBFFI_DIST}/libffi.a

ifeq (${OS},solaris)
CFLAGS+=-m64 -pthread
LDFLAGS+=-m64 -pthread
endif

DEPFLAGS = -MT $@ -MMD -MP -MF bin/$*.Td
$(shell mkdir -p bin/ >/dev/null)

bin/${LIBTRUFFLENFI}: ${OBJECTFILES} ${LIBFFI_LIB}
	@echo LINK $@
	$(QUIETLY) gcc -shared ${LDFLAGS} $+ -o $@

bin/%.o: src/%.c bin/%.d ${LIBFFI_DIST}/include/ffi.h
	@echo CC $@
	$(QUIETLY) gcc -c -Wall -Werror ${CFLAGS} ${CPPFLAGS} ${DEPFLAGS} -o $@ $<
	$(QUIETLY) mv -f bin/$*.Td bin/$*.d && touch $@

bin/%.d: ;
.PRECIOUS: bin/%.d

-include ${OBJECTS:%.o=bin/%.d}

else
all:
	echo "Building libffi on Windows is not currently supported"
	$(shell mkdir -p bin/ >/dev/null)
	$(shell touch bin/${LIBTRUFFLENFI} >/dev/null)
endif
