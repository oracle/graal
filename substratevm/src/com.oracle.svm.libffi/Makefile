#
# Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

CFLAGS=-g -fPIC -DPIC
ifeq (${OS},solaris)
CFLAGS+=-m64
endif

TARGET=${OS}-${ARCH}

DEPLOYED_HEADERS=ffi.h ffitarget.h svm_libffi.h trufflenfi.h

HEADERFILES=${DEPLOYED_HEADERS:%.h=${TARGET}/include/%.h}

ifneq (${OS},windows)

default: ${TARGET}/libffi.a ${HEADERFILES}

${TARGET}/libffi.a: libffi-build/.libs/libffi.a bin/svm_initialize.o
	$(QUIETLY) echo AR libffi.a
	$(QUIETLY) mkdir -p ${TARGET}
	$(QUIETLY) cp libffi-build/.libs/libffi.a $@
	$(QUIETLY) ar r $@ bin/svm_initialize.o

bin/svm_initialize.o: svm_initialize.c ${HEADERFILES}
	$(QUIETLY) echo CC svm_initialize.c
	$(QUIETLY) mkdir -p bin
	$(QUIETLY) $(CC) -c -I${TARGET}/include ${CFLAGS} $< -o $@

${TARGET}/include/trufflenfi.h: ${TRUFFLE_NFI}/include/trufflenfi.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/svm_libffi.h: include/svm_libffi.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/%.h: libffi-build/include/%.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

libffi-build/include/ffi.h libffi-build/include/ffitarget.h: libffi-build/.libs/libffi.a

libffi-build/.libs/libffi.a: libffi-build/Makefile
	$(QUIETLY) echo MAKE svm-libffi
	$(QUIETLY) $(MAKE) -C libffi-build > libffi.build.log

# on the Mac /bin/sh is bash which will produce output if CDPATH is set which causes
# this command to fail, so clear it out before running the command.
libffi-build/Makefile: libffi-3.2.1/configure
	$(QUIETLY) echo CONFIGURE svm-libffi
	$(QUIETLY) mkdir -p libffi-build
	$(QUIETLY) unset CDPATH && cd libffi-build && CFLAGS="${CFLAGS}" ../libffi-3.2.1/configure --enable-static >../libffi.configure.log

libffi-3.2.1/configure: ${LIBFFI_SRC}
	$(QUIETLY) tar xzf ${LIBFFI_SRC}
	$(QUIETLY) touch $@

else

all: ${TARGET} ${HEADERFILES}

${TARGET}:
	echo "Building libffi on Windows is not currently supported"
	$(shell mkdir -p ${TARGET})
	$(shell touch ${TARGET}/libffi.a)

${TARGET}/include/trufflenfi.h: ${TRUFFLE_NFI}/include/trufflenfi.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/svm_libffi.h: include/svm_libffi.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/ffi.h ${TARGET}/include/ffitarget.h: 
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) touch $@

endif
