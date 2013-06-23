/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.printer.method;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

public final class MethodHolder {

    private final List<CallStackElement> callStack;
    private final ResolvedJavaMethod method;

    public static MethodHolder getNewTruffleExecuteMethod(MethodCallTargetNode targetNode) {
        return new MethodHolder(getCallStack(targetNode), targetNode.targetMethod());
    }

    private MethodHolder(List<CallStackElement> callStack, ResolvedJavaMethod callee) {
        this.callStack = callStack;
        this.method = callee;
    }

    public List<CallStackElement> getCallStack() {
        return callStack;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    private static List<CallStackElement> getCallStack(MethodCallTargetNode targetNode) {
        List<CallStackElement> callStack = new ArrayList<>();
        FrameState state = targetNode.invoke().stateAfter();
        while (state != null) {
            ResolvedJavaMethod method = state.method();
            LineNumberTable table = method.getLineNumberTable();
            int lineNr = table.getLineNumber(state.bci - 1);
            callStack.add(new CallStackElement(method, lineNr));
            state = state.outerFrameState();
        }
        return callStack;
    }
}
