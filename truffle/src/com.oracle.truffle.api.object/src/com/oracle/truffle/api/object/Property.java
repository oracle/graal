/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

/**
 * Property objects represent the mapping between property identifiers (keys) and storage locations.
 * Optionally, properties may have metadata attached to them.
 *
 * @since 0.8 or earlier
 */
public abstract class Property {
    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     * @deprecated do not use.
     */
    @Deprecated(since = "22.2")
    protected Property() {
    }

    /**
     * Create a new property.
     *
     * @param key the key of the property
     * @param location location of the property
     * @param flags for language-specific use
     * @return new instance of the property
     * @since 0.8 or earlier
     */
    @Deprecated(since = "22.2")
    @SuppressWarnings("deprecation")
    public static Property create(Object key, Location location, int flags) {
        return Layout.getFactory().createProperty(key, location, flags);
    }

    /**
     * Get property identifier.
     *
     * @since 0.8 or earlier
     */
    public abstract Object getKey();

    /**
     * Get property flags, which are free for language-specific use.
     *
     * @since 0.8 or earlier
     */
    public abstract int getFlags();

    /**
     * Gets the value of this property of the object.
     *
     * @param store the store that this property resides in
     * @param shape the current shape of the object, which must contain this location
     * @see DynamicObjectLibrary#getOrDefault(DynamicObject, Object, Object)
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#getOrDefault(DynamicObject, Object, Object)}.
     */
    @Deprecated(since = "22.2")
    public abstract Object get(DynamicObject store, Shape shape);

    /**
     * Gets the value of this property of the object.
     *
     * @param store the store that this property resides in
     * @param condition the result of a shape check or {@code false}
     * @see DynamicObjectLibrary#getOrDefault(DynamicObject, Object, Object)
     * @see #get(DynamicObject, Shape)
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#getOrDefault(DynamicObject, Object, Object)}.
     */
    @Deprecated(since = "22.2")
    public abstract Object get(DynamicObject store, boolean condition);

    /**
     * Get the property location.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract Location getLocation();

    /**
     * Is this property hidden from iteration.
     *
     * @see HiddenKey
     * @since 0.8 or earlier
     */
    public abstract boolean isHidden();
}
