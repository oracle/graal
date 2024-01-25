/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleSuppressedWarnings;
import com.oracle.truffle.dsl.processor.generator.DSLExpressionGenerator;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public abstract class DSLExpression {

    private TypeMirror resolvedTargetType;

    private DSLExpression() {
    }

    private static final class DSLErrorListener extends BaseErrorListener {
        static final DSLErrorListener INSTANCE = new DSLErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new InvalidExpressionException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public List<DSLExpression> flatten() {
        List<DSLExpression> expressions = new ArrayList<>();
        accept(new DSLExpressionVisitor() {
            public void visitVariable(Variable binary) {
                expressions.add(binary);
            }

            public void visitNegate(Negate negate) {
                expressions.add(negate);
            }

            public void visitIntLiteral(IntLiteral binary) {
                expressions.add(binary);
            }

            public void visitClassLiteral(ClassLiteral classLiteral) {
                expressions.add(classLiteral);
            }

            public void visitCall(Call binary) {
                expressions.add(binary);
            }

            public void visitBooleanLiteral(BooleanLiteral binary) {
                expressions.add(binary);
            }

            public void visitBinary(Binary binary) {
                expressions.add(binary);
            }

            public void visitCast(Cast cast) {
                expressions.add(cast);
            }
        });
        return expressions;
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public boolean mayAllocate() {
        final AtomicBoolean mayAllocate = new AtomicBoolean(false);
        accept(new AbstractDSLExpressionVisitor() {

            @Override
            public void visitCall(Call binary) {
                mayAllocate.set(true);
            }

        });
        return mayAllocate.get();
    }

    /**
     * Whether the node instance is bound. This includes @Bind("$node").
     */
    public boolean isNodeReceiverBound() {
        final AtomicBoolean bindsReceiver = new AtomicBoolean(false);
        accept(new AbstractDSLExpressionVisitor() {

            @Override
            public void visitVariable(Variable var) {
                if (var.getReceiver() == null) {
                    VariableElement resolvedVar = var.getResolvedVariable();
                    if (resolvedVar != null && !resolvedVar.getModifiers().contains(Modifier.STATIC) &&
                                    (resolvedVar.getEnclosingElement() == null || resolvedVar.getEnclosingElement().getKind() != ElementKind.METHOD)) {
                        String name = resolvedVar.getSimpleName().toString();
                        if (!name.equals("null")) {
                            bindsReceiver.set(true);
                        }
                    }
                }
            }

            @Override
            public void visitCall(Call binary) {
                if (binary.getReceiver() == null) {
                    ExecutableElement method = binary.getResolvedMethod();
                    if (method != null && method.getKind() != ElementKind.CONSTRUCTOR && !method.getModifiers().contains(Modifier.STATIC)) {
                        bindsReceiver.set(true);
                    }
                }
            }

        });
        return bindsReceiver.get();
    }

    /**
     * Whether the node instance is bound implicitly. This ignores @Bind("$node").
     */
    public boolean isNodeReceiverImplicitlyBound() {
        final AtomicBoolean bindsReceiver = new AtomicBoolean(false);
        accept(new AbstractDSLExpressionVisitor() {

            @Override
            public void visitVariable(Variable var) {
                if (var.getReceiver() == null) {
                    VariableElement resolvedVar = var.getResolvedVariable();
                    if (resolvedVar != null && !resolvedVar.getModifiers().contains(Modifier.STATIC) &&
                                    (resolvedVar.getEnclosingElement() == null || resolvedVar.getEnclosingElement().getKind() != ElementKind.METHOD)) {
                        String name = resolvedVar.getSimpleName().toString();
                        if (!name.equals("null") && !name.equals("this") && !name.equals(NodeParser.NODE_KEYWORD)) {
                            bindsReceiver.set(true);
                        }
                    }
                }
            }

            @Override
            public void visitCall(Call binary) {
                if (binary.getReceiver() == null) {
                    ExecutableElement method = binary.getResolvedMethod();
                    if (method != null && method.getKind() != ElementKind.CONSTRUCTOR && !method.getModifiers().contains(Modifier.STATIC)) {
                        bindsReceiver.set(true);
                    }
                }
            }

        });
        return bindsReceiver.get();
    }

    public static DSLExpression resolve(DSLExpressionResolver resolver, MessageContainer container, String annotationValueName, DSLExpression expression, String originalString) {
        try {
            expression.accept(resolver);
            List<Element> deprecatedElements = expression.findBoundDeprecatedElements();
            if (!deprecatedElements.isEmpty() && !TruffleSuppressedWarnings.isSuppressed(container.getMessageElement(), "deprecated")) {
                AnnotationMirror mirror = container.getMessageAnnotation();
                AnnotationValue value = null;
                if (mirror != null && annotationValueName != null) {
                    value = ElementUtils.getAnnotationValue(mirror, annotationValueName);
                }
                StringBuilder b = new StringBuilder();
                b.append(String.format("The expression '%s' binds the following deprecated elements and should be updated:", originalString));
                for (Element deprecatedElement : deprecatedElements) {
                    String relativeName = ElementUtils.getReadableReference(container.getMessageElement(), deprecatedElement);
                    b.append(String.format("%n  - "));
                    b.append(relativeName);
                }
                b.append(String.format("%nUpdate the usage of the elements or suppress the warning with @SuppressWarnings(\"deprecated\")."));
                container.addWarning(value, b.toString());
            }
            return expression;
        } catch (InvalidExpressionException e) {
            AnnotationMirror mirror = container.getMessageAnnotation();
            AnnotationValue value = null;
            if (mirror != null && annotationValueName != null) {
                value = ElementUtils.getAnnotationValue(mirror, annotationValueName);
            }
            container.addError(value, "Error parsing expression '%s': %s", originalString, e.getMessage());
        }
        return null;
    }

    public static DSLExpression parseAndResolve(DSLExpressionResolver resolver, MessageContainer container, String annotationValueName, String string) {
        DSLExpression expression = DSLExpression.parse(container, annotationValueName, string);
        if (expression == null) {
            return null;
        }
        return resolve(resolver, container, annotationValueName, expression, string);
    }

    public static DSLExpression parse(MessageContainer container, String annotationValueName, String input) {
        ExpressionLexer lexer = new ExpressionLexer(CharStreams.fromString(input));
        TokenStream tokens = new CommonTokenStream(lexer);
        ExpressionParser parser = new ExpressionParser(tokens);
        parser.setBuildParseTree(false);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(DSLErrorListener.INSTANCE);
        parser.addErrorListener(DSLErrorListener.INSTANCE);
        try {
            return parser.expression().result;
        } catch (InvalidExpressionException | RecognitionException e) {
            AnnotationMirror mirror = container.getMessageAnnotation();
            AnnotationValue value = null;
            if (mirror != null && annotationValueName != null) {
                value = ElementUtils.getAnnotationValue(mirror, annotationValueName);
            }
            container.addError(value, "Error parsing expression '%s': %s", input, e.getMessage());
            return null;
        }
    }

    public final Set<ExecutableElement> findBoundExecutableElements() {
        final Set<ExecutableElement> methods = new HashSet<>();
        this.accept(new AbstractDSLExpressionVisitor() {

            @Override
            public void visitCall(Call binary) {
                if (binary.getResolvedMethod() != null) {
                    methods.add(binary.getResolvedMethod());
                }
            }

        });
        return methods;
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

    private List<Element> findBoundDeprecatedElements() {
        final List<Element> deprecatedElements = new ArrayList<>();
        accept(new AbstractDSLExpressionVisitor() {
            @Override
            public void visitCall(Call n) {
                visitElement(n.getResolvedMethod());
            }

            @Override
            public void visitVariable(Variable n) {
                visitElement(n.getResolvedVariable());
            }

            @Override
            public void visitClassLiteral(ClassLiteral n) {
                visitElement(ElementUtils.castTypeElement(n.getLiteral()));
            }

            @Override
            public void visitCast(Cast n) {
                visitElement(ElementUtils.castTypeElement(n.getCastType()));
            }

            private void visitElement(Element element) {
                if (element != null && ElementUtils.isDeprecated(element)) {
                    deprecatedElements.add(element);
                }
            }
        });
        return deprecatedElements;
    }

    public Object resolveConstant() {
        return null;
    }

    public ExecutableElement resolveExecutable() {
        return null;
    }

    public VariableElement resolveVariable() {
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
        public ExecutableElement resolveExecutable() {
            return receiver.resolveExecutable();
        }

        @Override
        public VariableElement resolveVariable() {
            return receiver.resolveVariable();
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

    public static final class Cast extends DSLExpression {

        private final DSLExpression receiver;
        private final TypeMirror castType;

        public Cast(DSLExpression receiver, TypeMirror castType) {
            Objects.requireNonNull(receiver);
            this.receiver = receiver;
            this.castType = castType;
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            receiver.accept(visitor);
            visitor.visitCast(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer visitor) {
            DSLExpression newReceiver = receiver.reduceImpl(visitor);
            DSLExpression negate = this;
            if (newReceiver != receiver) {
                negate = new Cast(newReceiver, castType);
                negate.setResolvedTargetType(getResolvedTargetType());
            }
            return negate;
        }

        @Override
        public ExecutableElement resolveExecutable() {
            return receiver.resolveExecutable();
        }

        @Override
        public VariableElement resolveVariable() {
            return receiver.resolveVariable();
        }

        public TypeMirror getCastType() {
            return castType;
        }

        public DSLExpression getReceiver() {
            return receiver;
        }

        @Override
        public TypeMirror getResolvedType() {
            return castType;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Cast) {
                Cast otherCast = ((Cast) obj);
                return receiver.equals(otherCast.receiver) && ElementUtils.typeEquals(castType, otherCast.castType);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(receiver, castType);
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

    public static final class ClassLiteral extends DSLExpression {

        private final TypeMirror literal;

        public ClassLiteral(TypeMirror literal) {
            this.literal = literal;
        }

        public TypeMirror getLiteral() {
            return literal;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ClassLiteral)) {
                return false;
            }
            return ElementUtils.typeEquals(literal, ((ClassLiteral) obj).literal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(literal);
        }

        @Override
        public TypeMirror getResolvedType() {
            return ProcessorContext.getInstance().getType(Class.class);
        }

        @Override
        public void accept(DSLExpressionVisitor visitor) {
            visitor.visitClassLiteral(this);
        }

        @Override
        public DSLExpression reduce(DSLExpressionReducer visitor) {
            return this;
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
            for (DSLExpression parameter : parameters) {
                if (parameter == null) {
                    throw new NullPointerException();
                }
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Call) {
                Call other = (Call) obj;
                return Objects.equals(receiver, other.receiver) && name.equals(other.name) && parameters.equals(other.parameters);
            }
            return false;
        }

        public List<TypeMirror> getResolvedParameterTypes() {
            List<TypeMirror> types = new ArrayList<>(parameters.size());
            for (DSLExpression parameter : parameters) {
                types.add(parameter.getResolvedType());
            }
            return types;
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
        public ExecutableElement resolveExecutable() {
            if (resolvedMethod != null) {
                return resolvedMethod;
            }
            return null;
        }

        @Override
        public TypeMirror getResolvedType() {
            if (resolvedMethod == null) {
                return null;
            }
            if (resolvedMethod.getKind() == ElementKind.CONSTRUCTOR) {
                return resolvedMethod.getEnclosingElement().asType();
            } else {
                TypeMirror type = resolvedMethod.getReturnType();
                TypeMirror receiverType = receiver != null ? receiver.getResolvedType() : null;
                if (receiverType != null && type.getKind() == TypeKind.TYPEVAR && receiverType.getKind() == TypeKind.DECLARED) {
                    // try to do some basic type inference
                    TypeVariable variable = (TypeVariable) type;
                    TypeElement receiverTypeElement = ElementUtils.fromTypeMirror(receiverType);
                    Element variableElement = variable.asElement();
                    int foundIndex = -1;
                    int index = 0;
                    for (TypeParameterElement typeParam : receiverTypeElement.getTypeParameters()) {
                        if (ElementUtils.elementEquals(typeParam, variableElement)) {
                            foundIndex = index;
                            break;
                        }
                        index++;
                    }
                    DeclaredType declaredReceiverType = (DeclaredType) receiverType;
                    if (foundIndex != -1) {
                        return declaredReceiverType.getTypeArguments().get(foundIndex);
                    }
                }
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
                return ElementUtils.variableEquals(resolvedVariable, other.resolvedVariable) && Objects.equals(receiver, other.receiver);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolvedVariable, receiver);
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
        public Object resolveConstant() {
            /*
             * Unfortunately calling resolvedVariable.getConstantValue() here leads to bad
             * compilation problems later, at least in the ECJ compiler.
             */
            return super.resolveConstant();
        }

        @Override
        public VariableElement resolveVariable() {
            if (resolvedVariable != null) {
                return resolvedVariable;
            }
            return null;
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

        public boolean isCompilationFinalField() {
            VariableElement v = this.getResolvedVariable();
            if (v == null) {
                throw new IllegalStateException("not resolved yet");
            }

            if (v.getKind() != ElementKind.FIELD) {
                return false;
            }

            if (v.getModifiers().contains(Modifier.FINAL)) {
                return true;
            }

            if (ElementUtils.findAnnotationMirror(v, ProcessorContext.getInstance().getTypes().CompilerDirectives_CompilationFinal) != null) {
                return true;
            }

            return false;
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

    public abstract static class AbstractDSLExpressionVisitor implements DSLExpressionVisitor {

        @Override
        public void visitBinary(Binary n) {
        }

        @Override
        public void visitCall(Call n) {
        }

        @Override
        public void visitIntLiteral(IntLiteral n) {
        }

        public void visitClassLiteral(ClassLiteral n) {
        }

        @Override
        public void visitNegate(Negate n) {
        }

        @Override
        public void visitVariable(Variable n) {
        }

        public void visitBooleanLiteral(BooleanLiteral n) {
        }

        public void visitCast(Cast n) {

        }
    }

    public interface DSLExpressionVisitor {

        void visitBinary(Binary binary);

        void visitClassLiteral(ClassLiteral classLiteral);

        void visitNegate(Negate negate);

        void visitCall(Call binary);

        void visitVariable(Variable binary);

        void visitIntLiteral(IntLiteral binary);

        void visitBooleanLiteral(BooleanLiteral binary);

        void visitCast(Cast binary);

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
