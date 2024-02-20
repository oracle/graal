/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nfi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.backend.spi.NFIBackend;
import com.oracle.truffle.nfi.backend.spi.NFIBackendFactory;
import com.oracle.truffle.nfi.backend.spi.NFIBackendLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIState;
import com.oracle.truffle.nfi.backend.spi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

import java.io.IOException;

@TruffleLanguage.Registration(id = "internal/nfi-llvm", name = "nfi-llvm", version = "12.0.0", internal = true, interactive = false, //
                services = NFIBackendFactory.class, contextPolicy = ContextPolicy.SHARED, dependentLanguages = "llvm")
public final class SulongNFI extends TruffleLanguage<Env> {

    @CompilationFinal private SulongNFIBackend backend;

    @Override
    protected Env createContext(Env env) {
        env.registerService(new NFIBackendFactory() {

            @Override
            public String getBackendId() {
                return "llvm";
            }

            @Override
            public NFIBackend createBackend(ContextThreadLocal<NFIState> state) {
                if (backend == null) {
                    backend = new SulongNFIBackend();
                }
                return backend;
            }
        });
        return env;
    }

    @ExportLibrary(NFIBackendLibrary.class)
    final class SulongNFIBackend implements NFIBackend {

        SulongNFIBackend() {
        }

        @Override
        public CallTarget parse(NativeLibraryDescriptor descriptor) {
            Env env = getContext(null);
            if (descriptor.isDefaultLibrary()) {
                throw new SulongNFIException("default lib not implemented yet");
            } else {
                TruffleFile file = env.getInternalTruffleFile(descriptor.getFilename());
                try {
                    Source source = Source.newBuilder("llvm", file).build();
                    return SulongNFIRootNodeGen.create(SulongNFI.this, source).getCallTarget();
                } catch (IOException ex) {
                    throw new SulongNFIException(ex.getMessage());
                }
            }
        }

        @ExportMessage
        Object getSimpleType(NativeSimpleType type) {
            switch (type) {
                case STRING:
                    // not implemented
                    return null;
                default:
                    // Sulong does not need extra information here
                    return type;
            }
        }

        @ExportMessage
        Object getArrayType(@SuppressWarnings("unused") NativeSimpleType type) {
            // not implemented
            return null;
        }

        @ExportMessage
        Object getEnvType() {
            // not implemented
            return null;
        }

        @ExportMessage
        Object createSignatureBuilder() {
            return SulongNFISignature.BUILDER;
        }
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        return new RootNode(this) {

            @Override
            public Object execute(VirtualFrame frame) {
                throw CompilerDirectives.shouldNotReachHere("illegal access to internal language");
            }
        }.getCallTarget();
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    private static final LanguageReference<SulongNFI> REFERENCE = LanguageReference.create(SulongNFI.class);

    static SulongNFI get(Node node) {
        return REFERENCE.get(node);
    }

    private static final ContextReference<Env> CONTEXT_REFERENCE = ContextReference.create(SulongNFI.class);

    static Env getContext(Node node) {
        return CONTEXT_REFERENCE.get(node);
    }
}
