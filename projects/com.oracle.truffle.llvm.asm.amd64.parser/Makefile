.PHONY: default clean

PARSER_PATH=../com.oracle.truffle.llvm.asm.amd64/src

PACKAGE_NAME=com.oracle.truffle.llvm.asm.amd64

GENERATED_PACKAGE_PATH=${PARSER_PATH}/$(subst .,/,${PACKAGE_NAME})

TARGETS=${GENERATED_PACKAGE_PATH}/Parser.java \
        ${GENERATED_PACKAGE_PATH}/Scanner.java

ATG=${PARSER_PATH}/InlineAssembly.atg

SOURCES=${ATG} \
	${PARSER_PATH}/Parser.frame \
	${PARSER_PATH}/Scanner.frame \
	${PARSER_PATH}/Copyright.frame

default: ${TARGETS}

clean:
	rm -f ${TARGETS}

${TARGETS}: ${SOURCES}
	${JAVA_HOME}/bin/java -jar ${COCO_JAR} -package ${PACKAGE_NAME} -o ${GENERATED_PACKAGE_PATH} ${ATG}
