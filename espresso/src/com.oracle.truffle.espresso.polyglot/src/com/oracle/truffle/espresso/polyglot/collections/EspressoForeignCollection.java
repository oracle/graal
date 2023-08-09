/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.polyglot.collections;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

import com.oracle.truffle.espresso.polyglot.Interop;
import com.oracle.truffle.espresso.polyglot.InteropException;
import com.oracle.truffle.espresso.polyglot.UnsupportedMessageException;

public class EspressoForeignCollection<T> extends AbstractCollection<T> implements Collection<T> {

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<T> iterator() {
        assert Interop.hasIterator(this);
        try {
            return EspressoForeignIterator.create(Interop.getIterator(this));
        } catch (UnsupportedMessageException e) {
            return (Iterator<T>) EspressoForeignIterable.EMPTY_ITERATOR;
        }
    }

    @Override
    public int size() {
        // (GR-47128) If/When iterator size becomes available through interop, switch to use that
        Iterator<T> it = iterator();
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }
        return count;
    }

    @Override
    public boolean add(T t) {
        // This assumes the presence of a member "add". Known to work for host collections.
        try {
            return Interop.asBoolean(Interop.invokeMember(this, "add", t));
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean remove(Object o) {
        // This assumes the presence of a member "remove". Known to work for host collections.
        try {
            return Interop.asBoolean(Interop.invokeMember(this, "remove", o));
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object obj : c) {
            if (remove(obj)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Override
    public String toString() {
        try {
            return Interop.asString(Interop.toDisplayString(this));
        } catch (UnsupportedMessageException e) {
            return super.toString();
        }
    }
}
