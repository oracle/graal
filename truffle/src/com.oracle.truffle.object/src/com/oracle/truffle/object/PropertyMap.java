/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.Iterator;
import java.util.Map;

import com.oracle.truffle.api.object.Property;

/**
 * Immutable property map.
 *
 * @since 0.17 or earlier
 */
public abstract class PropertyMap implements ImmutableMap<Object, Property> {
    /**
     * @since 0.17 or earlier
     */
    protected PropertyMap() {
    }

    /** @since 0.17 or earlier */
    public static PropertyMap empty() {
        if (ObjectStorageOptions.TriePropertyMap) {
            return TriePropertyMap.empty();
        } else {
            return ConsListPropertyMap.empty();
        }
    }

    /** @since 0.17 or earlier */
    public abstract Iterator<Object> orderedKeyIterator();

    /** @since 0.17 or earlier */
    public abstract Iterator<Object> reverseOrderedKeyIterator();

    /** @since 0.17 or earlier */
    public abstract Iterator<Property> orderedValueIterator();

    /** @since 0.17 or earlier */
    public abstract Iterator<Property> reverseOrderedValueIterator();

    /** @since 0.17 or earlier */
    public abstract Property getLastProperty();

    /** @since 0.17 or earlier */
    public abstract PropertyMap putCopy(Property element);

    /** @since 0.17 or earlier */
    public abstract PropertyMap replaceCopy(Property oldValue, Property newValue);

    /** @since 0.17 or earlier */
    public abstract PropertyMap removeCopy(Property value);

    /** @since 0.17 or earlier */
    @Override
    public Property put(final Object key, final Property value) {
        throw unmodifiableException();
    }

    /** @since 0.17 or earlier */
    @Override
    public void putAll(final Map<? extends Object, ? extends Property> m) {
        throw unmodifiableException();
    }

    /** @since 0.17 or earlier */
    @Override
    public Property remove(final Object key) {
        throw unmodifiableException();
    }

    /** @since 0.17 or earlier */
    @Override
    public void clear() {
        throw unmodifiableException();
    }

    /** @since 0.17 or earlier */
    protected static RuntimeException unmodifiableException() {
        throw new UnsupportedOperationException();
    }
}
