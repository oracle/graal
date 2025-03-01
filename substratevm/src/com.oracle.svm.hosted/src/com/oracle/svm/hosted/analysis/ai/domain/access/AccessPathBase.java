package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface AccessPathBase {

    ResolvedJavaType type();

    BytecodePosition getByteCodePosition();

    AccessPathBase addPrefix(String prefix);

    AccessPathBase removePrefix(String regex);
}
