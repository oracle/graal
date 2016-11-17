.PHONY: default

TARGET=libpipe.so

NATIVE_CLASSES=com.oracle.truffle.llvm.pipe.CaptureOutput

OBJECTS=capture.o

default: bin/${TARGET}

src_gen/native.h: Makefile
	@echo JAVAH $@
	@rm -f $@
	@${JAVA_HOME}/bin/javah -classpath ${MX_CLASSPATH} -o $@ ${NATIVE_CLASSES}

bin/src-gen-deps: Makefile
	@rm -f $@
	@mkdir -p bin/
	@${JAVA_HOME}/bin/javap -sysinfo -classpath ${MX_CLASSPATH} ${NATIVE_CLASSES} | grep "Classfile " | sed -e 's|Classfile |src_gen/native.h: |' >> $@

OBJECTFILES=${OBJECTS:%.o=bin/%.o}
OS := $(shell uname)
ifeq ($(OS),Darwin)
CFLAGS=-g -fPIC -I${VPATH}/include -Isrc_gen -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin $(shell pkg-config)
else
CFLAGS=-g -fPIC -I${VPATH}/include -Isrc_gen -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux $(shell pkg-config)
endif
LIBS=-g $(shell pkg-config)

bin/${TARGET}: ${OBJECTFILES}
	@echo LINK $@
	@$(CXX) -std=gnu++0x -shared ${LIBS} $+ -o $@

bin/%.d: src/%.cpp src_gen/native.h
	@$(CXX) -MM ${CFLAGS} $< | sed 's|\($*\)\.o[ :]*|bin/\1.o $@ : |g' > $@

bin/%.o: src/%.cpp
	@echo CXX $@
	@$(CXX) -c ${CFLAGS} -o $@ $<

-include ${OBJECTS:%.o=bin/%.d}
-include bin/src-gen-deps
