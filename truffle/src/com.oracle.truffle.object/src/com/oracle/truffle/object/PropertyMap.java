/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.Iterator;
import java.util.Map;

import com.oracle.truffle.api.object.Property;

/**
 * Immutable property map.
 *
 * @since 0.17 or earlier
 */
@SuppressWarnings("deprecation")
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
