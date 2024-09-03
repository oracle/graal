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

import static com.oracle.truffle.espresso.nodes.quick.invoke.inline.ConditionalInlinedMethodNode.getFallback;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeQuickNode;

public final class GuardedConditionalInlinedMethodNode extends InlinedMethodNode {
    private final ConditionalInlinedMethodNode.Recipes recipes;

    @Child InvokeQuickNode fallbackNode;
    private final InlinedMethodPredicate condition;
    private final InlinedMethodPredicate guard;

    public GuardedConditionalInlinedMethodNode(Method.MethodVersion inlinedMethod, int top, int opcode, int callerBCI, int statementIndex,
                    ConditionalInlinedMethodNode.Recipes recipes,
                    InlinedMethodPredicate condition, InlinedMethodPredicate guard) {
        super(inlinedMethod, top, opcode, callerBCI, statementIndex, null);
        this.fallbackNode = insert(getFallback(inlinedMethod.getMethod(), top, callerBCI, opcode));
        this.condition = condition;
        this.guard = guard;
        this.recipes = recipes;
    }

    @Override
    public int execute(VirtualFrame frame, boolean isContinuationResume) {
        preludeChecks(frame);
        if (guard.isValid(getContext(), method, frame, this)) {
            if (condition.isValid(getContext(), method, frame, this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InlinedMethodNode replacement = getDefinitiveNode(recipes, guard, inlinedMethod(), top, opcode, getCallerBCI(), statementIndex);
                return getBytecodeNode().replaceQuickAt(frame, opcode, getCallerBCI(), this,
                                replacement);
            } else {
                return fallbackNode.execute(frame, false);
            }
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return getBytecodeNode().reQuickenInvoke(frame, top, opcode, getCallerBCI(), statementIndex, method.getMethod());
        }
    }

    public static InlinedMethodNode getDefinitiveNode(ConditionalInlinedMethodNode.Recipes recipes, InlinedMethodPredicate oldGuard,
                    Method.MethodVersion method, int top, int opcode, int callerBci, int statementIndex) {
        BodyNode newBody = recipes.cookBody();
        InlinedMethodPredicate cookedGuard = recipes.cookGuard();
        InlinedMethodPredicate newGuard = cookedGuard == null
                        ? oldGuard
                        : (ctx, m, f, node) -> cookedGuard.isValid(ctx, m, f, node) && oldGuard.isValid(ctx, m, f, node);
        InlinedMethodNode replacement;
        replacement = new GuardedInlinedMethodNode(method, top, opcode, callerBci, statementIndex, newBody, newGuard);
        return replacement;
    }
}
