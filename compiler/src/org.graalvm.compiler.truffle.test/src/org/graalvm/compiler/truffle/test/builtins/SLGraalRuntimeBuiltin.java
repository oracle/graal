/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.truffle.test.builtins;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.builtins.SLBuiltinNode;

public abstract class SLGraalRuntimeBuiltin extends SLBuiltinNode {

    protected SLGraalRuntimeBuiltin() {
        if (!(Truffle.getRuntime() instanceof GraalTruffleRuntime)) {
            throw new AssertionError("Graal runtime builtins can only be used inside of a Graal runtime.");
        }
    }

    /**
     * Finds all {@link DirectCallNode} instances calling a certain original {@link CallTarget} in
     * the caller function.
     */
    @TruffleBoundary
    protected static final Set<DirectCallNode> findCallsTo(OptimizedCallTarget originalCallTarget) {
        FrameInstance frame = Truffle.getRuntime().getCallerFrame();
        RootNode root = frame.getCallNode().getRootNode();
        return findCallsTo(root, originalCallTarget);
    }

    /**
     * Finds all {@link DirectCallNode} instances calling a certain original {@link CallTarget} in a
     * given {@link RootNode}.
     */
    @TruffleBoundary
    protected static final Set<DirectCallNode> findCallsTo(RootNode root, OptimizedCallTarget originalCallTarget) {
        final Set<DirectCallNode> allCallNodes = new HashSet<>();
        root.accept(new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (node instanceof DirectCallNode) {
                    DirectCallNode callNode = (DirectCallNode) node;
                    if (callNode.getCallTarget() == originalCallTarget || callNode.getClonedCallTarget() == originalCallTarget) {
                        allCallNodes.add(callNode);
                    }
                }
                return true;
            }
        });
        return allCallNodes;
    }
}
