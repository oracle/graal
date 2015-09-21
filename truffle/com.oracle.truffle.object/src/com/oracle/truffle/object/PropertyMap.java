/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import com.oracle.truffle.api.object.Property;
import java.util.Iterator;
import java.util.Map;

/**
 * Immutable property map.
 */
public abstract class PropertyMap implements ImmutableMap<Object, Property> {

    public static PropertyMap empty() {
        return ConsListPropertyMap.empty();
    }

    public abstract Iterator<Object> orderedKeyIterator();

    public abstract Iterator<Object> reverseOrderedKeyIterator();

    public abstract Iterator<Property> orderedValueIterator();

    public abstract Iterator<Property> reverseOrderedValueIterator();

    public abstract Property getLastProperty();

    public abstract PropertyMap putCopy(final Property element);

    public abstract PropertyMap replaceCopy(final Property oldValue, final Property newValue);

    public abstract PropertyMap removeCopy(final Property value);

    public abstract PropertyMap getParentMap();

    @Override
    public Property put(final Object key, final Property value) {
        throw unmodifiableException();
    }

    @Override
    public void putAll(final Map<? extends Object, ? extends Property> m) {
        throw unmodifiableException();
    }

    @Override
    public Property remove(final Object key) {
        throw unmodifiableException();
    }

    @Override
    public void clear() {
        throw unmodifiableException();
    }

    protected static RuntimeException unmodifiableException() {
        throw new UnsupportedOperationException();
    }
}
