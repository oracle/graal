/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.backend.spi.NFIBackend;
import com.oracle.truffle.nfi.backend.spi.NFIBackendFactory;
import com.oracle.truffle.nfi.backend.spi.NFIState;

@TruffleLanguage.Registration(id = "internal/nfi-panama", name = "nfi-panama", version = "0.1", characterMimeTypes = PanamaNFILanguage.MIME_TYPE, internal = true, services = NFIBackendFactory.class, contextPolicy = ContextPolicy.SHARED)
public class PanamaNFILanguage extends TruffleLanguage<AbstractPanamaNFIContext> {

    public static final String MIME_TYPE = "trufflenfi/panama";

    @CompilationFinal private NFIBackend backend;

    private final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("panama backend single context");

    public final ContextThreadLocal<AbstractErrorContext> errorContext = createErrorContext();

    @CompilationFinal private ContextThreadLocal<NFIState> state;

    static Assumption getSingleContextAssumption() {
        return get(null).singleContextAssumption;
    }

    NFIState getNFIState() {
        return state.get();
    }

    @Override
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContextAssumption.invalidate();
    }

    @Override
    protected AbstractPanamaNFIContext createContext(Env env) {
        env.registerService(new NFIBackendFactory() {

            @Override
            public String getBackendId() {
                return "panama";
            }

            @Override
            public NFIBackend createBackend(ContextThreadLocal<NFIState> newState) {
                if (PanamaAccessor.isSupported()) {
                    if (backend == null) {
                        /*
                         * Make sure there is exactly one backend instance per engine. That way we
                         * can use identity equality on the backend object for caching decisions.
                         */
                        backend = PanamaAccessor.createNFIBackend(PanamaNFILanguage.this);
                        state = newState;
                    }
                    return backend;
                } else {
                    return null;
                }
            }
        });

        if (PanamaAccessor.isSupported()) {
            return PanamaAccessor.createPanamaNFIContext(this, env);
        } else {
            // JDK < 22, Panama is not available. Fail gracefully here to give a proper
            // NFIParserException later.
            return null;
        }
    }

    public final ContextThreadLocal<AbstractErrorContext> createErrorContext() {
        if (PanamaAccessor.isSupported()) {
            return locals.createContextThreadLocal((ctx, thread) -> PanamaAccessor.createErrorContext());
        } else {
            // JDK < 22, Panama is not available. Fail gracefully here to give a proper
            // NFIParserException later.
            return null;
        }
    }

    @Override
    protected void initializeContext(AbstractPanamaNFIContext context) throws Exception {
        if (PanamaAccessor.isSupported()) {
            context.initialize();
            errorContext.get().initialize();
        }
    }

    @Override
    protected void initializeThread(AbstractPanamaNFIContext context, Thread thread) {
        if (PanamaAccessor.isSupported()) {
            assert thread == Thread.currentThread();
            // we need to get the thread-local errno pointer
            // this is only possible on the correct thread
            errorContext.get().initialize();
        }
    }

    @Override
    protected boolean patchContext(AbstractPanamaNFIContext context, Env newEnv) {
        if (PanamaAccessor.isSupported()) {
            context.patchEnv(newEnv);
            context.initialize();
        }
        return true;
    }

    @Override
    protected void disposeContext(AbstractPanamaNFIContext context) {
        if (PanamaAccessor.isSupported()) {
            context.dispose();
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        // the NFI is fully thread-safe
        return true;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        RootNode ret = new RootNode(this) {

            @Override
            public Object execute(VirtualFrame frame) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException("illegal access to internal language");
            }
        };
        return ret.getCallTarget();
    }

    private static final LanguageReference<PanamaNFILanguage> REFERENCE = LanguageReference.create(PanamaNFILanguage.class);

    static PanamaNFILanguage get(Node node) {
        return REFERENCE.get(node);
    }
}
