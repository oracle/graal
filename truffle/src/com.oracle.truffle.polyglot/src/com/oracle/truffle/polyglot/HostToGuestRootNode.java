/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

abstract class HostToGuestRootNode extends RootNode {

    // offset used to indicate where the argument start.
    // first two arguments are language context and receiver.
    protected static final int ARGUMENT_OFFSET = 2;

    @CompilationFinal private boolean seenEnter;
    @CompilationFinal private boolean seenNonEnter;

    private final PolyglotLanguage language;
    private final PolyglotSharingLayer layer;
    @CompilationFinal private boolean seenError;

    /*
     * This constructor is only used for uncached locations. Which will never get executed.
     */
    HostToGuestRootNode(PolyglotSharingLayer layer) {
        super(null);
        assert layer != null;
        this.layer = layer;
        this.language = null;
        EngineAccessor.NODES.setSharingLayer(this, layer);
    }

    HostToGuestRootNode(PolyglotLanguageInstance language) {
        super(null);
        EngineAccessor.NODES.setSharingLayer(this, language.sharing);
        this.layer = language.sharing;
        this.language = language.language;
    }

    protected abstract Class<?> getReceiverType();

    @Override
    public final Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        PolyglotLanguageContext languageContext = layer.getSingleConstantLanguageContext(language);
        if (languageContext == null) {
            languageContext = (PolyglotLanguageContext) args[0];
        }
        PolyglotContextImpl constantContext = layer.getSingleConstantContext();
        if (constantContext == null) {
            constantContext = languageContext.context;
        }
        assert languageContext.context == constantContext;
        PolyglotContextImpl context = constantContext;

        assert Objects.equals(layer, languageContext.context.layer) : PolyglotSharingLayer.invalidSharingError(this, layer, languageContext.context.layer);

        Object[] prev;
        boolean needsEnter;
        try {
            needsEnter = layer.engine.needsEnter(context);
            if (needsEnter) {
                if (!seenEnter) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenEnter = true;
                }
                prev = layer.engine.enterCached(context, true);
            } else {
                if (!seenNonEnter) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNonEnter = true;
                }
                prev = null;
            }
        } catch (Throwable e) {
            throw handleException(languageContext, e, false, RuntimeException.class);
        }
        try {
            Object[] arguments = frame.getArguments();
            Object receiver = getReceiverType().cast(arguments[1]);
            Object result;
            result = executeImpl(languageContext, receiver, arguments);
            assert !(result instanceof TruffleObject);
            return result;
        } catch (Throwable e) {
            throw handleException(languageContext, e, true, RuntimeException.class);
        } finally {
            if (needsEnter) {
                try {
                    layer.engine.leaveCached(prev, context);
                } catch (Throwable e) {
                    throw handleException(languageContext, e, false, RuntimeException.class);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private <E extends Throwable> E handleException(PolyglotLanguageContext languageContext, Throwable e, boolean entered, Class<E> exceptionType) throws E {
        if (!seenError) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seenError = true;
        }
        throw PolyglotImpl.guestToHostException(languageContext, e, entered);
    }

    protected abstract Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args);

    static <T> T installHostCodeCache(PolyglotLanguageContext languageContext, Object key, T value, Class<T> expectedType) {
        T result = expectedType.cast(languageContext.getLanguageInstance().hostToGuestCodeCache.putIfAbsent(key, value));
        if (result != null) {
            return result;
        } else {
            return value;
        }
    }

    static <T> T lookupHostCodeCache(PolyglotLanguageContext languageContext, Object key, Class<T> expectedType) {
        return expectedType.cast(languageContext.getLanguageInstance().hostToGuestCodeCache.get(key));
    }

}
