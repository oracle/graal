/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.listeners;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.ValueSymbol;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.LazyFunctionParser;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.target.TargetTriple;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.parser.text.LLSourceBuilder;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class Module implements ParserListener {

    private final ModelModule module;

    private final ParameterAttributes paramAttributes;

    private final StringTable stringTable;

    private int mode = 1;

    private final Types types;

    private final IRScope scope;

    private final ArrayDeque<FunctionDefinition> functionQueue;

    private final LLSourceBuilder llSource;

    private final AtomicInteger index;

    Module(ModelModule module, StringTable stringTable, IRScope scope, LLSourceBuilder llSource) {
        this.module = module;
        this.stringTable = stringTable;
        this.types = new Types(module);
        this.scope = scope;
        this.llSource = llSource;
        this.paramAttributes = new ParameterAttributes(types);
        functionQueue = new ArrayDeque<>();
        index = new AtomicInteger(0);
    }

    // private static final int STRTAB_RECORD_OFFSET = 2;
    // private static final int STRTAB_RECORD_OFFSET_INDEX = 0;
    // private static final int STRTAB_RECORD_LENGTH_INDEX = 1;

    private boolean useStrTab() {
        return mode == 2;
    }

    private long readNameFromStrTab(RecordBuffer buffer) {
        if (useStrTab()) {
            int offset = buffer.readInt();
            int length = buffer.readInt();
            return offset | (((long) length) << 32);
        } else {
            return 0;
        }
    }

    private void assignNameFromStrTab(long name, ValueSymbol target) {
        if (useStrTab()) {
            int offset = (int) (name & 0xFFFFFFFF);
            int length = (int) (name >> 32);
            stringTable.requestName(offset, length, target);
        }
    }

    // private static final int FUNCTION_TYPE = 0;
    // private static final int FUNCTION_ISPROTOTYPE = 2;
    // private static final int FUNCTION_LINKAGE = 3;
    // private static final int FUNCTION_PARAMATTR = 4;
    // private static final int FUNCTION_VISIBILITY = 7;

    private void createFunction(RecordBuffer buffer) {
        long name = readNameFromStrTab(buffer);
        Type type = types.get(buffer.readInt());
        if (type instanceof PointerType) {
            type = ((PointerType) type).getPointeeType();
        }

        buffer.skip();
        final FunctionType functionType = Types.castToFunction(type);
        final boolean isPrototype = buffer.readBoolean();
        final Linkage linkage = Linkage.decode(buffer.read());

        final AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(buffer.read());
        buffer.skip();
        buffer.skip();

        Visibility visibility = Visibility.DEFAULT;
        if (buffer.remaining() > 0) {
            visibility = Visibility.decode(buffer.read());
        }

        if (isPrototype) {
            final FunctionDeclaration function = new FunctionDeclaration(functionType, linkage, paramAttr, index.getAndIncrement());
            module.addFunctionDeclaration(function);
            scope.addSymbol(function, function.getType());
            assignNameFromStrTab(name, function);
        } else {
            final FunctionDefinition function = new FunctionDefinition(functionType, linkage, visibility, paramAttr, index.getAndIncrement());
            module.addFunctionDefinition(function);
            scope.addSymbol(function, function.getType());
            assignNameFromStrTab(name, function);
            functionQueue.addLast(function);
        }
    }

    // private static final int GLOBALVAR_TYPE = 0;
    // private static final int GLOBALVAR_FLAGS = 1;
    private static final long GLOBALVAR_EXPLICICTTYPE_MASK = 0x2;
    private static final long GLOBALVAR_ISCONSTANT_MASK = 0x1;
    // private static final int GLOBALVAR_INTITIALIZER = 2;
    // private static final int GLOBALVAR_LINKAGE = 3;
    // private static final int GLOBALVAR_ALIGN = 4;
    // private static final int GLOBALVAR_VISIBILITY = 6;

    private void createGlobalVariable(RecordBuffer buffer) {
        long name = readNameFromStrTab(buffer);
        final long typeField = buffer.read();
        final long flagField = buffer.read();

        Type type = types.get(typeField);
        if ((flagField & GLOBALVAR_EXPLICICTTYPE_MASK) != 0) {
            type = new PointerType(type);
        }

        final boolean isConstant = (flagField & GLOBALVAR_ISCONSTANT_MASK) != 0;
        final int initialiser = buffer.readInt();
        final long linkage = buffer.read();
        final int align = buffer.readInt();
        buffer.skip();

        long visibility = Visibility.DEFAULT.getEncodedValue();
        if (buffer.remaining() > 0) {
            visibility = buffer.read();
        }

        GlobalVariable global = GlobalVariable.create(isConstant, (PointerType) type, align, linkage, visibility, scope.getSymbols(), initialiser, index.getAndIncrement());
        assignNameFromStrTab(name, global);
        module.addGlobalVariable(global);
        scope.addSymbol(global, global.getType());
    }

    // private static final int GLOBALALIAS_TYPE = 0;
    // private static final int GLOBALALIAS_NEW_VALUE = 2;
    // private static final int GLOBALALIAS_NEW_LINKAGE = 3;

    private void createGlobalAliasNew(RecordBuffer buffer) {
        long name = readNameFromStrTab(buffer);
        final PointerType type = new PointerType(types.get(buffer.read()));

        buffer.skip(); // idx = 1 is address space information
        final int value = buffer.readInt();
        final long linkage = buffer.read();

        final GlobalAlias global = GlobalAlias.create(type, linkage, Visibility.DEFAULT.ordinal(), scope.getSymbols(), value);
        assignNameFromStrTab(name, global);
        module.addAlias(global);
        scope.addSymbol(global, global.getType());
    }

    // private static final int GLOBALALIAS_OLD_VALUE = 1;
    // private static final int GLOBALALIAS_OLD_LINKAGE = 2;

    private void createGlobalAliasOld(RecordBuffer buffer) {
        long name = readNameFromStrTab(buffer);
        final PointerType type = Types.castToPointer(types.get(buffer.read()));
        int value = buffer.readInt();
        long linkage = buffer.read();

        final GlobalAlias global = GlobalAlias.create(type, linkage, Visibility.DEFAULT.ordinal(), scope.getSymbols(), value);
        assignNameFromStrTab(name, global);
        module.addAlias(global);
        scope.addSymbol(global, global.getType());
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case PARAMATTR:
                return paramAttributes;

            case PARAMATTR_GROUP:
                return paramAttributes;

            case CONSTANTS:
                return new Constants(types, scope);

            case FUNCTION: {
                throw new LLVMParserException("Function is not parsed lazily!");
            }

            case TYPE:
                return types;

            case VALUE_SYMTAB:
                return new ValueSymbolTable(scope);

            case METADATA:
            case METADATA_KIND:
                return new Metadata(types, scope);

            case STRTAB:
                return stringTable;

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void skip(Block block, LLVMScanner.LazyScanner lazyScanner) {
        if (block == Block.FUNCTION) {
            if (functionQueue.isEmpty()) {
                throw new LLVMParserException("Missing Function Prototype in Bitcode File!");
            }
            final FunctionDefinition definition = functionQueue.removeFirst();
            module.addFunctionParser(definition, new LazyFunctionParser(lazyScanner, scope, types, definition, mode, paramAttributes, llSource));

        } else {
            ParserListener.super.skip(block, lazyScanner);
        }
    }

    private static final int MODULE_VERSION = 1;
    private static final int MODULE_TARGET_TRIPLE = 2;
    private static final int MODULE_TARGET_DATALAYOUT = 3;
    // private static final int MODULE_ASM = 4;
    // private static final int MODULE_SECTION_NAME = 5;
    // private static final int MODULE_DEPLIB = 6;
    private static final int MODULE_GLOBAL_VARIABLE = 7;
    private static final int MODULE_FUNCTION = 8;
    private static final int MODULE_ALIAS_OLD = 9;
    // private static final int MODULE_PURGE_VALUES = 10;
    // private static final int MODULE_GC_NAME = 11;
    // private static final int MODULE_COMDAT = 12;
    // private static final int MODULE_VSTOFFSET = 13;
    private static final int MODULE_ALIAS = 14;
    // private static final int MODULE_METADATA_VALUES = 15;
    // private static final int MODULE_SOURCE_FILENAME = 16;
    // private static final int MODULE_CODE_HASH = 17;
    // private static final int MODULE_CODE_IFUNC = 18;

    @Override
    public void record(RecordBuffer buffer) {
        switch (buffer.getId()) {
            case MODULE_VERSION:
                mode = buffer.readInt();
                break;

            case MODULE_TARGET_TRIPLE:
                module.addTargetInformation(new TargetTriple(buffer.readString()));
                break;

            case MODULE_TARGET_DATALAYOUT:
                final TargetDataLayout layout = TargetDataLayout.fromString(buffer.readString());
                module.setTargetDataLayout(layout);
                break;

            case MODULE_GLOBAL_VARIABLE:
                createGlobalVariable(buffer);
                break;

            case MODULE_FUNCTION:
                createFunction(buffer);
                break;

            case MODULE_ALIAS:
                createGlobalAliasNew(buffer);
                break;
            case MODULE_ALIAS_OLD:
                createGlobalAliasOld(buffer);
                break;

            default:
                break;
        }
    }
}
