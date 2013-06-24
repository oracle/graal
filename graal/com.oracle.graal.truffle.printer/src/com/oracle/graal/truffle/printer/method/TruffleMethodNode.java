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

public final class TruffleMethodNode {

    private final TruffleMethodNode parent;
    private final MethodHolder truffleExecuteMethod;
    private final Map<Integer, List<TruffleMethodNode>> inlinings;

    public TruffleMethodNode(TruffleMethodNode parent, MethodHolder truffleExecuteMethod) {
        this.parent = parent;
        this.truffleExecuteMethod = truffleExecuteMethod;
        this.inlinings = new HashMap<>();
    }

    public TruffleMethodNode getParent() {
        return parent;
    }

    public ResolvedJavaMethod getJavaMethod() {
        return truffleExecuteMethod.getMethod();
    }

    public Map<Integer, List<TruffleMethodNode>> getInlinings() {
        return inlinings;
    }

    public void putInlineList(int lineOfInvoke, List<TruffleMethodNode> list) {
        inlinings.put(lineOfInvoke, list);
    }

    public List<TruffleMethodNode> getInliningsAtLine(int line) {
        return inlinings.get(line);
    }

    public MethodHolder getTruffleExecuteMethod() {
        return truffleExecuteMethod;
    }

    public TruffleMethodNode addTruffleExecuteMethodNode(MethodHolder newMethod) {
        int lineOfInvoke = newMethod.getCallStack().get(0).getLineOfInvoke();

        if (!callStackMatch(newMethod.getCallStack())) {
            return null;
        } else {
            TruffleMethodNode node = new TruffleMethodNode(this, newMethod);
            if (getInliningsAtLine(lineOfInvoke) == null) {
                List<TruffleMethodNode> list = new ArrayList<>();
                list.add(node);
                putInlineList(lineOfInvoke, list);
            } else {
                getInliningsAtLine(lineOfInvoke).add(node);
            }
            return node;
        }
    }

    private boolean callStackMatch(List<CallStackElement> callStack) {
        List<CallStackElement> curCallStack = truffleExecuteMethod.getCallStack();
        if (curCallStack.size() == callStack.size() - 1) {
            if (curCallStack.size() >= 1) {
                if (curCallStack.get(0).getCallerMethod() != callStack.get(1).getCallerMethod()) {
                    return false;
                }
            }
            for (int i = 1; i < curCallStack.size(); i++) {
                if (!curCallStack.get(i).equals(callStack.get(i + 1))) {
                    return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }

}
