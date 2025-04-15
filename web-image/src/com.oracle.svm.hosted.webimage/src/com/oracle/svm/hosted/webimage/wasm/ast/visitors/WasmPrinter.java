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

import java.io.IOException;
import java.io.Writer;

import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import com.oracle.svm.hosted.webimage.wasm.ast.Data;
import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Global;
import com.oracle.svm.hosted.webimage.wasm.ast.Import;
import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Limit;
import com.oracle.svm.hosted.webimage.wasm.ast.Literal;
import com.oracle.svm.hosted.webimage.wasm.ast.Memory;
import com.oracle.svm.hosted.webimage.wasm.ast.ModuleField;
import com.oracle.svm.hosted.webimage.wasm.ast.StartFunction;
import com.oracle.svm.hosted.webimage.wasm.ast.Table;
import com.oracle.svm.hosted.webimage.wasm.ast.Tag;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.WasmModule;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.BinaryenCompat;
import com.oracle.svm.hosted.webimage.wasmgc.ast.ArrayType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FieldType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FunctionType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.RecursiveGroup;
import com.oracle.svm.hosted.webimage.wasmgc.ast.StructType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.TypeDefinition;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmPackedType;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmStorageType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;

/**
 * Prints a {@link WasmModule} using the WASM text format with folded instructions.
 */
public class WasmPrinter extends WasmVisitor {

    private static final int INDENT_SIZE = 2;
    private final WriterWrapper writer;

    public WasmPrinter(Writer writer) {
        this.writer = new WriterWrapper(writer);
    }

    static class WriterWrapper {

        private final Writer writer;

        /**
         * Whether the next text to be printed is the beginning of a new line.
         * <p>
         * If so, indentation will be printed before it. This way we only print indentations on
         * lines with text.
         */
        private boolean isNewLine = true;

        private int indent = 0;

        WriterWrapper(Writer writer) {
            this.writer = writer;
        }

        private void writeString(String str) {
            try {
                writer.write(str);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeChar(char c) {
            try {
                writer.write(c);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void maybePrintIndent() {
            if (isNewLine) {
                isNewLine = false;
                writeString(" ".repeat(indent * INDENT_SIZE));
            }
        }

        public void print(String str) {
            maybePrintIndent();
            writeString(str);
        }

        public void print(char c) {
            maybePrintIndent();
            writeChar(c);
        }

        public void newline() {
            writeString(System.lineSeparator());
            isNewLine = true;
        }
    }

    class Indenter implements AutoCloseable {
        Indenter() {
            assert NumUtil.assertNonNegativeInt(writer.indent);
            writer.indent++;
        }

        @Override
        public void close() {
            assert NumUtil.assertPositiveInt(writer.indent);
            writer.indent--;
        }
    }

    private void print(String str) {
        writer.print(str);
    }

    private void print(char c) {
        writer.print(c);
    }

    private void newline() {
        writer.newline();
    }

    private void space() {
        print(' ');
    }

    private void parenOpen() {
        parenOpen(null);
    }

    private void parenOpen(String op) {
        print('(');
        if (op != null) {
            print(op);
        }
    }

    private void parenClose() {
        print(')');
    }

    /**
     * Prints a symbolic WASM index, an identifier.
     * <p>
     * Ref: https://webassembly.github.io/spec/core/text/values.html#text-id
     */
    private void printId(String id) {
        /*
         * Checks that id only contains valid characters.
         */
        String validPattern = "^[0-9A-Za-z!#$%&'*+\\-./:<=>?@\\\\^_`|~]+$";
        GraalError.guarantee(id.matches(validPattern), "Id contains invalid character: %s", id);

        print('$');
        print(id);
    }

    private void printId(WasmId id) {
        if (id == null) {
            forcePrintComment("no id");
        } else {
            assert id.isResolved() : "Unresolved Id found: " + id;
            printId(id.getName());
            printVerboseComment(id);
        }
    }

    /**
     * Prints a quoted string as described in the WASM text format spec.
     *
     * Ref: https://webassembly.github.io/spec/core/text/values.html#text-string
     */
    private void printString(String str) {
        /*-
         * We don't support string literals that contain any special characters. According to WASM,
         * the following are special characters:
         * - Control characters (0x00-0x1F, 0x7F)
         * - Double quote
         * - Backslash
         */
        GraalError.guarantee(!str.matches("[\u0000-\u001F\u007F\"\\\\]"), "String does not contain invalid characters: '%s'", str);
        print('"');
        print(str);
        print('"');
    }

    private void printInt(int i) {
        print(Integer.toString(i));
    }

    private void printHeapType(WasmRefType refType) {
        if (refType instanceof WasmRefType.AbsHeap absHeapType) {
            print(switch (absHeapType.kind) {
                case ANY -> "any";
                case EQ -> "eq";
                case I31 -> "i31";
                case STRUCT -> "struct";
                case ARRAY -> "array";
                case NONE -> "none";
                case FUNC -> "func";
                case NOFUNC -> "nofunc";
                case EXTERN -> "extern";
                case NOEXTERN -> "noextern";
            });
        } else if (refType instanceof WasmRefType.TypeIndex typeIndex) {
            printId(typeIndex.id);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(refType);
        }
    }

    private void printType(WasmStorageType type) {
        switch (type) {
            case WasmPrimitiveType primitiveType -> print(primitiveType.name());
            case WasmRefType refType -> {
                if (refType.nullable && refType instanceof WasmRefType.AbsHeap absHeapType) {
                    // Print abbreviations
                    print(switch (absHeapType.kind) {
                        case ANY -> "anyref";
                        case EQ -> "eqref";
                        case I31 -> "i31ref";
                        case STRUCT -> "structref";
                        case ARRAY -> "arrayref";
                        case NONE -> "nullref";
                        case FUNC -> "funcref";
                        case NOFUNC -> "nullfuncref";
                        case EXTERN -> "externref";
                        case NOEXTERN -> "nullexternref";
                    });
                } else {
                    parenOpen("ref");

                    if (refType.nullable) {
                        space();
                        print("null");
                    }

                    space();
                    printHeapType(refType);
                    parenClose();
                }
            }
            case WasmPackedType packedType -> print(packedType.name());
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(type);
        }
    }

    private void printLocal(boolean isParam, WasmId.Local id, WasmValType type) {
        parenOpen(isParam ? "param" : "local");
        if (id != null) {
            space();
            printId(id);
        }
        space();
        printType(type);
        parenClose();
    }

    private void printParam(WasmId.Local id, WasmValType type) {
        printLocal(true, id, type);
    }

    private void printParam(WasmId.Local param) {
        printParam(param, param.getVariableType());
    }

    private void printTypeUse(TypeUse typeUse) {
        for (WasmValType param : typeUse.params) {
            space();
            printParam(null, param);
        }

        for (WasmValType result : typeUse.results) {
            space();
            printResult(result);
        }
    }

    private void printLocal(WasmId.Local local) {
        printLocal(false, local, local.getVariableType());
    }

    private void printResult(WasmValType type) {
        parenOpen("result");
        space();
        printType(type);
        parenClose();
    }

    private void forcePrintComment(Object comment) {
        printComment(comment, WebImageOptions.CommentVerbosity.NONE);
    }

    private boolean printComment(Object comment) {
        return printComment(comment, WebImageOptions.CommentVerbosity.NORMAL);
    }

    private boolean printVerboseComment(Object comment) {
        return printComment(comment, WebImageOptions.CommentVerbosity.VERBOSE);
    }

    private boolean printComment(Object comment, WebImageOptions.CommentVerbosity requiredVerbosity) {
        if (!WebImageWasmOptions.genComments(requiredVerbosity) || comment == null) {
            return false;
        }

        print("(; ");
        // Replace strings that could invalidate the comment with question marks
        print(comment.toString().replace("(;", "??").replace(";)", "??").replaceAll("[^\\x20-\\x7F]", "?"));
        print(" ;)");

        return true;
    }

    private void printLimit(Limit limit) {
        printInt(limit.getMin());
        if (limit.hasMax()) {
            space();
            printInt(limit.getMax());
        }
    }

    private void printExtensionSuffix(WasmUtil.Extension extension) {
        switch (extension) {
            case None -> {
                // No suffix necessary
            }
            case Sign -> {
                print("_s");
            }
            case Zero -> {
                print("_u");
            }
        }
    }

    @Override
    @SuppressWarnings("try")
    public void visitModule(WasmModule m) {
        parenOpen("module");
        space();
        try (var ignored = new Indenter()) {
            super.visitModule(m);
        }
        newline();

        print(')');
        newline();
    }

    @Override
    public void visitModuleField(ModuleField f) {
        newline();
        newline();
        if (printComment(f.getComment())) {
            newline();
        }
        super.visitModuleField(f);
    }

    @Override
    public void visitMemory(Memory m) {
        parenOpen("memory");
        space();
        printId(m.id);
        space();
        printLimit(m.limit);
        parenClose();
    }

    @Override
    @SuppressWarnings("try")
    public void visitData(Data data) {
        parenOpen("data");
        space();
        printId(data.id);
        space();
        if (data.active) {
            parenOpen("offset");
            space();
            parenOpen();
            visitConst(Instruction.Const.forInt((int) data.offset));
            parenClose();
            parenClose();
        }
        space();

        StringBuilder memorySb = new StringBuilder();

        for (byte signed : data.data) {
            int b = signed & 0xFF;
            switch (b) {
                case 0x09:
                    memorySb.append("\\t");
                    break;
                case 0x0a:
                    memorySb.append("\\n");
                    break;
                case 0x0d:
                    memorySb.append("\\r");
                    break;
                case 0x22:
                    memorySb.append("\\\"");
                    break;
                case 0x5c:
                    memorySb.append("\\\\");
                    break;
                default:
                    if (b >= 0x20 && b < 0x7f) {
                        memorySb.append((char) b);
                    } else {
                        // Everything else is lowered as \xy where xy are hex digits
                        memorySb.append("\\");
                        memorySb.append(Character.forDigit((b >> 4) & 0xF, 16));
                        memorySb.append(Character.forDigit((b) & 0xF, 16));
                    }

                    break;
            }
        }

        try (var ignored = new Indenter()) {
            newline();
            printString(memorySb.toString());
        }

        newline();
        parenClose();
    }

    protected void printFieldType(FieldType fieldType) {
        if (fieldType.mutable) {
            parenOpen("mut");
            space();
        }

        printType(fieldType.storageType);

        if (fieldType.mutable) {
            parenClose();
        }
    }

    @Override
    @SuppressWarnings("try")
    public void visitTypeDefinition(TypeDefinition def) {
        parenOpen("type");
        space();
        printId(def.getId());

        // Final types without supertypes can be written without (sub final ...)
        boolean isSimplified = def.isFinal && def.supertype == null;

        if (!isSimplified) {
            space();
            parenOpen("sub");

            if (def.isFinal) {
                space();
                print("final");
            }

            if (def.supertype != null) {
                space();
                printId(def.supertype);
            }
        }

        space();

        if (def instanceof StructType struct) {
            parenOpen("struct");

            for (StructType.Field field : struct.fields) {
                try (var ignored = new Indenter()) {
                    newline();

                    if (printComment(field.comment)) {
                        newline();
                    }

                    parenOpen("field");
                    space();
                    printId(field.id);
                    space();
                    printFieldType(field.fieldType);
                    parenClose();
                }
            }
            newline();
            parenClose();
        } else if (def instanceof ArrayType array) {
            parenOpen("array");
            space();
            printFieldType(array.elementType);
            parenClose();
        } else if (def instanceof FunctionType function) {
            parenOpen("func");
            printTypeUse(function.typeUse);
            parenClose();
        } else {
            throw GraalError.unimplemented(def.toString());
        }

        if (!isSimplified) {
            parenClose();
        }

        parenClose();
    }

    @Override
    @SuppressWarnings("try")
    public void visitRecursiveGroup(RecursiveGroup def) {
        parenOpen("rec");
        try (var ignored = new Indenter()) {
            super.visitRecursiveGroup(def);
        }
        newline();
        parenClose();
    }

    @Override
    public void visitTag(Tag tag) {
        parenOpen("tag");
        space();
        printId(tag.id);
        printTypeUse(tag.id.typeUse);
        parenClose();
    }

    @Override
    @SuppressWarnings("try")
    public void visitGlobal(Global global) {
        parenOpen("global");
        space();
        printId(global.getId());

        if (global.mutable) {
            space();
            parenOpen("mut");
        }
        space();
        printType(global.getType());
        if (global.mutable) {
            parenClose();
        }
        space();
        try (var ignored = new Indenter()) {
            super.visitGlobal(global);
        }
        newline();
        parenClose();
    }

    @Override
    public void visitImport(Import importDecl) {
        parenOpen("import");
        space();
        printString(importDecl.getModule());
        space();
        printString(importDecl.getName());
        space();
        parenOpen(importDecl.getDescriptor().getType());
        space();
        printId(importDecl.getId());
        super.visitImport(importDecl);
        parenClose();
        parenClose();
    }

    @Override
    public void visitFunImport(ImportDescriptor.Function funcImport) {
        printTypeUse(funcImport.typeUse);
        super.visitFunImport(funcImport);
    }

    @Override
    public void visitExport(Export e) {
        parenOpen("export");
        space();
        printString(e.name);
        space();
        parenOpen(e.type.name);
        space();
        printId(e.getId());
        parenClose();
        parenClose();
    }

    @Override
    public void visitStartFunction(StartFunction startFunction) {
        parenOpen("start");
        space();
        printId(startFunction.function);
        parenClose();
    }

    @Override
    @SuppressWarnings("try")
    public void visitFunction(Function f) {
        parenOpen("func");
        space();

        printId(f.getId());

        space();
        parenOpen("type");
        space();
        printId(f.getFuncType());
        parenClose();

        for (WasmId.Local param : f.params) {
            space();
            printParam(param);
        }

        for (WasmValType result : f.getResults()) {
            space();
            printResult(result);
        }

        try (var ignored = new Indenter()) {
            for (WasmId.Local local : f.getLocals()) {
                newline();
                printLocal(local);
            }

            super.visitFunction(f);
        }
        newline();
        parenClose();
    }

    @Override
    @SuppressWarnings("try")
    public void visitTable(Table t) {
        parenOpen("table");
        space();
        printId(t.id);

        if (t.elements == null) {
            space();
            printLimit(t.limit);
            space();
            printType(t.elementType);
        } else {
            space();
            printType(t.elementType);
            space();
            parenOpen("elem");

            try (var ignored = new Indenter()) {
                int idx = 0;
                for (Instruction elem : t.elements) {
                    newline();
                    parenOpen("item");
                    space();
                    try (var ignored2 = new Indenter()) {
                        printComment(String.format("index: %d, 0x%x", idx, idx));
                        visitInstruction(elem);
                    }
                    newline();
                    parenClose();
                    idx++;
                }
            }
            newline();
            parenClose();
        }

        parenClose();
    }

    @Override
    @SuppressWarnings("try")
    public void visitBlock(Instruction.Block block) {
        print("block");
        space();
        printId(block.getLabel());
        space();
        printComment(block.getComment());
        try (var ignored = new Indenter()) {
            super.visitBlock(block);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitLoop(Instruction.Loop loop) {
        print("loop");
        space();
        printId(loop.getLabel());
        space();
        printComment(loop.getComment());
        try (var ignored = new Indenter()) {
            super.visitLoop(loop);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitIf(Instruction.If ifBlock) {
        print("if");
        space();
        printId(ifBlock.getLabel());
        space();
        printComment(ifBlock.getComment());

        try (var ignored = new Indenter()) {
            visitInstruction(ifBlock.condition);

            newline();
            parenOpen("then");
            try (var ignored2 = new Indenter()) {
                super.visitInstructions(ifBlock.thenInstructions);
            }
            newline();
            parenClose();

            if (ifBlock.hasElse()) {
                newline();
                parenOpen("else");
                try (var ignored2 = new Indenter()) {
                    super.visitInstructions(ifBlock.elseInstructions);
                }
                newline();
                parenClose();
            }
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitTry(Instruction.Try tryBlock) {
        print("try");
        space();
        printId(tryBlock.getLabel());
        space();
        printComment(tryBlock.getComment());

        try (var ignored = new Indenter()) {
            newline();
            parenOpen("do");

            try (var ignored2 = new Indenter()) {
                super.visitInstructions(tryBlock.instructions);
            }
            newline();
            parenClose();

            for (Instruction.Try.Catch catchBlock : tryBlock.catchBlocks) {
                newline();
                parenOpen("catch");
                space();
                printId(catchBlock.tag);
                try (var ignored2 = new Indenter()) {
                    super.visitInstructions(catchBlock.instructions);
                }
                newline();
                parenClose();
            }
        }
        newline();
    }

    @Override
    public void visitNop(Instruction.Nop inst) {
        print("nop");
    }

    @Override
    public void visitInstruction(Instruction inst) {
        newline();
        parenOpen();
        super.visitInstruction(inst);
        parenClose();
        space();
        printComment(inst.getComment());
    }

    @Override
    public void visitUnreachable(Instruction.Unreachable unreachable) {
        print("unreachable");
    }

    @Override
    public void visitBreak(Instruction.Break inst) {
        print(inst.condition == null ? "br" : "br_if");
        space();
        printId(inst.getTarget());
        super.visitBreak(inst);
    }

    @Override
    @SuppressWarnings("try")
    public void visitBreakTable(Instruction.BreakTable inst) {
        print("br_table");

        for (int i = 0; i < inst.numTargets(); i++) {
            space();
            printId(inst.getTarget(i));
        }

        space();
        printId(inst.getDefaultTarget());

        try (var ignored = new Indenter()) {
            super.visitBreakTable(inst);
        }

        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitReturn(Instruction.Return ret) {
        print("return");
        if (!ret.isVoid()) {
            try (var ignored = new Indenter()) {
                visitInstruction(ret.result);
            }
            newline();
        }
    }

    @Override
    public void visitLocalGet(Instruction.LocalGet localGet) {
        print("local.get");
        space();
        printId(localGet.getLocal());
    }

    @Override
    @SuppressWarnings("try")
    public void visitLocalSet(Instruction.LocalSet localSet) {
        print("local.set");
        space();
        printId(localSet.getLocal());
        try (var ignored = new Indenter()) {
            visitInstruction(localSet.value);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitLocalTee(Instruction.LocalTee localTee) {
        print("local.tee");
        space();
        printId(localTee.getLocal());
        try (var ignored = new Indenter()) {
            visitInstruction(localTee.value);
        }
        newline();
    }

    @Override
    public void visitGlobalGet(Instruction.GlobalGet globalGet) {
        print("global.get");
        space();
        printId(globalGet.getGlobal());
    }

    @Override
    @SuppressWarnings("try")
    public void visitGlobalSet(Instruction.GlobalSet globalSet) {
        print("global.set");
        space();
        printId(globalSet.getGlobal());
        try (var ignored = new Indenter()) {
            visitInstruction(globalSet.value);
        }
        newline();
    }

    @Override
    public void visitConst(Instruction.Const constValue) {
        printType(constValue.literal.type);
        print(".const");
        space();
        printLiteral(constValue.literal);
    }

    @Override
    public void visitRelocation(Instruction.Relocation relocation) {
        assert relocation.wasProcessed() : "Unprocessed relocation found in image: " + relocation;
        /*
         * TODO GR-46987 Workaround for binaryen because it needs a non-standard pop instruction
         * sometimes.
         */
        if (relocation.target instanceof BinaryenCompat.Pop pop) {
            print("pop");
            space();
            printType(pop.type);
        } else {
            /*
             * Must be a super-call since the wrapping parenthesis have already been generated for
             * the relocation instruction.
             */
            super.visitInstruction(relocation.getValue());
        }
    }

    @Override
    @SuppressWarnings("try")
    public void visitBinary(Instruction.Binary inst) {
        print(inst.op.opName);
        try (var ignored = new Indenter()) {
            super.visitBinary(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitUnary(Instruction.Unary inst) {
        print(inst.op.opName);
        try (var ignored = new Indenter()) {
            super.visitUnary(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitDrop(Instruction.Drop inst) {
        print("drop");
        try (var ignored = new Indenter()) {
            super.visitDrop(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitCall(Instruction.Call inst) {
        print("call");
        space();
        printId(inst.getTarget());
        try (var ignored = new Indenter()) {
            super.visitCall(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitCallRef(Instruction.CallRef inst) {
        print("call_ref");
        space();
        printId(inst.functionType);
        space();

        try (var ignored = new Indenter()) {
            super.visitCallRef(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitCallIndirect(Instruction.CallIndirect inst) {
        print("call_indirect");
        space();
        printId(inst.table);
        space();

        parenOpen("type");
        space();
        printId(inst.funcId);
        parenClose();

        try (var ignored = new Indenter()) {
            super.visitCallIndirect(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitThrow(Instruction.Throw inst) {
        print("throw");
        space();
        printId(inst.tag);

        try (var ignored = new Indenter()) {
            super.visitThrow(inst);
        }
        newline();
    }

    private void printLiteral(Literal literal) {
        print(literal.asText());
    }

    @Override
    @SuppressWarnings("try")
    public void visitSelect(Instruction.Select inst) {
        print("select");

        // Non-numeric types must have a type annotation for select
        if (!inst.type.isNumeric()) {
            space();
            printResult(inst.type);
        }

        try (var ignored = new Indenter()) {
            super.visitSelect(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitTableGet(Instruction.TableGet inst) {
        print("table.get");
        space();
        printId(inst.table);
        space();

        try (var ignored = new Indenter()) {
            super.visitTableGet(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitTableSet(Instruction.TableSet inst) {
        print("table.set");
        space();
        printId(inst.table);
        space();

        try (var ignored = new Indenter()) {
            super.visitTableSet(inst);
        }
        newline();
    }

    @Override
    public void visitTableSize(Instruction.TableSize inst) {
        print("table.size");
        space();
        printId(inst.table);
    }

    @Override
    @SuppressWarnings("try")
    public void visitTableGrow(Instruction.TableGrow inst) {
        print("table.grow");
        space();
        printId(inst.table);
        space();

        try (var ignored = new Indenter()) {
            super.visitTableGrow(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitTableFill(Instruction.TableFill inst) {
        print("table.fill");
        space();
        printId(inst.table);
        space();

        try (var ignored = new Indenter()) {
            super.visitTableFill(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitTableCopy(Instruction.TableCopy inst) {
        print("table.get");
        space();
        printId(inst.destTable);
        space();
        printId(inst.srcTable);
        space();

        try (var ignored = new Indenter()) {
            super.visitTableCopy(inst);
        }
        newline();
    }

    private void printMemarg(Instruction.Memory memoryInst) {
        int offset = memoryInst.calculateOffset();
        if (offset != 0) {
            space();
            print("offset=");
            printLiteral(Literal.forInt(offset));
        }
    }

    @Override
    @SuppressWarnings("try")
    public void visitLoad(Instruction.Load inst) {
        printType(inst.stackType);
        print(".load");
        if (inst.memoryWidth != 0) {
            printInt(inst.memoryWidth);
            print('_');
            print(inst.signed ? 's' : 'u');
        }
        printMemarg(inst);
        try (var ignored = new Indenter()) {
            visitInstruction(inst.baseAddress);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitStore(Instruction.Store inst) {
        printType(inst.stackType);
        print(".store");
        if (inst.memoryWidth != 0) {
            printInt(inst.memoryWidth);
        }
        printMemarg(inst);
        try (var ignored = new Indenter()) {
            visitInstruction(inst.baseAddress);
            visitInstruction(inst.value);
        }
        newline();
    }

    @Override
    public void visitMemorySize(Instruction.MemorySize inst) {
        print("memory.size");
    }

    @Override
    @SuppressWarnings("try")
    public void visitMemoryGrow(Instruction.MemoryGrow inst) {
        print("memory.grow");
        try (var ignored = new Indenter()) {
            super.visitMemoryGrow(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitMemoryFill(Instruction.MemoryFill inst) {
        print("memory.fill");
        try (var ignored = new Indenter()) {
            super.visitMemoryFill(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitMemoryCopy(Instruction.MemoryCopy inst) {
        print("memory.copy");
        try (var ignored = new Indenter()) {
            super.visitMemoryCopy(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitMemoryInit(Instruction.MemoryInit inst) {
        print("memory.init");
        space();
        visitId(inst.dataSegment);
        space();

        try (var ignored = new Indenter()) {
            super.visitMemoryInit(inst);
        }
        newline();
    }

    @Override
    public void visitDataDrop(Instruction.DataDrop inst) {
        print("data.drop");
        space();
        visitId(inst.dataSegment);
    }

    @Override
    public void visitRefNull(Instruction.RefNull inst) {
        print("ref.null");
        space();
        printHeapType(inst.heapType);
    }

    @Override
    public void visitRefFunc(Instruction.RefFunc inst) {
        print("ref.func");
        space();
        printId(inst.func);
    }

    @Override
    @SuppressWarnings("try")
    public void visitRefTest(Instruction.RefTest inst) {
        print("ref.test");
        space();

        printType(inst.testType);

        try (var ignored = new Indenter()) {
            super.visitRefTest(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitRefCast(Instruction.RefCast inst) {
        print("ref.cast");
        space();
        printType(inst.newType);

        try (var ignored = new Indenter()) {
            super.visitRefCast(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitStructNew(Instruction.StructNew inst) {
        print("struct.new");

        if (inst.isDefault()) {
            print("_default");
        }

        space();
        printId(inst.type);

        if (!inst.isDefault()) {
            try (var ignored = new Indenter()) {
                visitInstructions(inst.getFieldValues());
            }
            newline();
        }
    }

    @Override
    @SuppressWarnings("try")
    public void visitStructGet(Instruction.StructGet inst) {
        print("struct.get");
        printExtensionSuffix(inst.extension);
        space();
        printId(inst.refType);
        space();
        printId(inst.fieldId);

        try (var ignored = new Indenter()) {
            super.visitStructGet(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitStructSet(Instruction.StructSet inst) {
        print("struct.set");
        space();
        printId(inst.refType);
        space();
        printId(inst.fieldId);

        try (var ignored = new Indenter()) {
            super.visitStructSet(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayNew(Instruction.ArrayNew inst) {
        print("array.new");

        if (inst.isDefault()) {
            print("_default");
        }

        space();
        printId(inst.type);

        try (var ignored = new Indenter()) {
            super.visitArrayNew(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayNewFixed(Instruction.ArrayNewFixed inst) {
        print("array.new_fixed");

        space();
        printId(inst.type);

        space();
        printInt(inst.getLength());

        try (var ignored = new Indenter()) {
            super.visitArrayNewFixed(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayNewData(Instruction.ArrayNewData inst) {
        print("array.new_data");

        space();
        printId(inst.type);
        space();
        printId(inst.dataSegment);

        try (var ignored = new Indenter()) {
            super.visitArrayNewData(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayLen(Instruction.ArrayLen inst) {
        print("array.len");
        space();
        try (var ignored = new Indenter()) {
            super.visitArrayLen(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayFill(Instruction.ArrayFill inst) {
        print("array.fill");
        space();
        printId(inst.arrayType);
        space();
        try (var ignored = new Indenter()) {
            super.visitArrayFill(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayGet(Instruction.ArrayGet inst) {
        print("array.get");
        printExtensionSuffix(inst.extension);
        space();
        printId(inst.refType);
        space();

        try (var ignored = new Indenter()) {
            super.visitArrayGet(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArraySet(Instruction.ArraySet inst) {
        print("array.set");
        space();
        printId(inst.refType);
        space();

        try (var ignored = new Indenter()) {
            super.visitArraySet(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayCopy(Instruction.ArrayCopy inst) {
        print("array.copy");
        space();
        printId(inst.destType);
        space();
        printId(inst.srcType);
        space();

        try (var ignored = new Indenter()) {
            super.visitArrayCopy(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitArrayInitData(Instruction.ArrayInitData inst) {
        print("array.init_data");
        space();
        printId(inst.type);
        space();
        printId(inst.dataSegment);
        space();

        try (var ignored = new Indenter()) {
            super.visitArrayInitData(inst);
        }
        newline();
    }

    @Override
    @SuppressWarnings("try")
    public void visitAnyExternConversion(Instruction.AnyExternConversion inst) {
        print(inst.isToExtern ? "extern.convert_any" : "any.convert_extern");
        space();
        try (var ignored = new Indenter()) {
            super.visitAnyExternConversion(inst);
        }
        newline();
    }
}
