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

/**
 * A sequence of {@linkplain PoolObject objects} that are uniquely identified <i>within a sequence</i> by a
 * {@linkplain PoolObject#serial() serial number} (i.e. an index into the sequence). A subset of the objects
 * within a pool can be efficiently represented by a {@link PoolSet}.
 */
public abstract class Pool<T extends PoolObject> implements Iterable<T> {

    /**
     * Gets the object in the pool identified by a given serial number.
     */
    public abstract T get(int serial);

    /**
     * The number of objects in this pool. All objects in the pool must have a unique serial number in the range
     * {@code [0 .. length() - 1]}.
     */
    public abstract int length();

    /**
     * Determines if this pool is empty.
     */
    public boolean isEmpty() {
        return length() == 0;
    }
}
