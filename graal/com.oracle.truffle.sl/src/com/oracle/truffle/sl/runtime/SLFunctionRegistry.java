/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.sl.runtime;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * Manages the mapping from function names to {@link SLFunction function objects}.
 */
public final class SLFunctionRegistry {

    private final Map<String, SLFunction> functions = new HashMap<>();

    /**
     * Returns the canonical {@link SLFunction} object for the given name. If it does not exist yet,
     * it is created.
     */
    public SLFunction lookup(String name) {
        SLFunction result = functions.get(name);
        if (result == null) {
            result = new SLFunction(name);
            functions.put(name, result);
        }
        return result;
    }

    /**
     * Associates the {@link SLFunction} with the given name with the given implementation root
     * node. If the function did not exist before, it defines the function. If the function existed
     * before, it redefines the function and the old implementation is discarded.
     */
    public void register(String name, SLRootNode rootNode) {
        SLFunction function = lookup(name);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        function.setCallTarget(callTarget);
    }

    /**
     * Returns the sorted list of all functions, for printing purposes only.
     */
    public List<SLFunction> getFunctions() {
        List<SLFunction> result = new ArrayList<>(functions.values());
        Collections.sort(result, new Comparator<SLFunction>() {
            public int compare(SLFunction f1, SLFunction f2) {
                return f1.toString().compareTo(f2.toString());
            }
        });
        return result;
    }
}
