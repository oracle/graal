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

bin/${LIBPIPE}: ${OBJECTFILES}
	@echo LINK $@
	@$(CXX) -std=gnu++0x -shared ${LDFLAGS} $+ -o $@

bin/%.d: src/%.cpp
	@mkdir -p $(@D)
	@$(CXX) -MM ${CPPFLAGS} $< | sed 's|\($*\)\.o[ :]*|bin/\1.o $@ : |g' > $@

bin/%.o: src/%.cpp
	@echo CXX $@
	@$(CXX) -c ${CFLAGS} ${CPPFLAGS} -o $@ $<

-include ${OBJECTS:%.o=bin/%.d}
