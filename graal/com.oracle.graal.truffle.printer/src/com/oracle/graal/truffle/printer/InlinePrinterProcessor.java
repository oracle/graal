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
package com.oracle.graal.truffle.printer;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.truffle.printer.method.*;

public final class InlinePrinterProcessor {

    private static final String IDENT = "   ";
    private static InlinePrinterProcessor instance;

    private final List<TruffleMethodNode> inlineTree = new ArrayList<>();

    public static void initialize() {
        if (instance == null) {
            instance = new InlinePrinterProcessor();
        } else {
            throw new IllegalStateException();
        }
    }

    public static void addInlining(MethodHolder methodHolder) {
        instance.addExecuteInline(methodHolder);
    }

    public static void printTree() {
        instance.print();
    }

    public static void reset() {
        instance = null;
    }

    private void addExecuteInline(MethodHolder executeMethod) {
        if (inlineTree.isEmpty()) {
            inlineTree.add(new TruffleMethodNode(null, executeMethod));
        } else {
            TruffleMethodNode newNode = null;
            for (TruffleMethodNode node : inlineTree) {
                newNode = node.addTruffleExecuteMethodNode(executeMethod);
                if (newNode != null) {
                    break;
                }
            }
            if (newNode == null) {
                throw new AssertionError("Not able to add " + executeMethod.getMethod().toString() + " to the inlineing tree");
            }
            inlineTree.add(newNode);
        }
    }

    private TruffleMethodNode getInlineTree() {
        TruffleMethodNode root = inlineTree.get(0);
        while (root.getParent() != null) {
            root = root.getParent();
        }

        // asserting:
        for (TruffleMethodNode node : inlineTree) {
            TruffleMethodNode nodeRoot = node;
            while (nodeRoot.getParent() != null) {
                nodeRoot = nodeRoot.getParent();
            }
            if (root != nodeRoot) {
                throw new AssertionError("Different roots found");
            }
        }

        return root;
    }

    private void print() {
        String curIndent = "";
        TruffleMethodNode root = getInlineTree();
        String name = root.getJavaMethod().getDeclaringClass().getName();
        TTY.print(name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf(';')) + "::" + root.getJavaMethod().getName());
        TTY.println();
        recursivePrint(curIndent, root);
    }

    private void recursivePrint(String curIdent, TruffleMethodNode node) {
        Map<Integer, List<TruffleMethodNode>> inlinings = node.getInlinings();
        for (int l : inlinings.keySet()) {
            for (TruffleMethodNode n : inlinings.get(l)) {
                TTY.print(curIdent);
                TTY.print("L" + l + " ");
                String name = n.getJavaMethod().getDeclaringClass().getName();
                TTY.print(name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf(';')) + "::" + n.getJavaMethod().getName());
                TTY.println();
                recursivePrint(curIdent + IDENT, n);
            }
        }
    }
}
