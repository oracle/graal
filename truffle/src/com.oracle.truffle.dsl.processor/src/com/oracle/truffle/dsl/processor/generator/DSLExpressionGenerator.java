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
package com.oracle.truffle.dsl.processor.generator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.IntLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
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

    public void visitBinary(Binary binary) {
        CodeTree right = stack.pop();
        CodeTree left = stack.pop();
        stack.push(combine(left, string(" " + binary.getOperator() + " "), right));
    }

    public void visitCall(Call call) {
        ExecutableElement method = call.getResolvedMethod();
        CodeTree[] parameters = new CodeTree[method.getParameters().size()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[parameters.length - i - 1] = pop();
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        if (call.getResolvedMethod().getKind() == ElementKind.CONSTRUCTOR) {
            builder.startNew(call.getResolvedType());
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
        return CodeTreeBuilder.createBuilder().staticReference(var.getEnclosingElement().asType(), var.getSimpleName().toString()).build();
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
