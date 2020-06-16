/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.AbstractList;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Translation of a list of array elements to list of debugger values. The implementation is not
 * thread safe.
 */
final class ValueInteropList extends AbstractList<DebugValue> {

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private final DebuggerSession session;
    private final LanguageInfo language;
    private final Object list;

    ValueInteropList(DebuggerSession session, LanguageInfo language, Object list) {
        this.session = session;
        this.language = language;
        this.list = list;
    }

    @Override
    public DebugValue get(int index) {
        return new DebugValue.ArrayElementValue(session, language, null, list, index);
    }

    @Override
    public DebugValue set(int index, DebugValue newValue) {
        Object oldValue = null;
        DebugValue currentValue = get(index);
        if (INTEROP.isArrayElementReadable(list, index)) {
            oldValue = currentValue.get();
        }
        currentValue.set(newValue);
        if (oldValue != null) {
            return new DebugValue.HeapValue(session, String.valueOf(index), oldValue);
        } else {
            return null;
        }
    }

    @Override
    public int size() {
        try {
            return (int) INTEROP.getArraySize(list);
        } catch (UnsupportedMessageException e) {
            return 0;
        }
    }

}
