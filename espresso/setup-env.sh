#!/usr/bin/env bash

export JAVA_HOME=/Users/mchearn/.mx/jdks/labsjdk-ce-21-jvmci-23.1-b26/Contents/Home
export LLVM_JAVA_HOME=$HOME/Projects/graal/espresso/downloads/labsjdk-ce-21.0.1-jvmci-23.1-b22-sulong/Contents/Home
alias MX="mx --env jvm-llvm"
export CLASSPATH=$HOME/Projects/hello-maestro/out/production/hello-maestro/
alias r="MX build && echo -e '\n\n------\n\n' && MX espresso --vm.ea --log.file=/tmp/truffle-log -ea Main"
alias d="MX build && echo -e '\n\n------\n\n' && MX espresso '--vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000' --vm.ea --log.file=/tmp/truffle-log Main"
arch -x86_64 $SHELL
