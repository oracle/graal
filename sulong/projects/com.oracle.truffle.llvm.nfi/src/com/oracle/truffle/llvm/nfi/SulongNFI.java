/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.spi.NFIBackend;
import com.oracle.truffle.nfi.spi.NFIBackendFactory;
import com.oracle.truffle.nfi.spi.NFIBackendTools;
import com.oracle.truffle.nfi.spi.types.NativeLibraryDescriptor;
import java.io.IOException;

@TruffleLanguage.Registration(id = "internal/nfi-llvm", name = "nfi-llvm", version = "6.0.0", internal = true, interactive = false, //
                services = NFIBackendFactory.class)
public final class SulongNFI extends TruffleLanguage<Env> {

    @CompilationFinal private SulongNFIBackend backend;

    NFIBackendTools getTools() {
        return backend.tools;
    }

    @Override
    protected Env createContext(Env env) {
        env.registerService(new NFIBackendFactory() {

            @Override
            public String getBackendId() {
                return "llvm";
            }

            @Override
            public NFIBackend createBackend(NFIBackendTools tools) {
                if (backend == null) {
                    backend = new SulongNFIBackend(tools);
                }
                return backend;
            }
        });
        return env;
    }

    private final class SulongNFIBackend implements NFIBackend {

        private final NFIBackendTools tools;

        SulongNFIBackend(NFIBackendTools tools) {
            this.tools = tools;
        }

        @Override
        public CallTarget parse(NativeLibraryDescriptor descriptor) {
            Env env = getCurrentContext(SulongNFI.class);
            TruffleFile file = env.getInternalTruffleFile(descriptor.getFilename());
            try {
                Source source = Source.newBuilder("llvm", file).build();
                CallTarget target = env.parsePublic(source);
                return wrap(SulongNFI.this, target);
            } catch (IOException ex) {
                throw new SulongNFIException(ex.getMessage());
            }
        }
    }

    private static CallTarget wrap(SulongNFI nfi, CallTarget target) {
        return Truffle.getRuntime().createCallTarget(new RootNode(nfi) {

            @Child DirectCallNode call = DirectCallNode.create(target);

            @Override
            public Object execute(VirtualFrame frame) {
                Object ret = call.call();
                return new SulongNFILibrary(ret);
            }
        });
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {

            @Override
            public Object execute(VirtualFrame frame) {
                throw new UnsupportedOperationException("illegal access to internal language");
            }
        });
    }

}
