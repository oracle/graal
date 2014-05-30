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
package com.oracle.graal.truffle.phases;

import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.inlining.*;

/**
 * Compiler phase for intrinsifying the access to the Truffle virtual frame.
 */
public class ReplaceIntrinsicsPhase extends Phase {

    private final Replacements replacements;

    public ReplaceIntrinsicsPhase(Replacements replacements) {
        this.replacements = replacements;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.class)) {
            if (methodCallTarget.isAlive()) {
                InvokeKind invokeKind = methodCallTarget.invokeKind();
                if (invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special) {
                    Class<? extends FixedWithNextNode> macroSubstitution = replacements.getMacroSubstitution(methodCallTarget.targetMethod());
                    if (macroSubstitution != null) {
                        InliningUtil.inlineMacroNode(methodCallTarget.invoke(), methodCallTarget.targetMethod(), macroSubstitution);
                        Debug.dump(graph, "After inlining %s", methodCallTarget.targetMethod().toString());
                    } else {
                        StructuredGraph inlineGraph = replacements.getMethodSubstitution(methodCallTarget.targetMethod());
                        if (inlineGraph != null) {
                            InliningUtil.inline(methodCallTarget.invoke(), inlineGraph, true);
                            Debug.dump(graph, "After inlining %s", methodCallTarget.targetMethod().toString());
                        }
                    }
                }
            }
        }
    }
}
