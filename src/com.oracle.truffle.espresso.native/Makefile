#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
.PHONY: default

default: ${TARGET}

OBJECTS=bin/nespresso.o

CPPFLAGS+="-I${JAVA_HOME}/include" "-I${JAVA_HOME}/include/${OS}" -Ilibffi-build/include -DOS_${OS}
CFLAGS+=-g -fPIC -Wall -Werror
LDFLAGS+=-g -lm -ldl
LIBFFI_LIB=libffi-build/.libs/libffi.a

ifeq (${OS},darwin)
LDFLAGS+=-undefined dynamic_lookup
endif

ifeq (${OS},solaris)
CFLAGS+=-m64
LDFLAGS+=-m64
endif

ifneq (${OS},windows)

DEPFLAGS = -MT $@ -MMD -MP -MF bin/$*.Td
POSTCOMPILE = @mv -f bin/$*.Td bin/$*.d && touch $@
$(shell mkdir -p bin/ >/dev/null)

bin/${LIBTRUFFLENFI}: ${OBJECTFILES} ${LIBFFI_LIB}
	@echo LINK $@
	$(QUIETLY) gcc -shared ${LDFLAGS} $+ -o $@
	
bin/%.o: src/%.c bin/%.d libffi-build/include/ffi.h
	@echo CC $@
	$(QUIETLY) gcc -c -Wall -Werror ${CFLAGS} ${CPPFLAGS} ${DEPFLAGS} -o $@ $<
	$(QUIETLY) mv -f bin/$*.Td bin/$*.d && touch $@
	
bin/%.o: src/%.cc bin/%.d libffi-build/include/ffi.h
	@echo CC $@
	$(QUIETLY) g++ -c -Wall -Werror ${CFLAGS} ${CPPFLAGS} ${DEPFLAGS} -o $@ $<
	$(QUIETLY) mv -f bin/$*.Td bin/$*.d && touch $@	

${LIBFFI_LIB}: libffi-build/Makefile
	$(QUIETLY) echo MAKE libffi
	$(QUIETLY) $(MAKE) -C libffi-build > libffi.build.log
	
# on the Mac /bin/sh is bash which will produce output if CDPATH is set which causes
# this command to fail, so clear it out before running the command.
libffi-build/Makefile: libffi-3.2.1/configure
	$(QUIETLY) echo CONFIGURE libffi
	$(QUIETLY) mkdir -p libffi-build
	$(QUIETLY) unset CDPATH CPPFLAGS && cd libffi-build && CFLAGS="${CFLAGS}" ../libffi-3.2.1/configure --enable-static > ../libffi.configure.log

libffi-build/include/ffi.h: libffi-build/Makefile

libffi-3.2.1/configure: ${LIBFFI_SRC}
	$(QUIETLY) tar xzf ${LIBFFI_SRC}
	$(QUIETLY) touch $@

${TARGET}: ${OBJECTS}
	@echo LINK $@
	@g++ -shared ${LDFLAGS} $+ -o $@

bin/%.o: src/%.c bin/%.d
	@echo CC $@
	@gcc -c ${CFLAGS} ${CPPFLAGS} ${DEPFLAGS} -o $@ $<
	${POSTCOMPILE}

bin/%.o: src/%.cc bin/%.d
	@echo CXX $@
	@g++ -c ${CFLAGS} ${CPPFLAGS} ${DEPFLAGS} -o $@ $<
	${POSTCOMPILE}

bin/%.d: ;
.PRECIOUS: bin/%.d

-include ${OBJECTS:%.o=%.d}

else
all:
	echo "Building libffi on Windows is not currently supported"
	$(shell mkdir -p bin/ >/dev/null)
	$(shell touch bin/${LIBTRUFFLENFI} >/dev/null)

${TARGET}:
	$(shell mkdir -p bin/ >/dev/null)
	touch ${TARGET}

endif
