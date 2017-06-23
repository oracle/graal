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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.generators.FunctionGenerator;
import com.oracle.truffle.llvm.parser.model.generators.ModuleGenerator;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.target.TargetInformation;
import com.oracle.truffle.llvm.parser.model.target.TargetTriple;
import com.oracle.truffle.llvm.parser.records.ModuleRecord;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class Module implements ParserListener {

    private final ModuleGenerator generator;

    private final ParameterAttributes paramAttributes = new ParameterAttributes();

    private int mode = 1;

    protected final Types types;

    private final List<TargetInformation> info = new ArrayList<>();

    protected final List<FunctionType> functions = new ArrayList<>();

    protected final List<Type> symbols = new ArrayList<>();

    public Module(ModuleGenerator generator) {
        this.generator = generator;
        types = new Types(generator);
    }

    private static final int FUNCTION_TYPE = 0;
    private static final int FUNCTION_ISPROTOTYPE = 2;
    private static final int FUNCTION_LINKAGE = 3;
    private static final int FUNCTION_PARAMATTR = 4;

    private void createFunction(long[] args) {
        Type type = types.get(args[FUNCTION_TYPE]);
        if (type instanceof PointerType) {
            type = ((PointerType) type).getPointeeType();
        }

        final FunctionType functionType = (FunctionType) type;
        final boolean isPrototype = args[FUNCTION_ISPROTOTYPE] != 0;
        final Linkage linkage = Linkage.decode(args[FUNCTION_LINKAGE]);

        final AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(args[FUNCTION_PARAMATTR]);

        generator.createFunction(functionType, isPrototype, linkage, paramAttr);
        symbols.add(functionType);
        if (!isPrototype) {
            functions.add(functionType);
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
        final long typeField = args[GLOBALVAR_TYPE];
        final long flagField = args[GLOBALVAR_FLAGS];

        Type type = types.get(typeField);
        if ((flagField & GLOBALVAR_EXPLICICTTYPE_MASK) != 0) {
            type = new PointerType(type);
        }

        final boolean isConstant = (flagField & GLOBALVAR_ISCONSTANT_MASK) != 0;
        final int initialiser = (int) args[GLOBALVAR_INTITIALIZER];
        final long linkage = args[GLOBALVAR_LINKAGE];
        final int align = (int) args[GLOBALVAR_ALIGN];

        long visibility = Visibility.DEFAULT.getEncodedValue();
        if (GLOBALVAR_VISIBILITY < args.length) {
            visibility = args[GLOBALVAR_VISIBILITY];
        }

        generator.createGlobal(type, isConstant, initialiser, align, linkage, visibility);
        symbols.add(type);
    }

    private static final int GLOBALALIAS_TYPE = 0;
    private static final int GLOBALALIAS_NEW_VALUE = 2;
    private static final int GLOBALALIAS_NEW_LINKAGE = 3;

    private void createGlobalAliasNew(long[] args) {
        final Type type = new PointerType(types.get(args[GLOBALALIAS_TYPE]));

        // idx = 1 is address space information
        final int value = (int) args[GLOBALALIAS_NEW_VALUE];
        final long linkage = args[GLOBALALIAS_NEW_LINKAGE];

        generator.createAlias(type, value, linkage, Visibility.DEFAULT.ordinal());
        symbols.add(type);
    }

    private static final int GLOBALALIAS_OLD_VALUE = 1;
    private static final int GLOBALALIAS_OLD_LINKAGE = 2;

    private void createGlobalAliasOld(long[] args) {
        final Type type = types.get(args[GLOBALALIAS_TYPE]);
        int value = (int) args[GLOBALALIAS_OLD_VALUE];
        long linkage = args[GLOBALALIAS_OLD_LINKAGE];

        generator.createAlias(type, value, linkage, Visibility.DEFAULT.ordinal());
        symbols.add(type);
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
                return new Constants(types, symbols, generator);

            case FUNCTION: {
                FunctionType function = functions.remove(0);

                FunctionGenerator gen = generator.generateFunction();

                List<Type> sym = new ArrayList<>(symbols);

                for (Type arg : function.getArgumentTypes()) {
                    gen.createParameter(arg);
                    sym.add(arg);
                }

                return new Function(types, sym, gen, mode);
            }

            case TYPE:
                return types;

            case VALUE_SYMTAB:
                return new ValueSymbolTable(generator);

            case METADATA:
            case METADATA_KIND:
                return new Metadata(types, generator);

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        generator.exitModule();
    }

    @Override
    public void record(long id, long[] args) {
        final ModuleRecord record = ModuleRecord.decode(id);
        switch (record) {
            case VERSION:
                mode = (int) args[0];
                break;

            case TARGET_TRIPLE:
                info.add(new TargetTriple(Records.toString(args)));
                break;

            case TARGET_DATALAYOUT:
                final TargetDataLayout layout = TargetDataLayout.fromString(Records.toString(args));
                info.add(layout);
                generator.createTargetDataLayout(layout);
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
