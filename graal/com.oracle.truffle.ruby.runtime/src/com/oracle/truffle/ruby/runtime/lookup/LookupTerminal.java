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
 * A terminal in the lookup graph.
 */
public class LookupTerminal implements LookupNode {

    public static final LookupTerminal INSTANCE = new LookupTerminal();

    public boolean setClassVariableIfAlreadySet(String variableName, Object value) {
        return false;
    }

    @Override
    public Object lookupConstant(String constantName) {
        return null;
    }

    @Override
    public Object lookupClassVariable(String constantName) {
        return null;
    }

    @Override
    public RubyMethod lookupMethod(String methodName) {
        return null;
    }

    @Override
    public Assumption getUnmodifiedAssumption() {
        return AlwaysValidAssumption.INSTANCE;
    }

    public Set<String> getClassVariables() {
        return Collections.emptySet();
    }

    public void getMethods(Map<String, RubyMethod> methods) {
    }

}
