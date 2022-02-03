/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.util;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLStrings;

/**
 * The node to normalize any value to an SL value. This is useful to reduce the number of values
 * expression nodes need to expect.
 */
@TypeSystemReference(SLTypes.class)
@GenerateUncached
public abstract class SLToTruffleStringNode extends Node {

    static final int LIMIT = 5;

    private static final TruffleString TRUE = SLStrings.constant("true");
    private static final TruffleString FALSE = SLStrings.constant("false");
    private static final TruffleString FOREIGN_OBJECT = SLStrings.constant("[foreign object]");

    public abstract TruffleString execute(Object value);

    @Specialization
    protected static TruffleString fromNull(@SuppressWarnings("unused") SLNull value) {
        return SLStrings.NULL;
    }

    @Specialization
    protected static TruffleString fromString(String value,
                    @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return fromJavaStringNode.execute(value, SLLanguage.STRING_ENCODING);
    }

    @Specialization
    protected static TruffleString fromTruffleString(TruffleString value) {
        return value;
    }

    @Specialization
    protected static TruffleString fromBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Specialization
    @TruffleBoundary
    protected static TruffleString fromLong(long value,
                    @Cached TruffleString.FromLongNode fromLongNode) {
        return fromLongNode.execute(value, SLLanguage.STRING_ENCODING, true);
    }

    @Specialization
    @TruffleBoundary
    protected static TruffleString fromBigNumber(SLBigNumber value,
                    @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return fromJavaStringNode.execute(value.toString(), SLLanguage.STRING_ENCODING);
    }

    @Specialization
    protected static TruffleString fromFunction(SLFunction value) {
        return value.getName();
    }

    @Specialization(limit = "LIMIT")
    protected static TruffleString fromInterop(Object value,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached TruffleString.FromLongNode fromLongNode,
                    @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        try {
            if (interop.fitsInLong(value)) {
                return fromLongNode.execute(interop.asLong(value), SLLanguage.STRING_ENCODING, true);
            } else if (interop.isString(value)) {
                return fromJavaStringNode.execute(interop.asString(value), SLLanguage.STRING_ENCODING);
            } else if (interop.isNumber(value) && value instanceof SLBigNumber) {
                return fromJavaStringNode.execute(bigNumberToString((SLBigNumber) value), SLLanguage.STRING_ENCODING);
            } else if (interop.isNull(value)) {
                return SLStrings.NULL_LC;
            } else {
                return FOREIGN_OBJECT;
            }
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    private static String bigNumberToString(SLBigNumber value) {
        return value.toString();
    }
}
