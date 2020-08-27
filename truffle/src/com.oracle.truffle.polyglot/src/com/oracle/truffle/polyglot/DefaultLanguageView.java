/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Default implementation for the instrumentation view in {@link TruffleLanguage}. Should be removed
 * with deprecated methods in {@link TruffleLanguage}.
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
@SuppressWarnings("static-method")
final class DefaultLanguageView<C> implements TruffleObject {

    private final TruffleLanguage<C> language;
    private final C context;
    protected final Object delegate;
    protected final Object unwrapped;

    DefaultLanguageView(TruffleLanguage<C> language, C context, Object delegate) {
        this.language = language;
        this.context = context;
        this.delegate = delegate;
        this.unwrapped = EngineAccessor.INTEROP.unwrapLegacyMetaObjectWrapper(delegate);
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasSourceLocation(@CachedLibrary("this.delegate") InteropLibrary delegateLib) {
        return EngineAccessor.LANGUAGE.legacyFindSourceLocation(language, context, unwrapped) != null || delegateLib.hasSourceLocation(this.delegate);
    }

    @ExportMessage
    @TruffleBoundary
    SourceSection getSourceLocation(@CachedLibrary("this.delegate") InteropLibrary delegateLib) throws UnsupportedMessageException {
        SourceSection location = EngineAccessor.LANGUAGE.legacyFindSourceLocation(language, context,
                        unwrapped);
        if (location != null) {
            return location;
        } else {
            return delegateLib.getSourceLocation(this.delegate);
        }
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean config) {
        String result = EngineAccessor.LANGUAGE.legacyToString(language, context, unwrapped);
        return result;
    }

    @SuppressWarnings("unchecked")
    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return (Class<? extends TruffleLanguage<?>>) language.getClass();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasMetaObject() {
        return EngineAccessor.LANGUAGE.legacyFindMetaObject(language, context, unwrapped) != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMetaObject() throws UnsupportedMessageException {
        Object result = EngineAccessor.LANGUAGE.legacyFindMetaObject(language, context, unwrapped);
        if (result != null) {
            return EngineAccessor.INTEROP.createLegacyMetaObjectWrapper(this, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

}
