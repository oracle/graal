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

package com.oracle.svm.hosted.webimage.wasm.ast.visitors;

import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i32;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Global;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Limit;
import com.oracle.svm.hosted.webimage.wasm.ast.Memory;
import com.oracle.svm.hosted.webimage.wasm.ast.StartFunction;
import com.oracle.svm.hosted.webimage.wasm.ast.Table;
import com.oracle.svm.hosted.webimage.wasm.ast.Tag;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.WasmModule;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.webimage.wasm.types.WasmLMUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.debug.GraalError;

/**
 * Implementation of the WASM validation algorithm with minor modifications.
 *
 * <ul>
 * <li>Makes some simplifying assumptions (which the AST currently satisfies):
 * <ul>
 * <li>Any labeled block has no input or output types.</li>
 * <li>No vector or reference types exist</li>
 * <li>Functions only have a single return value</li>
 * </ul>
 * </li>
 * <li>The `Unknown` value is encoded as 'null'</li>
 * <li>Breaks jump to {@link WasmId} instead of being numbered</li>
 * </ul>
 * <p>
 * Ref: https://webassembly.github.io/spec/core/appendix/algorithm.html#validation-algorithm
 */
public class WasmValidator extends WasmVisitor {
    static class CtrlFrame {
        final WasmId.Label label;
        boolean unreachable = false;

        CtrlFrame(WasmId.Label label) {
            this.label = label;
        }
    }

    interface Context {
        Instruction getCurrentInstruction();

        void setCurrentInstruction(Instruction inst);

        Function getCurrentFunc();

        void setCurrentFunc(Function func);

        WasmValType getReturnType();

        void setReturnType(WasmValType returnType);

        void addLocal(WasmId.Local id);

        boolean hasLocal(WasmId.Local id);

        void clearLocals();

        void addFunc(WasmId.Func id, TypeUse typeUse);

        TypeUse getFunc(WasmId.Func id);

        void clearFuncs();

        void addGlobal(Global global);

        Global getGlobal(WasmId.Global id);

        void addTable(WasmId.Table id, Table table);

        Table getTable(WasmId.Table id);

        void addMemory(WasmId.Memory id);

        boolean hasMemory(WasmId.Memory id);

        void addTag(WasmId.Tag id);

        boolean hasTag(WasmId.Tag id);

        void addExport(Export export);
    }

    class ContextImpl implements Context {
        /**
         * Current instruction.
         */
        private Instruction inst = null;
        /**
         * Current function.
         */
        private Function func = null;
        /**
         * Return type of {@link #func}.
         */
        private WasmValType returnType = null;
        /**
         * Local variables types of {@link #func}.
         */
        private final Set<WasmId.Local> locals = new HashSet<>();
        /**
         * All functions. declared in the module.
         */
        private final Map<WasmId.Func, TypeUse> funcs = new HashMap<>();
        /**
         * All globals declared in the module.
         */
        private final Map<WasmId.Global, Global> globals = new HashMap<>();

        /**
         * All tables in the module.
         */
        private final Map<WasmId.Table, Table> tables = new HashMap<>();
        /**
         * All memories in the module.
         */
        private final Set<WasmId.Memory> memories = new HashSet<>();
        /**
         * All tags in the module.
         */
        private final Set<WasmId.Tag> tags = new HashSet<>();

        private final Set<String> exportNames = new HashSet<>();

        @Override
        public Instruction getCurrentInstruction() {
            return inst;
        }

        @Override
        public void setCurrentInstruction(Instruction inst) {
            this.inst = inst;
        }

        @Override
        public Function getCurrentFunc() {
            return func;
        }

        @Override
        public void setCurrentFunc(Function func) {
            this.func = func;
        }

        @Override
        public WasmValType getReturnType() {
            return returnType;
        }

        @Override
        public void setReturnType(WasmValType returnType) {
            this.returnType = returnType;
        }

        @Override
        public void addLocal(WasmId.Local id) {
            addId(id, locals);
        }

        @Override
        public boolean hasLocal(WasmId.Local id) {
            return locals.contains(id);
        }

        @Override
        public void clearLocals() {
            locals.clear();
        }

        @Override
        public void addFunc(WasmId.Func id, TypeUse typeUse) {
            addIdMapping(id, typeUse, funcs);
        }

        @Override
        public TypeUse getFunc(WasmId.Func id) {
            return funcs.get(id);
        }

        @Override
        public void clearFuncs() {
            funcs.clear();
        }

        @Override
        public void addGlobal(Global global) {
            addIdMapping(global.getId(), global, globals);
        }

        @Override
        public Global getGlobal(WasmId.Global id) {
            return globals.get(id);
        }

        @Override
        public void addTable(WasmId.Table id, Table table) {
            addIdMapping(id, table, tables);
        }

        @Override
        public Table getTable(WasmId.Table id) {
            return tables.get(id);
        }

        @Override
        public void addMemory(WasmId.Memory id) {
            addId(id, memories);
        }

        @Override
        public boolean hasMemory(WasmId.Memory id) {
            return memories.contains(id);
        }

        @Override
        public void addTag(WasmId.Tag id) {
            addId(id, tags);
            errorIf(!id.typeUse.results.isEmpty(), "Tag has return value: " + id);
        }

        @Override
        public boolean hasTag(WasmId.Tag id) {
            return tags.contains(id);
        }

        @Override
        public void addExport(Export export) {
            errorIf(exportNames.contains(export.name), "Duplicate export: " + export);
            exportNames.add(export.name);

            switch (export.type) {
                case FUNC:
                    errorIf(getFunc(export.getFuncId()) == null, "No matching function for export: " + export);
                    break;
                case MEM:
                    errorIf(!hasMemory(export.getMemoryId()), "No matching memory for export: " + export);
                    break;
                case TAG:
                    errorIf(!hasTag(export.getTagId()), "No matching tag for export: " + export);
                    break;
                default:
                    throw error("Unsupported export " + export);
            }
        }

        private <I extends WasmId, T> void addIdMapping(I id, T data, Map<I, T> map) {
            // No name conflicts.
            assertIdUniqueName(id, map.keySet());
            T old = map.putIfAbsent(id, data);
            errorIf(old != null, "Duplicate id: " + id);
        }

        private <I extends WasmId> void addId(I id, Collection<I> others) {
            // No name conflicts.
            assertIdUniqueName(id, others);
            errorIf(others.contains(id), "Duplicate id: " + id);
            others.add(id);
        }
    }

    private final Context ctxt = new ContextImpl();

    /**
     * Value types currently on the stack.
     */
    private final ArrayDeque<WasmValType> vals = new ArrayDeque<>();
    /**
     * Stack of ctrl frames (labels you can jump to).
     */
    private final Deque<CtrlFrame> ctrls = new ArrayDeque<>();

    private RuntimeException error(String msg) {
        String errorMsg = "Error";

        Function currentFunction = ctxt.getCurrentFunc();
        Instruction currentInstruction = ctxt.getCurrentInstruction();

        if (currentFunction != null) {
            errorMsg += " in function: " + ctxt.getCurrentFunc().getId();
        }

        if (currentInstruction != null) {
            errorMsg += " at " + currentInstruction;
        }

        if (msg != null) {
            errorMsg += ": " + msg;
        }
        throw GraalError.shouldNotReachHere(errorMsg);
    }

    private void errorIf(boolean condition, String msg) {
        if (condition) {
            throw error(msg);
        }
    }

    private static boolean assertIdsEqual(WasmId first, WasmId second) {
        return Objects.equals(first, second);
    }

    private CtrlFrame topFrame() {
        return Objects.requireNonNull(ctrls.peek());
    }

    private void pushVal(WasmValType t) {
        vals.push(t);
    }

    private WasmValType popVal() {
        CtrlFrame top = topFrame();
        if (vals.isEmpty() && top.unreachable) {
            return null;
        }

        errorIf(vals.isEmpty(), "Expected some value on stack");

        return vals.pop();
    }

    private RuntimeException typeMismatch(WasmValType[] expected) {
        return typeMismatch(expected, vals.clone());
    }

    private RuntimeException typeMismatch(WasmValType[] expected, Deque<WasmValType> actual) {
        // actual.toArray has the stack top at the beginning of the array to print the stack, we
        // first need to reverse it.
        List<WasmValType> actualTypes = Arrays.asList(actual.toArray(WasmValType[]::new));
        Collections.reverse(actualTypes);

        throw error("type mismatch, expected " + Arrays.toString(expected) + " but got " + actualTypes);
    }

    /**
     * @param expected The top of the stack must match this (the last element is at the very top).
     */
    private void popVals(WasmValType... expected) {
        int numTypes = expected.length;
        if (vals.size() < numTypes) {
            throw typeMismatch(expected);
        } else {
            Deque<WasmValType> oldStack = vals.clone();
            for (int i = numTypes - 1; i >= 0; i--) {
                WasmValType expectedType = expected[i];
                WasmValType actualType = popVal();

                if (actualType != null && expectedType != null && !Objects.equals(expectedType, actualType)) {
                    throw typeMismatch(expected, oldStack);
                }
            }
        }
    }

    private void assertStackEmpty() {
        if (!vals.isEmpty()) {
            throw typeMismatch(new WasmValType[0]);
        }
    }

    private void assertLocalExists(WasmId.Local local) {
        errorIf(!ctxt.hasLocal(local), "Local does not exist: " + local);
    }

    private Global getAndAssertGlobalExists(WasmId.Global global) {
        Global g = ctxt.getGlobal(global);
        errorIf(g == null, "global does not exist: " + global);
        return g;
    }

    private void pushCtrl(WasmId.Label label) {
        assertStackEmpty();
        if (label != null) {
            // No name conflicts.
            assertIdUniqueName(label, ctrls.stream().map(frame -> frame.label).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        ctrls.push(new CtrlFrame(label));
    }

    private void popCtrl(WasmId.Label expectedLabel) {
        errorIf(ctrls.isEmpty(), "Expected a control frame");
        CtrlFrame frame = topFrame();
        assertStackEmpty();
        errorIf(frame.label != expectedLabel, "Expected control frame " + expectedLabel + " but got " + frame.label);
        ctrls.pop();
    }

    private void unreachable() {
        CtrlFrame top = topFrame();
        vals.clear();
        top.unreachable = true;
    }

    private void applyBinary(WasmValType left, WasmValType right, WasmValType result) {
        applyTypeUse(TypeUse.forBinary(result, left, right));
    }

    private void applyUnary(WasmValType input, WasmValType result) {
        applyTypeUse(TypeUse.forUnary(result, input));
    }

    private void applyTypeUse(TypeUse typeUse) {
        popVals(typeUse.params.toArray(WasmValType[]::new));
        typeUse.results.forEach(this::pushVal);
    }

    private void assertLabelExists(WasmId.Label label) {
        errorIf(ctrls.stream().noneMatch(frame -> assertIdsEqual(label, frame.label)), "Label " + label + " does not exist.");
    }

    /**
     * Checks that the given id has a unique name among all other ids.
     * <p>
     * Used to check for naming conflicts.
     */
    private <I extends WasmId> void assertIdUniqueName(I id, Collection<I> others) {
        errorIf(!id.isResolved(), "Found unresolved id: " + id);
        errorIf(others.stream().map(WasmId::getName).anyMatch(name -> name.equals(id.getName())), "Id does not have unique name: " + id);
    }

    private void checkLimit(Limit limit, long maxValue) {
        errorIf(limit.getMin() > maxValue, "Limit min larger than " + maxValue + ": " + limit);
        if (limit.hasMax()) {
            errorIf(limit.getMin() > maxValue, "Limit max larger than " + maxValue + ": " + limit);
            errorIf(limit.getMin() > limit.getMax(), "Limit min larger than max: " + limit);
        }
    }

    @Override
    public void visitModule(WasmModule m) {
        m.getFunctions().forEach(func -> ctxt.addFunc(func.getId(), func.getSignature()));

        m.getImports().values().forEach(importDecl -> {
            if (importDecl.getDescriptor() instanceof ImportDescriptor.Function funcImport) {
                ctxt.addFunc(importDecl.getFunctionId(), funcImport.typeUse);
            }
        });
        super.visitModule(m);

        ctxt.clearFuncs();
    }

    @Override
    public void visitMemory(Memory m) {
        super.visitMemory(m);

        ctxt.addMemory(m.id);

        // The memory must be valid in the range 2^16
        int maxSize = 1 << 16;
        checkLimit(m.limit, maxSize);
    }

    @Override
    public void visitTag(Tag tag) {
        ctxt.addTag(tag.id);
        super.visitTag(tag);
    }

    @Override
    public void visitGlobal(Global global) {
        ctxt.addGlobal(global);
        pushCtrl(null);
        super.visitGlobal(global);
        popVals(global.getType());
        popCtrl(null);
    }

    @Override
    public void visitExport(Export e) {
        super.visitExport(e);
        ctxt.addExport(e);
    }

    @Override
    public void visitStartFunction(StartFunction startFunction) {
        WasmId.Func function = startFunction.function;
        TypeUse typeUse = ctxt.getFunc(function);
        errorIf(typeUse == null, "Start function not found: " + function);
        errorIf(!typeUse.params.isEmpty() || !typeUse.results.isEmpty(), "Start function must not take arguments or produce results: " + function + ", " + typeUse);
    }

    @Override
    public void visitFunction(Function f) {
        f.getParams().forEach(ctxt::addLocal);
        f.getLocals().forEach(ctxt::addLocal);

        errorIf(f.getResults().size() > 1, "Function has multiple return values: " + f);

        ctxt.setReturnType(f.getResults().isEmpty() ? null : f.getResults().get(0));
        ctxt.setCurrentFunc(f);

        pushCtrl(null);
        super.visitFunction(f);
        popCtrl(null);

        ctxt.setCurrentFunc(null);
        ctxt.setReturnType(null);
        ctxt.clearLocals();
    }

    @Override
    public void visitTable(Table t) {
        ctxt.addTable(t.id, t);
        checkLimit(t.limit, (1L << 32) - 1L);
        errorIf(!t.elementType.isRef(), "Table has non reference element type: " + t);

        // TODO GR-56363 Verify that active table elements are constant and the result matches the
        // table type
    }

    @Override
    public void visitInstruction(Instruction inst) {
        Instruction parentInst = ctxt.getCurrentInstruction();
        ctxt.setCurrentInstruction(inst);
        super.visitInstruction(inst);
        ctxt.setCurrentInstruction(parentInst);
    }

    @Override
    public void visitBlock(Instruction.Block block) {
        pushCtrl(block.getLabel());
        super.visitBlock(block);
        popCtrl(block.getLabel());
    }

    @Override
    public void visitLoop(Instruction.Loop loop) {
        pushCtrl(loop.getLabel());
        super.visitLoop(loop);
        popCtrl(loop.getLabel());
    }

    @Override
    public void visitIf(Instruction.If ifBlock) {
        visitInstruction(ifBlock.condition);
        popVals(i32);
        pushCtrl(ifBlock.getLabel());
        visitInstructions(ifBlock.thenInstructions);
        if (ifBlock.hasElse()) {
            popCtrl(ifBlock.getLabel());
            pushCtrl(ifBlock.getLabel());
            visitInstructions(ifBlock.elseInstructions);
        }
        popCtrl(ifBlock.getLabel());
    }

    @Override
    public void visitTry(Instruction.Try tryBlock) {
        pushCtrl(tryBlock.getLabel());
        visitInstructions(tryBlock.instructions);

        for (Instruction.Try.Catch catchBlock : tryBlock.catchBlocks) {
            errorIf(!ctxt.hasTag(catchBlock.tag), "No matching tag for catch: " + catchBlock);
            pushCtrl(null);
            catchBlock.tag.typeUse.params.forEach(this::pushVal);
            visitInstructions(catchBlock.instructions);
            popCtrl(null);
        }

        popCtrl(tryBlock.getLabel());
    }

    @Override
    public void visitUnreachable(Instruction.Unreachable unreachable) {
        unreachable();
        super.visitUnreachable(unreachable);
    }

    @Override
    public void visitDrop(Instruction.Drop inst) {
        super.visitDrop(inst);
        popVal();
    }

    @Override
    public void visitBinary(Instruction.Binary inst) {
        super.visitBinary(inst);
        applyBinary(inst.op.leftInputType, inst.op.rightInputType, inst.op.outputType);
    }

    @Override
    public void visitBreak(Instruction.Break inst) {
        super.visitBreak(inst);

        WasmId.Label targetLabel = inst.getTarget();

        assertLabelExists(targetLabel);

        if (inst.condition == null) {
            unreachable();
        } else {
            popVals(i32);
        }
    }

    @Override
    public void visitBreakTable(Instruction.BreakTable inst) {
        super.visitBreakTable(inst);

        popVals(i32);

        WasmId.Label defaultLabel = inst.getDefaultTarget();
        assertLabelExists(defaultLabel);

        for (int i = 0; i < inst.numTargets(); i++) {
            assertLabelExists(inst.getTarget(i));
        }

        unreachable();
    }

    @Override
    public void visitConst(Instruction.Const constValue) {
        super.visitConst(constValue);
        pushVal(constValue.literal.type);
    }

    @Override
    public void visitRelocation(Instruction.Relocation relocation) {
        errorIf(!relocation.wasProcessed(), "Unhandled relocation: " + relocation);
        super.visitRelocation(relocation);
    }

    @Override
    public void visitLocalGet(Instruction.LocalGet localGet) {
        super.visitLocalGet(localGet);
        assertLocalExists(localGet.getLocal());
        pushVal(localGet.getType());
    }

    @Override
    public void visitLocalSet(Instruction.LocalSet localSet) {
        super.visitLocalSet(localSet);
        assertLocalExists(localSet.getLocal());
        popVals(localSet.getType());
    }

    @Override
    public void visitLocalTee(Instruction.LocalTee localTee) {
        super.visitLocalTee(localTee);
        assertLocalExists(localTee.getLocal());
        applyUnary(localTee.getType(), localTee.getType());
    }

    @Override
    public void visitGlobalGet(Instruction.GlobalGet globalGet) {
        super.visitGlobalGet(globalGet);
        getAndAssertGlobalExists(globalGet.getGlobal());
        pushVal(globalGet.getType());
    }

    @Override
    public void visitGlobalSet(Instruction.GlobalSet globalSet) {
        super.visitGlobalSet(globalSet);
        Global g = getAndAssertGlobalExists(globalSet.getGlobal());
        errorIf(!g.mutable, "Found global.set for immutable global " + g);
        popVals(globalSet.getType());
    }

    @Override
    public void visitReturn(Instruction.Return ret) {
        super.visitReturn(ret);

        if (!ret.isVoid()) {
            popVals(ctxt.getReturnType());
        }
    }

    @Override
    public void visitCall(Instruction.Call inst) {
        super.visitCall(inst);

        TypeUse typeUse = ctxt.getFunc(inst.getTarget());

        errorIf(typeUse == null, "Function call target does not exist: " + inst.getTarget());
        applyTypeUse(typeUse);
    }

    @Override
    public void visitCallIndirect(Instruction.CallIndirect inst) {
        super.visitCallIndirect(inst);

        Table table = ctxt.getTable(inst.table);
        errorIf(table == null, "No matching table for call_indirect: " + inst);

        errorIf(!table.elementType.isFuncRef(), "Target table is not a function table: " + table);

        popVals(i32);
        applyTypeUse(inst.signature);
    }

    @Override
    public void visitThrow(Instruction.Throw inst) {
        super.visitThrow(inst);

        errorIf(!ctxt.hasTag(inst.tag), "No matching tag for throw: " + inst);
        applyTypeUse(inst.tag.typeUse);
    }

    @Override
    public void visitUnary(Instruction.Unary inst) {
        super.visitUnary(inst);

        errorIf(inst.op == Instruction.Unary.Op.Nop, "Unary Nop left in module");

        applyUnary(inst.op.inputType, inst.op.outputType);
    }

    @Override
    public void visitSelect(Instruction.Select inst) {
        super.visitSelect(inst);
        popVals(i32);
        WasmValType t1 = popVal();
        WasmValType t2 = popVal();
        errorIf(t1 != t2 && t1 != null && t2 != null, "Select operand types do not match: " + t1 + ", " + t2);
        pushVal(t1 == null ? t2 : t1);
    }

    @Override
    public void visitLoad(Instruction.Load inst) {
        visitInstruction(inst.getOffset());
        popVals(i32);
        visitInstruction(inst.baseAddress);
        applyUnary(i32, inst.stackType);
    }

    @Override
    public void visitStore(Instruction.Store inst) {
        visitInstruction(inst.getOffset());
        popVals(i32);
        visitInstruction(inst.baseAddress);
        visitInstruction(inst.value);
        popVals(i32, inst.stackType);
    }

    @Override
    public void visitMemoryGrow(Instruction.MemoryGrow inst) {
        super.visitMemoryGrow(inst);
        applyUnary(WasmLMUtil.POINTER_TYPE, WasmLMUtil.POINTER_TYPE);
    }

    @Override
    public void visitMemoryFill(Instruction.MemoryFill inst) {
        super.visitMemoryFill(inst);
        popVals(i32, i32, i32);
    }

    @Override
    public void visitMemoryCopy(Instruction.MemoryCopy inst) {
        super.visitMemoryCopy(inst);
        popVals(i32, i32, i32);
    }

    @Override
    public void visitMemorySize(Instruction.MemorySize inst) {
        super.visitMemorySize(inst);
        pushVal(WasmLMUtil.POINTER_TYPE);
    }

}
