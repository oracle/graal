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
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * A node in the lookup graph. We abstract from modules and classes because with includes and
 * singletons and so on there are more nodes in the graph than there are classes and modules.
 */
public interface LookupNode {

    boolean setClassVariableIfAlreadySet(String variableName, Object value);

    Object lookupConstant(String constantName);

    Object lookupClassVariable(String variableName);

    RubyMethod lookupMethod(String methodName);

    Assumption getUnmodifiedAssumption();

    Set<String> getClassVariables();

    void getMethods(Map<String, RubyMethod> methods);

}
