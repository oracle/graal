/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke.inline.bodies;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.GuardedInlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedFrameAccess;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;

public final class InlinedSubstitutionBodyNode extends InlinedMethodNode.BodyNode {
    @Child JavaSubstitution substitution;

    InlinedSubstitutionBodyNode(Method.MethodVersion m, JavaSubstitution substitution) {
        super(m);
        this.substitution = insert(substitution);
    }

    public static InlinedMethodNode create(Method inlinedMethod, int top, int opcode, int callerBCI, int statementIndex, JavaSubstitution.Factory factory) {
        Method.MethodVersion methodVersion = inlinedMethod.getMethodVersion();
        InlinedSubstitutionBodyNode bodyNode = new InlinedSubstitutionBodyNode(methodVersion, factory.create());
        if (factory.guard() != null) {
            return new GuardedInlinedMethodNode(methodVersion, top, opcode, callerBCI, statementIndex, bodyNode, factory.guard());
        } else {
            return new InlinedMethodNode(methodVersion, top, opcode, callerBCI, statementIndex, bodyNode);
        }
    }

    @Override
    public void execute(VirtualFrame frame, InlinedFrameAccess frameAccess) {
        substitution.invokeInlined(frame, frameAccess.top(), frameAccess);
    }
}
