#! bin/bash
mx -J-Djava.library.path=/workspace/graal/vincent vm \
    -Xmx10g \
    --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
    -javaagent:../joonhwan/agent-joon.jar \
    -jar ../dacapo/dacapo-9.12-bach.jar sunflow