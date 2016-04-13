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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;
import com.intel.llvm.ireditor.LLVM_IRStandaloneSetup;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.parser.factories.NodeFactoryFacadeImpl;
import com.oracle.truffle.llvm.parser.impl.LLVMVisitor;
import com.oracle.truffle.llvm.parser.impl.LLVMVisitor.ParserResult;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMPropertyOptimizationConfiguration;

/**
 * This is the main LLVM execution class.
 */
public class LLVM {

    static final LLVMPropertyOptimizationConfiguration OPTIMIZATION_CONFIGURATION = new LLVMPropertyOptimizationConfiguration();

    static {
        LLVMLanguage.provider = getProvider();
    }

    private static LLVMLanguage.LLVMLanguageProvider getProvider() {
        return new LLVMLanguage.LLVMLanguageProvider() {

            @Override
            public CallTarget parse(Source code, Node context, String... argumentNames) {
                Node findContext = LLVMLanguage.INSTANCE.createFindContextNode0();
                LLVMContext llvmContext = LLVMLanguage.INSTANCE.findContext0(findContext);

                if (code.getMimeType() == LLVMLanguage.LLVM_MIME_TYPE) {
                    ParserResult parserResult = parseFile(code.getPath(), llvmContext);
                    llvmContext.getFunctionRegistry().register(parserResult.getParsedFunctions());
                    return parserResult.getMainFunction();
                } else if (code.getMimeType() == LLVMLanguage.SULONG_LIBRARY_MIME_TYPE) {
                    final List<CallTarget> mainFunctions = new ArrayList<>();

                    final SulongLibrary library = new SulongLibrary(new File(code.getPath()));

                    try {
                        library.readContents(dependentLibrary -> {
                            throw new UnsupportedOperationException();
                        }, source -> {
                            ParserResult parserResult;
                            try {
                                parserResult = parseString(source.getCode(), llvmContext);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }

                            llvmContext.getFunctionRegistry().register(parserResult.getParsedFunctions());
                            mainFunctions.add(parserResult.getMainFunction());
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    if (mainFunctions.size() != 1) {
                        throw new UnsupportedOperationException();
                    }

                    return mainFunctions.get(0);
                } else {
                    throw new IllegalArgumentException("undeclared mime type");
                }
            }

            @Override
            public LLVMContext createContext(Env env) {
                NodeFactoryFacadeImpl facade = new NodeFactoryFacadeImpl();
                LLVMContext context = new LLVMContext(facade, OPTIMIZATION_CONFIGURATION);
                LLVMVisitor runtime = new LLVMVisitor(OPTIMIZATION_CONFIGURATION, context.getMainArguments(), context.getSourceFile());
                facade.setParserRuntime(runtime);
                context.setMainArguments((Object[]) env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY));
                context.setSourceFile((Source) env.getConfig().get(LLVMLanguage.LLVM_SOURCE_FILE_KEY));
                return context;
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

    public static ParserResult parseString(String source, LLVMContext context) throws IOException {
        final File file = File.createTempFile("sulong", ".ll");

        try {
            try (FileOutputStream stream = new FileOutputStream(file.getPath())) {
                stream.write(source.getBytes(StandardCharsets.UTF_8));
            }

            return parseFile(file.getPath(), context);
        } finally {
            file.delete();
        }
    }

    public static ParserResult parseFile(String filePath, LLVMContext context) {
        LLVM_IRStandaloneSetup setup = new LLVM_IRStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        Resource resource = resourceSet.getResource(URI.createURI(filePath), true);
        EList<EObject> contents = resource.getContents();
        if (contents.size() == 0) {
            throw new IllegalStateException("empty file?");
        }
        Model model = (Model) contents.get(0);
        LLVMVisitor llvmVisitor = new LLVMVisitor(OPTIMIZATION_CONFIGURATION, context.getMainArguments(), context.getSourceFile());
        return llvmVisitor.getMain(model, new NodeFactoryFacadeImpl(llvmVisitor));
    }

    public static int executeMain(File file, Object... args) {
        LLVMLogger.info("current file: " + file.getAbsolutePath());
        Source fileSource;
        try {
            fileSource = Source.fromFileName(file.getAbsolutePath());
            return evaluateFromSource(fileSource, args);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static int executeMain(String codeString, Object... args) {
        Source fromText = Source.fromText(codeString, "code string").withMimeType(LLVMLanguage.LLVM_MIME_TYPE);
        LLVMLogger.info("current code string: " + codeString);
        return evaluateFromSource(fromText, args);
    }

    private static int evaluateFromSource(Source fileSource, Object... args) {
        Builder engineBuilder = PolyglotEngine.newBuilder();
        engineBuilder.config(LLVMLanguage.LLVM_MIME_TYPE, LLVMLanguage.MAIN_ARGS_KEY, args);
        engineBuilder.config(LLVMLanguage.LLVM_MIME_TYPE, LLVMLanguage.LLVM_SOURCE_FILE_KEY, fileSource);
        PolyglotEngine vm = engineBuilder.build();
        try {
            Integer result = vm.eval(fileSource).as(Integer.class);
            return result;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
