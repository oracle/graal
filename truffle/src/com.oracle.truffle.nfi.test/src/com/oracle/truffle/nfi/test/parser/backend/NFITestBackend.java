/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi.test.parser.backend;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.backend.spi.NFIBackend;
import com.oracle.truffle.nfi.backend.spi.NFIBackendFactory;
import com.oracle.truffle.nfi.backend.spi.NFIBackendLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendTools;
import com.oracle.truffle.nfi.backend.spi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

@Registration(id = "test/nfi-backend", name = "NFITestBackend", internal = true, services = NFIBackendFactory.class)
public class NFITestBackend extends TruffleLanguage<Env> {

    NFIBackendTools tools;

    NFIBackend backend = new NFITestBackendImpl();

    public static final class ArrayType {

        public final NativeSimpleType base;

        ArrayType(NativeSimpleType base) {
            this.base = base;
        }
    }

    public static final class EnvType {
    }

    @ExportLibrary(NFIBackendLibrary.class)
    @SuppressWarnings("static-method")
    static final class NFITestBackendImpl implements NFIBackend {

        @Override
        public CallTarget parse(NativeLibraryDescriptor descriptor) {
            return RootNode.createConstantNode(new TestLibrary(descriptor)).getCallTarget();
        }

        @ExportMessage
        Object getSimpleType(NativeSimpleType type) {
            return type;
        }

        @ExportMessage
        Object getArrayType(NativeSimpleType type) {
            return new ArrayType(type);
        }

        @ExportMessage
        Object getEnvType() {
            return new EnvType();
        }

        @ExportMessage
        @TruffleBoundary
        Object createSignatureBuilder() {
            return new TestSignature();
        }
    }

    @Override
    protected Env createContext(Env env) {
        env.registerService(new NFIBackendFactory() {

            @Override
            public String getBackendId() {
                return "test";
            }

            @Override
            public NFIBackend createBackend(NFIBackendTools t) {
                tools = t;
                return backend;
            }
        });
        return env;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException("illegal access to internal language");
            }
        }.getCallTarget();
    }

    private static final LanguageReference<NFITestBackend> REFERENCE = LanguageReference.create(NFITestBackend.class);

    static NFITestBackend get(Node node) {
        return REFERENCE.get(node);
    }

}
