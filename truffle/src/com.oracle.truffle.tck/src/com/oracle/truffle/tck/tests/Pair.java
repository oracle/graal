/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import java.util.Map;

final class Pair<F, S> implements Map.Entry<F, S> {
    private final F first;
    private final S second;

    private Pair(final F first, final S second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public F getKey() {
        return first;
    }

    @Override
    public S getValue() {
        return second;
    }

    @Override
    public S setValue(S value) {
        throw new UnsupportedOperationException("Immutable Entry.");
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }
        if (other.getClass() != Pair.class) {
            return false;
        }
        final Pair<?, ?> otherPair = (Pair<?, ?>) other;
        return (first == null ? otherPair.first == null : first.equals(otherPair.first)) &&
                        (second == null ? otherPair.second == null : second.equals(otherPair.second));
    }

    @Override
    public int hashCode() {
        int res = 17;
        res = res * 31 + (first == null ? 0 : first.hashCode());
        res = res * 31 + (second == null ? 0 : second.hashCode());
        return res;
    }

    @Override
    public String toString() {
        return String.format("[%s,%s]", first, second);
    }

    public static <F, S> Pair<F, S> of(final F f, final S s) {
        return new Pair<>(f, s);
    }
}
