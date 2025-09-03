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

import com.oracle.svm.hosted.webimage.wasm.ast.Data;
import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Global;
import com.oracle.svm.hosted.webimage.wasm.ast.Import;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.Memory;
import com.oracle.svm.hosted.webimage.wasm.ast.ModuleField;
import com.oracle.svm.hosted.webimage.wasm.ast.StartFunction;
import com.oracle.svm.hosted.webimage.wasm.ast.Table;
import com.oracle.svm.hosted.webimage.wasm.ast.Tag;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.WasmModule;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasmgc.ast.ArrayType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FunctionType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.RecursiveGroup;
import com.oracle.svm.hosted.webimage.wasmgc.ast.StructType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.TypeDefinition;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmStorageType;

import jdk.graal.compiler.debug.GraalError;

/**
 * Traverses a {@link WasmModule} (or components of it).
 * <p>
 * Call {@link #visitModule(WasmModule)} to traverse the entire module or any of the other public
 * methods to traverse only that component.
 * <p>
 * Largely visits {@link ModuleField}s and the {@link Instruction}s within. But also has hooks to
 * visit {@link WasmId ids} (see {@link #visitId(WasmId)}) and {@link WasmStorageType types} (see
 * {@link #visitType(WasmStorageType)}).
 * <p>
 * Instructions for the input values of another instruction are visited in the order they are pushed
 * onto the stack.
 */
public abstract class WasmVisitor {

    protected WasmModule module;

    public void visitModule(WasmModule m) {
        this.module = m;

        for (Import importDecl : m.getImports().values()) {
            visitModuleField(importDecl);
            visitImport(importDecl);
        }

        Memory memory = m.getMemory();
        if (memory != null) {
            visitModuleField(memory);
            visitMemory(memory);
        }

        for (RecursiveGroup def : m.getRecursiveGroups()) {
            assert def.size() > 0 : "Empty recursive group";
            if (def.size() == 1) {
                visitSingletonRecursiveGroup(def);
            } else {
                visitModuleField(def);
                visitRecursiveGroup(def);
            }
        }

        for (Tag tag : m.getTags()) {
            visitModuleField(tag);
            visitTag(tag);
        }

        for (Global global : m.getGlobals().sequencedValues()) {
            visitModuleField(global);
            visitGlobal(global);
        }

        for (Export export : m.getExports().sequencedValues()) {
            visitModuleField(export);
            visitExport(export);
        }

        StartFunction startFunction = m.getStartFunction();
        if (startFunction != null) {
            visitModuleField(startFunction);
            visitStartFunction(startFunction);
        }

        for (Table table : m.getTables()) {
            visitModuleField(table);
            visitTable(table);
        }

        for (Function function : m.getFunctions()) {
            visitModuleField(function);
            visitFunction(function);
        }

        for (Data data : m.getDataSegments()) {
            visitModuleField(data);
            visitData(data);
        }

        this.module = null;
    }

    /**
     * Called when a {@link WasmId} is encountered.
     *
     * @param id May be {@code null}.
     */
    protected void visitId(WasmId id) {
        if (id instanceof WasmId.Variable variable) {
            visitType(variable.getVariableType());
        }
    }

    protected void visitType(WasmStorageType type) {
        if (type instanceof WasmRefType.TypeIndex typeIndex) {
            visitId(typeIndex.id);
        }
    }

    protected void visitTypeUse(TypeUse typeUse) {
        typeUse.params.forEach(this::visitType);
        typeUse.results.forEach(this::visitType);
    }

    public void visitModuleField(@SuppressWarnings("unused") ModuleField f) {
    }

    public void visitMemory(@SuppressWarnings("unused") Memory m) {
    }

    public void visitData(Data data) {
        visitId(data.id);
    }

    public void visitTypeDefinition(TypeDefinition def) {
        visitId(def.getId());
        visitId(def.supertype);

        switch (def) {
            case StructType structType -> visitStructTypeDefinition(structType);
            case ArrayType arrayType -> visitArrayTypeDefinition(arrayType);
            case FunctionType functionType -> visitFunctionTypeDefinition(functionType);
            default -> throw GraalError.shouldNotReachHere(def.toString());
        }
    }

    public void visitStructTypeDefinition(StructType structType) {
        for (StructType.Field field : structType.fields) {
            visitId(field.id);
            visitType(field.fieldType.storageType);
        }
    }

    public void visitArrayTypeDefinition(ArrayType arrayType) {
        visitType(arrayType.elementType.storageType);
    }

    public void visitFunctionTypeDefinition(FunctionType functionType) {
        visitTypeUse(functionType.typeUse);
    }

    public void visitSingletonRecursiveGroup(RecursiveGroup def) {
        assert def.size() == 1 : def.size();
        TypeDefinition typeDefinition = def.getTypeDefinitions().getFirst();
        visitModuleField(typeDefinition);
        visitTypeDefinition(typeDefinition);
    }

    public void visitRecursiveGroup(RecursiveGroup def) {
        for (TypeDefinition typeDefinition : def.getTypeDefinitions()) {
            visitModuleField(typeDefinition);
            visitTypeDefinition(typeDefinition);
        }
    }

    public void visitTag(Tag tag) {
        visitId(tag.id);
    }

    public void visitGlobal(Global global) {
        visitId(global.getId());
        visitInstruction(global.init);
    }

    public void visitImport(Import importDecl) {
        visitId(importDecl.getId());
        switch (importDecl.getDescriptor()) {
            case ImportDescriptor.Function functionImport -> visitFunImport(functionImport);
            default -> throw GraalError.unimplemented("Import: " + importDecl); // ExcludeFromJacocoGeneratedReport
        }
    }

    public void visitFunImport(ImportDescriptor.Function funcImport) {
        visitTypeUse(funcImport.typeUse);
    }

    public void visitExport(Export e) {
        visitId(e.getId());
    }

    public void visitStartFunction(StartFunction startFunction) {
        visitId(startFunction.function);
    }

    public void visitFunction(Function f) {
        visitId(f.getId());
        visitId(f.getFuncType());
        f.getParams().forEach(this::visitId);
        f.getLocals().forEach(this::visitId);
        f.getResults().forEach(this::visitType);
        visitInstructions(f.getInstructions());
    }

    public void visitTable(Table t) {
        visitId(t.id);
        visitType(t.elementType);
        if (t.elements != null) {
            for (Instruction element : t.elements) {
                visitInstruction(element);
            }
        }
    }

    public void visitInstructions(Instructions instructions) {
        for (Instruction instruction : instructions) {
            visitInstruction(instruction);
        }
    }

    protected void maybeVisitInstruction(Instruction inst) {
        if (inst != null) {
            visitInstruction(inst);
        }
    }

    public void visitInstruction(Instruction inst) {
        switch (inst) {
            case Instruction.Block i -> {
                visitBlockInstr(i);
                visitBlock(i);
            }
            case Instruction.Loop i -> {
                visitBlockInstr(i);
                visitLoop(i);
            }
            case Instruction.If i -> {
                visitBlockInstr(i);
                visitIf(i);
            }
            case Instruction.Try i -> {
                visitBlockInstr(i);
                visitTry(i);
            }
            case Instruction.Nop i -> visitNop(i);
            case Instruction.Unreachable i -> visitUnreachable(i);
            case Instruction.Break i -> visitBreak(i);
            case Instruction.Return i -> visitReturn(i);
            case Instruction.LocalGet i -> visitLocalGet(i);
            case Instruction.LocalSet i -> visitLocalSet(i);
            case Instruction.LocalTee i -> visitLocalTee(i);
            case Instruction.GlobalGet i -> visitGlobalGet(i);
            case Instruction.GlobalSet i -> visitGlobalSet(i);
            case Instruction.Const i -> visitConst(i);
            case Instruction.Relocation i -> visitRelocation(i);
            case Instruction.Binary i -> visitBinary(i);
            case Instruction.Unary i -> visitUnary(i);
            case Instruction.Call i -> visitCall(i);
            case Instruction.CallRef i -> visitCallRef(i);
            case Instruction.CallIndirect i -> visitCallIndirect(i);
            case Instruction.Throw i -> visitThrow(i);
            case Instruction.Drop i -> visitDrop(i);
            case Instruction.Select i -> visitSelect(i);
            case Instruction.BreakTable i -> visitBreakTable(i);
            case Instruction.TableGet i -> visitTableGet(i);
            case Instruction.TableSet i -> visitTableSet(i);
            case Instruction.TableSize i -> visitTableSize(i);
            case Instruction.TableGrow i -> visitTableGrow(i);
            case Instruction.TableFill i -> visitTableFill(i);
            case Instruction.TableCopy i -> visitTableCopy(i);
            case Instruction.Load i -> visitLoad(i);
            case Instruction.Store i -> visitStore(i);
            case Instruction.MemorySize i -> visitMemorySize(i);
            case Instruction.MemoryGrow i -> visitMemoryGrow(i);
            case Instruction.MemoryFill i -> visitMemoryFill(i);
            case Instruction.MemoryCopy i -> visitMemoryCopy(i);
            case Instruction.MemoryInit i -> visitMemoryInit(i);
            case Instruction.DataDrop i -> visitDataDrop(i);
            case Instruction.RefNull i -> visitRefNull(i);
            case Instruction.RefFunc i -> visitRefFunc(i);
            case Instruction.RefTest i -> visitRefTest(i);
            case Instruction.RefCast i -> visitRefCast(i);
            case Instruction.StructNew i -> visitStructNew(i);
            case Instruction.StructGet i -> visitStructGet(i);
            case Instruction.StructSet i -> visitStructSet(i);
            case Instruction.ArrayNew i -> visitArrayNew(i);
            case Instruction.ArrayNewFixed i -> visitArrayNewFixed(i);
            case Instruction.ArrayNewData i -> visitArrayNewData(i);
            case Instruction.ArrayLen i -> visitArrayLen(i);
            case Instruction.ArrayFill i -> visitArrayFill(i);
            case Instruction.ArrayGet i -> visitArrayGet(i);
            case Instruction.ArraySet i -> visitArraySet(i);
            case Instruction.ArrayCopy i -> visitArrayCopy(i);
            case Instruction.ArrayInitData i -> visitArrayInitData(i);
            case Instruction.AnyExternConversion i -> visitAnyExternConversion(i);
            default -> throw GraalError.shouldNotReachHere(inst.toString());
        }
    }

    public void visitBlockInstr(@SuppressWarnings("unused") Instruction.WasmBlock block) {
        visitId(block.getLabel());
    }

    public void visitBlock(Instruction.Block block) {
        visitInstructions(block.instructions);
    }

    public void visitLoop(Instruction.Loop loop) {
        visitInstructions(loop.instructions);
    }

    public void visitIf(Instruction.If ifBlock) {
        visitInstruction(ifBlock.condition);
        visitInstructions(ifBlock.thenInstructions);
        if (ifBlock.hasElse()) {
            visitInstructions(ifBlock.elseInstructions);
        }
    }

    public void visitTry(Instruction.Try tryBlock) {
        for (Instruction.Try.Catch catchBlock : tryBlock.catchBlocks) {
            visitId(catchBlock.tag);
        }
        visitInstructions(tryBlock.instructions);
        for (Instruction.Try.Catch catchBlock : tryBlock.catchBlocks) {
            visitInstructions(catchBlock.instructions);
        }
    }

    public void visitNop(@SuppressWarnings("unused") Instruction.Nop inst) {
    }

    public void visitUnreachable(@SuppressWarnings("unused") Instruction.Unreachable unreachable) {
    }

    public void visitBreak(Instruction.Break inst) {
        visitId(inst.getTarget());
        maybeVisitInstruction(inst.condition);
    }

    public void visitBreakTable(Instruction.BreakTable inst) {
        visitId(inst.getDefaultTarget());
        for (int i = 0; i < inst.numTargets(); i++) {
            visitId(inst.getTarget(i));
        }
        visitInstruction(inst.index);
    }

    public void visitReturn(Instruction.Return ret) {
        maybeVisitInstruction(ret.result);
    }

    public void visitLocalGet(Instruction.LocalGet localGet) {
        visitId(localGet.getLocal());
    }

    public void visitLocalSet(Instruction.LocalSet localSet) {
        visitId(localSet.getLocal());
        visitInstruction(localSet.value);
    }

    public void visitLocalTee(Instruction.LocalTee localTee) {
        visitId(localTee.getLocal());
        visitInstruction(localTee.value);
    }

    public void visitGlobalGet(Instruction.GlobalGet globalGet) {
        visitId(globalGet.getGlobal());
    }

    public void visitGlobalSet(Instruction.GlobalSet globalSet) {
        visitId(globalSet.getGlobal());
        visitInstruction(globalSet.value);
    }

    public void visitConst(@SuppressWarnings("unused") Instruction.Const constValue) {
    }

    public void visitRelocation(Instruction.Relocation relocation) {
        if (relocation.wasProcessed()) {
            visitInstruction(relocation.getValue());
        }
    }

    public void visitCall(Instruction.Call inst) {
        visitId(inst.getTarget());
        visitInstructions(inst.args);
    }

    public void visitCallRef(Instruction.CallRef inst) {
        visitId(inst.functionType);
        visitInstructions(inst.args);
        visitInstruction(inst.functionReference);
    }

    public void visitCallIndirect(Instruction.CallIndirect inst) {
        visitId(inst.table);
        visitId(inst.funcId);
        visitInstructions(inst.args);
        visitInstruction(inst.index);
    }

    public void visitThrow(Instruction.Throw inst) {
        visitId(inst.tag);
        visitInstructions(inst.arguments);
    }

    public void visitBinary(Instruction.Binary inst) {
        visitType(inst.op.leftInputType);
        visitType(inst.op.rightInputType);
        visitType(inst.op.outputType);
        visitInstruction(inst.left);
        visitInstruction(inst.right);
    }

    public void visitUnary(Instruction.Unary inst) {
        visitType(inst.op.inputType);
        visitType(inst.op.outputType);
        visitInstruction(inst.value);
    }

    public void visitDrop(Instruction.Drop inst) {
        visitInstruction(inst.value);
    }

    public void visitSelect(Instruction.Select inst) {
        visitType(inst.type);
        visitInstruction(inst.trueValue);
        visitInstruction(inst.falseValue);
        visitInstruction(inst.condition);
    }

    public void visitTableGet(Instruction.TableGet inst) {
        visitId(inst.table);
        visitInstruction(inst.index);
    }

    public void visitTableSet(Instruction.TableSet inst) {
        visitId(inst.table);
        visitInstruction(inst.index);
        visitInstruction(inst.value);
    }

    public void visitTableSize(Instruction.TableSize inst) {
        visitId(inst.table);
    }

    public void visitTableGrow(Instruction.TableGrow inst) {
        visitId(inst.table);
        visitInstruction(inst.initValue);
        visitInstruction(inst.delta);
    }

    public void visitTableFill(Instruction.TableFill inst) {
        visitId(inst.table);
        visitInstruction(inst.offset);
        visitInstruction(inst.value);
        visitInstruction(inst.size);
    }

    public void visitTableCopy(Instruction.TableCopy inst) {
        visitId(inst.destTable);
        visitId(inst.srcTable);
        visitInstruction(inst.destOffset);
        visitInstruction(inst.srcOffset);
        visitInstruction(inst.size);
    }

    public void visitLoad(Instruction.Load inst) {
        visitInstruction(inst.getOffset());
        visitInstruction(inst.baseAddress);
    }

    public void visitStore(Instruction.Store inst) {
        visitInstruction(inst.getOffset());
        visitInstruction(inst.baseAddress);
        visitInstruction(inst.value);
    }

    public void visitMemorySize(@SuppressWarnings("unused") Instruction.MemorySize inst) {
    }

    public void visitMemoryGrow(Instruction.MemoryGrow inst) {
        visitInstruction(inst.numPages);
    }

    public void visitMemoryFill(Instruction.MemoryFill inst) {
        visitInstruction(inst.start);
        visitInstruction(inst.fillValue);
        visitInstruction(inst.size);
    }

    public void visitMemoryCopy(Instruction.MemoryCopy inst) {
        visitInstruction(inst.destOffset);
        visitInstruction(inst.srcOffset);
        visitInstruction(inst.size);
    }

    public void visitMemoryInit(Instruction.MemoryInit inst) {
        visitId(inst.dataSegment);
        visitInstruction(inst.destOffset);
        visitInstruction(inst.srcOffset);
        visitInstruction(inst.size);
    }

    public void visitDataDrop(Instruction.DataDrop inst) {
        visitId(inst.dataSegment);
    }

    public void visitRefNull(Instruction.RefNull inst) {
        visitType(inst.heapType);
    }

    public void visitRefFunc(Instruction.RefFunc inst) {
        visitId(inst.func);
    }

    public void visitRefTest(Instruction.RefTest inst) {
        visitType(inst.testType);
        visitInstruction(inst.input);
    }

    public void visitRefCast(Instruction.RefCast inst) {
        visitType(inst.newType);
        visitInstruction(inst.input);
    }

    public void visitStructNew(Instruction.StructNew inst) {
        visitId(inst.type);
        if (!inst.isDefault()) {
            visitInstructions(inst.getFieldValues());
        }
    }

    public void visitStructGet(Instruction.StructGet inst) {
        visitId(inst.refType);
        visitId(inst.fieldId);
        visitInstruction(inst.ref);
    }

    public void visitStructSet(Instruction.StructSet inst) {
        visitId(inst.refType);
        visitId(inst.fieldId);
        visitInstruction(inst.ref);
        visitInstruction(inst.value);
    }

    public void visitArrayNew(Instruction.ArrayNew inst) {
        visitId(inst.type);
        if (!inst.isDefault()) {
            visitInstruction(inst.getElementValue());
        }

        visitInstruction(inst.length);
    }

    public void visitArrayNewFixed(Instruction.ArrayNewFixed inst) {
        visitId(inst.type);
        visitInstructions(inst.elementValues);
    }

    public void visitArrayNewData(Instruction.ArrayNewData inst) {
        visitId(inst.type);
        visitId(inst.dataSegment);
        visitInstruction(inst.offset);
        visitInstruction(inst.size);
    }

    public void visitArrayLen(Instruction.ArrayLen inst) {
        visitInstruction(inst.ref);
    }

    public void visitArrayFill(Instruction.ArrayFill inst) {
        visitId(inst.arrayType);
        visitInstruction(inst.array);
        visitInstruction(inst.offset);
        visitInstruction(inst.value);
        visitInstruction(inst.size);
    }

    public void visitArrayGet(Instruction.ArrayGet inst) {
        visitId(inst.refType);
        visitInstruction(inst.ref);
        visitInstruction(inst.idx);
    }

    public void visitArraySet(Instruction.ArraySet inst) {
        visitId(inst.refType);
        visitInstruction(inst.ref);
        visitInstruction(inst.idx);
        visitInstruction(inst.value);
    }

    public void visitArrayCopy(Instruction.ArrayCopy inst) {
        visitId(inst.destType);
        visitId(inst.srcType);
        visitInstruction(inst.dest);
        visitInstruction(inst.destOffset);
        visitInstruction(inst.src);
        visitInstruction(inst.srcOffset);
        visitInstruction(inst.size);
    }

    public void visitArrayInitData(Instruction.ArrayInitData inst) {
        visitId(inst.type);
        visitId(inst.dataSegment);
        visitInstruction(inst.dest);
        visitInstruction(inst.destOffset);
        visitInstruction(inst.srcOffset);
        visitInstruction(inst.size);
    }

    public void visitAnyExternConversion(Instruction.AnyExternConversion inst) {
        visitInstruction(inst.input);
    }
}
