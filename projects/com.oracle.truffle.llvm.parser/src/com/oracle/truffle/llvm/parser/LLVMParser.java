/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.Function;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LazyLLVMIRFunction;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

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
        List<String> importedFunctions = new ArrayList<>();
        List<String> importedGlobals = new ArrayList<>();

        defineGlobals(module.getGlobalVariables(), definedGlobals, externalGlobals, importedGlobals);
        defineFunctions(module, externalFunctions, importedFunctions);
        defineAliases(module.getAliases(), importedFunctions, importedGlobals);

        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(runtime, StackManager.createRootFrame());
        createDebugInfo(module, symbolResolver);

        return new LLVMParserResult(runtime, externalFunctions, definedGlobals, externalGlobals, importedFunctions, importedGlobals);
    }

    public static SymbolImpl getAliasValue(GlobalAlias alias) {
        // TEMP (chaeubl): is this really correct? one alias could point to a global symbol that is
        // exported by another lib - so I don't think that we can just loop over all the aliases.
        SymbolImpl value = alias.getValue();
        while (value instanceof GlobalAlias) {
            value = ((GlobalAlias) value).getValue();
        }
        return value;
    }

    private void defineGlobals(List<GlobalVariable> globals, List<GlobalVariable> definedGlobals, List<GlobalVariable> externalGlobals, List<String> importedGlobals) {
        for (GlobalVariable global : globals) {
            if (global.isExternal()) {
                externalGlobals.add(global);
                importedGlobals.add(global.getName());
            } else {
                defineGlobal(global, importedGlobals);
                definedGlobals.add(global);
            }
        }
    }

    private void defineFunctions(ModelModule model, List<FunctionSymbol> externalFunctions, List<String> importedFunctions) {
        for (FunctionDefinition function : model.getDefinedFunctions()) {
            if (function.isExternal()) {
                externalFunctions.add(function);
                importedFunctions.add(function.getName());
            } else {
                defineFunction(function, model, importedFunctions);
            }
        }

        for (FunctionDeclaration function : model.getDeclaredFunctions()) {
            assert function.isExternal();
            externalFunctions.add(function);
            importedFunctions.add(function.getName());
        }
    }

    private void defineAliases(List<GlobalAlias> aliases, List<String> importedFunctions, List<String> importedGlobals) {
        for (GlobalAlias alias : aliases) {
            defineAlias(alias, importedFunctions, importedGlobals);
        }
    }

    private void defineGlobal(GlobalVariable global, List<String> importedGlobals) {
        assert !global.isExternal();
        if (global.isExported()) {
            LLVMGlobal exportedDescriptor = runtime.getGlobalScope().globals().getOrCreate(context, global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
            if (exportedDescriptor.isDefined()) {
                // use a global variable and define a shadowed one with the same name
                importedGlobals.add(global.getName());
                LLVMGlobal descriptor = runtime.getFileScope().globals().getOrCreate(context, global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
                descriptor.define(global.getType(), library);
            } else {
                // define a global variable
                assert !exportedDescriptor.isDefined();
                exportedDescriptor.define(global.getType(), library);
                runtime.getFileScope().globals().register(exportedDescriptor);
            }
        } else {
            // define a not exported global
            LLVMGlobal descriptor = runtime.getFileScope().globals().getOrCreate(context, global.getName(), global.getType(), global.getSourceSymbol(), global.isReadOnly());
            descriptor.define(global.getType(), library);
        }
    }

    private void defineFunction(FunctionSymbol functionSymbol, ModelModule model, List<String> importedFunctions) {
        assert !functionSymbol.isExternal();
        LLVMFunctionDescriptor descriptor = createFunctionDescriptor(functionSymbol.getName(), functionSymbol.getType(), functionSymbol.isExported(), functionSymbol.isExternal(), importedFunctions);
        FunctionDefinition functionDefinition = (FunctionDefinition) functionSymbol;
        LazyToTruffleConverterImpl lazyConverter = new LazyToTruffleConverterImpl(runtime, functionDefinition, source, model.getFunctionParser(functionDefinition),
                        model.getFunctionProcessor());
        Function function = new LazyLLVMIRFunction(lazyConverter);
        defineFunction(descriptor, functionSymbol.getName(), functionSymbol.getType(), function);
    }

    private void defineFunction(LLVMFunctionDescriptor descriptor, String functionName, FunctionType functionType, Function function) {
        LLVMFunctionDescriptor finalDescriptor = descriptor;
        if (descriptor.isDefined() && library.renameConflictingSymbols()) {
            assert !descriptor.getLibrary().equals(library) : "two symbols with the same name can't be in one bitcode file";
            /*
             * We already have a symbol with the same name in another (more important) library. We
             * rename the already existing symbol by prefixing it with "__libName_", e.g.,
             * "@__clock_gettime" would be renamed to "@__libc___clock_gettime".
             */
            String renamedFunctionName = getRenamedSymbol(functionName);
            finalDescriptor = createFunctionDescriptor(renamedFunctionName, functionType, true, false, null);
        }
        finalDescriptor.define(library, function);
    }

    private LLVMFunctionDescriptor createFunctionDescriptor(String functionName, FunctionType functionType, boolean exported, boolean external, List<String> importedFunctions) {
        assert !external;
        if (exported) {
            LLVMFunctionDescriptor exportedDescriptor = runtime.getGlobalScope().functions().getOrCreate(context, functionName, functionType);
            if (exportedDescriptor.isDefined()) {
                // use an exported function and define a shadowed one with the same name
                importedFunctions.add(functionName);
                return runtime.getFileScope().functions().getOrCreate(context, functionName, functionType);
            } else {
                // define an exported function
                assert !exportedDescriptor.isDefined();
                runtime.getFileScope().functions().register(exportedDescriptor);
                return exportedDescriptor;
            }
        } else {
            // define a not exported function
            assert !external;
            return runtime.getFileScope().functions().getOrCreate(context, functionName, functionType);
        }
    }

    private String getRenamedSymbol(String functionName) {
        assert functionName.charAt(0) == '@';
        return "@__" + library.getName() + "_" + functionName.substring(1);
    }

    private void defineAlias(GlobalAlias alias, List<String> importedFunctions, List<String> importedGlobals) {
        SymbolImpl value = getAliasValue(alias);

        // Due to dynamic linking, the symbol that we have here is not necessarily the correct
        // one. Therefore, we need to fetch it from the proper scope instead.
        if (value instanceof FunctionSymbol) {
            FunctionSymbol function = (FunctionSymbol) value;
            defineFunctionAlias(function.getName(), function.isExported(), alias.getName(), alias.isExported(), importedFunctions);
        } else if (value instanceof GlobalVariable) {
            GlobalVariable global = (GlobalVariable) value;
            defineGlobalVariableAlias(global, alias.getName(), alias.isExported(), importedGlobals);
        } else if (value instanceof CastConstant) {
            // TODO (chaeubl): not fully supported at the moment as we don't register an actual
            // alias
        } else {
            throw new IllegalStateException("Unknown alias type: " + value.getClass());
        }
    }

    private void defineFunctionAlias(String existingName, boolean existingExported, String newName, boolean newExported, List<String> importedFunctions) {
        LLVMFunctionDescriptor existingDescriptor = runtime.lookupFunction(existingName, existingExported);
        if (existingExported && existingDescriptor.getLibrary() != library) {
            importedFunctions.add(existingDescriptor.getName());
        }

        if (newExported) {
            if (!runtime.getGlobalScope().functions().contains(newName)) {
                runtime.getGlobalScope().functions().registerAlias(newName, existingDescriptor);
            } else if (library.renameConflictingSymbols()) {
                String renamedName = getRenamedSymbol(newName);
                runtime.getGlobalScope().functions().registerAlias(renamedName, existingDescriptor);
            }
        }

        if (!runtime.getFileScope().functions().contains(newName)) {
            runtime.getFileScope().functions().registerAlias(newName, existingDescriptor);
        } else if (library.renameConflictingSymbols()) {
            String renamedName = getRenamedSymbol(newName);
            runtime.getFileScope().functions().registerAlias(renamedName, existingDescriptor);
        }
    }

    private void defineGlobalVariableAlias(GlobalVariable global, String newName, boolean newExported, List<String> importedGlobals) {
        LLVMGlobal existingDescriptor = runtime.lookupGlobal(global.getName(), global.isExported());
        if (global.isExported() && existingDescriptor.getLibrary() != library) {
            importedGlobals.add(existingDescriptor.getName());
        }

        if (newExported) {
            if (!runtime.getGlobalScope().globals().contains(newName)) {
                runtime.getGlobalScope().globals().registerAlias(newName, existingDescriptor);
            } else if (library.renameConflictingSymbols()) {
                String renamedName = getRenamedSymbol(newName);
                runtime.getGlobalScope().globals().registerAlias(renamedName, existingDescriptor);
            }
        }

        if (!runtime.getFileScope().globals().contains(newName)) {
            runtime.getFileScope().globals().registerAlias(newName, existingDescriptor);
        } else if (library.renameConflictingSymbols()) {
            String renamedName = getRenamedSymbol(newName);
            runtime.getFileScope().globals().registerAlias(renamedName, existingDescriptor);
        }
    }

    private void createDebugInfo(ModelModule model, LLVMSymbolReadResolver symbolResolver) {
        if (context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI)) {
            final LLVMSourceContext sourceContext = context.getSourceContext();

            model.getSourceGlobals().forEach((symbol, irValue) -> {
                final LLVMExpressionNode node = symbolResolver.resolve(irValue);
                final LLVMDebugValue value = runtime.getNodeFactory().createDebugStaticValue(node);
                sourceContext.registerStatic(symbol, value);
            });

            model.getSourceStaticMembers().forEach(((type, symbol) -> {
                final LLVMExpressionNode node = symbolResolver.resolve(symbol);
                final LLVMDebugValue value = runtime.getNodeFactory().createDebugStaticValue(node);
                type.setValue(value);
            }));
        }
    }
}
