.PHONY: default

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)

SOURCES=${SOURCE_FILES:${VPATH}/%=%}
TARGETS=${SOURCES:src/%.c=bin/%.bc}

OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -instcombine -dse -loop-simplify -reassociate -licm -gvn

default: ${TARGETS}

bin/%.bc: bin/%.noopt.bc
	${MX_OPT} -o $@ $+ ${OPT_FLAGS}

bin/%.noopt.bc: src/%.c
	@mkdir -p bin
	${MX_CLANG} -c -emit-llvm -o $@ $+
