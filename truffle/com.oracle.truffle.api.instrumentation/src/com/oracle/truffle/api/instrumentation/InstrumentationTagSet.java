/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.instrumentation;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Immutable instrumentation tag set designed for fast-path operations in Truffle interpreters.
 */
final class InstrumentationTagSet extends AbstractSet<InstrumentationTag> implements Cloneable {

    private final int elements;

    InstrumentationTagSet(int elements) {
        this.elements = elements;
    }

    @Override
    public boolean contains(Object e) {
        if (!(e instanceof InstrumentationTag)) {
            return false;
        }
        return (elements & (1 << ((InstrumentationTag) e).ordinal())) != 0;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (!(c instanceof InstrumentationTagSet)) {
            // not recommended to use like this
            CompilerDirectives.transferToInterpreter();
            return super.containsAll(c);
        }
        InstrumentationTagSet es = (InstrumentationTagSet) c;
        return (es.elements & ~elements) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstrumentationTagSet)) {
            // not recommended to use like this
            CompilerDirectives.transferToInterpreter();
            return super.equals(o);
        }
        InstrumentationTagSet es = (InstrumentationTagSet) o;
        return es.elements == elements;
    }

    @Override
    public InstrumentationTagIterator iterator() {
        return new InstrumentationTagIterator();
    }

    @Override
    public InstrumentationTagSet clone() {
        try {
            return (InstrumentationTagSet) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public int size() {
        return Integer.bitCount(elements);
    }

    @Override
    public boolean isEmpty() {
        return elements == 0;
    }

    // Modification Operations

    @Override
    @TruffleBoundary
    public boolean add(InstrumentationTag e) {
        throw new UnsupportedOperationException();
    }

    @Override
    @TruffleBoundary
    public boolean remove(Object e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends InstrumentationTag> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    public static int createTags(InstrumentationTag... tags) {
        int e = 0;
        for (int i = 0; i < tags.length; i++) {
            e |= 1 << tags[i].ordinal();
        }
        return e;
    }

    public static boolean containsAny(int elements, int anyTags) {
        return (elements & anyTags) != 0;
    }

    private final class InstrumentationTagIterator implements Iterator<InstrumentationTag> {

        int unseen;
        int lastReturned = 0;

        InstrumentationTagIterator() {
            unseen = elements;
        }

        public boolean hasNext() {
            return unseen != 0;
        }

        public InstrumentationTag next() {
            if (unseen == 0) {
                throw new NoSuchElementException();
            }
            lastReturned = unseen & -unseen;
            unseen -= lastReturned;
            return InstrumentationTag.values()[Integer.numberOfTrailingZeros(lastReturned)];
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}
