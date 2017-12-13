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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.BitcodeParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.nfi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.types.Parser;

public final class Runner {

    private final NodeFactory nodeFactory;

    static final class SulongLibrary implements TruffleObject {

        private final LLVMContext context;
        private final ExternalLibrary library;

        private SulongLibrary(LLVMContext context, ExternalLibrary library) {
            this.context = context;
            this.library = library;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return SulongLibraryMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = SulongLibrary.class)
    abstract static class SulongLibraryMessageResolution {

        @Resolve(message = "IS_NULL")
        abstract static class IsNullNode extends Node {

            Object access(SulongLibrary boxed) {
                return boxed.context == null;
            }
        }

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            @TruffleBoundary
            Object access(SulongLibrary boxed, String name) {
                String atname = "@" + name;
                LLVMFunctionDescriptor d = lookup(boxed, atname);
                if (d != null) {
                    return d;
                }
                return lookup(boxed, name);
            }
        }

        private static LLVMFunctionDescriptor lookup(SulongLibrary boxed, String name) {
            LLVMContext context = boxed.context;
            if (context.getGlobalScope().functionExists(name)) {
                LLVMFunctionDescriptor d = context.getGlobalScope().getFunctionDescriptor(name);
                if (d.getLibrary().equals(boxed.library)) {
                    return d;
                }
            }
            return null;
        }

        @CanResolve
        abstract static class CanResolveNoMain extends Node {

            boolean test(TruffleObject object) {
                return object instanceof SulongLibrary;
            }
        }
    }

    public Runner(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    private CallTarget parse(LLVMLanguage language, LLVMContext context, ExternalLibrary library) throws IOException {
        File libFile = library.getPath().toFile();
        Source source = Source.newBuilder(libFile).build();
        ByteBuffer bytes = read(library.getPath());
        return parse(language, context, source, library, bytes);
    }

    public CallTarget parse(LLVMLanguage language, LLVMContext context, Source source) throws IOException {
        ByteBuffer bytes;
        ExternalLibrary library;
        if (source.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
            ByteBuffer buffer = Charset.forName("ascii").newEncoder().encode(CharBuffer.wrap(source.getCharacters()));
            bytes = Base64.getDecoder().decode(buffer);
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

        return parse(language, context, source, library, bytes);
    }

    private CallTarget parse(LLVMLanguage language, LLVMContext context, Source code, ExternalLibrary library, ByteBuffer bytes) throws IOException {
        try {
            assert bytes != null;
            assert library != null;

            if (!LLVMScanner.isSupportedFile(bytes)) {
                throw new IOException("Unsupported file: " + code.toString());
            }

            BitcodeParserResult bitcodeParserResult = BitcodeParserResult.getFromSource(code, bytes);
            context.addLibraryPaths(bitcodeParserResult.getLibraryPaths());
            context.addExternalLibraries(bitcodeParserResult.getLibraries());
            parseDynamicBitcodeLibraries(language, context);
            LLVMParserResult parserResult = parseBitcodeFile(code, library, bitcodeParserResult, language, context);
            CallTarget mainFunction = parserResult.getMainCallTarget();
            if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
                mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            } else if (mainFunction == null) {
                mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new SulongLibrary(context, library)));
            }
            handleParserResult(context, parserResult);
            return mainFunction;
        } catch (Throwable t) {
            throw new IOException("Error while trying to parse " + code.getPath(), t);
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

    private void parseDynamicBitcodeLibraries(LLVMLanguage language, LLVMContext context) {
        if (!context.bcLibrariesLoaded()) {
            context.setBcLibrariesLoaded();
            List<ExternalLibrary> externalLibraries = context.getExternalLibraries(lib -> lib.getPath().toString().endsWith(".bc"));
            for (ExternalLibrary lib : externalLibraries) {
                try {
                    new Runner(nodeFactory).parse(language, context, lib);
                } catch (Throwable t) {
                    throw new RuntimeException("Error while trying to parse dynamic library " + lib.getName(), t);
                }
            }
        }
    }

    private static void handleParserResult(LLVMContext context, LLVMParserResult result) {
        context.registerGlobalVarInit(result.getGlobalVarInit());
        context.registerGlobalVarDealloc(result.getGlobalVarDealloc());
        if (result.getConstructorFunction() != null) {
            context.registerConstructorFunction(result.getConstructorFunction());
        }
        if (result.getDestructorFunction() != null) {
            context.registerDestructorFunction(result.getDestructorFunction());
        }
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

    public static void disposeContext(LLVMMemory memory, LLVMContext context) {
        for (RootCallTarget destructorFunction : context.getDestructorFunctions()) {
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                destructorFunction.call(stackPointer);
            }
        }
        for (RootCallTarget destructor : context.getGlobalVarDeallocs()) {
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                destructor.call(stackPointer);
            }
        }
        context.getThreadingStack().freeMainStack(memory);
        context.getGlobalsStack().free();
    }

    private LLVMParserResult parseBitcodeFile(Source source, ExternalLibrary library, BitcodeParserResult bitcodeParserResult, LLVMLanguage language, LLVMContext context) {
        return LLVMParserRuntime.parse(source, library, bitcodeParserResult, language, context, nodeFactory);
    }
}
