package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.vm.ci.meta.ResolvedJavaType;

public interface AccessPathBase {

    ResolvedJavaType type();

    AccessPathBase addPrefix(String prefix);

    AccessPathBase removePrefix(String regex);
}
