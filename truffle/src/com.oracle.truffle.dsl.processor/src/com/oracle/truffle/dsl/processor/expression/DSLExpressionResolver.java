/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Cast;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.ClassLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.IntLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class DSLExpressionResolver implements DSLExpressionVisitor {

    private static final List<String> LOGIC_OPERATORS = Arrays.asList("||");
    public static final List<String> COMPARABLE_OPERATORS = Arrays.asList("<", "<=", ">", ">=");
    public static final List<String> IDENTITY_OPERATORS = Arrays.asList("==", "!=");
    private static final String CONSTRUCTOR_KEYWORD = "new";

    private final Map<String, List<ExecutableElement>> methods = new HashMap<>();
    private final Map<String, List<VariableElement>> variables = new HashMap<>();

    private final ProcessorContext context;
    private final DSLExpressionResolver parent;
    private final TypeElement accessType;

    private DSLExpressionResolver(ProcessorContext context, TypeElement accessType, DSLExpressionResolver parent, List<? extends Element> lookupElements) {
        this.context = context;
        this.parent = parent;
        this.accessType = accessType;
        processElements(lookupElements);
    }

    public TypeElement getAccessType() {
        return accessType;
    }

    public void addVariable(String variableName, VariableElement element) {
        variables.computeIfAbsent(variableName, (l) -> new ArrayList<>()).add(element);
    }

    private void processElements(List<? extends Element> lookupElements) {
        for (Element element : lookupElements) {
            ElementKind kind = element.getKind();
            if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
                methods.computeIfAbsent(getMethodName((ExecutableElement) element), (l) -> new ArrayList<>()).add((ExecutableElement) element);
            } else if (kind == ElementKind.LOCAL_VARIABLE || kind == ElementKind.PARAMETER || kind == ElementKind.FIELD || kind == ElementKind.ENUM_CONSTANT) {
                String simpleName = element.getSimpleName().toString();
                if (kind == ElementKind.PARAMETER && simpleName.equals("this")) {
                    TypeMirror type = element.asType();
                    if (type.getKind() == TypeKind.DECLARED) {
                        processElements(getMembers((TypeElement) ((DeclaredType) type).asElement()));
                    }
                }
                variables.computeIfAbsent(simpleName, (l) -> new ArrayList<>()).add((VariableElement) element);
            }
        }
    }

    public DSLExpressionResolver(ProcessorContext context, TypeElement accessType, List<? extends Element> lookupElements) {
        this(context, accessType, null, lookupElements);
    }

    public DSLExpressionResolver copy(List<? extends Element> prefixElements) {
        return new DSLExpressionResolver(context, accessType, this, prefixElements);
    }

    private static String getMethodName(ExecutableElement method) {
        if (method.getKind() == ElementKind.CONSTRUCTOR) {
            return CONSTRUCTOR_KEYWORD;
        } else {
            return method.getSimpleName().toString();
        }
    }

    public void visitCast(Cast binary) {
    }

    public void visitClassLiteral(ClassLiteral classLiteral) {
    }

    public void visitBinary(Binary binary) {
        String operator = binary.getOperator();
        TypeMirror leftType = binary.getLeft().getResolvedType();
        TypeMirror rightType = binary.getRight().getResolvedType();
        if (!ElementUtils.areTypesCompatible(leftType, rightType)) {
            throw new InvalidExpressionException(String.format("Incompatible operand types %s and %s.", ElementUtils.getSimpleName(leftType), ElementUtils.getSimpleName(rightType)));
        }

        TypeMirror booleanType = context.getType(boolean.class);
        boolean valid;
        if (LOGIC_OPERATORS.contains(operator)) {
            valid = ElementUtils.typeEquals(leftType, booleanType);
        } else if (COMPARABLE_OPERATORS.contains(operator)) {
            valid = ElementUtils.isPrimitive(leftType);
        } else if (IDENTITY_OPERATORS.contains(operator)) {
            valid = leftType.getKind().isPrimitive() || leftType.getKind() == TypeKind.DECLARED || leftType.getKind() == TypeKind.ARRAY;
        } else {
            throw new InvalidExpressionException(String.format("The operator %s is undefined.", operator));
        }
        binary.setResolvedType(booleanType);

        if (!valid) {
            throw new InvalidExpressionException(String.format("The operator %s is undefined for the argument type(s) %s %s.", operator, ElementUtils.getSimpleName(leftType),
                            ElementUtils.getSimpleName(rightType)));
        }
    }

    public void visitNegate(Negate negate) {
        TypeMirror booleanType = context.getType(boolean.class);
        TypeMirror resolvedType = negate.getResolvedType();
        if (!ElementUtils.typeEquals(resolvedType, booleanType)) {
            throw new InvalidExpressionException(String.format("The operator %s is undefined for the argument type %s.", "!", ElementUtils.getSimpleName(resolvedType)));
        }
    }

    private ExecutableElement resolveCall(Call call) {
        List<ExecutableElement> methodsWithName = this.methods.get(call.getName());
        ExecutableElement foundWithName = null;
        if (methodsWithName != null) {
            for (ExecutableElement method : methodsWithName) {
                if (matchExecutable(call, method) && ElementUtils.isVisible(accessType, method)) {
                    return method;
                }
                foundWithName = method;
            }
        }
        if (parent != null) {
            ExecutableElement parentResult = parent.resolveCall(call);
            if (parentResult != null) {
                return parentResult;
            }
        }
        return foundWithName;
    }

    private static boolean matchExecutable(Call call, ExecutableElement method) {
        if (!getMethodName(method).equals(call.getName())) {
            return false;
        }
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.size() != call.getParameters().size()) {
            return false;
        }
        int parameterIndex = 0;
        for (DSLExpression expression : call.getParameters()) {
            TypeMirror sourceType = expression.getResolvedType();
            TypeMirror targetType = parameters.get(parameterIndex).asType();
            if (!ElementUtils.isAssignable(sourceType, targetType)) {
                return false;
            }
            expression.setResolvedTargetType(targetType);
            parameterIndex++;
        }
        return true;
    }

    private VariableElement resolveVariable(Variable variable) {
        final String name = variable.getName();

        switch (name) {
            case "null":
                return new CodeVariableElement(new CodeTypeMirror(TypeKind.NULL), "null");
            case "false":
                return new CodeVariableElement(new CodeTypeMirror(TypeKind.BOOLEAN), "false");
            case "true":
                return new CodeVariableElement(new CodeTypeMirror(TypeKind.BOOLEAN), "true");
            default:
                List<VariableElement> vars = variables.get(name);
                if (vars != null && vars.size() > 0) {
                    for (VariableElement var : vars) {
                        if (ElementUtils.isVisible(accessType, var)) {
                            return var;
                        }
                    }
                    // fail in visibility check later
                    return vars.iterator().next();
                }

                if (parent != null) {
                    return parent.resolveVariable(variable);
                }

                return null;
        }
    }

    public void visitCall(Call call) {
        DSLExpression receiver = call.getReceiver();
        DSLExpressionResolver resolver;
        if (receiver == null) {
            resolver = this;
        } else {
            List<Element> elements = new ArrayList<>();
            TypeMirror type = receiver.getResolvedType();
            if (type.getKind() == TypeKind.DECLARED) {
                type = context.reloadType(type); // ensure ECJ has the type loaded
                TypeElement t = (TypeElement) ((DeclaredType) type).asElement();
                for (Element element : getMembers(t)) {
                    ElementKind kind = element.getKind();
                    if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
                        elements.add(element);
                    }
                }
            }
            resolver = new DSLExpressionResolver(context, this.accessType, elements);
        }

        ExecutableElement resolvedMethod = resolver.resolveCall(call);
        if (resolvedMethod == null) {
            String message = String.format("The method %s is undefined for the enclosing scope.", call.getName());
            throw new InvalidExpressionException(message);
        } else if (!ElementUtils.isVisible(accessType, resolvedMethod)) {
            throw new InvalidExpressionException(String.format("The method %s is not visible.", ElementUtils.getReadableSignature(resolvedMethod)));
        } else if (!matchExecutable(call, resolvedMethod)) {
            StringBuilder arguments = new StringBuilder();
            String sep = "";
            for (DSLExpression expression : call.getParameters()) {
                arguments.append(sep).append(ElementUtils.getSimpleName(expression.getResolvedType()));
                sep = ", ";
            }
            // name mismatch
            throw new InvalidExpressionException(String.format("The method %s in the type %s is not applicable for the arguments %s.",   //
                            ElementUtils.getReadableSignature(resolvedMethod),   //
                            ElementUtils.getSimpleName((TypeElement) resolvedMethod.getEnclosingElement()), arguments.toString()));
        }
        call.setResolvedMethod(resolvedMethod);
    }

    public void visitVariable(Variable variable) {
        DSLExpression receiver = variable.getReceiver();
        DSLExpressionResolver resolver;
        if (receiver == null) {
            resolver = this;
        } else {
            List<Element> elements = new ArrayList<>();
            TypeMirror type = receiver.getResolvedType();
            if (type.getKind() == TypeKind.DECLARED) {
                type = context.reloadType(type); // ensure ECJ has the type loaded
                TypeElement t = (TypeElement) ((DeclaredType) type).asElement();
                for (Element element : getMembers(t)) {
                    ElementKind kind = element.getKind();
                    if (kind == ElementKind.LOCAL_VARIABLE || kind == ElementKind.PARAMETER || kind == ElementKind.FIELD || kind == ElementKind.ENUM_CONSTANT) {
                        elements.add(element);
                    }
                }
            } else if (type.getKind() == TypeKind.ARRAY) {
                elements.add(new CodeVariableElement(context.getType(int.class), "length"));
            }
            resolver = new DSLExpressionResolver(context, this.accessType, elements);
        }

        VariableElement var = resolver.resolveVariable(variable);
        if (var == null) {
            throw new InvalidExpressionException(String.format("%s cannot be resolved.", variable.getName()));
        } else if (!ElementUtils.isVisible(accessType, var)) {
            throw new InvalidExpressionException(String.format("%s is not visible.", variable.getName()));
        }
        variable.setResolvedVariable(var);

    }

    private List<? extends Element> getMembers(TypeElement t) {
        return context.getEnvironment().getElementUtils().getAllMembers(t);
    }

    public void visitBooleanLiteral(BooleanLiteral binary) {
        binary.setResolvedType(context.getType(boolean.class));
    }

    public void visitIntLiteral(IntLiteral binary) {
        try {
            binary.setResolvedType(context.getType(int.class));

            final int base;
            final String literal;

            if (binary.getLiteral().startsWith("0x")) {
                base = 16;
                literal = binary.getLiteral().substring(2);
            } else if (binary.getLiteral().startsWith("0b")) {
                base = 2;
                literal = binary.getLiteral().substring(2);
            } else if (binary.getLiteral().startsWith("0")) {
                base = 8;
                literal = binary.getLiteral();
            } else {
                base = 10;
                literal = binary.getLiteral();
            }

            binary.setResolvedValueInt(Integer.parseInt(literal, base));
        } catch (NumberFormatException e) {
            throw new InvalidExpressionException(String.format("Type mismatch: cannot convert from String '%s' to int", binary.getLiteral()));
        }
    }

}
