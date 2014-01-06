/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.lookup;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * A fork in the lookup graph. Look at first and then look at second.
 */
public class LookupFork implements LookupNode {

    private LookupNode first;
    private LookupNode second;

    public LookupFork(LookupNode first, LookupNode second) {
        this.first = first;
        this.second = second;
    }

    public boolean setClassVariableIfAlreadySet(String variableName, Object value) {
        if (first.setClassVariableIfAlreadySet(variableName, value)) {
            return true;
        }

        return second.setClassVariableIfAlreadySet(variableName, value);
    }

    @Override
    public Object lookupConstant(String constantName) {
        final Object firstResult = first.lookupConstant(constantName);

        if (firstResult != null) {
            return firstResult;
        }

        return second.lookupConstant(constantName);
    }

    @Override
    public Object lookupClassVariable(String classVariable) {
        final Object firstResult = first.lookupClassVariable(classVariable);

        if (firstResult != null) {
            return firstResult;
        }

        return second.lookupClassVariable(classVariable);
    }

    @Override
    public RubyMethod lookupMethod(String methodName) {
        final RubyMethod firstResult = first.lookupMethod(methodName);

        if (firstResult != null) {
            return firstResult;
        }

        return second.lookupMethod(methodName);
    }

    @Override
    public Assumption getUnmodifiedAssumption() {
        return new UnionAssumption(first.getUnmodifiedAssumption(), second.getUnmodifiedAssumption());
    }

    public LookupNode getFirst() {
        return first;
    }

    public LookupNode getSecond() {
        return second;
    }

    public Set<String> getClassVariables() {
        final Set<String> classVariables = new HashSet<>();
        classVariables.addAll(first.getClassVariables());
        classVariables.addAll(second.getClassVariables());
        return classVariables;
    }

    public void getMethods(Map<String, RubyMethod> methods) {
        second.getMethods(methods);
        first.getMethods(methods);
    }

}
