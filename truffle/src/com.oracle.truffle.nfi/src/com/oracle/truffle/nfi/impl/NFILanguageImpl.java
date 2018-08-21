/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.types.Parser;

@TruffleLanguage.Registration(id = "native", name = "nfi-native", version = "0.1", characterMimeTypes = NFILanguageImpl.MIME_TYPE, internal = true)
public class NFILanguageImpl extends TruffleLanguage<NFIContext> {

    public static final String MIME_TYPE = "trufflenfi/native";

    @Override
    protected NFIContext createContext(Env env) {
        return new NFIContext(env);
    }

    @Override
    protected void initializeContext(NFIContext context) throws Exception {
        context.initialize();
    }

    @Override
    protected boolean patchContext(NFIContext context, Env newEnv) {
        context.patchEnv(newEnv);
        context.initialize();
        return true;
    }

    @Override
    protected void disposeContext(NFIContext context) {
        context.dispose();
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        // the NFI is fully thread-safe
        return true;
    }

    private static class LoadLibraryNode extends RootNode {

        private final String name;
        private final int flags;

        @CompilationFinal private LibFFILibrary cached;
        private final ContextReference<NFIContext> ctxRef;

        LoadLibraryNode(NFILanguageImpl language, String name, int flags) {
            super(language);
            this.name = name;
            this.flags = flags;
            this.ctxRef = language.getContextReference();
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (!ctxRef.get().env.isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreter();
                throw new NFIUnsatisfiedLinkError("Access to native code is not allowed by the host environment.");
            }
            if (cached == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cached = ctxRef.get().loadLibrary(name, flags);
            }
            return cached;
        }
    }

    private static class GetDefaultLibraryNode extends RootNode {

        private final ContextReference<NFIContext> ctxRef;

        GetDefaultLibraryNode(NFILanguageImpl language) {
            super(language);
            this.ctxRef = language.getContextReference();
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (!ctxRef.get().env.isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreter();
                throw new NFIUnsatisfiedLinkError("Access to native code is not allowed by the host environment.");
            }
            return LibFFILibrary.createDefault();
        }
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        CharSequence library = request.getSource().getCharacters();
        RootNode root;
        NativeLibraryDescriptor descriptor = Parser.parseLibraryDescriptor(library);
        NFIContext ctx = getContextReference().get();

        if (descriptor.isDefaultLibrary()) {
            root = new GetDefaultLibraryNode(this);
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
                    }
                }
            }
            if (!lazyOrNow) {
                // default to 'RTLD_NOW' if neither 'RTLD_LAZY' nor 'RTLD_NOW' was specified
                flags |= ctx.RTLD_NOW;
            }
            root = new LoadLibraryNode(this, descriptor.getFilename(), flags);
        }

        return Truffle.getRuntime().createCallTarget(root);
    }

    static ContextReference<NFIContext> getCurrentContextReference() {
        return getCurrentLanguage(NFILanguageImpl.class).getContextReference();
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof LibFFIFunction || object instanceof LibFFILibrary || object instanceof NativePointer || object instanceof NativeString;
    }
}
