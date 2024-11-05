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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.NativeSource.Content;
import com.oracle.truffle.nfi.NativeSource.ParsedLibrary;
import com.oracle.truffle.nfi.NativeSource.ParsedSignature;
import com.oracle.truffle.nfi.backend.spi.NFIState;

@TruffleLanguage.Registration(id = "nfi", name = "TruffleNFI", version = "0.1", characterMimeTypes = NFILanguage.MIME_TYPE, internal = true, contextPolicy = ContextPolicy.SHARED)
public class NFILanguage extends TruffleLanguage<NFIContext> {

    public static final String MIME_TYPE = "application/x-native";

    private final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("NFI single context");

    final ContextThreadLocal<NFIState> nfiState = locals.createContextThreadLocal((ctx, thread) -> new NFIState(thread));

    protected void setPendingException(Throwable pendingException) {
        TruffleStackTrace.fillIn(pendingException);
        NFIState state = nfiState.get();
        state.setPendingException(pendingException);
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T silenceException(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    protected void rethrowPendingException() {
        NFIState state = nfiState.get();
        Throwable t = state.getPendingException();
        state.clearPendingException();
        if (t != null) {
            throw silenceException(RuntimeException.class, t);
        }
    }

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
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContextAssumption.invalidate();
    }

    static Assumption getSingleContextAssumption() {
        return get(null).singleContextAssumption;
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

        Content c = source.getContent();
        assert c != null;
        RootNode root;
        if (c instanceof ParsedLibrary lib) {
            root = new NFIRootNode(this, lib, backendId);
        } else {
            ParsedSignature sig = (ParsedSignature) c;
            root = new SignatureRootNode(this, backendId, sig.getBuildSignatureNode());
        }
        return root.getCallTarget();
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    protected void disposeThread(NFIContext context, Thread thread) {
        NFIState state = nfiState.get(thread);
        state.dispose();
    }

    private static final LanguageReference<NFILanguage> REFERENCE = LanguageReference.create(NFILanguage.class);

    static NFILanguage get(Node node) {
        return REFERENCE.get(node);
    }
}
