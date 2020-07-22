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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

@ExportLibrary(InteropLibrary.class)
public abstract class AbstractConstantKeysObject extends AbstractRegexObject {

    public abstract TruffleReadOnlyKeysArray getKeys();

    public abstract Object readMemberImpl(String symbol) throws UnknownIdentifierException;

    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return getKeys();
    }

    @ExportMessage
    public abstract static class IsMemberReadable {

        @SuppressWarnings("unused")
        @Specialization(guards = "symbol == cachedSymbol", limit = "8")
        public static boolean cacheIdentity(AbstractConstantKeysObject receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "8", replaces = "cacheIdentity")
        public static boolean cacheEquals(AbstractConstantKeysObject receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "cacheEquals")
        public static boolean isReadable(AbstractConstantKeysObject receiver, String symbol) {
            return receiver.getKeys().contains(symbol);
        }
    }

    @ExportMessage
    public abstract static class ReadMember {

        @Specialization(guards = "symbol == cachedSymbol", limit = "8")
        public static Object readIdentity(AbstractConstantKeysObject receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") String cachedSymbol) throws UnknownIdentifierException {
            return read(receiver, cachedSymbol);
        }

        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "8", replaces = "readIdentity")
        public static Object readEquals(AbstractConstantKeysObject receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") String cachedSymbol) throws UnknownIdentifierException {
            return read(receiver, cachedSymbol);
        }

        @Specialization(replaces = "readEquals")
        public static Object read(AbstractConstantKeysObject receiver, String symbol) throws UnknownIdentifierException {
            return receiver.readMemberImpl(symbol);
        }
    }
}
