/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.Function;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LazyLLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public final class LLVMParser {
    private final Source source;
    private final LLVMParserRuntime runtime;
    private final LLVMContext context;
    private final ExternalLibrary library;

    public LLVMParser(Source source, LLVMParserRuntime runtime) {
        this.source = source;
        this.runtime = runtime;
        this.context = runtime.getContext();
        this.library = runtime.getLibrary();
    }

    public LLVMParserResult parse(ModelModule module) {
        TargetDataLayout layout = module.getTargetDataLayout();
        DataLayout targetDataLayout = new DataLayout(layout.getDataLayout());
        runtime.getContext().addDataLayout(targetDataLayout);

        List<GlobalVariable> externalGlobals = new ArrayList<>();
        List<GlobalVariable> definedGlobals = new ArrayList<>();
        List<FunctionSymbol> externalFunctions = new ArrayList<>();
        List<String> importedSymbols = new ArrayList<>();

        defineGlobals(module.getGlobalVariables(), definedGlobals, externalGlobals, importedSymbols);
        defineFunctions(module, externalFunctions, importedSymbols);
        defineAliases(module.getAliases(), importedSymbols);

        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(runtime, StackManager.createRootFrame(), GetStackSpaceFactory.createAllocaFactory());
        createDebugInfo(module, symbolResolver);

        return new LLVMParserResult(runtime, externalFunctions, definedGlobals, externalGlobals, importedSymbols);
    }

    private void defineGlobals(List<GlobalVariable> globals, List<GlobalVariable> definedGlobals, List<GlobalVariable> externalGlobals, List<String> importedSymbols) {
        for (GlobalVariable global : globals) {
            if (global.isExternal()) {
                externalGlobals.add(global);
                importedSymbols.add(global.getName());
            } else {
                defineGlobal(global, importedSymbols);
                definedGlobals.add(global);
            }
        }
    }

    private void defineFunctions(ModelModule model, List<FunctionSymbol> externalFunctions, List<String> importedSymbols) {
        for (FunctionDefinition function : model.getDefinedFunctions()) {
            if (function.isExternal()) {
                externalFunctions.add(function);
                importedSymbols.add(function.getName());
            } else {
                defineFunction(function, model, importedSymbols);
            }
        }

        for (FunctionDeclaration function : model.getDeclaredFunctions()) {
            assert function.isExternal();
            externalFunctions.add(function);
            importedSymbols.add(function.getName());
        }
    }

    private void defineAliases(List<GlobalAlias> aliases, List<String> importedSymbols) {
        for (GlobalAlias alias : aliases) {
            defineAlias(alias, importedSymbols);
        }
    }

    private void defineGlobal(GlobalVariable global, List<String> importedSymbols) {
        assert !global.isExternal();
        // handle the file scope
        LLVMGlobal descriptor = LLVMGlobal.create(context, global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
        descriptor.define(global.getType(), library);
        runtime.getFileScope().register(descriptor);

        // handle the global scope
        if (global.isExported()) {
            LLVMSymbol exportedDescriptor = runtime.getGlobalScope().get(global.getName());
            if (exportedDescriptor == null) {
                runtime.getGlobalScope().register(descriptor);
            } else if (exportedDescriptor.isGlobalVariable()) {
                importedSymbols.add(global.getName());
            } else {
                assert exportedDescriptor.isFunction();
                throw new LLVMLinkerException("The global variable " + global.getName() + " conflicts with a function that has the same name.");
            }
        }
    }

    private void defineFunction(FunctionSymbol functionSymbol, ModelModule model, List<String> importedSymbols) {
        assert !functionSymbol.isExternal();
        // handle the file scope
        LLVMFunctionDescriptor descriptor = context.createFunctionDescriptor(functionSymbol.getName(), functionSymbol.getType());
        FunctionDefinition functionDefinition = (FunctionDefinition) functionSymbol;
        LazyToTruffleConverterImpl lazyConverter = new LazyToTruffleConverterImpl(runtime, functionDefinition, source, model.getFunctionParser(functionDefinition),
                        model.getFunctionProcessor());
        Function function = new LazyLLVMIRFunction(lazyConverter);
        descriptor.define(library, function);
        runtime.getFileScope().register(descriptor);

        // handle the global scope
        if (functionSymbol.isExported()) {
            LLVMSymbol exportedDescriptor = runtime.getGlobalScope().get(functionSymbol.getName());
            if (exportedDescriptor == null) {
                runtime.getGlobalScope().register(descriptor);
            } else if (exportedDescriptor.isFunction()) {
                importedSymbols.add(functionSymbol.getName());
            } else {
                assert exportedDescriptor.isGlobalVariable();
                throw new LLVMLinkerException("The function " + functionSymbol.getName() + " conflicts with a global variable that has the same name.");
            }
        }
    }

    private void defineAlias(GlobalAlias alias, List<String> importedSymbols) {
        LLVMSymbol alreadyRegisteredSymbol = runtime.getFileScope().get(alias.getName());
        if (alreadyRegisteredSymbol != null) {
            // this alias was already registered by a recursive call
            assert alreadyRegisteredSymbol instanceof LLVMAlias;
            return;
        }

        defineAlias(alias.getName(), alias.isExported(), alias.getValue(), importedSymbols);
    }

    private void defineAlias(String aliasName, boolean isAliasExported, SymbolImpl value, List<String> importedSymbols) {
        if (value instanceof FunctionSymbol) {
            FunctionSymbol function = (FunctionSymbol) value;
            defineAlias(function.getName(), function.isExported(), aliasName, isAliasExported, importedSymbols);
        } else if (value instanceof GlobalVariable) {
            GlobalVariable global = (GlobalVariable) value;
            defineAlias(global.getName(), global.isExported(), aliasName, isAliasExported, importedSymbols);
        } else if (value instanceof GlobalAlias) {
            GlobalAlias target = (GlobalAlias) value;
            defineAlias(target, importedSymbols);
            defineAlias(target.getName(), target.isExported(), aliasName, isAliasExported, importedSymbols);
        } else if (value instanceof CastConstant) {
            // TODO (chaeubl): this is not perfectly accurate as we are loosing the type cast
            CastConstant cast = (CastConstant) value;
            defineAlias(aliasName, isAliasExported, cast.getValue(), importedSymbols);
        } else {
            throw new LLVMLinkerException("Unknown alias type: " + value.getClass());
        }
    }

    private void defineAlias(String existingName, boolean existingExported, String newName, boolean newExported, List<String> importedSymbols) {
        // handle the file scope
        LLVMSymbol aliasTarget = runtime.lookupSymbol(existingName, existingExported);
        LLVMAlias descriptor = new LLVMAlias(library, newName, aliasTarget);
        runtime.getFileScope().register(descriptor);

        if (existingExported && aliasTarget.getLibrary() != library) {
            importedSymbols.add(aliasTarget.getName());
        }

        // handle the global scope
        if (newExported) {
            LLVMSymbol exportedDescriptor = runtime.getGlobalScope().get(newName);
            if (exportedDescriptor == null) {
                runtime.getGlobalScope().register(descriptor);
            } else if (descriptor.isFunction() && exportedDescriptor.isFunction() || descriptor.isGlobalVariable() && exportedDescriptor.isGlobalVariable()) {
                importedSymbols.add(newName);
            } else {
                throw new LLVMLinkerException("The alias " + newName + " conflicts with another symbol that has a different type but the same name.");
            }
        }
    }

    private void createDebugInfo(ModelModule model, LLVMSymbolReadResolver symbolResolver) {
        if (context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI)) {
            final LLVMSourceContext sourceContext = context.getSourceContext();

            model.getSourceGlobals().forEach((symbol, irValue) -> {
                final LLVMExpressionNode node = symbolResolver.resolve(irValue);
                final LLVMDebugObjectBuilder value = context.getLanguage().getNodeFactory().createDebugStaticValue(node, irValue instanceof GlobalVariable);
                sourceContext.registerStatic(symbol, value);
            });

            model.getSourceStaticMembers().forEach(((type, symbol) -> {
                final LLVMExpressionNode node = symbolResolver.resolve(symbol);
                final LLVMDebugObjectBuilder value = context.getLanguage().getNodeFactory().createDebugStaticValue(node, symbol instanceof GlobalVariable);
                type.setValue(value);
            }));
        }
    }
}
