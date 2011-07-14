#!/bin/bash
if [ -z "${JDK7}" ]; then
  echo "JDK7 is not defined."
  exit 1;
fi
if [ -z "${MAXINE}" ]; then
  echo "MAXINE is not defined. It must point to a maxine repository directory."
  exit 1;
fi
if [ -z "${GRAAL}" ]; then
  echo "GRAAL is not defined. It must point to a maxine repository directory."
  exit 1;
fi
if [ -z "${DACAPO}" ]; then
  echo "DACAPO is not defined. It must point to a Dacapo benchmark directory."
  exit 1;
fi
TEST=$1
shift
ant -f create_examples.xml
COMMAND="${JDK7G}/bin/java -d64 -Xmx1g -esa -XX:+PrintCompilation -XX:PrintIdealGraphLevel=1 -Xcomp -XX:CompileOnly=examples -XX:CompileCommand=quiet -XX:CompileCommand=exclude,*,<init> -XX:CompileCommand=exclude,*,run -XX:CompileCommand=exclude,com.oracle.max.graal.examples.Main::main $* -jar examples.jar ${TEST}"
# echo $COMMAND
$COMMAND
