/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.dsl.processor.util;

import java.util.*;

public class Filterator<T> implements Iterator<T> {

    private final Predicate<T> includePredicate;
    private final Iterator<T> elements;

    private boolean hasCached;
    private T cached;

    public Filterator(Iterator<T> elements, Predicate<T> includePredicate) {
        this.elements = elements;
        this.includePredicate = includePredicate;
    }

    public boolean hasNext() {
        if (hasCached) {
            return true;
        }
        nextValue();
        return hasCached;
    }

    private void nextValue() {
        while (!hasCached && elements.hasNext()) {
            T element = elements.next();
            if (includePredicate.evaluate(element)) {
                cached = element;
                hasCached = true;
            }
        }
    }

    public T next() {
        T foundCached = getCached();
        if (foundCached != null) {
            return foundCached;
        } else {
            nextValue();
            if (!hasCached) {
                throw new NoSuchElementException();
            }
            return getCached();
        }
    }

    private T getCached() {
        if (hasCached) {
            hasCached = false;
            T value = cached;
            cached = null;
            return value;
        }
        return null;
    }

    @Override
    public void remove() {
        elements.remove();
    }

}
