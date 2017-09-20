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
import java.util.function.Consumer;

import com.oracle.truffle.api.CallTarget;
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
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public final class Runner {

    private final NodeFactory nodeFactory;

    static final class NoMain implements TruffleObject {

        private NoMain() {
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return NoMainMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = NoMain.class)
    abstract static class NoMainMessageResolution {

        @Resolve(message = "IS_NULL")
        abstract static class IsNullNode extends Node {

            @SuppressWarnings("unused")
            Object access(NoMain boxed) {
                return true;
            }
        }

        @CanResolve
        abstract static class CanResolvNoMain extends Node {

            boolean test(TruffleObject object) {
                return object instanceof NoMain;
            }
        }
    }

    public Runner(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    public CallTarget parse(LLVMLanguage language, LLVMContext context, Source code) throws IOException {
        try {
            parseDynamicBitcodeLibraries(language, context);

            CallTarget mainFunction = null;
            ByteBuffer bytes;

            if (code.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                ByteBuffer buffer = Charset.forName("ascii").newEncoder().encode(CharBuffer.wrap(code.getCharacters()));
                bytes = Base64.getDecoder().decode(buffer);
                assert LLVMScanner.isSupportedFile(bytes);
            } else if (code.getPath() != null) {
                bytes = read(code.getPath());
                assert LLVMScanner.isSupportedFile(bytes);
            } else {
                throw new IllegalStateException();
            }

            assert bytes != null;

            LLVMParserResult parserResult = parseBitcodeFile(code, bytes, language, context);
            mainFunction = parserResult.getMainCallTarget();
            if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
                mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            } else if (mainFunction == null) {
                mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new NoMain()));
            }
            handleParserResult(context, parserResult);
            return mainFunction;
        } catch (Throwable t) {
            throw new IOException("Error while trying to parse " + code.getPath(), t);
        }
    }

    private static ByteBuffer read(String filename) {
        try {
            return ByteBuffer.wrap(Files.readAllBytes(Paths.get(filename)));
        } catch (IOException ignore) {
            return ByteBuffer.allocate(0);
        }
    }

    private static void visitBitcodeLibraries(LLVMContext context, Consumer<Source> sharedLibraryConsumer) throws IOException {
        for (Path p : context.getBitcodeLibraries()) {
            addLibrary(p, sharedLibraryConsumer);
        }
    }

    private static void addLibrary(Path s, Consumer<Source> sharedLibraryConsumer) throws IOException {
        File lib = s.toFile();
        Source source = Source.newBuilder(lib).build();
        sharedLibraryConsumer.accept(source);
    }

    private void parseDynamicBitcodeLibraries(LLVMLanguage language, LLVMContext context) throws IOException {
        if (!context.bcLibrariesLoaded()) {
            context.setBcLibrariesLoaded();
            visitBitcodeLibraries(context, source -> {
                try {
                    new Runner(nodeFactory).parse(language, context, source);
                } catch (Throwable t) {
                    throw new RuntimeException("Error while trying to parse dynamic library " + source.getName(), t);
                }
            });
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
            assert context.getThreadingStack().checkThread();
            long stackPointer = context.getThreadingStack().getStack().getStackPointer();
            result.getGlobalVarInit().call(stackPointer);
            context.getThreadingStack().getStack().setStackPointer(stackPointer);
            if (result.getConstructorFunction() != null) {
                stackPointer = context.getThreadingStack().getStack().getStackPointer();
                result.getConstructorFunction().call(stackPointer);
                context.getThreadingStack().getStack().setStackPointer(stackPointer);
            }
        }
    }

    public static void disposeContext(LLVMContext context) {
        assert context.getThreadingStack().checkThread();
        for (RootCallTarget destructorFunction : context.getDestructorFunctions()) {
            long stackPointer = context.getThreadingStack().getStack().getStackPointer();
            destructorFunction.call(stackPointer);
            context.getThreadingStack().getStack().setStackPointer(stackPointer);
        }
        for (RootCallTarget destructor : context.getGlobalVarDeallocs()) {
            long stackPointer = context.getThreadingStack().getStack().getStackPointer();
            destructor.call(stackPointer);
            context.getThreadingStack().getStack().setStackPointer(stackPointer);
        }
        context.getThreadingStack().freeStacks();
    }

    private LLVMParserResult parseBitcodeFile(Source source, ByteBuffer bytes, LLVMLanguage language, LLVMContext context) {
        return LLVMParserRuntime.parse(source, bytes, language, context, nodeFactory);
    }
}
