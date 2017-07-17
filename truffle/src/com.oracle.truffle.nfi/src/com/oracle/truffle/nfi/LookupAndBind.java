/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.types.NativeSignature;
import java.util.HashMap;
import java.util.Map;

final class LookupAndBind extends RootNode {

    private final ContextReference<NFIContext> ctxRef;

    @Child private RootNode libraryNode;
    @CompilerDirectives.CompilationFinal private LibFFILibrary cached;
    private final Map<String, NativeSignature> bindings;

    LookupAndBind(NFILanguage language, RootNode root, Map<String, NativeSignature> functions) {
        super(language);
        this.ctxRef = language.getContextReference();
        this.libraryNode = root;
        this.bindings = functions;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (cached != null) {
            return cached;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        LibFFILibrary library = (LibFFILibrary) libraryNode.execute(frame);
        return cached = initializeLib(library);
    }

    @CompilerDirectives.TruffleBoundary
    private LibFFILibrary initializeLib(LibFFILibrary library) {
        Map<String, LibFFIFunction> libraryWrapper = new HashMap<>();
        for (Map.Entry<String, NativeSignature> entry : bindings.entrySet()) {
            String symbolName = entry.getKey();
            NativeSignature signature = entry.getValue();
            LibFFISymbol symbol = ctxRef.get().lookupSymbol(library, symbolName);
            LibFFIFunction fun = new LibFFIFunction(symbol, LibFFISignature.create(ctxRef.get(), signature));
            libraryWrapper.put(symbolName, fun);
        }
        return library.register(libraryWrapper);
    }

}
