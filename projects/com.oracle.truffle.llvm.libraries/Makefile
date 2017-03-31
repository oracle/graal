.PHONY: default

UNAME := $(shell uname)

ifdef MX_OPT_V32

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)
SOURCES=${SOURCE_FILES:${VPATH}/%=%}
OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -instcombine -dse -loop-simplify -reassociate -licm -gvn


TARGETS=${SOURCES:src/%.c=bin/v32/%.bc}
default: ${TARGETS}

bin/v32/%.bc: bin/v32/%.noopt.bc
	${MX_OPT_V32} -o $@ $+ ${OPT_FLAGS}

bin/v32/%.noopt.bc: src/%.c
	@mkdir -p bin/v32
	${MX_CLANG_V32} -c -emit-llvm -o $@ $+

ifndef MX_OPT_V38
# build Sulong's mock EH library:
# Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.

SOURCE_FILES=$(wildcard ${VPATH}/lib/sulongehNotAvailable.cpp)
SOURCES=${SOURCE_FILES:${VPATH}/%=%}

ifeq ($(UNAME), Darwin)
TARGETS=${SOURCES:lib/sulongehNotAvailable.cpp=native/libsulongeh.dylib}
default: ${TARGETS}

native/libsulongeh.dylib: lib/sulongehNotAvailable.cpp
	@mkdir -p native
	clang++ -dynamiclib -O0 -o $@ $+
else
TARGETS=${SOURCES:lib/sulongehNotAvailable.cpp=native/libsulongeh.so}
default: ${TARGETS}

native/libsulongeh.so: lib/sulongehNotAvailable.cpp
	@mkdir -p native
	clang++ -shared -g -O0 -o $@ -fPIC $+
endif

endif
endif

ifdef MX_OPT_V38

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)
SOURCES=${SOURCE_FILES:${VPATH}/%=%}
OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -instcombine -dse -loop-simplify -reassociate -licm -gvn


TARGETS=${SOURCES:src/%.c=bin/v38/%.bc}
default: ${TARGETS}

bin/v38/%.bc: bin/v38/%.noopt.bc
	${MX_OPT_V38} -o $@ $+ ${OPT_FLAGS}

bin/v38/%.noopt.bc: src/%.c
	@mkdir -p bin/v38
	${MX_CLANG_V38} -c -emit-llvm -o $@ $+

# build Sulong's EH library:
# the implementation of this library requires llvm v3.8

SOURCE_FILES=$(wildcard ${VPATH}/lib/sulongeh.cpp)
SOURCES=${SOURCE_FILES:${VPATH}/%=%}

ifeq ($(UNAME), Darwin)
TARGETS=${SOURCES:lib/sulongeh.cpp=native/libsulongeh.dylib}
default: ${TARGETS}

native/libsulongeh.dylib: lib/sulongeh.cpp
	@mkdir -p native
	clang++ -dynamiclib -O0 -stdlib=libc++ -std=c++11 -lc++abi -I/usr/include/libcxxabi -o $@ $+
else
TARGETS=${SOURCES:lib/sulongeh.cpp=native/libsulongeh.so}
default: ${TARGETS}

native/libsulongeh.so: lib/sulongeh.cpp
	@mkdir -p native
	clang++ -shared -g -O0 -stdlib=libc++ -std=c++11 -lc++abi -I/usr/include/libcxxabi -o $@ -fPIC $+
endif


endif
