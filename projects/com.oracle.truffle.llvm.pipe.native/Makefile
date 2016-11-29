.PHONY: default

NATIVE_CLASSES=com.oracle.truffle.llvm.pipe.CaptureOutput

OBJECTS=capture.o

default: bin/${LIBPIPE}

src_gen/native.h: Makefile
	@echo JAVAH $@
	@rm -f $@
	@${JAVA_HOME}/bin/javah -classpath ${MX_CLASSPATH} -o $@ ${NATIVE_CLASSES}

bin/src-gen-deps: Makefile
	@rm -f $@
	@mkdir -p bin/
	@${JAVA_HOME}/bin/javap -sysinfo -classpath ${MX_CLASSPATH} ${NATIVE_CLASSES} | grep "Classfile " | sed -e 's|Classfile |src_gen/native.h: |' >> $@

OBJECTFILES=${OBJECTS:%.o=bin/%.o}
CPPFLAGS=-I${VPATH}/include -Isrc_gen -I${JAVA_HOME}/include -I${JAVA_HOME}/include/${OS}
CFLAGS=-g -fPIC
LDFLAGS=-g

ifeq (${OS},solaris)
CFLAGS+=-m64
LDFLAGS+=-m64
endif

bin/${LIBPIPE}: ${OBJECTFILES}
	@echo LINK $@
	@$(CXX) -std=gnu++0x -shared ${LDFLAGS} $+ -o $@

bin/%.d: src/%.cpp src_gen/native.h
	@$(CXX) -MM ${CPPFLAGS} $< | sed 's|\($*\)\.o[ :]*|bin/\1.o $@ : |g' > $@

bin/%.o: src/%.cpp
	@echo CXX $@
	@$(CXX) -c ${CFLAGS} ${CPPFLAGS} -o $@ $<

-include ${OBJECTS:%.o=bin/%.d}
-include bin/src-gen-deps
