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

package com.oracle.truffle.espresso.nodes.quick.invoke.inline;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEVIRTUAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeSpecialQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeStaticQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeVirtualQuickNode;

public class ConditionalInlinedMethodNode extends InlinedMethodNode {

    public interface Recipes {
        BodyNode cookBody();

        InlinedMethodPredicate cookGuard();
    }

    private final Recipes recipes;

    @Child protected InvokeQuickNode fallbackNode;
    private final InlinedMethodPredicate condition;

    public ConditionalInlinedMethodNode(Method.MethodVersion inlinedMethod, int top, int opcode, int callerBCI, int statementIndex, Recipes recipes, InlinedMethodPredicate condition) {
        super(inlinedMethod, top, opcode, callerBCI, statementIndex, null);
        this.fallbackNode = getFallback(inlinedMethod.getMethod(), top, callerBCI, opcode);
        this.condition = condition;
        this.recipes = recipes;
    }

    @Override
    public final int execute(VirtualFrame frame, boolean isContinuationResume) {
        preludeChecks(frame);
        if (condition.isValid(getContext(), method, frame, this)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            InlinedMethodNode replacement = getDefinitiveNode(recipes, inlinedMethod(), top, opcode, getCallerBCI(), statementIndex);
            return getBytecodeNode().replaceQuickAt(frame, opcode, getCallerBCI(), this,
                            replacement);
        } else {
            return fallbackNode.execute(frame, false);
        }
    }

    public static InlinedMethodNode getDefinitiveNode(Recipes recipes,
                    Method.MethodVersion inlinedMethod, int top, int opcode, int callerBci, int statementIndex) {
        BodyNode newBody = recipes.cookBody();
        InlinedMethodPredicate guard = recipes.cookGuard();
        InlinedMethodNode replacement;
        if (guard == null) {
            replacement = new InlinedMethodNode(inlinedMethod, top, opcode, callerBci, statementIndex, newBody);
        } else {
            replacement = new GuardedInlinedMethodNode(inlinedMethod, top, opcode, callerBci, statementIndex, newBody, guard);
        }
        return replacement;
    }

    static InvokeQuickNode getFallback(Method inlinedMethod, int top, int callerBCI, int opcode) {
        // @formatter:off
        switch (opcode) {
            case INVOKESTATIC    : return new InvokeStaticQuickNode(inlinedMethod, top, callerBCI);
            case INVOKESPECIAL   : return new InvokeSpecialQuickNode(inlinedMethod, top, callerBCI);
            case INVOKEVIRTUAL   : return new InvokeVirtualQuickNode(inlinedMethod, top, callerBCI);
            case INVOKEINTERFACE : // fallback.
            default              :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.unimplemented("Conditional bytecode-level inlining only available for invokestatic, invokespecial and invokevirtual");
        }
        // @formatter:on
    }
}
