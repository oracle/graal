/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.collect;

import java.util.*;

import com.sun.max.util.*;

/**
 * Filters the elements returned by a given iterator with a given predicate.
 */
public class FilterIterator<T> implements Iterator<T> {

    private final Iterator<? extends T> iterator;
    private final Predicate<T> predicate;
    private T next;
    private boolean advanced;

    public FilterIterator(Iterator<? extends T> iterator, Predicate<T> predicate) {
        this.iterator = iterator;
        this.predicate = predicate;
    }

    public boolean hasNext() {
        if (advanced) {
            return true;
        }
        return advance();
    }

    public T next() {
        if (!advanced) {
            if (!advance()) {
                throw new NoSuchElementException();
            }
        }
        advanced = false;
        return next;
    }

    public void remove() {
        if (advanced) {
            throw new IllegalStateException("remove() cannot be called");
        }
        iterator.remove();
    }

    private boolean advance() {
        while (iterator.hasNext()) {
            final T n = iterator.next();
            if (predicate.evaluate(n)) {
                next = n;
                advanced = true;
                return true;
            }
        }
        return false;
    }
}
