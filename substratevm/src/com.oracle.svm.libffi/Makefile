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

TARGET=${OS}-${ARCH}/..

DEPLOYED_HEADERS=ffi.h ffitarget.h svm_libffi.h trufflenfi.h

HEADERFILES=${DEPLOYED_HEADERS:%.h=${TARGET}/include/%.h}

ifneq (${OS},windows)

default: ${TARGET}/libffi.a ${HEADERFILES}

${TARGET}/libffi.a: ${LIBFFI_DIST}/libffi.a
	$(QUIETLY) mkdir -p ${TARGET}
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/trufflenfi.h: ${TRUFFLE_NFI}/include/trufflenfi.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/svm_libffi.h: include/svm_libffi.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
	$(QUIETLY) touch $@

${TARGET}/include/%.h: ${LIBFFI_DIST}/include/%.h
	$(QUIETLY) mkdir -p ${TARGET}/include
	$(QUIETLY) cp $< $@
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
