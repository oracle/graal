/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.util;

import org.graalvm.collections.Pair;

import java.util.Iterator;

public final class IteratorUtil {
    /**
     * Combines two iterators into an iterator of pairs with as many elements as the longer
     * iterator. Elements are paired in the order of the original iterators. When one of the
     * iterators is exhausted, {@code null}s are returned in its place.
     *
     * @param lhs the first iterator
     * @param rhs the second iterator
     * @return an iterator of pairs of the input elements
     */
    public static <L, R> Iterator<Pair<L, R>> zipLongest(Iterator<L> lhs, Iterator<R> rhs) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return lhs.hasNext() || rhs.hasNext();
            }

            @Override
            public Pair<L, R> next() {
                if (lhs.hasNext() && rhs.hasNext()) {
                    return Pair.create(lhs.next(), rhs.next());
                } else if (lhs.hasNext()) {
                    return Pair.createLeft(lhs.next());
                } else {
                    return Pair.createRight(rhs.next());
                }
            }
        };
    }
}
