/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.backend.libffi.LibFFISignature.SignatureBuilder;
import com.oracle.truffle.nfi.backend.spi.NFIBackend;
import com.oracle.truffle.nfi.backend.spi.NFIBackendLibrary;
import com.oracle.truffle.nfi.backend.spi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayBuilderFactory;

@ExportLibrary(NFIBackendLibrary.class)
@SuppressWarnings("static-method")
final class LibFFINFIBackend implements NFIBackend {

    private final LibFFILanguage language;

    LibFFINFIBackend(LibFFILanguage language) {
        this.language = language;
    }

    @Override
    public CallTarget parse(NativeLibraryDescriptor descriptor) {
        RootNode root;
        LibFFIContext ctx = LibFFILanguage.getCurrentContext();
        if (descriptor.isDefaultLibrary()) {
            root = new GetDefaultLibraryNode(language);
        } else {
            int flags = 0;
            boolean lazyOrNow = false;
            if (descriptor.getFlags() != null) {
                for (String flag : descriptor.getFlags()) {
                    switch (flag) {
                        case "RTLD_GLOBAL":
                            flags |= ctx.RTLD_GLOBAL;
                            break;
                        case "RTLD_LOCAL":
                            flags |= ctx.RTLD_LOCAL;
                            break;
                        case "RTLD_LAZY":
                            flags |= ctx.RTLD_LAZY;
                            lazyOrNow = true;
                            break;
                        case "RTLD_NOW":
                            flags |= ctx.RTLD_NOW;
                            lazyOrNow = true;
                            break;
                        case "ISOLATED_NAMESPACE":
                            if (ctx.ISOLATED_NAMESPACE == 0) {
                                // undefined
                                throw new NFIUnsupportedException("isolated namespace not supported");
                            }
                            flags |= ctx.ISOLATED_NAMESPACE;
                            break;
                    }
                }
            }
            if (!lazyOrNow) {
                // default to 'RTLD_NOW' if neither 'RTLD_LAZY' nor 'RTLD_NOW' was specified
                flags |= ctx.RTLD_NOW;
            }
            root = new LoadLibraryNode(language, descriptor.getFilename(), flags);
        }
        return root.getCallTarget();
    }

    private static class LoadLibraryNode extends RootNode {

        private final String name;
        private final int flags;

        LoadLibraryNode(LibFFILanguage language, String name, int flags) {
            super(language);
            this.name = name;
            this.flags = flags;
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LibFFIContext context = LibFFIContext.get(this);
            if (!context.env.isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreter();
                throw new NFIUnsatisfiedLinkError("Access to native code is not allowed by the host environment.", this);
            }
            return context.loadLibrary(name, flags);
        }
    }

    private static class GetDefaultLibraryNode extends RootNode {

        GetDefaultLibraryNode(LibFFILanguage language) {
            super(language);
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (!LibFFIContext.get(this).env.isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreter();
                throw new NFIUnsatisfiedLinkError("Access to native code is not allowed by the host environment.", this);
            }
            return LibFFILibrary.createDefault();
        }
    }

    @ExportMessage
    Object getSimpleType(NativeSimpleType type,
                    @CachedLibrary("this") InteropLibrary self) {
        return LibFFIContext.get(self).lookupSimpleType(type);
    }

    @ExportMessage
    Object getArrayType(NativeSimpleType type,
                    @CachedLibrary("this") InteropLibrary self) {
        return LibFFIContext.get(self).lookupArrayType(type);
    }

    @ExportMessage
    Object getEnvType(@CachedLibrary("this") InteropLibrary self) {
        return LibFFIContext.get(self).lookupEnvType();
    }

    @ExportMessage
    Object createSignatureBuilder(
                    @CachedLibrary("this") NFIBackendLibrary self,
                    @Cached ArrayBuilderFactory builderFactory) {
        if (!LibFFIContext.get(self).env.isNativeAccessAllowed()) {
            CompilerDirectives.transferToInterpreter();
            throw new NFIUnsatisfiedLinkError("Access to native code is not allowed by the host environment.", self);
        }
        return new SignatureBuilder(builderFactory);
    }
}
