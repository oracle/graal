/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.nfi.spi.NFIBackend;

@TruffleLanguage.Registration(id = "nfi", name = "TruffleNFI", version = "0.1", characterMimeTypes = NFILanguage.MIME_TYPE, internal = true)
public class NFILanguage extends TruffleLanguage<NFIContext> {

    public static final String MIME_TYPE = "application/x-native";

    @Override
    protected NFIContext createContext(Env env) {
        return new NFIContext(env);
    }

    @Override
    protected boolean patchContext(NFIContext context, Env newEnv) {
        context.patch(newEnv);
        return true;
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

        NFIBackend backend = getCurrentContext(NFILanguage.class).getBackend(backendId);
        CallTarget loadLibrary = backend.parse(source.getLibraryDescriptor());
        return Truffle.getRuntime().createCallTarget(new NFIRootNode(this, loadLibrary, source));
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }
}
