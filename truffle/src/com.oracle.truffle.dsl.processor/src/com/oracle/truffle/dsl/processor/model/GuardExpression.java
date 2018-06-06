/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.AbstractDSLExpressionReducer;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class GuardExpression extends MessageContainer {

    private static final Set<String> IDENTITY_FOLD_OPERATORS = new HashSet<>(Arrays.asList("<=", ">=", "=="));

    private final SpecializationData source;
    private final DSLExpression expression;

    public GuardExpression(SpecializationData source, DSLExpression expression) {
        this.source = source;
        this.expression = expression;
    }

    @Override
    public Element getMessageElement() {
        return source.getMessageElement();
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return source.getMessageAnnotation();
    }

    @Override
    public AnnotationValue getMessageAnnotationValue() {
        return ElementUtils.getAnnotationValue(getMessageAnnotation(), "guards");
    }

    public DSLExpression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "Guard[" + (expression != null ? expression.asString() : "null") + "]";
    }

    public boolean isConstantTrueInSlowPath(ProcessorContext context) {
        DSLExpression reducedExpression = getExpression().reduce(new AbstractDSLExpressionReducer() {

            @Override
            public DSLExpression visitVariable(Variable binary) {
                // on the slow path we can assume all cache expressions inlined.
                for (CacheExpression cache : source.getCaches()) {
                    if (ElementUtils.variableEquals(cache.getParameter().getVariableElement(), binary.getResolvedVariable())) {
                        return cache.getExpression();
                    }
                }
                return super.visitVariable(binary);
            }

            @Override
            public DSLExpression visitCall(Call binary) {
                ExecutableElement method = binary.getResolvedMethod();
                if (!method.getSimpleName().toString().equals("equals")) {
                    return binary;
                }
                if (method.getModifiers().contains(Modifier.STATIC)) {
                    return binary;
                }
                if (!ElementUtils.typeEquals(method.getReturnType(), context.getType(boolean.class))) {
                    return binary;
                }
                if (method.getParameters().size() != 1) {
                    return binary;
                }
                // signature: receiver.equals(receiver) can be folded to true
                DSLExpression receiver = binary.getReceiver();
                DSLExpression firstArg = binary.getParameters().get(0);
                if (receiver instanceof Variable && firstArg instanceof Variable) {
                    if (receiver.equals(firstArg)) {
                        return new BooleanLiteral(true);
                    }
                }
                return super.visitCall(binary);
            }

            @Override
            public DSLExpression visitBinary(Binary binary) {
                // signature: value == value can be folded to true
                if (IDENTITY_FOLD_OPERATORS.contains(binary.getOperator())) {
                    if (binary.getLeft() instanceof Variable && binary.getRight() instanceof Variable) {
                        Variable leftVar = ((Variable) binary.getLeft());
                        Variable rightVar = ((Variable) binary.getRight());
                        if (leftVar.equals(rightVar)) {
                            // double and float cannot be folded as NaN is never identity equal
                            if (!ElementUtils.typeEquals(leftVar.getResolvedType(), context.getType(float.class)) &&
                                            !ElementUtils.typeEquals(leftVar.getResolvedType(), context.getType(double.class))) {
                                return new BooleanLiteral(true);
                            }
                        }
                    }
                }
                return super.visitBinary(binary);

            }
        });

        Object o = reducedExpression.resolveConstant();
        if (o instanceof Boolean) {
            if (((Boolean) o).booleanValue()) {
                return true;
            }
        }
        return false;
    }

    public boolean equalsNegated(GuardExpression other) {
        boolean negated = false;
        DSLExpression thisExpression = expression;
        if (thisExpression instanceof Negate) {
            negated = true;
            thisExpression = ((Negate) thisExpression).getReceiver();
        }

        boolean otherNegated = false;
        DSLExpression otherExpression = other.expression;
        if (otherExpression instanceof Negate) {
            otherNegated = true;
            otherExpression = ((Negate) otherExpression).getReceiver();
        }
        return Objects.equals(thisExpression, otherExpression) && negated != otherNegated;
    }

    public boolean implies(GuardExpression other) {
        if (Objects.equals(expression, other.expression)) {
            return true;
        }
        return false;
    }

}
