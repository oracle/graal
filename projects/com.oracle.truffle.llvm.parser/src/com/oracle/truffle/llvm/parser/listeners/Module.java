/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.target.TargetTriple;
import com.oracle.truffle.llvm.parser.records.ModuleRecord;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class Module implements ParserListener {

    private final ModelModule module;

    private final ParameterAttributes paramAttributes = new ParameterAttributes();

    private final StringTable stringTable = new StringTable();

    private int mode = 1;

    private final Types types;

    public Module(ModelModule module) {
        this.module = module;
        types = new Types(module);
    }

    private static final int STRTAB_RECORD_OFFSET = 2;
    private static final int STRTAB_RECORD_OFFSET_INDEX = 0;
    private static final int STRTAB_RECORD_LENGTH_INDEX = 1;

    private boolean useStrTab() {
        return mode == 2;
    }

    private void readNameFromStrTab(long[] args, ValueSymbol target) {
        final int offset = (int) args[STRTAB_RECORD_OFFSET_INDEX];
        final int length = (int) args[STRTAB_RECORD_LENGTH_INDEX];
        stringTable.requestName(offset, length, target);
    }

    private static final int FUNCTION_TYPE = 0;
    private static final int FUNCTION_ISPROTOTYPE = 2;
    private static final int FUNCTION_LINKAGE = 3;
    private static final int FUNCTION_PARAMATTR = 4;

    private void createFunction(long[] args) {
        final int recordOffset = useStrTab() ? STRTAB_RECORD_OFFSET : 0;
        Type type = types.get(args[FUNCTION_TYPE + recordOffset]);
        if (type instanceof PointerType) {
            type = ((PointerType) type).getPointeeType();
        }

        final FunctionType functionType = (FunctionType) type;
        final boolean isPrototype = args[FUNCTION_ISPROTOTYPE + recordOffset] != 0;
        final Linkage linkage = Linkage.decode(args[FUNCTION_LINKAGE + recordOffset]);

        final AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(args[FUNCTION_PARAMATTR + recordOffset]);

        if (isPrototype) {
            final FunctionDeclaration function = new FunctionDeclaration(functionType, linkage, paramAttr);
            module.addFunctionDeclaration(function);
            if (useStrTab()) {
                readNameFromStrTab(args, function);
            }
        } else {
            final FunctionDefinition function = new FunctionDefinition(functionType, linkage, paramAttr);
            module.addFunctionDefinition(function);
            if (useStrTab()) {
                readNameFromStrTab(args, function);
            }
        }
    }

    private static final int GLOBALVAR_TYPE = 0;
    private static final int GLOBALVAR_FLAGS = 1;
    private static final long GLOBALVAR_EXPLICICTTYPE_MASK = 0x2;
    private static final long GLOBALVAR_ISCONSTANT_MASK = 0x1;
    private static final int GLOBALVAR_INTITIALIZER = 2;
    private static final int GLOBALVAR_LINKAGE = 3;
    private static final int GLOBALVAR_ALIGN = 4;
    private static final int GLOBALVAR_VISIBILITY = 6;

    private void createGlobalVariable(long[] args) {
        final int recordOffset = useStrTab() ? STRTAB_RECORD_OFFSET : 0;
        final long typeField = args[GLOBALVAR_TYPE + recordOffset];
        final long flagField = args[GLOBALVAR_FLAGS + recordOffset];

        Type type = types.get(typeField);
        if ((flagField & GLOBALVAR_EXPLICICTTYPE_MASK) != 0) {
            type = new PointerType(type);
        }

        final boolean isConstant = (flagField & GLOBALVAR_ISCONSTANT_MASK) != 0;
        final int initialiser = (int) args[GLOBALVAR_INTITIALIZER + recordOffset];
        final long linkage = args[GLOBALVAR_LINKAGE + recordOffset];
        final int align = (int) args[GLOBALVAR_ALIGN + recordOffset];

        long visibility = Visibility.DEFAULT.getEncodedValue();
        if (GLOBALVAR_VISIBILITY + recordOffset < args.length) {
            visibility = args[GLOBALVAR_VISIBILITY + recordOffset];
        }

        final GlobalValueSymbol global;
        if (isConstant) {
            global = GlobalConstant.create(type, initialiser, align, linkage, visibility);
        } else {
            global = GlobalVariable.create(type, initialiser, align, linkage, visibility);
        }
        if (useStrTab()) {
            readNameFromStrTab(args, global);
        }
        module.addGlobalSymbol(global);
    }

    private static final int GLOBALALIAS_TYPE = 0;
    private static final int GLOBALALIAS_NEW_VALUE = 2;
    private static final int GLOBALALIAS_NEW_LINKAGE = 3;

    private void createGlobalAliasNew(long[] args) {
        final int recordOffset = useStrTab() ? STRTAB_RECORD_OFFSET : 0;
        final Type type = new PointerType(types.get(args[GLOBALALIAS_TYPE + recordOffset]));

        // idx = 1 is address space information
        final int value = (int) args[GLOBALALIAS_NEW_VALUE + recordOffset];
        final long linkage = args[GLOBALALIAS_NEW_LINKAGE + recordOffset];

        final GlobalAlias global = GlobalAlias.create(type, value, linkage, Visibility.DEFAULT.ordinal());
        if (useStrTab()) {
            readNameFromStrTab(args, global);
        }
        module.addGlobalSymbol(global);
    }

    private static final int GLOBALALIAS_OLD_VALUE = 1;
    private static final int GLOBALALIAS_OLD_LINKAGE = 2;

    private void createGlobalAliasOld(long[] args) {
        final int recordOffset = useStrTab() ? STRTAB_RECORD_OFFSET : 0;
        final Type type = types.get(args[GLOBALALIAS_TYPE + recordOffset]);
        int value = (int) args[GLOBALALIAS_OLD_VALUE + recordOffset];
        long linkage = args[GLOBALALIAS_OLD_LINKAGE + recordOffset];

        final GlobalAlias global = GlobalAlias.create(type, value, linkage, Visibility.DEFAULT.ordinal());
        if (useStrTab()) {
            readNameFromStrTab(args, global);
        }
        module.addGlobalSymbol(global);
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case MODULE:
                return this; // Entering from root

            case PARAMATTR:
                return paramAttributes;

            case PARAMATTR_GROUP:
                return paramAttributes;

            case CONSTANTS:
                return new Constants(types, module);

            case FUNCTION: {
                final FunctionDefinition functionDefinition = module.generateFunction();
                final FunctionType functionType = functionDefinition.getType();
                for (Type arg : functionType.getArgumentTypes()) {
                    functionDefinition.createParameter(arg);
                }
                return new Function(types, functionDefinition, mode, paramAttributes);
            }

            case TYPE:
                return types;

            case VALUE_SYMTAB:
                return new ValueSymbolTable(module);

            case METADATA:
            case METADATA_KIND:
                return new Metadata(types, module);

            case STRTAB:
                return stringTable;

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        module.exitModule();
    }

    @Override
    public void record(long id, long[] args) {
        final ModuleRecord record = ModuleRecord.decode(id);
        switch (record) {
            case VERSION:
                mode = (int) args[0];
                break;

            case TARGET_TRIPLE:
                module.addTargetInformation(new TargetTriple(Records.toString(args)));
                break;

            case TARGET_DATALAYOUT:
                final TargetDataLayout layout = TargetDataLayout.fromString(Records.toString(args));
                module.setTargetDataLayout(layout);
                break;

            case GLOBAL_VARIABLE:
                createGlobalVariable(args);
                break;

            case FUNCTION:
                createFunction(args);
                break;

            case ALIAS:
                createGlobalAliasNew(args);
                break;
            case ALIAS_OLD:
                createGlobalAliasOld(args);
                break;

            default:
                break;
        }
    }
}
