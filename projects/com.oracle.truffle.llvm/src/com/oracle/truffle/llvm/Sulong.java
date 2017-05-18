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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.SulongNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

@TruffleLanguage.Registration(name = "Sulong", version = "0.01", mimeType = {Sulong.LLVM_BITCODE_MIME_TYPE, Sulong.LLVM_BITCODE_BASE64_MIME_TYPE,
                Sulong.SULONG_LIBRARY_MIME_TYPE})
public final class Sulong extends LLVMLanguage {

    public interface LLVMLanguageProvider {
        LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env);

        CallTarget parse(LLVMLanguage language, LLVMContext context, Source code, String... argumentNames) throws IOException;

        void disposeContext(LLVMContext context);
    }

    public static final LLVMLanguageProvider provider = getProvider();

    public static final String MAIN_ARGS_KEY = "Sulong Main Args";
    public static final String LLVM_SOURCE_FILE_KEY = "Sulong Source File";
    public static final String PARSE_ONLY_KEY = "Parse only";

    private com.oracle.truffle.api.TruffleLanguage.Env environment;

    @Override
    protected LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        this.environment = env;
        return provider.createContext(env);
    }

    @Override
    public com.oracle.truffle.api.TruffleLanguage.Env getEnvironment() {
        return environment;
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        context.printNativeCallStatistic();
        provider.disposeContext(context);
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Source source = request.getSource();
        return provider.parse(this, findLLVMContext(), source, request.getArgumentNames().toArray(new String[request.getArgumentNames().size()]));
    }

    @Override
    protected Object findExportedSymbol(LLVMContext context, String globalName, boolean onlyExplicit) {
        String atname = "@" + globalName; // for interop
        for (LLVMFunctionDescriptor descr : context.getFunctionDescriptors()) {
            if (descr != null && descr.getName().equals(globalName)) {
                return descr;
            } else if (descr != null && descr.getName().equals(atname)) {
                return descr;
            }
        }
        return null;
    }

    @Override
    protected Object getLanguageGlobal(LLVMContext context) {
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        throw new AssertionError();
    }

    @Override
    public LLVMContext findLLVMContext() {
        return getContextReference().get();
    }

    private static SulongNodeFactory getNodeFactory() {
        ServiceLoader<SulongNodeFactory> loader = ServiceLoader.load(SulongNodeFactory.class);
        if (!loader.iterator().hasNext()) {
            throw new AssertionError("Could not find a " + SulongNodeFactory.class.getSimpleName() + " for the creation of the Truffle nodes");
        }
        SulongNodeFactory factory = null;
        String expectedConfigName = LLVMOptions.ENGINE.nodeConfiguration();
        for (SulongNodeFactory prov : loader) {
            String configName = prov.getConfigurationName();
            if (configName != null && configName.equals(expectedConfigName)) {
                factory = prov;
            }
        }
        if (factory == null) {
            throw new AssertionError("Could not find a " + SulongNodeFactory.class.getSimpleName() + " with the name " + expectedConfigName);
        }
        return factory;
    }

    private static Sulong.LLVMLanguageProvider getProvider() {
        return new Sulong.LLVMLanguageProvider() {

            @Override
            public CallTarget parse(LLVMLanguage language, LLVMContext context, Source code, String... argumentNames) throws IOException {
                try {
                    return parse(language, context, code);
                } catch (Throwable t) {
                    throw new IOException("Error while trying to parse " + code.getPath(), t);
                }
            }

            private CallTarget parse(LLVMLanguage language, LLVMContext context, Source code) throws IOException {
                CallTarget mainFunction = null;
                if (code.getMimeType().equals(Sulong.LLVM_BITCODE_MIME_TYPE) || code.getMimeType().equals(Sulong.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                    LLVMParserResult parserResult = parseBitcodeFile(code, language, context);
                    mainFunction = parserResult.getMainFunction();
                    handleParserResult(context, parserResult);
                } else if (code.getMimeType().equals(Sulong.SULONG_LIBRARY_MIME_TYPE)) {
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
                            if (mimeType.equals(Sulong.LLVM_BITCODE_MIME_TYPE) || mimeType.equals(Sulong.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                                parserResult = parseBitcodeFile(source, language, context);
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
                parseDynamicBitcodeLibraries(language, context);
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

            private void parseDynamicBitcodeLibraries(LLVMLanguage language, LLVMContext context) throws IOException {
                if (!context.haveLoadedDynamicBitcodeLibraries()) {
                    context.setHaveLoadedDynamicBitcodeLibraries();
                    visitBitcodeLibraries(source -> {
                        try {
                            getProvider().parse(language, context, source);
                        } catch (Throwable t) {
                            throw new RuntimeException("Error while trying to parse dynamic library " + source.getName(), t);
                        }
                    });
                }
            }

            private void handleParserResult(LLVMContext context, LLVMParserResult result) {
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
                LLVMContext context = new LLVMContext(env);
                if (env != null) {
                    Object mainArgs = env.getConfig().get(Sulong.MAIN_ARGS_KEY);
                    if (mainArgs != null) {
                        context.setMainArguments((Object[]) mainArgs);
                    }
                    Object sourceFile = env.getConfig().get(Sulong.LLVM_SOURCE_FILE_KEY);
                    if (sourceFile != null) {
                        context.setMainSourceFile((Source) sourceFile);
                    }
                    Object parseOnly = env.getConfig().get(Sulong.PARSE_ONLY_KEY);
                    if (parseOnly != null) {
                        context.setParseOnly((boolean) parseOnly);
                    }
                }
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
                context.getThreadingStack().freeStacks();
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

    private static LLVMParserResult parseBitcodeFile(Source source, LLVMLanguage language, LLVMContext context) {
        SulongNodeFactory nodeFactory = getNodeFactory();
        context.setNativeIntrinsicsFactory(nodeFactory.getNativeIntrinsicsFactory(language, context));
        return LLVMParserRuntime.parse(source, language, context, nodeFactory);
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

    private static int evaluateFromSource(Source fileSource, Object... args) {
        Builder engineBuilder = PolyglotEngine.newBuilder();
        engineBuilder.config(Sulong.LLVM_BITCODE_MIME_TYPE, Sulong.MAIN_ARGS_KEY, args);
        engineBuilder.config(Sulong.LLVM_BITCODE_MIME_TYPE, Sulong.LLVM_SOURCE_FILE_KEY, fileSource);
        PolyglotEngine vm = engineBuilder.build();
        try {
            Integer result = vm.eval(fileSource).as(Integer.class);
            return result;
        } finally {
            vm.dispose();
        }
    }

}
