/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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

    private boolean libraryAcceptsGuard;
    private boolean weakReferenceGuard;

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

    public void setLibraryAcceptsGuard(boolean forceConstantTrueInSlowPath) {
        this.libraryAcceptsGuard = forceConstantTrueInSlowPath;
    }

    public boolean isWeakReferenceGuard() {
        return weakReferenceGuard;
    }

    public void setWeakReferenceGuard(boolean weakReferenceGuard) {
        this.weakReferenceGuard = weakReferenceGuard;
    }

    public boolean isLibraryAcceptsGuard() {
        return libraryAcceptsGuard;
    }

    @Override
    public String toString() {
        return "Guard[" + (expression != null ? expression.asString() : "null") + "]";
    }

    public boolean isConstantTrueInSlowPath(ProcessorContext context, boolean uncached) {
        if (libraryAcceptsGuard) {
            return true;
        }
        DSLExpression reducedExpression = getExpression().reduce(new AbstractDSLExpressionReducer() {

            @Override
            public DSLExpression visitVariable(Variable binary) {
                // on the slow path we can assume all cache expressions inlined.
                for (CacheExpression cache : source.getCaches()) {
                    if (ElementUtils.variableEquals(cache.getParameter().getVariableElement(), binary.getResolvedVariable())) {
                        return uncached ? cache.getUncachedExpression() : cache.getDefaultExpression();
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
