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
package com.oracle.truffle.llvm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.MissingNameException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.parser.api.LLVMParserResult;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacadeProvider;
import com.oracle.truffle.llvm.parser.bc.LLVMBitcodeVisitor;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import java.nio.file.Paths;

/**
 * This is the main LLVM execution class.
 */
public class LLVM {

    static {
        LLVMLanguage.provider = getProvider();
    }

    private static NodeFactoryFacade getNodeFactoryFacade() {
        ServiceLoader<NodeFactoryFacadeProvider> loader = ServiceLoader.load(NodeFactoryFacadeProvider.class);
        if (!loader.iterator().hasNext()) {
            throw new AssertionError("Could not find a " + NodeFactoryFacadeProvider.class.getSimpleName() + " for the creation of the Truffle nodes");
        }
        NodeFactoryFacade facade = null;
        String expectedConfigName = LLVMOptions.ENGINE.nodeConfiguration();
        for (NodeFactoryFacadeProvider prov : loader) {
            String configName = prov.getConfigurationName();
            if (configName != null && configName.equals(expectedConfigName)) {
                facade = prov.getNodeFactoryFacade();
            }
        }
        if (facade == null) {
            throw new AssertionError("Could not find a " + NodeFactoryFacadeProvider.class.getSimpleName() + " with the name " + expectedConfigName);
        }
        return facade;
    }

    private static LLVMLanguage.LLVMLanguageProvider getProvider() {
        return new LLVMLanguage.LLVMLanguageProvider() {

            @Override
            public CallTarget parse(Source code, Node contextNode, String... argumentNames) throws IOException {
                try {
                    return parse(code);
                } catch (Throwable t) {
                    throw new IOException("Error while trying to parse " + code.getPath(), t);
                }
            }

            private CallTarget parse(Source code) throws IOException {
                Node findContext = LLVMLanguage.INSTANCE.createFindContextNode0();
                LLVMContext context = LLVMLanguage.INSTANCE.findContext0(findContext);
                CallTarget mainFunction = null;
                if (code.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_MIME_TYPE) || code.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                    LLVMParserResult parserResult = parseBitcodeFile(code, context);
                    mainFunction = parserResult.getMainFunction();
                    handleParserResult(context, parserResult);
                } else if (code.getMimeType().equals(LLVMLanguage.SULONG_LIBRARY_MIME_TYPE)) {
                    final SulongLibrary library = new SulongLibrary(new File(code.getPath()));
                    List<Source> sourceFiles = new ArrayList<>();
                    library.readContents(dependentLibrary -> {
                        context.addLibraryToNativeLookup(dependentLibrary);
                    }, source -> {
                        sourceFiles.add(source);
                    });
                    for (Source source : sourceFiles) {
                        String mimeType = source.getMimeType();
                        try {
                            LLVMParserResult parserResult;
                            if (mimeType.equals(LLVMLanguage.LLVM_BITCODE_MIME_TYPE) || mimeType.equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                                parserResult = parseBitcodeFile(source, context);
                            } else {
                                throw new UnsupportedOperationException(mimeType);
                            }
                            handleParserResult(context, parserResult);
                            if (parserResult.getMainFunction() != null) {
                                mainFunction = parserResult.getMainFunction();
                            }
                        } catch (Throwable t) {
                            throw new IOException("Error while trying to parse " + source.getName(), t);
                        }
                    }
                    if (mainFunction == null) {
                        mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(null));
                    }
                } else {
                    throw new IllegalArgumentException("undeclared mime type");
                }
                parseDynamicBitcodeLibraries(context);
                if (context.isParseOnly()) {
                    return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(mainFunction));
                } else {
                    return mainFunction;
                }
            }

            private void visitBitcodeLibraries(Consumer<Source> sharedLibraryConsumer) throws IOException {
                String[] dynamicLibraryPaths = LLVMOptions.ENGINE.dynamicBitcodeLibraries();
                if (dynamicLibraryPaths != null && dynamicLibraryPaths.length != 0) {
                    for (String s : dynamicLibraryPaths) {
                        addLibrary(s, sharedLibraryConsumer);
                    }
                }
            }

            private void addLibrary(String s, Consumer<Source> sharedLibraryConsumer) throws IOException {
                File lib = Paths.get(s).toFile();
                Source source = Source.newBuilder(lib).build();
                sharedLibraryConsumer.accept(source);
            }

            private void parseDynamicBitcodeLibraries(LLVMContext context) throws IOException {
                if (!context.haveLoadedDynamicBitcodeLibraries()) {
                    context.setHaveLoadedDynamicBitcodeLibraries();
                    visitBitcodeLibraries(source -> {
                        try {
                            getProvider().parse(source, null);
                        } catch (Throwable t) {
                            throw new RuntimeException("Error while trying to parse dynamic library " + source.getName(), t);
                        }
                    });
                }
            }

            private void handleParserResult(LLVMContext context, LLVMParserResult result) {
                context.getFunctionRegistry().register(result.getParsedFunctions());
                context.registerGlobalVarInit(result.getGlobalVarInits());
                context.registerGlobalVarDealloc(result.getGlobalVarDeallocs());
                if (result.getConstructorFunctions() != null) {
                    for (RootCallTarget constructorFunction : result.getConstructorFunctions()) {
                        context.registerConstructorFunction(constructorFunction);
                    }
                }
                if (result.getDestructorFunctions() != null) {
                    for (RootCallTarget destructorFunction : result.getDestructorFunctions()) {
                        context.registerDestructorFunction(destructorFunction);
                    }
                }
                if (!context.isParseOnly()) {
                    result.getGlobalVarInits().call();
                    for (RootCallTarget constructorFunction : result.getConstructorFunctions()) {
                        constructorFunction.call(result.getConstructorFunctions());
                    }
                }
            }

            @Override
            public LLVMContext createContext(Env env) {
                NodeFactoryFacade facade = getNodeFactoryFacade();
                LLVMContext context = new LLVMContext(facade);
                if (env != null) {
                    Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
                    if (mainArgs != null) {
                        context.setMainArguments((Object[]) mainArgs);
                    }
                    Object sourceFile = env.getConfig().get(LLVMLanguage.LLVM_SOURCE_FILE_KEY);
                    if (sourceFile != null) {
                        context.setMainSourceFile((Source) sourceFile);
                    }
                    Object parseOnly = env.getConfig().get(LLVMLanguage.PARSE_ONLY_KEY);
                    if (parseOnly != null) {
                        context.setParseOnly((boolean) parseOnly);
                    }
                }
                context.getStack().allocate();
                return context;
            }

            @Override
            public void disposeContext(LLVMContext context) {
                for (RootCallTarget destructorFunction : context.getDestructorFunctions()) {
                    destructorFunction.call(destructorFunction);
                }
                for (RootCallTarget destructor : context.getGlobalVarDeallocs()) {
                    destructor.call();
                }
                context.getStack().free();
            }
        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("please provide a file to execute!");
        }
        File file = new File(args[0]);
        Object[] otherArgs = new Object[args.length - 1];
        System.arraycopy(args, 1, otherArgs, 0, otherArgs.length);
        int status = executeMain(file, otherArgs);
        System.exit(status);
    }

    public static LLVMParserResult parseBitcodeFile(Source source, LLVMContext context) {
        return LLVMBitcodeVisitor.parse(source, context, getNodeFactoryFacade());
    }

    public static int executeMain(File file, Object... args) {
        LLVMLogger.info("current file: " + file.getAbsolutePath());
        Source fileSource;
        try {
            fileSource = Source.newBuilder(file).build();
            return evaluateFromSource(fileSource, args);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static int executeMain(String codeString, Object... args) {
        try {
            Source fromText = Source.newBuilder(codeString).mimeType(LLVMLanguage.LLVM_BITCODE_MIME_TYPE).build();
            LLVMLogger.info("current code string: " + codeString);
            return evaluateFromSource(fromText, args);
        } catch (MissingNameException e) {
            throw new AssertionError(e);
        }
    }

    private static int evaluateFromSource(Source fileSource, Object... args) {
        Builder engineBuilder = PolyglotEngine.newBuilder();
        engineBuilder.config(LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.MAIN_ARGS_KEY, args);
        engineBuilder.config(LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_SOURCE_FILE_KEY, fileSource);
        PolyglotEngine vm = engineBuilder.build();
        try {
            Integer result = vm.eval(fileSource).as(Integer.class);
            return result;
        } finally {
            vm.dispose();
        }
    }

}
