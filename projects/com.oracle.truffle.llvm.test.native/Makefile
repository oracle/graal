.PHONY: default

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)

SOURCES=${SOURCE_FILES:${VPATH}/%=%}

TARGETS=${SOURCES:src/%.c=bin/%.so}
default: ${TARGETS}

bin/%.so: src/%.c
	@mkdir -p bin
	clang -shared -fPIC -o $@ $+
