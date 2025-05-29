/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.ConditionalInlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.GuardedConditionalInlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodPredicate;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;

public abstract class InlinedFieldAccessNode extends InlinedMethodNode.BodyNode implements InlinedMethodPredicate {

    static final class FieldAccessRecipes implements ConditionalInlinedMethodNode.Recipes {
        private volatile InlinedFieldAccessNode body;
        private final Supplier<InlinedFieldAccessNode> supplier;

        FieldAccessRecipes(Supplier<InlinedFieldAccessNode> supplier) {
            this.supplier = supplier;
        }

        @Override
        public InlinedMethodNode.BodyNode cookBody() {
            return ensureInit();
        }

        @Override
        public InlinedMethodPredicate cookGuard() {
            return ensureInit();
        }

        private InlinedFieldAccessNode ensureInit() {
            CompilerAsserts.neverPartOfCompilation();
            InlinedFieldAccessNode bodyNode = body;
            if (bodyNode == null) {
                synchronized (this) {
                    bodyNode = body;
                    if (bodyNode == null) {
                        bodyNode = body = supplier.get();
                    }
                }
            }
            return bodyNode;
        }

    }

    private static final byte INSTANCE_SETTER = 0b01;
    private static final byte STATIC_SETTER = 0b11;
    private static final byte INSTANCE_GETTER = 0b00;
    private static final byte STATIC_GETTER = 0b10;

    private static final int INSTANCE_SETTER_BCI = 2;
    private static final int STATIC_SETTER_BCI = 1;
    private static final int INSTANCE_GETTER_BCI = 1;
    private static final int STATIC_GETTER_BCI = 0;

    protected final Field field;

    protected InlinedFieldAccessNode(Method.MethodVersion method, char fieldCpi) {
        super(method);
        assert isResolutionSuccessAt(method, fieldCpi);
        this.field = getInlinedField(method, fieldCpi);
    }

    public static InlinedMethodNode createGetter(ResolvedCall<Klass, Method, Field> resolvedCall, int top, int opCode, int curBCI, int statementIndex) {
        Method.MethodVersion methodVersion = resolvedCall.getResolvedMethod().getMethodVersion();
        char fieldCpi = InlinedFieldAccessNode.getFieldCpi(false, methodVersion);
        ConditionalInlinedMethodNode.Recipes recipes = new FieldAccessRecipes(() -> new InlinedGetterNode(methodVersion, fieldCpi));
        return create(resolvedCall, top, opCode, curBCI, statementIndex, recipes, fieldCpi);
    }

    public static InlinedMethodNode createSetter(ResolvedCall<Klass, Method, Field> resolvedCall, int top, int opCode, int curBCI, int statementIndex) {
        Method.MethodVersion methodVersion = resolvedCall.getResolvedMethod().getMethodVersion();
        char fieldCpi = InlinedFieldAccessNode.getFieldCpi(true, methodVersion);
        ConditionalInlinedMethodNode.Recipes recipes = new FieldAccessRecipes(() -> new InlinedSetterNode(methodVersion, fieldCpi));
        return create(resolvedCall, top, opCode, curBCI, statementIndex, recipes, fieldCpi);
    }

    /**
     * Creates a bytecode-inlined version of a getter or setter.
     * 
     * The node itself has a few different behaviors:
     * <ul>
     * <li>The created node will wait until the field constant in the constant pool is resolved
     * before performing the bytecode-level inlining.</li>
     * <li>Once the inlining happens, the resulting node will guard against the field needing
     * re-resolution (due to redefinition).</li>
     * <li>If re-resolution is indeed needed, the node will revert to a generic invoke.</li>
     * <li>Furthermore, in all cases, when inlining an {@code invokevirtual}, the node will revert
     * to a generic invoke if the method is no longer a leaf.</li>
     * </ul>
     */
    private static InlinedMethodNode create(ResolvedCall<Klass, Method, Field> resolvedCall, int top, int opCode, int curBCI, int statementIndex,
                    ConditionalInlinedMethodNode.Recipes recipes, char fieldCpi) {
        assert isInlineCandidate(resolvedCall);
        Method.MethodVersion inlinedMethod = resolvedCall.getResolvedMethod().getMethodVersion();
        boolean isDefinitive = isResolutionSuccessAt(inlinedMethod, fieldCpi);
        if (isDefinitive) {
            if (isUnconditionalInlineCandidate(resolvedCall)) {
                return ConditionalInlinedMethodNode.getDefinitiveNode(recipes, inlinedMethod, top, opCode, curBCI, statementIndex);
            } else {
                return GuardedConditionalInlinedMethodNode.getDefinitiveNode(recipes, InlinedMethodPredicate.LEAF_ASSUMPTION_CHECK,
                                inlinedMethod, top, opCode, curBCI, statementIndex);
            }
        }
        InlinedMethodPredicate condition = (context, version, frame, node) -> isResolutionSuccessAt(version, fieldCpi);
        if (isUnconditionalInlineCandidate(resolvedCall)) {
            return new ConditionalInlinedMethodNode(resolvedCall, top, opCode, curBCI, statementIndex, recipes, condition);
        } else {
            return new GuardedConditionalInlinedMethodNode(resolvedCall, top, opCode, curBCI, statementIndex, recipes, condition, InlinedMethodPredicate.LEAF_ASSUMPTION_CHECK);
        }
    }

    @Override
    public boolean isValid(EspressoContext context, Method.MethodVersion version, VirtualFrame frame, InlinedMethodNode node) {
        return !field.needsReResolution();
    }

    private static boolean isResolutionSuccessAt(Method.MethodVersion version, char fieldCpi) {
        return version.getPool().isResolutionSuccessAt(fieldCpi) && !getInlinedField(version, fieldCpi).needsReResolution();
    }

    private static Field getInlinedField(Method.MethodVersion method, char fieldCpi) {
        assert method.getPool().isResolutionSuccessAt(fieldCpi);
        return method.getPool().resolvedFieldAt(method.getDeclaringKlass(), fieldCpi);
    }

    private static char getFieldCpi(boolean isSetter, Method.MethodVersion method) {
        byte desc = 0;
        desc |= (byte) (isSetter ? 0b01 : 0b00);
        desc |= (byte) (method.isStatic() ? 0b10 : 0b00);
        char bci = switch (desc) {
            case INSTANCE_SETTER -> INSTANCE_SETTER_BCI;
            case STATIC_SETTER -> STATIC_SETTER_BCI;
            case INSTANCE_GETTER -> INSTANCE_GETTER_BCI;
            case STATIC_GETTER -> STATIC_GETTER_BCI;
            default -> throw EspressoError.shouldNotReachHere();
        };
        return new BytecodeStream(method.getOriginalCode()).readCPI(bci);
    }
}
