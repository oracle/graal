/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Objects;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
final class PolyglotBindings implements TruffleObject {

    // a bindings object for engine.
    private final PolyglotContextImpl context;
    // a bindings object for each language, can be null.
    private final PolyglotLanguageContext languageContext;
    // the bindings map that shared across a bindings object for each language context
    private volatile Map<String, Value> bindings;

    PolyglotBindings(PolyglotContextImpl context) {
        this(context, null);
    }

    PolyglotBindings(PolyglotLanguageContext languageContext) {
        this(languageContext.context, languageContext);
    }

    private PolyglotBindings(PolyglotContextImpl context, PolyglotLanguageContext languageContext) {
        Objects.requireNonNull(context);
        this.context = context;
        this.languageContext = languageContext; // Can be null
    }

    public Map<String, Value> getBindings() {
        Map<String, Value> localBindings = this.bindings;
        if (localBindings == null) {
            this.bindings = localBindings = context.getPolyglotGuestBindings();
        }
        return localBindings;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        Value value = getBindings().get(member);
        if (value == null) {
            // legacy support
            Value legacyValue = context.findLegacyExportedSymbol(member);
            if (legacyValue != null) {
                return context.getAPIAccess().getReceiver(legacyValue);
            }
            throw UnknownIdentifierException.create(member);
        }
        if (languageContext != null) {
            return languageContext.toGuestValue(value);
        } else {
            return context.getAPIAccess().getReceiver(value);
        }
    }

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value) {
        Value v = (languageContext != null) ? languageContext.asValue(value) : context.asValue(value);
        getBindings().put(member, v);
    }

    @ExportMessage
    @TruffleBoundary
    void removeMember(String member) throws UnknownIdentifierException {
        Value ret = getBindings().remove(member);
        if (ret == null) {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new DefaultScope.VariableNamesObject(getBindings().keySet());
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    @TruffleBoundary
    boolean isMemberExisting(String member) {
        boolean existing = getBindings().containsKey(member);
        if (!existing) {
            return context.findLegacyExportedSymbol(member) != null;
        }
        return existing;
    }

    @ExportMessage
    boolean isMemberInsertable(String member) {
        return !isMemberExisting(member);
    }

}
