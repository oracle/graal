/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hightiercodegen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hightiercodegen.variables.ResolvedVar;
import org.graalvm.compiler.hightiercodegen.variables.VariableAllocation;
import org.graalvm.compiler.hightiercodegen.variables.VariableMap;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class CodeGenTool {

    protected final CodeBuffer codeBuffer;
    /**
     * Stores variable allocation result for a method.
     */
    public VariableMap variableMap;

    protected final VariableAllocation variableAllocation;

    /**
     * Used for generating method-scoped unique IDs.
     *
     * Its value is reset to 0 in {@link CodeGenTool#prepareForMethod(StructuredGraph)}.
     */
    private int methodScopeUniqueID = 0;

    protected CodeGenTool(CodeBuffer codeBuffer, VariableAllocation variableAllocation) {
        this.codeBuffer = codeBuffer;
        this.variableAllocation = variableAllocation;
    }

    /**
     * Generates an efficient representation of an integer value literal.
     */
    public static String getEfficientIntLiteral(int i) {
        StringBuilder sb = new StringBuilder();
        /*
         * Once a number gets larger than 10^6, representing it in hex gets cheaper in terms of
         * bytes.
         *
         * 10^6 takes 7 decimal digits and also 7 hex digits (0xF4240) to represent. After that
         * point, hex is more efficient.
         */
        int abs = Math.abs(i);
        if (abs >= 1000000) {
            if (i < 0) {
                sb.append('-');
            }
            sb.append("0x");
            sb.append(Integer.toHexString(abs));
        } else {
            // Write in decimal
            sb.append(i);
        }

        return sb.toString();
    }

    /**
     * Prepare the current object to be ready for use in generating code for a method.
     */
    public void prepareForMethod(StructuredGraph g) {
        this.variableMap = variableAllocation.compute(g, this);
        this.methodScopeUniqueID = 0;
    }

    public boolean declared(Node n) {
        return n instanceof ValueNode valueNode && getAllocatedVariable(valueNode) != null;
    }

    /**
     * Generate a unique ID within the scope of the current method.
     *
     * Invariant: each call of this method will return a different value for the current method
     * under lowering.
     */
    public int genUniqueID() {
        methodScopeUniqueID++;
        return methodScopeUniqueID;
    }

    public void lowerStatement(Node n) {
        nodeLowerer().lowerStatement(n);
    }

    public void lowerValue(ValueNode n) {
        nodeLowerer().lowerValue(n);
    }

    public void genLabeledBlockHeader(String label) {
        codeBuffer.emitLabel(label);
        codeBuffer.emitScopeBegin();
    }

    /**
     * Generates a binary operation. For example, if the operator is a plus, this generates:
     *
     * <pre>
     * (leftOperand + rightOperand)
     * </pre>
     */
    public abstract void genBinaryOperation(Keyword operator, ValueNode leftOperand, ValueNode righOperand);

    /**
     * Generates a unary operation. For example, if the operator is a {@code not}, this generates:
     *
     * <pre>
     * (~operand)
     * </pre>
     */
    public abstract void genUnaryOperation(Keyword operator, ValueNode operand);

    public void lower(IEmitter l) {
        l.lower(this);
    }

    public int getCodeSize() {
        return codeBuffer.codeSize();
    }

    public CodeBuffer getCodeBuffer() {
        return codeBuffer;
    }

    public abstract NodeLowerer nodeLowerer();

    public void genFunctionEnd() {
        codeBuffer.emitFunctionEnd();
    }

    public abstract void genMethodHeader(StructuredGraph graph, ResolvedJavaMethod method, List<ParameterNode> parameters);

    public void genArrayLoad(ValueNode index, ValueNode array) {
        nodeLowerer().lowerValue(array);
        genArrayAccessPrefix();
        nodeLowerer().lowerValue(index);
        genArrayAccessPostfix();
    }

    public void genArrayStore(IEmitter index, ValueNode array, ValueNode value) {
        nodeLowerer().lowerValue(array);
        genArrayAccessPrefix();
        lower(index);
        genArrayAccessPostfix();
        genAssignment();
        nodeLowerer().lowerValue(value);
    }

    protected abstract void genArrayAccessPostfix();

    protected abstract void genArrayAccessPrefix();

    public ResolvedVar getAllocatedVariable(ValueNode n) {
        return variableMap.getVarByNode(n);
    }

    public VariableAllocation getVariableAllocation() {
        return variableAllocation;
    }

    public abstract void genClassHeader(ResolvedJavaType type);

    public void genClassEnd() {
        genScopeEnd();
        codeBuffer.emitNewLine();
        codeBuffer.emitNewLine();
    }

    public void genIfHeader(LogicNode logicNode) {
        codeBuffer.emitIfHeaderLeft();
        lowerValue(logicNode);
        codeBuffer.emitIfHeaderRight();
    }

    public void genElseHeader() {
        codeBuffer.emitElse();
    }

    public void genScopeEnd() {
        codeBuffer.emitScopeEnd();
        codeBuffer.emitNewLine();
    }

    public void genSwitchHeader(ValueNode node) {
        codeBuffer.emitSwitchHeaderLeft();
        lowerValue(node);
        codeBuffer.emitSwitchHeaderRight();
    }

    public void genSwitchHeader(String controlVariable) {
        codeBuffer.emitSwitchHeaderLeft();
        codeBuffer.emitText(controlVariable);
        codeBuffer.emitSwitchHeaderRight();
    }

    public void genWhileTrueHeader() {
        codeBuffer.emitWhileTrueHeader();
    }

    public void genLoopContinue() {
        codeBuffer.emitContinue();
    }

    public void genVoidReturn() {
        codeBuffer.emitReturn();
    }

    public void genReturn(String returnValue) {
        genReturnPrefix();
        codeBuffer.emitText(returnValue);
        codeBuffer.emitInsEnd();
    }

    public void genReturn(ValueNode returnValue) {
        genReturnPrefix();
        lowerValue(returnValue);
    }

    private void genReturnPrefix() {
        codeBuffer.emitReturnSymbol();
        codeBuffer.emitWhiteSpace();
    }

    public void genSwitchCase(int... vals) {
        codeBuffer.emitIntCaseChain(vals);
    }

    public void genSwitchDefaultCase() {
        codeBuffer.emitDefaultCase();
    }

    public void genBreak() {
        codeBuffer.emitBreak();
    }

    public void genBreak(String label) {
        assert label != null;
        codeBuffer.emitBreakLabel(label);
    }

    public void genLabel(String label) {
        codeBuffer.emitLabel(label);
    }

    public void genCondition(CanonicalCondition cond) {
        codeBuffer.emitCondition(cond);
    }

    public void genResolvedVarAccess(String name) {
        codeBuffer.emitText(name);
    }

    public void genResolvedVarDeclPrefix(String name) {
        codeBuffer.emitDeclPrefix(name);
    }

    public abstract void genResolvedVarDeclPostfix(String comment);

    public void genResolvedVarAssignmentPrefix(String name) {
        codeBuffer.emitText(name);
        codeBuffer.emitWhiteSpace();
        codeBuffer.emitAssignmentSymbol();
        codeBuffer.emitWhiteSpace();
    }

    public abstract void genNewInstance(ResolvedJavaType t);

    /**
     * Generates a declaration without initialization. Marks the definition as lowered.
     *
     * @param node used to look up the variable name to be declared
     */
    public abstract void genEmptyDeclaration(ValueNode node);

    /**
     * Generates a comma-separated list of the given inputs.
     *
     * If an input is a {@link Node}, it is lowered in place, otherwise the object is converted to a
     * string.
     */
    public abstract void genCommaList(IEmitter... inputs);

    public void genCommaList(List<IEmitter> inputs) {
        genCommaList(inputs.toArray(new IEmitter[0]));
    }

    public String getStringLiteral(String s) {
        return codeBuffer.getStringLiteral(s);
    }

    public abstract void genComment(String comment);

    public abstract void genInlineComment(String comment);

    public void genTryBlock() {
        codeBuffer.emitTry();
    }

    public void genCatchBlockPrefix(String expName) {
        codeBuffer.emitCatch(expName);
    }

    public abstract void genThrow(ValueNode exception);

    @SuppressWarnings("deprecation")
    public String getExceptionObjectId(ExceptionObjectNode n) {
        return "exception_object_" + n.getId();
    }

    public abstract void genAssignment();

    public abstract void genNull();

    public abstract void genFieldName(ResolvedJavaField field);

    public abstract void genFieldName(Field field);

    public abstract void genTypeName(ResolvedJavaType type);

    public abstract void genTypeName(Class<?> type);

    public abstract void genMethodName(ResolvedJavaMethod method);

    public abstract void genMethodName(Method type);

    public void genPropertyAccess(IEmitter receiver, IEmitter prop) {
        if (receiver != null) {
            lower(receiver);
            genPropertyAccessInfix();
        }
        lower(prop);
    }

    /**
     * Generates code that is between a receiver and a property.
     */
    protected abstract void genPropertyAccessInfix();

    public abstract void genFunctionCall(IEmitter receiver, IEmitter fun, IEmitter... params);

    /**
     * Generates code that is between the function name and the start of the parameters.
     */
    protected abstract void genFunctionParameterInfix();

    /**
     * Generates code that is after the function parameters.
     */
    protected abstract void genFunctionParameterPostfix();

    public abstract void genStaticMethodReference(ResolvedJavaMethod m);

    public abstract void genStaticField(ResolvedJavaField m);

    public abstract void genStaticCall(ResolvedJavaMethod m, IEmitter... params);

    public abstract void genIndirectCall(IEmitter address, IEmitter... params);

    /**
     * Generates code that should not be reached during runtime. The generated code should throw an
     * error or similar.
     */
    public abstract void genShouldNotReachHere(String msg);

    @Override
    public String toString() {
        return codeBuffer.toString();
    }
}
