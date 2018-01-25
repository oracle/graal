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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.types.NativeSource;
import com.oracle.truffle.nfi.types.Parser;

@TruffleLanguage.Registration(id = "nfi", name = "TruffleNFI", version = "0.1", mimeType = NFILanguage.MIME_TYPE, internal = true)
public class NFILanguage extends TruffleLanguage<Env> {

    public static final String MIME_TYPE = "application/x-native";

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        CharSequence nfiSource = request.getSource().getCharacters();
        NativeSource source = Parser.parseNFISource(nfiSource);

        String backendId;
        if (source.isDefaultBackend()) {
            backendId = "native";
        } else {
            backendId = source.getNFIBackendId();
        }

        Source backendSource = Source.newBuilder(source.getLibraryDescriptor()).mimeType("trufflenfi/" + backendId).name("<nfi-impl>").build();
        CallTarget backendTarget = getContextReference().get().parse(backendSource);
        DirectCallNode loadLibrary = DirectCallNode.create(backendTarget);

        return Truffle.getRuntime().createCallTarget(new NFIRootNode(this, loadLibrary, source));
    }

    @Override
    protected Object getLanguageGlobal(Env context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof NFILibrary;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }
}
