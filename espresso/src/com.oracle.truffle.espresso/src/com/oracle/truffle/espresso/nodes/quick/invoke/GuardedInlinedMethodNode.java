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

package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class GuardedInlinedMethodNode extends InlinedMethodNode {
    @Child private InlinedMethodNode inlinedMethodNode;

    private final InlinedMethodGuard guard;

    @Override
    protected void invoke(VirtualFrame frame) {
        throw EspressoError.shouldNotReachHere("invoke method of guarding node should never be directly called.");
    }

    public interface InlinedMethodGuard {
        InlinedMethodGuard ALWAYS_VALID = new InlinedMethodGuard() {
            @Override
            public boolean isValid(EspressoContext context, Method.MethodVersion version, VirtualFrame frame, GuardedInlinedMethodNode node) {
                return true;
            }
        };

        InlinedMethodGuard LEAF_ASSUMPTION_CHECK = new InlinedMethodGuard() {
            @Override
            public boolean isValid(EspressoContext context, Method.MethodVersion version, VirtualFrame frame, GuardedInlinedMethodNode node) {
                return context.getClassHierarchyOracle().isLeafMethod(version).isValid();
            }
        };

        boolean isValid(EspressoContext context, Method.MethodVersion version, VirtualFrame frame, GuardedInlinedMethodNode node);
    }

    public GuardedInlinedMethodNode(InlinedMethodNode guardedNode, InlinedMethodGuard guard) {
        super(guardedNode.method.getMethod(), guardedNode.getTop(), guardedNode.opcode, guardedNode.getCallerBCI(), guardedNode.statementIndex);
        this.inlinedMethodNode = insert(guardedNode);
        this.guard = guard;
    }

    @Override
    public int execute(VirtualFrame frame) {
        if (guard.isValid(getContext(), method, frame, this)) {
            return inlinedMethodNode.execute(frame);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return getBytecodeNode().reQuickenInvoke(frame, top, opcode, getCallerBCI(), statementIndex, method.getMethod());
        }
    }
}
