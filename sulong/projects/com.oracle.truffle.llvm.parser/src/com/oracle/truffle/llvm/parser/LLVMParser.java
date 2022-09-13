/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetTriple;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMElemPtrSymbol;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.Function;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LazyLLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMThreadLocalSymbol;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class LLVMParser {
    private final Source source;
    private final LLVMParserRuntime runtime;
    private int threadLocalGlobalObjectCounter;

    public LLVMParser(Source source, LLVMParserRuntime runtime) {
        this.source = source;
        this.runtime = runtime;
        threadLocalGlobalObjectCounter = 0;
    }

    public LLVMParserResult parse(ModelModule module, DataLayout targetDataLayout) {
        List<GlobalVariable> externalGlobals = new ArrayList<>();
        List<GlobalVariable> definedGlobals = new ArrayList<>();
        List<GlobalVariable> threadLocalGlobals = new ArrayList<>();
        List<FunctionSymbol> externalFunctions = new ArrayList<>();
        List<FunctionSymbol> definedFunctions = new ArrayList<>();

        defineGlobals(module.getGlobalVariables(), definedGlobals, externalGlobals, threadLocalGlobals);
        defineFunctions(module, definedFunctions, externalFunctions, targetDataLayout);
        defineAliases(module.getAliases(), targetDataLayout);

        return new LLVMParserResult(runtime, definedFunctions, externalFunctions, definedGlobals, externalGlobals, threadLocalGlobals, threadLocalGlobalObjectCounter, targetDataLayout,
                        module.getTargetInformation(TargetTriple.class), module.getTotalSize());
    }

    private static boolean ignoreGlobal(GlobalVariable g) {
        String name = g.getName();
        /*
         * Dummy array to keep other globals alive during compilation and linking. We do not
         * initialize it as it's not relevant for execution, and it can cause problems during module
         * initialization (e.g. if a TLS variable is referenced).
         */
        return name.equals("llvm.used") || name.equals("llvm.compiler.used");
    }

    private void defineGlobals(List<GlobalVariable> globals, List<GlobalVariable> definedGlobals, List<GlobalVariable> externalGlobals, List<GlobalVariable> threadLocalGlobals) {
        for (GlobalVariable global : globals) {
            if (ignoreGlobal(global)) {
                continue;
            }
            if (global.isExternal()) {
                externalGlobals.add(global);
            } else {
                defineGlobal(global, definedGlobals, threadLocalGlobals);
            }
        }
    }

    private void defineFunctions(ModelModule model, List<FunctionSymbol> definedFunctions, List<FunctionSymbol> externalFunctions, DataLayout dataLayout) {
        for (FunctionDefinition function : model.getDefinedFunctions()) {
            if (function.isExternal()) {
                externalFunctions.add(function);
            } else {
                defineFunction(function, model, dataLayout);
                definedFunctions.add(function);
            }
        }

        for (FunctionDeclaration function : model.getDeclaredFunctions()) {
            assert function.isExternal();
            externalFunctions.add(function);
        }
    }

    private void defineAliases(List<GlobalAlias> aliases, DataLayout targetDataLayout) {
        for (GlobalAlias alias : aliases) {
            defineAlias(alias, targetDataLayout);
        }
    }

    private void defineGlobal(GlobalVariable global, List<GlobalVariable> definedGlobals, List<GlobalVariable> threadLocalGlobals) {
        assert !global.isExternal();
        LLVMSymbol symbol;
        if (global.isThreadLocal()) {
            symbol = LLVMThreadLocalSymbol.create(global.getName(), global.getSourceSymbol(), runtime.getBitcodeID(), global.getIndex(), global.isExported(), global.isExternalWeak());
            Type type = global.getType().getPointeeType();
            if (isSpecialGlobalSlot(type)) {
                threadLocalGlobalObjectCounter++;
            }
            threadLocalGlobals.add(global);
        } else {
            symbol = LLVMGlobal.create(global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly(), global.getIndex(), runtime.getBitcodeID(), global.isExported(),
                            global.isExternalWeak());
            definedGlobals.add(global);
        }
        runtime.getFileScope().register(symbol);
        registerInPublicFileScope(symbol);
    }

    /**
     * Globals of pointer type need to be handles specially because they can potentially contain a
     * foreign object.
     */
    public static boolean isSpecialGlobalSlot(Type type) {
        if (type instanceof PointerType) {
            return true;
        } else {
            return type == PrimitiveType.I64;
        }
    }

    private void defineFunction(FunctionSymbol functionSymbol, ModelModule model, DataLayout dataLayout) {
        assert !functionSymbol.isExternal();
        FunctionDefinition functionDefinition = (FunctionDefinition) functionSymbol;
        LazyToTruffleConverterImpl lazyConverter = new LazyToTruffleConverterImpl(runtime, functionDefinition, source, model.getFunctionParser(functionDefinition),
                        model.getFunctionProcessor(), dataLayout);
        Function function = new LazyLLVMIRFunction(lazyConverter);
        LLVMFunction llvmFunction = LLVMFunction.create(functionSymbol.getName(), function, functionSymbol.getType(), runtime.getBitcodeID(), functionSymbol.getIndex(),
                        functionDefinition.isExported(), runtime.getFile().getPath(), functionDefinition.isExternalWeak());
        lazyConverter.setRootFunction(llvmFunction);
        runtime.getFileScope().register(llvmFunction);
        registerInPublicFileScope(llvmFunction);
        final boolean cxxInterop = LLVMLanguage.getContext().getEnv().getOptions().get(SulongEngineOption.CXX_INTEROP);
        if (cxxInterop) {
            model.getFunctionParser(functionDefinition).parseLinkageName(runtime);
        }
    }

    private void defineAlias(GlobalAlias alias, DataLayout targetDataLayout) {
        LLVMSymbol alreadyRegisteredSymbol = runtime.getFileScope().get(alias.getName());
        if (alreadyRegisteredSymbol != null) {
            // this alias was already registered by a recursive call
            assert alreadyRegisteredSymbol instanceof LLVMAlias;
            return;
        }
        defineAlias(alias.getName(), alias.isExported(), alias.getValue(), targetDataLayout);
    }

    private void registerInPublicFileScope(LLVMSymbol symbol) {
        if (symbol.isExported()) {
            runtime.getPublicFileScope().register(symbol);
        }
    }

    private void defineAlias(String aliasName, boolean isAliasExported, SymbolImpl value, DataLayout targetDataLayout) {
        if (value instanceof FunctionSymbol) {
            FunctionSymbol function = (FunctionSymbol) value;
            defineAlias(function.getName(), aliasName, isAliasExported);
        } else if (value instanceof GlobalVariable) {
            GlobalVariable global = (GlobalVariable) value;
            defineAlias(global.getName(), aliasName, isAliasExported);
        } else if (value instanceof GlobalAlias) {
            GlobalAlias target = (GlobalAlias) value;
            defineAlias(target, targetDataLayout);
            defineAlias(target.getName(), aliasName, isAliasExported);
        } else if (value instanceof CastConstant) {
            // TODO (chaeubl): this is not perfectly accurate as we are loosing the type cast
            CastConstant cast = (CastConstant) value;
            defineAlias(aliasName, isAliasExported, cast.getValue(), targetDataLayout);
        } else if (value instanceof GetElementPointerConstant) {
            GetElementPointerConstant elementPointerConstant = (GetElementPointerConstant) value;
            defineExpressionSymbol(aliasName, isAliasExported, elementPointerConstant, targetDataLayout);
        } else {
            throw new LLVMLinkerException("Unknown alias type: " + value.getClass());
        }
    }

    private void defineExpressionSymbol(String aliasName, boolean isAliasExported, GetElementPointerConstant elementPointerConstant, DataLayout targetDataLayout) {
        LLVMSymbol baseSymbol = runtime.getFileScope().get(elementPointerConstant.getBasePointer().toString());
        Supplier<LLVMExpressionNode> createElemPtrNode = () -> elementPointerConstant.createNode(runtime, targetDataLayout, GetStackSpaceFactory.createAllocaFactory());
        LLVMElemPtrSymbol expressionSymbol = new LLVMElemPtrSymbol(aliasName, runtime.getBitcodeID(), -1, isAliasExported,
                        elementPointerConstant.getType(), baseSymbol, createElemPtrNode);
        runtime.getFileScope().register(expressionSymbol);
    }

    private void defineAlias(String existingName, String newName, boolean newExported) {
        // handle the file scope
        LLVMSymbol aliasTarget = runtime.lookupSymbol(existingName);
        LLVMAlias aliasSymbol = new LLVMAlias(newName, aliasTarget, newExported);
        runtime.getFileScope().register(aliasSymbol);
        registerInPublicFileScope(aliasSymbol);
    }
}
