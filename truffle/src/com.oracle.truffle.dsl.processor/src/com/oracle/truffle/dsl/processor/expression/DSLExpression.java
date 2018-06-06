/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;

import com.oracle.truffle.dsl.processor.generator.DSLExpressionGenerator;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;

public abstract class DSLExpression {

    private TypeMirror resolvedTargetType;

    private DSLExpression() {
    }

    private static final class DSLErrorListener extends BaseErrorListener {
        static DSLErrorListener INSTANCE = new DSLErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new InvalidExpressionException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public static DSLExpression parse(String input) {
        ExpressionLexer lexer = new ExpressionLexer(CharStreams.fromString(input));
        TokenStream tokens = new CommonTokenStream(lexer);
        ExpressionParser parser = new ExpressionParser(tokens);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(DSLErrorListener.INSTANCE);
        parser.addErrorListener(DSLErrorListener.INSTANCE);
        try {
            return parser.expression().result;
        } catch (RecognitionException e) {
            throw new InvalidExpressionException(e.getMessage());
        }
    }

    public final Set<VariableElement> findBoundVariableElements() {
        final Set<VariableElement> variables = new HashSet<>();
        this.accept(new AbstractDSLExpressionVisitor() {

            @Override
            public void visitVariable(Variable variable) {
                if (variable.getReceiver() == null) {
                    variables.add(variable.getResolvedVariable());
                }
            }

        });
        return variables;
    }

    public final Set<Variable> findBoundVariables() {
        final Set<Variable> variables = new HashSet<>();
        this.accept(new AbstractDSLExpressionVisitor() {

            @Override
            public void visitVariable(Variable variable) {
                if (variable.getReceiver() == null) {
                    variables.add(variable);
                }
            }

        });
        return variables;
    }

    public Object resolveConstant() {
        return null;
    }

    public void setResolvedTargetType(TypeMirror resolvedTargetType) {
        this.resolvedTargetType = resolvedTargetType;
    }

    public TypeMirror getResolvedTargetType() {
        return resolvedTargetType;
    }

    public String asString() {
        CodeTree tree = DSLExpressionGenerator.write(this, null, new HashMap<>());
        return tree.toString();
    }

    public abstract TypeMirror getResolvedType();

    public abstract void accept(DSLExpressionVisitor visitor);

    public abstract DSLExpression reduce(DSLExpressionReducer visitor);

    private DSLExpression reduceImpl(DSLExpressionReducer reducer) {
        DSLExpression expression = reduce(reducer);
        if (expression == null) {
            return this;
        }
        return expression;
    }

    public static final class Negate extends DSLExpression {

        private final DSLExpression receiver;

        public Negate(DSLExpression receiver) {
            this.receiver = receiver;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            receiver.accept(visitor);
            visitor.visitNegate(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer visitor) {
            DSLExpression newReceiver = receiver.reduceImpl(visitor);
            DSLExpression negate = this;
            if (newReceiver != receiver) {
                negate = new Negate(newReceiver);
                negate.setResolvedTargetType(getResolvedTargetType());
            }
            return negate;
        }

        public DSLExpression getReceiver() {
            return receiver;
        }

        @Override
        public Object resolveConstant() {
            Object constant = receiver.resolveConstant();
            if (constant instanceof Integer) {
                return -(int) constant;
            }
            return super.resolveConstant();
        }

        @Override
        public TypeMirror getResolvedType() {
            return receiver.getResolvedType();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Negate) {
                return receiver.equals(((Negate) obj).receiver);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return receiver.hashCode();
        }
    }

    public static final class Binary extends DSLExpression {

        private final String operator;
        private final DSLExpression left;
        private final DSLExpression right;

        private TypeMirror resolvedType;

        public Binary(String operator, DSLExpression left, DSLExpression right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        public boolean isComparison() {
            return DSLExpressionResolver.COMPARABLE_OPERATORS.contains(operator) || DSLExpressionResolver.IDENTITY_OPERATORS.contains(operator);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Binary) {
                Binary other = (Binary) obj;
                return operator.equals(other.operator) && left.equals(other.left) && right.equals(other.right);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(operator, left, right);
        }

        public String getOperator() {
            return operator;
        }

        public DSLExpression getLeft() {
            return left;
        }

        public DSLExpression getRight() {
            return right;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            left.accept(visitor);
            right.accept(visitor);
            visitor.visitBinary(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer reducer) {
            DSLExpression newLeft = left.reduceImpl(reducer);
            DSLExpression newRight = right.reduceImpl(reducer);
            Binary b = this;
            if (newLeft != left || newRight != right) {
                b = new Binary(getOperator(), newLeft, newRight);
                b.setResolvedTargetType(getResolvedTargetType());
                b.setResolvedType(getResolvedType());
            }
            return reducer.visitBinary(b);
        }

        @Override
        public TypeMirror getResolvedType() {
            return resolvedType;
        }

        public void setResolvedType(TypeMirror resolvedType) {
            this.resolvedType = resolvedType;
        }

        @Override
        public String toString() {
            return "Binary [left=" + left + ", operator=" + operator + ", right=" + right + ", resolvedType=" + resolvedType + "]";
        }

    }

    public static final class Call extends DSLExpression {

        private final DSLExpression receiver;
        private final String name;
        private final List<DSLExpression> parameters;

        private ExecutableElement resolvedMethod;

        public Call(DSLExpression receiver, String name, List<DSLExpression> parameters) {
            this.receiver = receiver;
            this.name = name;
            this.parameters = parameters;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Call) {
                Call other = (Call) obj;
                return Objects.equals(receiver, other.receiver) && name.equals(other.name) && parameters.equals(other.parameters);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(receiver, name, parameters);
        }

        public DSLExpression getReceiver() {
            return receiver;
        }

        public String getName() {
            return name;
        }

        public List<DSLExpression> getParameters() {
            return parameters;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            if (receiver != null) {
                receiver.accept(visitor);
            }
            for (DSLExpression parameter : getParameters()) {
                parameter.accept(visitor);
            }
            visitor.visitCall(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer reducer) {
            DSLExpression newReceiver = null;
            if (receiver != null) {
                newReceiver = receiver.reduceImpl(reducer);
            }

            boolean parameterChanged = false;
            List<DSLExpression> newParameters = new ArrayList<>();
            for (DSLExpression param : getParameters()) {
                DSLExpression newParam = param.reduceImpl(reducer);
                if (newParam != param) {
                    parameterChanged = true;
                    newParameters.add(newParam);
                } else {
                    newParameters.add(param);
                }
            }

            Call c = this;
            if (newReceiver != receiver || parameterChanged) {
                c = new Call(newReceiver, getName(), newParameters);
                c.setResolvedMethod(getResolvedMethod());
                c.setResolvedTargetType(getResolvedTargetType());
            }
            return reducer.visitCall(c);
        }

        @Override
        public TypeMirror getResolvedType() {
            if (resolvedMethod == null) {
                return null;
            }
            if (resolvedMethod.getKind() == ElementKind.CONSTRUCTOR) {
                return resolvedMethod.getEnclosingElement().asType();
            } else {
                return resolvedMethod.getReturnType();
            }
        }

        public ExecutableElement getResolvedMethod() {
            return resolvedMethod;
        }

        public void setResolvedMethod(ExecutableElement resolvedMethod) {
            this.resolvedMethod = resolvedMethod;
        }

        @Override
        public String toString() {
            return "Call [receiver=" + receiver + ", name=" + name + ", parameters=" + parameters + ", resolvedMethod=" + resolvedMethod + "]";
        }

    }

    public static final class Variable extends DSLExpression {

        private final DSLExpression receiver;
        private final String name;

        private VariableElement resolvedVariable;

        public Variable(DSLExpression receiver, String name) {
            this.receiver = receiver;
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Variable) {
                Variable other = (Variable) obj;
                return Objects.equals(receiver, other.receiver) && name.equals(other.name);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(receiver, name);
        }

        public DSLExpression getReceiver() {
            return receiver;
        }

        public String getName() {
            return name;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            if (receiver != null) {
                receiver.accept(visitor);
            }
            visitor.visitVariable(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer reducer) {
            DSLExpression newReceiver = null;
            if (receiver != null) {
                newReceiver = receiver.reduceImpl(reducer);
            }
            Variable c = this;
            if (newReceiver != receiver) {
                c = new Variable(newReceiver, getName());
                c.setResolvedTargetType(getResolvedTargetType());
                c.setResolvedVariable(getResolvedVariable());
            }
            return reducer.visitVariable(c);
        }

        @Override
        public TypeMirror getResolvedType() {
            return resolvedVariable != null ? resolvedVariable.asType() : null;
        }

        public void setResolvedVariable(VariableElement resolvedVariable) {
            this.resolvedVariable = resolvedVariable;
        }

        public VariableElement getResolvedVariable() {
            return resolvedVariable;
        }

        @Override
        public String toString() {
            return "Variable [receiver=" + receiver + ", name=" + name + ", resolvedVariable=" + resolvedVariable + "]";
        }

    }

    public static final class IntLiteral extends DSLExpression {

        private final String literal;

        private int resolvedValueInt;
        private TypeMirror resolvedType;

        public IntLiteral(String literal) {
            this.literal = literal;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof IntLiteral) {
                IntLiteral other = (IntLiteral) obj;
                return resolvedValueInt == other.resolvedValueInt;
            }
            return false;
        }

        @Override
        public Object resolveConstant() {
            return resolvedValueInt;
        }

        @Override
        public int hashCode() {
            return resolvedValueInt;
        }

        public String getLiteral() {
            return literal;
        }

        public int getResolvedValueInt() {
            return resolvedValueInt;
        }

        public void setResolvedValueInt(int resolved) {
            this.resolvedValueInt = resolved;
        }

        @Override
        public TypeMirror getResolvedType() {
            return resolvedType;
        }

        public void setResolvedType(TypeMirror resolvedType) {
            this.resolvedType = resolvedType;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            visitor.visitIntLiteral(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer reducer) {
            return this;
        }

        @Override
        public String toString() {
            return "IntLiteral [literal=" + literal + ", resolvedValueInt=" + resolvedValueInt + ", resolvedType=" + resolvedType + "]";
        }

    }

    public static final class BooleanLiteral extends DSLExpression {

        private final boolean literal;
        private TypeMirror resolvedType;

        public BooleanLiteral(boolean literal) {
            this.literal = literal;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BooleanLiteral) {
                BooleanLiteral other = (BooleanLiteral) obj;
                return literal == other.literal;
            }
            return false;
        }

        @Override
        public Object resolveConstant() {
            return literal;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(literal);
        }

        public boolean getLiteral() {
            return literal;
        }

        @Override
        public TypeMirror getResolvedType() {
            return resolvedType;
        }

        public void setResolvedType(TypeMirror resolvedType) {
            this.resolvedType = resolvedType;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            visitor.visitBooleanLiteral(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer reducer) {
            return this;
        }

        @Override
        public String toString() {
            return "BooleanLiteral [literal=" + literal + ", resolvedType=" + resolvedType + "]";
        }

    }

    public abstract class AbstractDSLExpressionVisitor implements DSLExpressionVisitor {
        @Override
        public void visitBinary(Binary binary) {
        }

        @Override
        public void visitCall(Call binary) {
        }

        @Override
        public void visitIntLiteral(IntLiteral binary) {
        }

        @Override
        public void visitNegate(Negate negate) {
        }

        @Override
        public void visitVariable(Variable binary) {
        }

        public void visitBooleanLiteral(BooleanLiteral binary) {
        }
    }

    public interface DSLExpressionVisitor {

        void visitBinary(Binary binary);

        void visitNegate(Negate negate);

        void visitCall(Call binary);

        void visitVariable(Variable binary);

        void visitIntLiteral(IntLiteral binary);

        void visitBooleanLiteral(BooleanLiteral binary);

    }

    public abstract static class AbstractDSLExpressionReducer implements DSLExpressionReducer {

        @Override
        public DSLExpression visitBinary(Binary binary) {
            return binary;
        }

        @Override
        public DSLExpression visitCall(Call binary) {
            return binary;
        }

        @Override
        public DSLExpression visitNegate(Negate negate) {
            return negate;
        }

        @Override
        public DSLExpression visitVariable(Variable binary) {
            return binary;
        }

    }

    public interface DSLExpressionReducer {

        DSLExpression visitBinary(Binary binary);

        DSLExpression visitNegate(Negate negate);

        DSLExpression visitCall(Call binary);

        DSLExpression visitVariable(Variable binary);

    }

}
