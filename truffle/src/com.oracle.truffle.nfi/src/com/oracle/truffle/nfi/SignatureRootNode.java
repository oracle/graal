/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.NFISignature.SignatureBuilder;
import com.oracle.truffle.nfi.backend.spi.NFIBackendLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayBuilderFactory;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayFactory;

final class SignatureRootNode extends RootNode {

    final String backendId;

    @Child BuildSignatureNode buildSignature;

    SignatureRootNode(NFILanguage language, String backendId, BuildSignatureNode buildSignature) {
        super(language);
        this.backendId = backendId;
        this.buildSignature = buildSignature;
    }

    @Override
    public String getName() {
        return "buildSignature";
    }

    @Override
    public Object execute(VirtualFrame frame) {
        API api = NFIContext.get(this).getAPI(backendId, this);
        return buildSignature.execute(api);
    }

    abstract static class BuildSignatureNode extends Node {

        @Children ArgumentBuilderNode[] argBuilders;

        abstract Object execute(API api);

        BuildSignatureNode(ArgumentBuilderNode[] argBuilders) {
            this.argBuilders = argBuilders;
        }

        private static final ArrayFactory<NFIType> FACTORY = new ArrayFactory<>() {

            @Override
            public NFIType[] create(int size) {
                return new NFIType[size];
            }
        };

        @Specialization(limit = "3")
        @ExplodeLoop
        Object doBuild(API api,
                        @CachedLibrary("api.backend") NFIBackendLibrary backendLibrary,
                        @CachedLibrary(limit = "1") NFIBackendSignatureBuilderLibrary sigBuilderLibrary,
                        @Cached ArrayBuilderFactory factory) {
            Object backendBuilder = backendLibrary.createSignatureBuilder(api.backend);
            SignatureBuilder sigBuilder = new SignatureBuilder(api.backendId, backendBuilder, factory.allocate(FACTORY));

            for (int i = 0; i < argBuilders.length; i++) {
                argBuilders[i].execute(api, sigBuilder);
            }

            return sigBuilderLibrary.build(sigBuilder);
        }
    }

    abstract static class ArgumentBuilderNode extends Node {

        static final ArgumentBuilderNode[] EMPTY = {};

        abstract void execute(API api, Object sigBuilder);
    }

    abstract static class SetRetTypeNode extends ArgumentBuilderNode {

        @Child GetTypeNode retType;

        SetRetTypeNode(GetTypeNode retType) {
            this.retType = retType;
        }

        @Specialization(limit = "1")
        void setRetType(API api, Object sigBuilder,
                        @CachedLibrary("sigBuilder") NFIBackendSignatureBuilderLibrary sigBuilderLib) {
            sigBuilderLib.setReturnType(sigBuilder, retType.execute(api));
        }
    }

    abstract static class AddArgumentNode extends ArgumentBuilderNode {

        @Child GetTypeNode argType;

        AddArgumentNode(GetTypeNode argType) {
            this.argType = argType;
        }

        @Specialization(limit = "1")
        void addArgument(API api, Object sigBuilder,
                        @CachedLibrary("sigBuilder") NFIBackendSignatureBuilderLibrary sigBuilderLib) {
            sigBuilderLib.addArgument(sigBuilder, argType.execute(api));
        }
    }

    @GenerateInline(false)
    abstract static class MakeVarargs extends ArgumentBuilderNode {

        @Specialization(limit = "1")
        void makeVarargs(@SuppressWarnings("unused") API api, Object sigBuilder,
                        @CachedLibrary("sigBuilder") NFIBackendSignatureBuilderLibrary sigBuilderLib) {
            sigBuilderLib.makeVarargs(sigBuilder);
        }
    }

    abstract static class GetTypeNode extends Node {

        abstract Object execute(API api);
    }

    abstract static class GetSimpleTypeNode extends GetTypeNode {

        private final NativeSimpleType type;

        GetSimpleTypeNode(NativeSimpleType type) {
            this.type = type;
        }

        @Specialization(limit = "1")
        Object getType(API api,
                        @CachedLibrary("api.backend") NFIBackendLibrary backendLibrary) {
            Object backendType = backendLibrary.getSimpleType(api.backend, type);
            if (backendType == null) {
                throw new NFIUnsupportedTypeException(type.name());
            }
            return new NFIType(SimpleTypeCachedState.get(type), backendType);
        }
    }

    abstract static class GetArrayTypeNode extends GetTypeNode {

        private final NativeSimpleType type;

        GetArrayTypeNode(NativeSimpleType type) {
            this.type = type;
        }

        @Specialization(limit = "1")
        Object getType(API api,
                        @CachedLibrary("api.backend") NFIBackendLibrary backendLibrary) {
            Object backendType = backendLibrary.getArrayType(api.backend, type);
            if (backendType == null) {
                throw new NFIUnsupportedTypeException("[%s]", type.name());
            }
            return new NFIType(SimpleTypeCachedState.nop(), backendType);
        }
    }

    @GenerateInline(false)
    abstract static class GetEnvTypeNode extends GetTypeNode {

        @Specialization(limit = "1")
        Object getType(API api,
                        @CachedLibrary("api.backend") NFIBackendLibrary backend) {
            Object backendType = backend.getEnvType(api.backend);
            if (backendType == null) {
                throw new NFIUnsupportedTypeException("ENV");
            }
            return new NFIType(SimpleTypeCachedState.injected(), backendType, null);
        }
    }

    abstract static class GetSignatureTypeNode extends GetTypeNode {

        @Child BuildSignatureNode buildSignature;

        GetSignatureTypeNode(BuildSignatureNode buildSignature) {
            this.buildSignature = buildSignature;
        }

        @Specialization(limit = "1")
        Object getType(API api,
                        @CachedLibrary("api.backend") NFIBackendLibrary backend) {
            Object signature = buildSignature.execute(api);
            Object backendType = backend.getSimpleType(api.backend, NativeSimpleType.POINTER);
            return new NFIType(SignatureTypeCachedState.INSTANCE, backendType, signature);
        }
    }
}
