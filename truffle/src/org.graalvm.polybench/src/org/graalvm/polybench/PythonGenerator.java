/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import org.graalvm.polybench.ast.Decl;
import org.graalvm.polybench.ast.Decl.Compound;
import org.graalvm.polybench.ast.Decl.Main;
import org.graalvm.polybench.ast.Decl.Raw;
import org.graalvm.polybench.ast.Decl.Subroutine;
import org.graalvm.polybench.ast.Decl.Subroutine.Constructor;
import org.graalvm.polybench.ast.Decl.Variable;
import org.graalvm.polybench.ast.Expr;
import org.graalvm.polybench.ast.Expr.Atom.Bool;
import org.graalvm.polybench.ast.Expr.Atom.EmptyList;
import org.graalvm.polybench.ast.Expr.Atom.Floating;
import org.graalvm.polybench.ast.Expr.Atom.Int;
import org.graalvm.polybench.ast.Expr.Atom.Null;
import org.graalvm.polybench.ast.Expr.BinaryOp;
import org.graalvm.polybench.ast.Expr.FunctionCall;
import org.graalvm.polybench.ast.Expr.AppendToListCall;
import org.graalvm.polybench.ast.Expr.ConstructorCall;
import org.graalvm.polybench.ast.Expr.ListLengthCall;
import org.graalvm.polybench.ast.Expr.ListSortCall;
import org.graalvm.polybench.ast.Expr.LogCall;
import org.graalvm.polybench.ast.Expr.Reference.CompoundReference;
import org.graalvm.polybench.ast.Expr.Reference.Ident;
import org.graalvm.polybench.ast.Expr.Reference.Super;
import org.graalvm.polybench.ast.Expr.Reference.This;
import org.graalvm.polybench.ast.Expr.StringConcatenation;
import org.graalvm.polybench.ast.Operator;
import org.graalvm.polybench.ast.Stat;
import org.graalvm.polybench.ast.Stat.Assign;
import org.graalvm.polybench.ast.Stat.Block;
import org.graalvm.polybench.ast.Stat.Comment;
import org.graalvm.polybench.ast.Stat.For;
import org.graalvm.polybench.ast.Stat.Foreach;
import org.graalvm.polybench.ast.Stat.If;
import org.graalvm.polybench.ast.Stat.Return;
import org.graalvm.polybench.ast.Stat.Throw;
import org.graalvm.polybench.ast.Tree;
import org.graalvm.polybench.ast.Tree.Program;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

/**
 * Generates a Python program from AST.
 */
class PythonGenerator implements LanguageGenerator {
    private static final int INDENTATION_SPACES = 4;
    private ByteArrayOutputStream outputStream;
    private Deque<Class<? extends Tree>> context;

    PythonGenerator() {
        this.outputStream = new ByteArrayOutputStream();
        this.context = new ArrayDeque<>();
    }

    @Override
    public byte[] generate(Program program) throws IOException {
        reset();
        visitDecl(program);
        return this.outputStream.toByteArray();
    }

    private void visitDecl(Tree tree) throws IOException {
        if (tree instanceof Program program) {
            for (Decl decl : program.declarations()) {
                visitDecl(decl);
            }
            defineHelperFunctions();
            visitDecl(program.main());
        } else if (tree instanceof Main main) {
            visitDecl(main.subroutine());
            visitStatement(FunctionCall.of(new Ident(main.subroutine().name())));
            emptyLine();
        } else if (tree instanceof Compound compound) {
            if (compound.baseClasses() == null || compound.baseClasses().length == 0) {
                addLine("class " + compound.name() + ":");
            } else {
                String baseClasses = String.join(", ",
                                Arrays.stream(compound.baseClasses()).map(Ident::value).toList());
                addLine("class " + compound.name() + "(" + baseClasses + "):");
            }
            enterContext(compound);
            for (Decl member : compound.members()) {
                visitDecl(member);
            }
            exitContext();
            emptyLine();
        } else if (tree instanceof Constructor constructor) {
            ArrayList<String> params = new ArrayList<>();
            params.add("self");
            if (constructor.params() != null && constructor.params().length > 0) {
                params.addAll(Arrays.stream(constructor.params()).map(Variable::name).toList());
            }
            addLine("def __init__(" + String.join(", ", params) + "):");
            enterContext(constructor);
            visitStatement(constructor.body());
            exitContext();
            emptyLine();
        } else if (tree instanceof Subroutine subroutine) {
            ArrayList<String> params = new ArrayList<>();
            if (peekContext() == Compound.class) {
                params.add("self");
            }
            if (subroutine.params() != null && subroutine.params().length > 0) {
                params.addAll(Arrays.stream(subroutine.params()).map(Variable::name).toList());
            }
            addLine("def " + subroutine.name() + "(" + String.join(", ", params) + "):");
            enterContext(subroutine);
            visitStatement(subroutine.body());
            exitContext();
            emptyLine();
        } else if (tree instanceof Variable variable) {
            visitStatement(variable);
        } else if (tree instanceof Raw raw) {
            addRaw(raw.code());
        } else if (tree instanceof Comment comment) {
            addLine("# " + comment.text());
        } else {
            String msg = "Failed at parsing an AST node into Python! " +
                            "Expected a declaration but found " + tree.getClass().getName() + " node!";
            throw new IllegalArgumentException(msg);
        }
    }

    private void visitStatement(Tree tree) throws IOException {
        if (tree instanceof Block block) {
            for (Stat stat : block.stats()) {
                visitStatement(stat);
            }
        } else if (tree instanceof If ifStat) {
            addLine("if " + visitExpression(ifStat.expr()) + ":");
            enterContext(ifStat);
            visitStatement(ifStat.thenPart());
            exitContext();
            if (ifStat.elsePart() != null) {
                addLine("else:");
                enterContext(ifStat);
                visitStatement(ifStat.elsePart());
                exitContext();
            }
        } else if (tree instanceof For forStat) {
            visitStatement(forStat.init());
            addLine("while " + visitExpression(forStat.cond()) + ":");
            enterContext(forStat);
            visitStatement(forStat.body());
            visitStatement(forStat.update());
            exitContext();
        } else if (tree instanceof Foreach foreach) {
            addLine("for " + foreach.iterator().name() +
                            " in " + visitExpression(foreach.collection()) + ":");
            enterContext(foreach);
            visitStatement(foreach.body());
            exitContext();
        } else if (tree instanceof Assign assign) {
            addLine(visitExpression(assign.lhs()) + " = " + visitExpression(assign.rhs()));
        } else if (tree instanceof Return returnStat) {
            addLine("return " + visitExpression(returnStat.expr()));
        } else if (tree instanceof Throw throwStat) {
            addLine("raise Exception(" + visitExpression(throwStat.message()) + ")");
        } else if (tree instanceof FunctionCall functionCall) {
            addLine(visitExpression(functionCall));
        } else if (tree instanceof ConstructorCall constructorCall) {
            addLine(visitExpression(constructorCall));
        } else if (tree instanceof LogCall logCall) {
            addLine(visitExpression(logCall));
        } else if (tree instanceof AppendToListCall append) {
            addLine(visitExpression(append));
        } else if (tree instanceof ListLengthCall length) {
            addLine(visitExpression(length));
        } else if (tree instanceof ListSortCall sort) {
            addLine(visitExpression(sort));
        } else if (tree instanceof Variable variable) {
            if (variable.initialValue() != null) {
                visitStatement(new Assign(new Ident(variable.name()), variable.initialValue()));
            }
        } else if (tree instanceof Comment comment) {
            addLine("# " + comment.text());
        } else {
            String msg = "Failed at parsing an AST node into Python! " +
                            "Expected a statement but found " + tree.getClass().getName() + " node!";
            throw new IllegalArgumentException(msg);
        }
    }

    private String visitExpression(Tree tree) {
        if (tree instanceof BinaryOp binary) {
            return visitExpression(binary.lhs()) + visitOperator(binary.op()) + visitExpression(binary.rhs());
        } else if (tree instanceof FunctionCall functionCall) {
            String args = "";
            if (functionCall.arguments() != null && functionCall.arguments().length > 0) {
                args = String.join(", ",
                                Arrays.stream(functionCall.arguments()).map(this::visitExpression).toList());
            }
            return visitExpression(functionCall.ref()) + "(" + args + ")";
        } else if (tree instanceof ConstructorCall constructorCall) {
            String args = "";
            if (constructorCall.arguments() != null && constructorCall.arguments().length > 0) {
                args = String.join(", ",
                                Arrays.stream(constructorCall.arguments()).map(this::visitExpression).toList());
            }
            return visitExpression(constructorCall.ident()) + "(" + args + ")";
        } else if (tree instanceof LogCall logCall) {
            return "print(" + visitExpression(logCall.message()) + ")";
        } else if (tree instanceof AppendToListCall append) {
            return visitExpression(append.list()) + ".append(" + visitExpression(append.element()) + ")";
        } else if (tree instanceof ListLengthCall length) {
            return "len(" + visitExpression(length.list()) + ")";
        } else if (tree instanceof ListSortCall listSortCall) {
            return "sorted(" + visitExpression(listSortCall.list()) + ")";
        } else if (tree instanceof StringConcatenation concatenation) {
            return String.join(visitOperator(Operator.PLUS),
                            Arrays.stream(concatenation.arguments()).map(expr -> "str(" + visitExpression(expr) + ")").toList());
        } else if (tree instanceof Ident ident) {
            return ident.value();
        } else if (tree instanceof This) {
            return "self";
        } else if (tree instanceof Super) {
            return "super()";
        } else if (tree instanceof CompoundReference compoundRef) {
            return String.join(".",
                            Arrays.stream(compoundRef.components()).map(this::visitExpression).toList());
        } else if (tree instanceof Floating floating) {
            return Float.toString(floating.value());
        } else if (tree instanceof Int intExpr) {
            return Integer.toString(intExpr.value());
        } else if (tree instanceof Bool bool) {
            return bool.value() ? "True" : "False";
        } else if (tree instanceof Null) {
            return "None";
        } else if (tree instanceof Expr.Atom.String str) {
            String sanitized = str.value().replace("\"", "\\\"").replace("\n", "\\n");
            return "\"" + sanitized + "\"";
        } else if (tree instanceof EmptyList) {
            return "[]";
        } else {
            String msg = "Failed at parsing an AST node into Python! " +
                            "Expected an expression but found " + tree.getClass().getName() + " node!";
            throw new IllegalArgumentException(msg);
        }
    }

    private static String visitOperator(Operator operator) {
        if (Operator.PLUS.equals(operator)) {
            return " + ";
        } else if (Operator.MINUS.equals(operator)) {
            return " - ";
        } else if (Operator.MUL.equals(operator)) {
            return " * ";
        } else if (Operator.DIV.equals(operator)) {
            return " / ";
        } else if (Operator.EQUALS.equals(operator)) {
            return " == ";
        } else if (Operator.NOT_EQUALS.equals(operator)) {
            return " != ";
        } else if (Operator.LESS_THAN.equals(operator)) {
            return " < ";
        } else if (Operator.GREATER_THAN.equals(operator)) {
            return " > ";
        } else if (Operator.GREATER_OR_EQUALS.equals(operator)) {
            return " >= ";
        } else if (Operator.OR.equals(operator)) {
            return " or ";
        }
        throw new IllegalArgumentException("Unknown operator " + operator + " encountered!");
    }

    private void defineHelperFunctions() throws IOException {
        String[] helperFunctions = {
                        roundToTwoDecimals(),
                        currentTimeNanos(),
                        ceil(),
                        floor(),
                        checkIfFunctionExists(),
                        sliceList()
        };
        for (String helper : helperFunctions) {
            visitDecl(new Raw(helper.getBytes()));
        }
    }

    private static String roundToTwoDecimals() {
        return "" +
                        "def roundToTwoDecimals(value):\n" +
                        "    return f\"{value:.2f}\"\n\n";
    }

    private static String currentTimeNanos() {
        return "" +
                        "import time\n" +
                        "def currentTimeNanos():\n" +
                        "    return time.time_ns()\n\n";
    }

    private static String ceil() {
        return "" +
                        "import math\n" +
                        "def ceil(x):\n" +
                        "    return int(math.ceil(x))\n\n";
    }

    private static String floor() {
        return "" +
                        "import math\n" +
                        "def floor(x):\n" +
                        "    return int(math.floor(x))\n\n";
    }

    private static String checkIfFunctionExists() {
        return "" +
                        "def checkIfFunctionExists(functionName):\n" +
                        "    globals_symbol_table = globals()\n" +
                        "    return functionName in globals_symbol_table and callable(globals_symbol_table[functionName])\n\n";
    }

    private static String sliceList() {
        return "" +
                        "def sliceList(lst, fromIndex, toIndex):\n" +
                        "    return lst[fromIndex:toIndex]\n\n";
    }

    private void reset() {
        outputStream.reset();
        context.clear();
    }

    private void enterContext(Tree tree) {
        context.push(tree.getClass());
    }

    private void exitContext() {
        context.pop();
    }

    private Class<? extends Tree> peekContext() {
        return context.isEmpty() ? null : context.peek();
    }

    private void addLine(String line) throws IOException {
        String fullLine = indentation() + line + "\n";
        outputStream.write(fullLine.getBytes());
    }

    private void emptyLine() throws IOException {
        outputStream.write("\n".getBytes());
    }

    private void addRaw(byte[] bytes) throws IOException {
        outputStream.write(bytes);
    }

    private String indentation() {
        return " ".repeat(INDENTATION_SPACES * indentationLevel());
    }

    private int indentationLevel() {
        return context.size();
    }
}
