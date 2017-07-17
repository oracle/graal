#
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# 'make MX_VERBOSE=y' will report all lines executed.  The actual value doesn't
# matter as long as it's not empty.
QUIETLY$(MX_VERBOSE)  = @

.PHONY: default

NATIVE_CLASSES=com.oracle.truffle.nfi.ClosureNativePointer \
	       com.oracle.truffle.nfi.NativeAllocation \
	       com.oracle.truffle.nfi.NativeString \
	       com.oracle.truffle.nfi.NFIContext

OBJECTS=api.o \
	closure.o \
	jni.o \
	lookup.o \
	signature.o

default: bin/${LIBTRUFFLENFI}

src_gen/native.h: Makefile
	$(QUIETLY) echo JAVAH $@
	$(QUIETLY) rm -f $@
	$(QUIETLY) ${JAVA_HOME}/bin/javah -classpath ${MX_CLASSPATH} -o $@ ${NATIVE_CLASSES}

bin/src-gen-deps: Makefile
	$(QUIETLY) rm -f $@
	$(QUIETLY) mkdir -p bin/
	$(QUIETLY) ${JAVA_HOME}/bin/javap -sysinfo -classpath ${MX_CLASSPATH} ${NATIVE_CLASSES} | grep "Classfile " | sed -e 's|Classfile |src_gen/native.h: |' >> $@

OBJECTFILES=${OBJECTS:%.o=bin/%.o}
CPPFLAGS=-I${VPATH}/include -Isrc_gen -I${JAVA_HOME}/include -I${JAVA_HOME}/include/${OS} -Ilibffi-build/include
CFLAGS=-g -fPIC
LDFLAGS=-g -ldl
LIBFFI_LIB=libffi-build/.libs/libffi.a

ifeq (${OS},solaris)
CFLAGS+=-m64
LDFLAGS+=-m64
endif


bin/${LIBTRUFFLENFI}: ${OBJECTFILES} ${LIBFFI_LIB}
	$(QUIETLY) echo LINK $@
	$(QUIETLY) gcc -shared ${LDFLAGS} $+ -o $@

bin/%.d: src/%.c src_gen/native.h libffi-build/include/ffi.h
	$(QUIETLY) gcc -MM ${CPPFLAGS} $< | sed 's|\($*\)\.o[ :]*|bin/\1.o $@ : |g' > $@

bin/%.o: src/%.c
	$(QUIETLY) echo CC $@
	$(QUIETLY) gcc -c -Wall -Werror ${CFLAGS} ${CPPFLAGS} -o $@ $<

${LIBFFI_LIB}: libffi-build/Makefile
	$(QUIETLY) echo MAKE libffi
	$(QUIETLY) $(MAKE) -C libffi-build > libffi.build.log

# on the Mac /bin/sh is bash which will produce output if CDPATH is set which causes
# this command to fail, so clear it out before running the command.
libffi-build/Makefile libffi-build/include/ffi.h: libffi-3.2.1/configure
	$(QUIETLY) echo CONFIGURE libffi
	$(QUIETLY) mkdir -p libffi-build
	$(QUIETLY) unset CDPATH && cd libffi-build && CFLAGS="${CFLAGS}" ../libffi-3.2.1/configure --enable-static > ../libffi.configure.log

libffi-3.2.1/configure: ${LIBFFI_SRC}
	$(QUIETLY) tar xzf ${LIBFFI_SRC}
	$(QUIETLY) touch $@

-include ${OBJECTS:%.o=bin/%.d}
-include bin/src-gen-deps
