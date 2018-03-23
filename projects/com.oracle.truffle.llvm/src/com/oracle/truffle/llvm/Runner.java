/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.RunnerFactory.SulongLibraryMessageResolutionFactory.ExecuteMainNodeGen;
import com.oracle.truffle.llvm.parser.BitcodeParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.nfi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.types.Parser;

public final class Runner {

    private final NodeFactory nodeFactory;

    /**
     * Object that is returned when a bitcode library is parsed.
     */
    static final class SulongLibrary implements TruffleObject {

        private final LLVMScope scope;
        private final ExternalLibrary library;
        private final CallTarget main;

        private SulongLibrary(LLVMScope scope, ExternalLibrary library, CallTarget main) {
            this.scope = scope;
            this.library = library;
            this.main = main;
        }

        private LLVMFunctionDescriptor lookup(String name) {
            if (scope.functionExists(name)) {
                LLVMFunctionDescriptor d = scope.getFunctionDescriptor(name);
                if (d.getLibrary().equals(library)) {
                    return d;
                }
            }
            return null;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return SulongLibraryMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = SulongLibrary.class)
    abstract static class SulongLibraryMessageResolution {

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            Object access(SulongLibrary boxed, String name) {
                Object ret = lookup(boxed, name);
                if (ret == null) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(name);
                }
                return ret;
            }

            @TruffleBoundary
            Object lookup(SulongLibrary boxed, String name) {
                if (name.startsWith("@")) {
                    // safeguard: external users are never supposed to see the "@"
                    // TODO remove after getting rid of the @
                    return null;
                }

                String atname = "@" + name;
                LLVMFunctionDescriptor d = boxed.lookup(atname);
                if (d != null) {
                    return d;
                }
                return boxed.lookup(name);
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutableNode extends Node {

            boolean access(SulongLibrary library) {
                return library.main != null;
            }
        }

        abstract static class ExecuteMainNode extends Node {

            abstract Object execute(SulongLibrary library, Object[] args);

            @Specialization(guards = "library == cachedLibrary")
            @SuppressWarnings("unused")
            Object executeCached(SulongLibrary library, Object[] args,
                            @Cached("library") SulongLibrary cachedLibrary,
                            @Cached("createMainCall(cachedLibrary)") DirectCallNode call) {
                return call.call(args);
            }

            static DirectCallNode createMainCall(SulongLibrary library) {
                return DirectCallNode.create(library.main);
            }

            @Specialization(replaces = "executeCached")
            Object executeGeneric(SulongLibrary library, Object[] args,
                            @Cached("create()") IndirectCallNode call) {
                return call.call(library.main, args);
            }
        }

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteNode extends Node {

            @Child ExecuteMainNode executeMain = ExecuteMainNodeGen.create();

            Object access(SulongLibrary library, Object[] args) {
                assert library.main != null;
                return executeMain.execute(library, args);
            }
        }

        @CanResolve
        abstract static class CanResolveSulongLibrary extends Node {

            boolean test(TruffleObject object) {
                return object instanceof SulongLibrary;
            }
        }
    }

    public Runner(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    /**
     * Parse bitcode data and do first initializations to prepare bitcode execution.
     */
    public CallTarget parse(LLVMLanguage language, LLVMContext context, Source source) throws IOException {
        initializeContext(language, context);

        try {
            ByteBuffer bytes;
            ExternalLibrary library;
            if (source.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                bytes = ByteBuffer.wrap(decodeBase64(source.getCharacters()));
                library = new ExternalLibrary("<STREAM>");
            } else if (source.getMimeType().equals(LLVMLanguage.LLVM_SULONG_TYPE)) {
                NativeLibraryDescriptor descriptor = Parser.parseLibraryDescriptor(source.getCharacters());
                String filename = descriptor.getFilename();
                bytes = read(filename);
                library = new ExternalLibrary(Paths.get(filename), null);
            } else if (source.getPath() != null) {
                bytes = read(source.getPath());
                library = new ExternalLibrary(Paths.get(source.getPath()), null);
            } else {
                throw new IllegalStateException();
            }

            LLVMParserResult parserResult = parse(language, context, source, library, bytes);
            handleParserResult(context, parserResult);
            return createLibraryCallTarget(context, library, parserResult);
        } catch (Throwable t) {
            throw new IOException("Error while trying to parse " + source.getPath(), t);
        }
    }

    private LLVMParserResult[] parse(LLVMLanguage language, LLVMContext context, ExternalLibrary[] libs) {
        LLVMParserResult[] parserResults = new LLVMParserResult[libs.length];
        for (int i = 0; i < libs.length; i++) {
            ExternalLibrary lib = libs[i];
            if (!lib.isParsed()) {
                try {
                    Path path = lib.getPath();
                    byte[] bytes = Files.readAllBytes(path);
                    // at the moment, we don't need the bitcode as the content of the source
                    Source source = Source.newBuilder(path.toString()).mimeType(LLVMLanguage.LLVM_BITCODE_MIME_TYPE).name(path.getFileName().toString()).build();
                    parserResults[i] = parse(language, context, source, lib, ByteBuffer.wrap(bytes));
                    lib.setParsed();
                } catch (Throwable t) {
                    throw new RuntimeException("Error while trying to parse " + lib.getName(), t);
                }
            }
        }
        return parserResults;
    }

    private static byte[] decodeBase64(CharSequence charSequence) {
        byte[] result = new byte[charSequence.length()];
        for (int i = 0; i < result.length; i++) {
            char ch = charSequence.charAt(i);
            assert ch >= 0 && ch <= Byte.MAX_VALUE;
            result[i] = (byte) ch;
        }
        return Base64.getDecoder().decode(result);
    }

    private LLVMParserResult parse(LLVMLanguage language, LLVMContext context, Source source, ExternalLibrary library, ByteBuffer bytes) throws IOException {
        assert library != null;
        BitcodeParserResult bitcodeParserResult = BitcodeParserResult.getFromSource(source, bytes);
        context.addLibraryPaths(bitcodeParserResult.getLibraryPaths());
        List<String> libraries = bitcodeParserResult.getLibraries();
        if (!libraries.isEmpty()) {
            ExternalLibrary[] libs = context.addExternalLibraries(libraries);
            LLVMParserResult[] parserResults = parse(language, context, libs);
            handleParserResult(context, parserResults);
        }
        return LLVMParserRuntime.parse(source, library, bitcodeParserResult, language, context, nodeFactory);
    }

    private void initializeContext(LLVMLanguage language, LLVMContext context) {
        // we can't do the initialization in the LLVMContext constructor nor in
        // Sulong.createContext() because Truffle is not properly initialized there. So, we need to
        // do it here...
        if (!context.isInitialized()) {
            LLVMParserResult[] parserResults = parse(language, context, context.getExternalLibraries());
            handleParserResult(context, parserResults);
            context.initialize();
        }
    }

    private static ByteBuffer read(String filename) {
        return read(Paths.get(filename));
    }

    private static ByteBuffer read(Path path) {
        try {
            return ByteBuffer.wrap(Files.readAllBytes(path));
        } catch (IOException ignore) {
            return ByteBuffer.allocate(0);
        }
    }

    private static void handleParserResult(LLVMContext context, LLVMParserResult result) {
        // register destructor functions so that we can execute them when exit is called
        if (result.getDestructorFunction() != null) {
            context.registerDestructorFunction(result.getDestructorFunction());
        }

        // initialize global variables and execute constructor functions
        if (!context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                result.getGlobalVarInit().call(stackPointer);
            }
            if (result.getConstructorFunction() != null) {
                try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                    result.getConstructorFunction().call(stackPointer);
                }
            }
        }
    }

    private static void handleParserResult(LLVMContext context, LLVMParserResult[] parserResults) {
        for (int i = 0; i < parserResults.length; i++) {
            if (parserResults[i] != null) {
                handleParserResult(context, parserResults[i]);
            }
        }
    }

    private static CallTarget createLibraryCallTarget(LLVMContext context, ExternalLibrary library, LLVMParserResult parserResult) {
        if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
        } else {
            SulongLibrary lib = new SulongLibrary(parserResult.getScope(), library, parserResult.getMainCallTarget());
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(lib));
        }
    }

}
