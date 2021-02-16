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
package com.oracle.truffle.dsl.processor.generator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.expression.DSLExpression;
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
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class DSLExpressionGenerator implements DSLExpressionVisitor {

    private final Map<Variable, CodeTree> bindings;
    private final CodeTree root;
    private final Deque<CodeTree> stack = new ArrayDeque<>();

    DSLExpressionGenerator(CodeTree root, Map<Variable, CodeTree> bindings) {
        this.bindings = bindings;
        this.root = root;
    }

    public void visitClassLiteral(ClassLiteral classLiteral) {
        push(CodeTreeBuilder.createBuilder().typeLiteral(classLiteral.getLiteral()).build());
    }

    public void visitBinary(Binary binary) {
        CodeTree right = stack.pop();
        CodeTree left = stack.pop();
        stack.push(combine(left, string(" " + binary.getOperator() + " "), right));
    }

    public void visitCast(Cast cast) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.string("(");
        builder.cast(cast.getCastType());
        builder.tree(pop());
        builder.string(")");
        push(builder.build());
    }

    public void visitCall(Call call) {
        ExecutableElement method = call.getResolvedMethod();
        CodeTree[] parameters = new CodeTree[method.getParameters().size()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[parameters.length - i - 1] = pop();
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        if (call.getResolvedMethod().getKind() == ElementKind.CONSTRUCTOR) {
            TypeMirror type = call.getResolvedType();
            if (type.getKind() == TypeKind.DECLARED && !((DeclaredType) type).getTypeArguments().isEmpty()) {
                builder.startNew(ElementUtils.getDeclaredName(((DeclaredType) type), false) + "<>");
            } else {
                builder.startNew(call.getResolvedType());
            }
        } else if (call.getReceiver() == null) {
            if (isStatic(method)) {
                builder.startStaticCall(method);
            } else {
                if (root != null) {
                    builder.tree(root).string(".");
                }
                builder.startCall(method.getSimpleName().toString());
            }
        } else {
            if (isStatic(method)) {
                throw new AssertionError("Static calls must not have receivers.");
            }
            builder.startCall(pop(), method.getSimpleName().toString());
        }
        for (CodeTree parameter : parameters) {
            builder.tree(parameter);
        }
        builder.end();

        push(builder.build());
    }

    public void visitIntLiteral(IntLiteral binary) {
        push(string(binary.getLiteral()));
    }

    public void visitBooleanLiteral(BooleanLiteral binary) {
        push(string(binary.getLiteral() ? "true" : "false"));
    }

    public void visitNegate(Negate negate) {
        push(combine(string("!"), combine(string("("), pop(), string(")"))));
    }

    public void visitVariable(Variable variable) {
        VariableElement resolvedVariable = variable.getResolvedVariable();
        CodeTree tree;
        if (variable.getResolvedType().getKind() == TypeKind.NULL) {
            tree = CodeTreeBuilder.singleString("null");
        } else if (variable.getReceiver() == null) {

            if (isStatic(resolvedVariable)) {
                tree = staticReference(resolvedVariable);
            } else {
                tree = bindings.get(variable);
                boolean bound = true;
                if (tree == null) {
                    tree = string(resolvedVariable.getSimpleName().toString());
                    bound = false;
                }
                if (root != null && !bound) {
                    tree = combine(root, string("."), tree);
                }
            }
        } else {
            if (isStatic(resolvedVariable)) {
                throw new AssertionError("Static variables cannot have receivers.");
            }
            tree = combine(pop(), string("."), string(resolvedVariable.getSimpleName().toString()));
        }
        push(tree);
    }

    private static boolean isStatic(Element element) {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    private static CodeTree combine(CodeTree tree1, CodeTree tree2) {
        return new CodeTreeBuilder(null).startGroup().tree(tree1).tree(tree2).end().build();
    }

    private static CodeTree combine(CodeTree tree1, CodeTree tree2, CodeTree tree3) {
        return new CodeTreeBuilder(null).startGroup().tree(tree1).tree(tree2).tree(tree3).end().build();
    }

    private static CodeTree string(String s) {
        return CodeTreeBuilder.singleString(s);
    }

    private static CodeTree staticReference(VariableElement var) {
        Element enclosing = var.getEnclosingElement();
        if (enclosing == null) {
            return CodeTreeBuilder.singleString(var.getSimpleName().toString());
        } else {
            return CodeTreeBuilder.createBuilder().staticReference(enclosing.asType(), var.getSimpleName().toString()).build();
        }
    }

    private void push(CodeTree tree) {
        stack.push(tree);
    }

    private CodeTree pop() {
        return stack.pop();
    }

    public static CodeTree write(DSLExpression expression, CodeTree root, Map<Variable, CodeTree> bindings) {
        DSLExpressionGenerator writer = new DSLExpressionGenerator(root, bindings);
        expression.accept(writer);
        return combine(string("("), writer.pop(), string(")"));
    }

}
