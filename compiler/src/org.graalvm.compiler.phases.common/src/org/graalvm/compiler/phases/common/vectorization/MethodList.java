package org.graalvm.compiler.phases.common.vectorization;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.List;

public class MethodList {

    private final List<String> list;
    private final boolean whitelist;

    public MethodList(List<String> list, boolean whitelist) {
        this.list = list;
        this.whitelist = whitelist;
    }

    boolean shouldSkip(ResolvedJavaMethod method) {
        final String name = String.format("%s.%s", method.getDeclaringClass().toJavaName(), method.getName());

        // In first order logic:
        // !listWhitelist ->  anyMatch  <=>   listWhitelist v  anyMatch
        //  listWhitelist -> noneMatch  <=>  !listWhitelist v noneMatch
        return (whitelist || list.stream().anyMatch(name::contains)) && (!whitelist || list.stream().noneMatch(name::contains));

    }

}
