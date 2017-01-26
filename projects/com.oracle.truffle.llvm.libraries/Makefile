.PHONY: default

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)

SOURCES=${SOURCE_FILES:${VPATH}/%=%}


OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -instcombine -dse -loop-simplify -reassociate -licm -gvn

ifdef MX_OPT_V32

TARGETS=${SOURCES:src/%.c=bin/v32/%.bc}
default: ${TARGETS}

bin/v32/%.bc: bin/v32/%.noopt.bc
	${MX_OPT_V32} -o $@ $+ ${OPT_FLAGS}

bin/v32/%.noopt.bc: src/%.c
	@mkdir -p bin/v32
	${MX_CLANG_V32} -c -emit-llvm -o $@ $+

endif

ifdef MX_OPT_V38

TARGETS=${SOURCES:src/%.c=bin/v38/%.bc}
default: ${TARGETS}

bin/v38/%.bc: bin/v38/%.noopt.bc
	${MX_OPT_V38} -o $@ $+ ${OPT_FLAGS}

bin/v38/%.noopt.bc: src/%.c
	@mkdir -p bin/v38
	${MX_CLANG_V38} -c -emit-llvm -o $@ $+

endif
