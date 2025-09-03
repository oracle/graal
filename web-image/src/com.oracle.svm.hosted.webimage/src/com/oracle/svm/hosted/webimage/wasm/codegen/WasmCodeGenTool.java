/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.oracle.svm.hosted.webimage.options.WebImageOptions.CommentVerbosity;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Relocation;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.id.KnownIds;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.hightiercodegen.Keyword;
import com.oracle.svm.webimage.hightiercodegen.variables.VariableAllocation;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Macro Assembler for a WASM function.
 */
public abstract class WasmCodeGenTool extends CodeGenTool {

    private final CoreProviders providers;

    public final WebImageWasmCompilationResult compilationResult;

    /**
     * Thread-safe factory for {@link WasmId}s, shared across all code gen tools.
     */
    public final WasmIdFactory idFactory;
    private final KnownIds knownIds;
    public final WebImageWasmProviders wasmProviders;
    private final WebImageWasmNodeLowerer nodeLowerer;

    protected final WasmBlockContext topContext;
    private WasmBlockContext currentContext;

    protected final ResolvedJavaMethod method;

    @SuppressWarnings("this-escape")
    protected WasmCodeGenTool(CoreProviders provider, VariableAllocation variableAllocation, WebImageWasmCompilationResult compilationResult, WebImageWasmProviders wasmProviders,
                    WasmBlockContext topContext, StructuredGraph graph) {
        super(null, variableAllocation);
        this.providers = provider;
        this.compilationResult = compilationResult;
        this.idFactory = wasmProviders.idFactory();
        this.knownIds = wasmProviders.knownIds();
        this.wasmProviders = wasmProviders;
        this.method = graph.method();
        this.nodeLowerer = wasmProviders.getNodeLowerer(this);
        this.topContext = topContext;
        this.currentContext = topContext;
    }

    @Override
    public CoreProviders getProviders() {
        return providers;
    }

    public WebImageWasmProviders getWasmProviders() {
        return wasmProviders;
    }

    public KnownIds getKnownIds() {
        return knownIds;
    }

    /**
     * Returns the list of instructions in the current scope.
     */
    public Instructions getInstructions() {
        return currentContext.currentBlock;
    }

    /**
     * Switches to a child scope with the given instruction list.
     *
     * @param instructions List into which generated instructions are placed.
     * @param label Scope label. When calling {@link #parentScope(Object)}, the same label must be
     *            provided to check for well-nestedness.
     */
    public void childScope(Instructions instructions, Object label) {
        currentContext = currentContext.getChildContext(instructions, label);
    }

    /**
     * Switches back to the parent scope.
     *
     * @param expectedLabel The label of the current scope, provided in
     *            {@link #childScope(Instructions, Object)}.
     */
    public void parentScope(Object expectedLabel) {
        assert currentContext.parent != null;
        // We are closing the correct block.
        assert currentContext.checkLabel(expectedLabel);
        currentContext = currentContext.parent;
    }

    public void recordCall(int posBefore, int posAfter, InvokeTarget target, boolean direct) {
        compilationResult.recordCall(posBefore, posAfter - posBefore, target, null, direct);
    }

    private Relocation recordRelocation(Relocation relocation) {
        compilationResult.recordDataPatch(0, relocation.target);
        return relocation;
    }

    public Relocation getRelocation(Reference reference) {
        return recordRelocation(new Relocation(reference));
    }

    public Relocation getConstantRelocation(JavaConstant constant) {
        return recordRelocation(Relocation.forConstant(constant));
    }

    /**
     * Performs all necessary actions for performing a function call.
     * <p>
     * Always use this method when generating a Wasm call to a Java method.
     */
    public abstract Instruction.AbstractCall getCall(InvokeTarget target, boolean direct, Instruction.AbstractCall callInstruction);

    public void genInst(Instruction inst, Node n) {
        genInst(inst, getNodeComment(n));
    }

    public void genInst(Instruction inst) {
        genInst(inst, (Object) null);
    }

    /**
     * Inserts the given instruction into the current block.
     */
    public void genInst(Instruction inst, Object comment) {
        if (comment != null && inst.getComment() == null) {
            inst.setComment(comment);
        }
        currentContext.currentBlock.add(inst);
    }

    @Override
    public void genComment(String comment) {
        // Ignore comments generated this way
    }

    @Override
    public void genInlineComment(String comment) {
        // Ignore comments generated this way
    }

    /**
     * Called at the beginning of the function. Use this to generate preamble code.
     */
    public void lowerPreamble() {
        // Do nothing by default
    }

    /**
     * Creates a catch block for catching java exceptions in the given try block.
     *
     * @param tryBlock The try block to attach the catch block to
     * @param blockLowerer Called within catch scope (see {@link #childScope}) to generate the
     *            contents of the catch block
     */
    public void lowerCatchBlock(Instruction.Try tryBlock, Runnable blockLowerer) {
        Instructions catchInstructions = tryBlock.addCatch(getKnownIds().getJavaThrowableTag());
        childScope(catchInstructions, tryBlock);

        if (BinaryenCompat.usesBinaryen()) {
            Relocation pop = new Relocation(new BinaryenCompat.Pop(nodeLowerer.exceptionObjectVariable.getVariableType()));
            pop.setValue(new Instruction.Nop());
            genInst(nodeLowerer.exceptionObjectVariable.setter(pop), "Store exception object");
        } else {
            genInst(nodeLowerer.exceptionObjectVariable.setter(new Instruction.Nop()), "Store exception object");
        }

        lowerCatchPreamble();

        blockLowerer.run();

        parentScope(tryBlock);
    }

    protected void lowerCatchPreamble() {
        /*
         * After a catch, the stack pointer is still set to the method where the throw happened.
         * Because of that we need to restore the stack pointer to the one stored in the local
         * variable. This ensures that for all IR nodes, the stack pointer has a consistent and
         * correct value.
         */
        genInst(getKnownIds().stackPointer.setter(nodeLowerer.stackPointerHolder.getter()), "Restore stack pointer");

    }

    public Instruction lowerExpression(ValueNode node, WasmIRWalker.Requirements reqs) {
        return nodeLowerer().lowerExpression(node, reqs);
    }

    public Instruction lowerExpression(ValueNode node) {
        return nodeLowerer().lowerExpression(node);
    }

    public Object getNodeComment(Node n) {
        if (WebImageWasmOptions.genComments(CommentVerbosity.VERBOSE)) {
            return nodeLowerer().nodeDebugInfo(n);
        }
        return n;
    }

    public void finish() {
        assert currentContext == topContext : currentContext;
    }

    // region Unsupported operations

    @Override
    public void genBinaryOperation(Keyword operator, ValueNode leftOperand, ValueNode righOperand) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genUnaryOperation(Keyword operator, ValueNode operand) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public WebImageWasmNodeLowerer nodeLowerer() {
        return nodeLowerer;
    }

    @Override
    public void genMethodHeader(StructuredGraph graph, ResolvedJavaMethod resolvedJavaMethod, List<ParameterNode> parameters) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    protected void genArrayAccessPostfix() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    protected void genArrayAccessPrefix() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genClassHeader(ResolvedJavaType type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genResolvedVarDeclPostfix(String comment) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genNewInstance(ResolvedJavaType t) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genNewArray(ResolvedJavaType arrayType, IEmitter length) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genEmptyDeclaration(ValueNode node) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genCommaList(IEmitter... inputs) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genThrow(ValueNode exception) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genAssignment() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genNull() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genFieldName(ResolvedJavaField field) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genFieldName(Field field) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genTypeName(ResolvedJavaType type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genTypeName(Class<?> type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genMethodName(ResolvedJavaMethod resolvedJavaMethod) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genMethodName(Method type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    protected void genPropertyAccessInfix() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genFunctionCall(IEmitter receiver, IEmitter fun, IEmitter... params) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    protected void genFunctionParameterInfix() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    protected void genFunctionParameterPostfix() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genStaticMethodReference(ResolvedJavaMethod m) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genStaticField(ResolvedJavaField m) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genStaticCall(ResolvedJavaMethod m, IEmitter... params) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genIndirectCall(IEmitter address, IEmitter... params) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void genShouldNotReachHere(String msg) {
        throw GraalError.unimplementedOverride();
    }

    // endregion
}
