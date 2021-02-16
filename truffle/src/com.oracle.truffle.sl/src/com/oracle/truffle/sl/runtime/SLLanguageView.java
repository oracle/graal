/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.sl.SLLanguage;

/**
 * Language views are needed in order to allow tools to have a consistent perspective on primitive
 * or foreign values from the perspective of this language. The interop interpretation for primitive
 * values like Integer or String is not language specific by default. Therefore this language view
 * calls routines to print such values the SimpleLanguage way. It is important to note that language
 * views are not passed as normal values through the interpreter execution. It is designed only as a
 * temporary helper for tools.
 * <p>
 * There is more information in {@link TruffleLanguage#getLanguageView(Object, Object)}
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
@SuppressWarnings("static-method")
public final class SLLanguageView implements TruffleObject {

    final Object delegate;

    SLLanguageView(Object delegate) {
        this.delegate = delegate;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    /*
     * Language views must always associate with the language they were created for. This allows
     * tooling to take a primitive or foreign value and create a value of simple language of it.
     */
    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return SLLanguage.class;
    }

    @ExportMessage
    @ExplodeLoop
    boolean hasMetaObject(@CachedLibrary("this.delegate") InteropLibrary interop) {
        /*
         * We use the isInstance method to find out whether one of the builtin simple language types
         * apply. If yes, then we can provide a meta object in getMetaObject. The interop contract
         * requires to be precise.
         *
         * Since language views are only created for primitive values and values of other languages,
         * values from simple language itself directly implement has/getMetaObject. For example
         * SLFunction is already associated with the SLLanguage and therefore the language view will
         * not be used.
         */
        for (SLType type : SLType.PRECEDENCE) {
            if (type.isInstance(delegate, interop)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    @ExplodeLoop
    Object getMetaObject(@CachedLibrary("this.delegate") InteropLibrary interop) throws UnsupportedMessageException {
        /*
         * We do the same as in hasMetaObject but actually return the type this time.
         */
        for (SLType type : SLType.PRECEDENCE) {
            if (type.isInstance(delegate, interop)) {
                return type;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @ExplodeLoop
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects, @CachedLibrary("this.delegate") InteropLibrary interop) {
        for (SLType type : SLType.PRECEDENCE) {
            if (type.isInstance(this.delegate, interop)) {
                try {
                    /*
                     * The type is a partial evaluation constant here as we use @ExplodeLoop. So
                     * this if-else cascade should fold after partial evaluation.
                     */
                    if (type == SLType.NUMBER) {
                        return longToString(interop.asLong(delegate));
                    } else if (type == SLType.BOOLEAN) {
                        return Boolean.toString(interop.asBoolean(delegate));
                    } else if (type == SLType.STRING) {
                        return interop.asString(delegate);
                    } else {
                        /* We use the type name as fallback for any other type */
                        return type.getName();
                    }
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            }
        }
        return "Unsupported";
    }

    /*
     * Long.toString is not safe for partial evaluation and therefore needs to be called behind a
     * boundary.
     */
    @TruffleBoundary
    private static String longToString(long l) {
        return Long.toString(l);
    }

    public static Object create(Object value) {
        assert isPrimitiveOrFromOtherLanguage(value);
        return new SLLanguageView(value);
    }

    /*
     * Language views are intended to be used only for primitives and other language values.
     */
    private static boolean isPrimitiveOrFromOtherLanguage(Object value) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(value);
        try {
            return !interop.hasLanguage(value) || interop.getLanguage(value) != SLLanguage.class;
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
    }

    /**
     * Returns a language view for primitive or foreign values. Returns the same value for values
     * that are already originating from SimpleLanguage. This is useful to view values from the
     * perspective of simple language in slow paths, for example, printing values in error messages.
     */
    @TruffleBoundary
    public static Object forValue(Object value) {
        if (value == null) {
            return null;
        }
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
        try {
            if (lib.hasLanguage(value) && lib.getLanguage(value) == SLLanguage.class) {
                return value;
            } else {
                return create(value);
            }
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
    }

}
