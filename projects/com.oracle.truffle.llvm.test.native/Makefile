.PHONY: default

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)

SOURCES=${SOURCE_FILES:${VPATH}/%=%}
OBJECTS=${SOURCES:src/%.c=bin/%.o}

default: bin/$(LIBSULONGTEST)

bin/$(LIBSULONGTEST): $(OBJECTS)
	clang -shared -fPIC -o $@ $+

bin/%.o: src/%.c
	@mkdir -p bin
	clang -c -fPIC -o $@ $+
