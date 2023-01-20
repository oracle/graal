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

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.quick.BaseQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.bodies.InlinedFieldAccessNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.bodies.InlinedSubstitutionBodyNode;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.Substitutions;

/**
 * A node that performs bytecode-level inlining of a method, removing the overhead of an entire
 * invoke.
 * <p>
 * Due to skipping the callee's frame, Bytecode-level inlining can only be correct if:
 * <ul>
 * <li>No exception arises during the callee's execution.</li>
 * <li>The callee does not perform any invoke.</li>
 * <li>There is no instrumentation in place for neither the caller nor callee.</li>
 * </ul>
 * <p>
 * The body of the callee should be inspected to prevent bytecode-level inlining of a method that
 * itself invokes.
 * <p>
 * This node will rewrite itself to a generic invoke when instrumentation happens (see
 * {@link #revertToGeneric(BytecodeNode)} and
 * {@link BytecodeNode#materializeInstrumentableNodes(Set)}).
 * <p>
 * Various methods of ensuring no exception arises from the callee are available:
 * <ul>
 * <li>{@link ConditionalInlinedMethodNode}: Uses the generic version of the invoke until a given
 * condition becomes {@code true}, at which point it will rewrite itself as the inlined version. Use
 * case includes waiting for pool constants to resolve before performing bytecode-level
 * inlining.</li>
 * <li>{@link GuardedInlinedMethodNode}: Performs bytecode-level inlining, but reverts to a generic
 * invoke if the given guard fails. Use case includes guarding against a {@code finalizable}
 * receiver for the {@code j.l.Object.<init>} substitution.</li>
 * <li>{@link GuardedConditionalInlinedMethodNode}: Combines both options. Waits until a condition
 * becomes true before bytecode-level inline, but will also revert to generic if the guard
 * fails.</li>
 * </ul>
 */
public class InlinedMethodNode extends InvokeQuickNode implements InlinedFrameAccess {

    public abstract static class BodyNode extends EspressoNode {
        protected final Method.MethodVersion m;
        private volatile SourceSection sourceSection;

        public BodyNode(Method.MethodVersion m) {
            this.m = m;
        }

        public abstract void execute(VirtualFrame frame, InlinedFrameAccess frameAccess);

        @Override
        public SourceSection getSourceSection() {
            if (m.getSource() == null) {
                return null;
            }
            if (sourceSection == null) {
                SourceSection localSourceSection = m.getWholeMethodSourceSection();
                synchronized (this) {
                    if (sourceSection == null) {
                        sourceSection = localSourceSection;
                    }
                }
            }
            return sourceSection;
        }
    }

    // Data needed to revert to the generic case.
    protected final int opcode;
    protected final int statementIndex;

    @Child BodyNode body;

    public static InlinedMethodNode createFor(Method resolutionSeed, int top, int opcode, int curBCI, int statementIndex) {
        if (!isInlineCandidate(resolutionSeed, opcode)) {
            return null;
        }
        if (resolutionSeed.isInlinableGetter()) {
            return InlinedFieldAccessNode.createGetter(resolutionSeed, top, opcode, curBCI, statementIndex);
        }
        if (resolutionSeed.isInlinableSetter()) {
            return InlinedFieldAccessNode.createSetter(resolutionSeed, top, opcode, curBCI, statementIndex);
        }
        if (isUnconditionalInlineCandidate(resolutionSeed, opcode)) {
            // Try to inline trivial substitutions.
            JavaSubstitution.Factory factory = Substitutions.lookupSubstitution(resolutionSeed);
            if (factory != null && factory.inlineInBytecode()) {
                return InlinedSubstitutionBodyNode.create(resolutionSeed, top, opcode, curBCI, statementIndex, factory);
            }
        }
        return null;
    }

    public InlinedMethodNode(Method inlinedMethod, int top, int opcode, int callerBCI, int statementIndex, BodyNode body) {
        this(inlinedMethod.getMethodVersion(), top, opcode, callerBCI, statementIndex, body);
    }

    public InlinedMethodNode(Method.MethodVersion inlinedMethod, int top, int opcode, int callerBCI, int statementIndex, BodyNode body) {
        super(inlinedMethod, top, callerBCI);
        this.opcode = opcode;
        this.statementIndex = statementIndex;
        this.body = insert(body);
    }

    @Override
    public int execute(VirtualFrame frame) {
        preludeChecks(frame);
        return executeBody(frame);
    }

    protected final void preludeChecks(VirtualFrame frame) {
        if (method.isStatic()) {
            initCheck();
        } else {
            nullCheck(peekReceiver(frame));
        }
    }

    protected final int executeBody(VirtualFrame frame) {
        body.execute(frame, this);
        return stackEffect;
    }

    @Override
    public int top() {
        return top;
    }

    @Override
    public int resultAt() {
        return resultAt;
    }

    @Override
    public int statementIndex() {
        return statementIndex;
    }

    @Override
    public Method.MethodVersion inlinedMethod() {
        return method;
    }

    private void initCheck() {
        /*
         * Everything is constant here, so it should fold away nicely.
         */
        ObjectKlass k = method.getDeclaringKlass();
        if (!k.isInitialized()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            k.safeInitialize();
        }
    }

    public final BaseQuickNode revertToGeneric(BytecodeNode parent) {
        return parent.generifyInlinedMethodNode(top, opcode, getCallerBCI(), statementIndex, method.getMethod());
    }

    public static boolean isInlineCandidate(Method resolutionSeed, int opcode) {
        if (resolutionSeed.isSynchronized()) {
            return false;
        }
        if (opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKESPECIAL) {
            return true;
        }
        if (opcode == Bytecodes.INVOKEVIRTUAL) {
            // InvokeVirtual can be bytecode-level inlined if method is final, or there are no
            // overrides yet.
            if ((resolutionSeed.isFinalFlagSet() || resolutionSeed.isPrivate() || resolutionSeed.getDeclaringKlass().isFinalFlagSet()) ||
                            resolutionSeed.getContext().getClassHierarchyOracle().isLeafMethod(resolutionSeed).isValid()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUnconditionalInlineCandidate(Method resolutionSeed, int opcode) {
        if (opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKESPECIAL) {
            return true;
        }
        if (opcode == Bytecodes.INVOKEVIRTUAL) {
            // InvokeVirtual can be unconditionally bytecode-level inlined if method can never be
            // overriden.
            if (resolutionSeed.isFinalFlagSet() || resolutionSeed.isPrivate() || resolutionSeed.getDeclaringKlass().isFinalFlagSet()) {
                return true;
            }
        }
        return false;
    }
}
