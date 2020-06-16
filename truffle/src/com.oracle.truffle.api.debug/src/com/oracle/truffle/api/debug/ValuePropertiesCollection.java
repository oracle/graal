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

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Translation of a map of object properties to a collection of debugger values. The implementation
 * is not thread safe.
 */
final class ValuePropertiesCollection extends AbstractCollection<DebugValue> {

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private final DebuggerSession session;
    private final LanguageInfo language;
    private final Object object;
    private final DebugScope scope;
    private final Object keys;

    ValuePropertiesCollection(DebuggerSession session, LanguageInfo language, Object object, Object keys, DebugScope scope) {
        this.session = session;
        this.language = language;
        this.object = object;
        this.keys = keys;
        this.scope = scope;
    }

    @Override
    public Iterator<DebugValue> iterator() {
        return new PropertiesIterator();
    }

    @Override
    public int size() {
        try {
            return (int) INTEROP.getArraySize(keys);
        } catch (UnsupportedMessageException e) {
            return 0;
        }
    }

    DebugValue get(String name) {
        if (INTEROP.isMemberExisting(object, name)) {
            return new DebugValue.ObjectMemberValue(session, language, scope, object, name);
        }
        return null;
    }

    private final class PropertiesIterator implements Iterator<DebugValue> {

        private long currentIndex;

        @Override
        public boolean hasNext() {
            return INTEROP.isArrayElementExisting(keys, currentIndex);
        }

        @Override
        public DebugValue next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            try {
                Object key = INTEROP.readArrayElement(keys, currentIndex);
                String member = INTEROP.asString(key);
                this.currentIndex++;
                return new DebugValue.ObjectMemberValue(session, language, scope, object, member);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(session, ex, language, null, true, null);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported.");
        }

    }

}
