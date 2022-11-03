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

import static com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode.isInlineCandidate;
import static com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode.isUnconditionalInlineCandidate;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.ConditionalInlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.GuardedConditionalInlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.GuardedInlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodPredicate;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public abstract class InlinedFieldAccessNode extends InlinedMethodNode.BodyNode implements InlinedMethodPredicate {

    private static final byte INSTANCE_SETTER = 0b01;
    private static final byte STATIC_SETTER = 0b11;
    private static final byte INSTANCE_GETTER = 0b00;
    private static final byte STATIC_GETTER = 0b10;

    private static final int INSTANCE_SETTER_BCI = 2;
    private static final int STATIC_SETTER_BCI = 1;
    private static final int INSTANCE_GETTER_BCI = 1;
    private static final int STATIC_GETTER_BCI = 0;

    private final char fieldCpi;

    public InlinedFieldAccessNode(boolean isSetter, Method.MethodVersion method) {
        byte desc = 0;
        desc |= (isSetter ? 0b01 : 0b00);
        desc |= (method.isStatic() ? 0b10 : 0b00);
        char bci;
        // @formatter:off
        switch (desc) {
            case INSTANCE_SETTER: bci = INSTANCE_SETTER_BCI; break;
            case STATIC_SETTER: bci = STATIC_SETTER_BCI; break;
            case INSTANCE_GETTER: bci = INSTANCE_GETTER_BCI; break;
            case STATIC_GETTER: bci = STATIC_GETTER_BCI; break;
            default: throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        this.fieldCpi = new BytecodeStream(method.getOriginalCode()).readCPI(bci);
    }

    public static InlinedMethodNode createGetter(Method inlinedMethod, int top, int opCode, int curBCI, int statementIndex) {
        InlinedFieldAccessNode bodyNode = InlinedGetterNodeGen.create(inlinedMethod.getMethodVersion());
        return create(inlinedMethod, top, opCode, curBCI, statementIndex, bodyNode);
    }

    public static InlinedMethodNode createSetter(Method inlinedMethod, int top, int opCode, int curBCI, int statementIndex) {
        InlinedFieldAccessNode bodyNode = InlinedSetterNodeGen.create(inlinedMethod.getMethodVersion());
        return create(inlinedMethod, top, opCode, curBCI, statementIndex, bodyNode);
    }

    private static InlinedMethodNode create(Method inlinedMethod, int top, int opCode, int curBCI, int statementIndex, InlinedFieldAccessNode bodyNode) {
        assert isInlineCandidate(inlinedMethod, opCode);
        if (isUnconditionalInlineCandidate(inlinedMethod, opCode)) {
            if (bodyNode.isResolutionSuccessAt(inlinedMethod.getMethodVersion())) {
                return new InlinedMethodNode(inlinedMethod, top, opCode, curBCI, statementIndex, bodyNode);
            } else {
                return new ConditionalInlinedMethodNode(inlinedMethod, top, opCode, curBCI, statementIndex, bodyNode, bodyNode);
            }
        } else {
            if (bodyNode.isResolutionSuccessAt(inlinedMethod.getMethodVersion())) {
                return new GuardedInlinedMethodNode(inlinedMethod, top, opCode, curBCI, statementIndex, bodyNode, InlinedMethodPredicate.LEAF_ASSUMPTION_CHECK);
            } else {
                return new GuardedConditionalInlinedMethodNode(inlinedMethod, top, opCode, curBCI, statementIndex, bodyNode, bodyNode, InlinedMethodPredicate.LEAF_ASSUMPTION_CHECK);
            }
        }
    }

    @Override
    public boolean isValid(EspressoContext context, Method.MethodVersion version, VirtualFrame frame, InlinedMethodNode node) {
        return isResolutionSuccessAt(version);
    }

    protected boolean isResolutionSuccessAt(Method.MethodVersion version) {
        return version.getPool().isResolutionSuccessAt(fieldCpi);
    }

    Field getInlinedField(Method.MethodVersion method) {
        return method.getPool().resolvedFieldAt(method.getDeclaringKlass(), fieldCpi);
    }
}
