package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface AccessPathBase {

    ResolvedJavaType type();

    NodeSourcePosition getByteCodePosition();

    AccessPathBase addPrefix(String prefix);

    AccessPathBase removePrefix(String regex);
}
