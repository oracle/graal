/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * A lightweight property getter that allows getting the value of a {@link DynamicObject}
 * {@linkplain Property property} without any lookup or cache dispatch. Only objects of a specific
 * {@link Shape} are {@linkplain #accepts(DynamicObject) accepted}. Therefore, it should only be
 * used where the object is already known to have the supported shape.
 *
 * @since 22.2
 * @see Shape#makePropertyGetter(Object)
 * @see DynamicObjectLibrary
 */
public final class PropertyGetter {

    private final Shape expectedShape;
    private final Property property;
    private final Location location;

    PropertyGetter(Shape expectedShape, Property property) {
        this.expectedShape = expectedShape;
        this.property = property;
        this.location = property.getLocation();
    }

    /**
     * Returns {@code true} if this {@link PropertyGetter} can be used with the given receiver
     * object to get the property's value.
     *
     * @param receiver the receiver object
     * @return {@code true} if the shape of the receiver object is supported by this
     *         {@link PropertyGetter}.
     * @since 22.2
     * @see #get(DynamicObject)
     */
    public boolean accepts(DynamicObject receiver) {
        return receiver.getShape() == expectedShape;
    }

    /**
     * Gets the property's value from the given receiver object if the object has the supported
     * {@link Shape}, i.e. the shape this property getter was created with. Otherwise, throws an
     * {@link IllegalArgumentException}.
     *
     * @param receiver the receiver object
     * @return the property's value
     * @throws IllegalArgumentException if the object does not have the expected shape.
     * @since 22.2
     */
    @SuppressWarnings("deprecation")
    public Object get(DynamicObject receiver) {
        boolean guard = accepts(receiver);
        if (guard) {
            return location.get(receiver, guard);
        } else {
            throw illegalArgumentException();
        }
    }

    /**
     * Gets the property's value from the given receiver object if the object has the supported
     * {@link Shape}. Expects an {@code int} value or throws an {@link UnexpectedResultException} if
     * the value is of a different type. If the object's shape is not supported, throws an
     * {@link IllegalArgumentException}.
     *
     * @param receiver the receiver object
     * @return the property's value
     * @throws IllegalArgumentException if the object does not have the expected shape.
     * @throws UnexpectedResultException if the location does not contain an int value.
     * @since 22.2
     * @see #get(DynamicObject)
     */
    public int getInt(DynamicObject receiver) throws UnexpectedResultException {
        boolean guard = accepts(receiver);
        if (guard) {
            return location.getInt(receiver, guard);
        } else {
            throw illegalArgumentException();
        }
    }

    /**
     * Gets the property's value from the given receiver object if the object has the supported
     * {@link Shape}. Expects a {@code long} value or throws an {@link UnexpectedResultException} if
     * the value is of a different type. If the object's shape is not supported, throws an
     * {@link IllegalArgumentException}.
     *
     * @param receiver the receiver object
     * @return the property's value
     * @throws IllegalArgumentException if the object does not have the expected shape.
     * @throws UnexpectedResultException if the location does not contain a long value.
     * @since 22.2
     * @see #get(DynamicObject)
     */
    public long getLong(DynamicObject receiver) throws UnexpectedResultException {
        boolean guard = accepts(receiver);
        if (guard) {
            return location.getLong(receiver, guard);
        } else {
            throw illegalArgumentException();
        }
    }

    /**
     * Gets the property's value from the given receiver object if the object has the supported
     * {@link Shape}. Expects a {@code double} value or throws an {@link UnexpectedResultException}
     * if the value is of a different type. If the object's shape is not supported, throws an
     * {@link IllegalArgumentException}.
     *
     * @param receiver the receiver object
     * @return the property's value
     * @throws IllegalArgumentException if the object does not have the expected shape.
     * @throws UnexpectedResultException if the location does not contain a double value.
     * @since 22.2
     * @see #get(DynamicObject)
     */
    public double getDouble(DynamicObject receiver) throws UnexpectedResultException {
        boolean guard = accepts(receiver);
        if (guard) {
            return location.getDouble(receiver, guard);
        } else {
            throw illegalArgumentException();
        }
    }

    /**
     * Returns the key of the property represented by this getter.
     *
     * @since 22.2
     * @see Property#getKey()
     */
    public Object getKey() {
        return property.getKey();
    }

    /**
     * Returns the flags associated with the property represented by this getter.
     *
     * @since 22.2
     * @see Property#getFlags()
     */
    public int getFlags() {
        return property.getFlags();
    }

    private static IllegalArgumentException illegalArgumentException() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalArgumentException("Receiver not supported.");
    }
}
