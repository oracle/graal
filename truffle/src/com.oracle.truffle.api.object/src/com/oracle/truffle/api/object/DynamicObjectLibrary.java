/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * {@link DynamicObject} access library.
 *
 * This is the central interface for accessing and mutating properties and other state (flags,
 * dynamic type) of {@link DynamicObject}s.
 *
 * It is recommended that you use the {@link CachedLibrary} annotation in Truffle DSL nodes. You can
 * also use the library either {@linkplain #getUncached() without caching} or create a
 * {@linkplain LibraryFactory#create(Object) manually} or
 * {@linkplain LibraryFactory#createDispatched(int) automatically} dispatched cached library. Cached
 * libraries must be adopted before use.
 *
 * The cached library instances dispatch by object {@linkplain Shape shape} and, if applicable,
 * automatically by property key.
 *
 * Note: cached library nodes do not profile the class of the object parameter; it is therefore the
 * caller's responsibility to do any desired profiling and ensure accurate type information.
 *
 * <h3>Usage examples:</h3>
 *
 * <pre>
 * &#64;Specialization(limit = "3")
 * static Object read(DynamicObject receiver, Object key,
 *                 &#64;CachedLibrary("receiver") DynamicObjectLibrary objects) {
 *     return objects.getOrDefault(receiver, key, NULL_VALUE);
 * }
 * </pre>
 *
 * <pre>
 * &#64;ExportMessage
 * Object readMember(String name,
 *                 &#64;CachedLibrary("this") DynamicObjectLibrary objects) throws UnknownIdentifierException {
 *     Object result = objects.getOrDefault(this, name, null);
 *     if (result == null) {
 *         throw UnknownIdentifierException.create(name);
 *     }
 *     return result;
 * }
 * </pre>
 *
 * @since 20.2.0
 */
@GenerateLibrary(defaultExportLookupEnabled = true, dynamicDispatchEnabled = false)
public abstract class DynamicObjectLibrary extends Library {

    static final LibraryFactory<DynamicObjectLibrary> FACTORY = LibraryFactory.resolve(DynamicObjectLibrary.class);

    /**
     * @since 20.2.0
     */
    protected DynamicObjectLibrary() {
    }

    /**
     * Returns the library factory for {@link DynamicObjectLibrary}.
     *
     * @since 20.2.0
     */
    public static LibraryFactory<DynamicObjectLibrary> getFactory() {
        return FACTORY;
    }

    /**
     * Gets the shared {@link DynamicObjectLibrary} instance for uncached accesses. Equivalent to
     * {@code DynamicObjectLibrary.getFactory().getUncached()}.
     *
     * @return an uncached automatically dispatched version of the library
     * @since 20.2.0
     */
    public static DynamicObjectLibrary getUncached() {
        return getFactory().getUncached();
    }

    /**
     * Gets the {@link Shape shape} of the object. Returns the cached shape if the library is a
     * cached library instance.
     *
     * @return the object's current {@link Shape}
     * @see DynamicObject#getShape()
     * @since 20.2.0
     */
    public abstract Shape getShape(DynamicObject object);

    /**
     * Gets the value of an existing property or returns the provided default value if no such
     * property exists.
     *
     * @param key property key
     * @param defaultValue value to be returned if the property does not exist
     * @return the property's value if it exists, else {@code defaultValue}.
     * @since 20.2.0
     */
    public abstract Object getOrDefault(DynamicObject object, Object key, Object defaultValue);

    /**
     * Gets the value of an existing property or returns the provided default value if no such
     * property exists.
     *
     * @param key property key
     * @param defaultValue value to be returned if the property does not exist
     * @return the property's value if it exists, else {@code defaultValue}.
     * @throws UnexpectedResultException if the (default) value is not an {@code int}
     * @see #getOrDefault(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public int getIntOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
        Object value = getOrDefault(object, key, defaultValue);
        if (value instanceof Integer) {
            return (int) value;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    /**
     * Gets the value of an existing property or returns the provided default value if no such
     * property exists.
     *
     * @param key property key
     * @param defaultValue value to be returned if the property does not exist
     * @return the property's value if it exists, else {@code defaultValue}.
     * @throws UnexpectedResultException if the (default) value is not a {@code double}
     * @see #getOrDefault(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
        Object value = getOrDefault(object, key, defaultValue);
        if (value instanceof Double) {
            return (double) value;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    /**
     * Gets the value of an existing property or returns the provided default value if no such
     * property exists.
     *
     * @param key property key
     * @param defaultValue value to be returned if the property does not exist
     * @return the property's value if it exists, else {@code defaultValue}.
     * @throws UnexpectedResultException if the (default) value is not a {@code long}
     * @see #getOrDefault(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public long getLongOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
        Object value = getOrDefault(object, key, defaultValue);
        if (value instanceof Long) {
            return (long) value;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    /**
     * Gets the value of an existing property or returns the provided default value if no such
     * property exists.
     *
     * @param key property key
     * @param defaultValue value to be returned if the property does not exist
     * @return the property's value if it exists, else {@code defaultValue}.
     * @throws UnexpectedResultException if the (default) value is not a {@code boolean}
     * @see #getOrDefault(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public boolean getBooleanOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
        Object value = getOrDefault(object, key, defaultValue);
        if (value instanceof Boolean) {
            return (boolean) value;
        } else {
            throw new UnexpectedResultException(value);
        }
    }

    /**
     * Sets the value of an existing property or adds a new property if no such property exists.
     *
     * A newly added property will have flags 0; flags of existing properties will not be changed.
     *
     * @param key property identifier
     * @param value value to be set
     * @see #putInt(DynamicObject, Object, int)
     * @see #putDouble(DynamicObject, Object, double)
     * @see #putLong(DynamicObject, Object, long)
     * @see #putBoolean(DynamicObject, Object, boolean)
     * @see #putIfPresent(DynamicObject, Object, Object)
     * @see #putWithFlags(DynamicObject, Object, Object, int)
     * @since 20.2.0
     */
    public abstract void put(DynamicObject object, Object key, Object value);

    /**
     * Int-typed variant of {@link #put}.
     *
     * @see #put(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public void putInt(DynamicObject object, Object key, int value) {
        put(object, key, value);
    }

    /**
     * Double-typed variant of {@link #put}.
     *
     * @see #put(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public void putDouble(DynamicObject object, Object key, double value) {
        put(object, key, value);
    }

    /**
     * Long-typed variant of {@link #put}.
     *
     * @see #put(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public void putLong(DynamicObject object, Object key, long value) {
        put(object, key, value);
    }

    /**
     * Boolean-typed variant of {@link #put}.
     *
     * @see #put(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public void putBoolean(DynamicObject object, Object key, boolean value) {
        put(object, key, value);
    }

    /**
     * Sets the value of the property if present, otherwise returns {@code false}.
     *
     * @param key property identifier
     * @param value value to be set
     * @return {@code true} if the property was present and the value set, otherwise {@code false}
     * @see #put(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public abstract boolean putIfPresent(DynamicObject object, Object key, Object value);

    /**
     * Like {@link #put}, but additionally assigns flags to the property. If the property already
     * exists, its flags will be updated.
     *
     * @param key property identifier
     * @param value value to be set
     * @param flags flags to be set
     * @see #put(DynamicObject, Object, Object)
     * @see #setPropertyFlags(DynamicObject, Object, int)
     * @since 20.2.0
     */
    public abstract void putWithFlags(DynamicObject object, Object key, Object value, int flags);

    /**
     * Adds a property with constant value or replaces an existing one. If the property already
     * exists, its flags will be updated.
     *
     * The constant value is stored in the shape rather than the object instance and a new shape
     * will be allocated if it does not already exist.
     *
     * Warning: this method will lead to a shape transition every time a new value is set and should
     * be used sparingly since it could cause an excessive amount of shapes to be created.
     *
     * Note: the value will be strongly referenced from the shape and should be a value type or
     * light-weight object such as a singleton in order to prevent potential memory leaks.
     *
     * @param key property identifier
     * @param value the constant value to be set
     * @param flags property flags or 0
     * @see #put(DynamicObject, Object, Object)
     * @since 20.2.0
     */
    public abstract void putConstant(DynamicObject object, Object key, Object value, int flags);

    /**
     * Removes the property with the given key from the object.
     *
     * @param key property key
     * @return {@code true} if the property was removed or {@code false} if property was not found
     * @since 20.2.0
     */
    public abstract boolean removeKey(DynamicObject object, Object key);

    /**
     * Sets the dynamic type identifier. It is recommended that this be a singleton or light-weight
     * object in order to keep memory footprint low and prevent potential memory leaks.
     *
     * @param type an instance of {@link com.oracle.truffle.api.object.ObjectType}.
     * @return {@code true} if the type (and the object's shape) changed
     * @since 20.2.0
     * @see #getDynamicType(DynamicObject)
     */
    public abstract boolean setDynamicType(DynamicObject object, Object type);

    /**
     * Gets the dynamic type identifier associated with this object.
     *
     * @return the object type
     * @since 20.2.0
     * @see Shape#getDynamicType()
     */
    public abstract Object getDynamicType(DynamicObject object);

    /**
     * Returns {@code true} if this object contains a property with the given key.
     *
     * @param key property key
     * @since 20.2.0
     */
    public abstract boolean containsKey(DynamicObject object, Object key);

    /**
     * Gets the shape flags.
     *
     * @return shape flags
     * @see Shape#getFlags()
     * @see #setShapeFlags(DynamicObject, int)
     * @since 20.2.0
     */
    public abstract int getShapeFlags(DynamicObject object);

    /**
     * Sets the shape flags, changing the object's shape if need be.
     *
     * @param flags the flags to set; must be in the range from 0 to 255 (inclusive).
     * @return {@code true} if the object's shape changed, {@code false} if no change was made.
     * @see #getShapeFlags(DynamicObject)
     * @since 20.2.0
     */
    public abstract boolean setShapeFlags(DynamicObject object, int flags);

    /**
     * Get property descriptor.
     *
     * @return {@link Property} if the property exists, else {@code null}
     * @since 20.2.0
     */
    public abstract Property getProperty(DynamicObject object, Object key);

    /**
     * Get property flags.
     *
     * @param defaultValue value to return if no such property exists
     * @return property flags if the property exists, else defaultValue
     * @see #getProperty(DynamicObject, Object)
     * @since 20.2.0
     */
    public final int getPropertyFlagsOrDefault(DynamicObject object, Object key, int defaultValue) {
        Property property = getProperty(object, key);
        return property != null ? property.getFlags() : defaultValue;
    }

    /**
     * Sets property flags.
     *
     * @param key property key
     * @return {@code true} if the property was found and its flags were changed, else {@code false}
     * @since 20.2.0
     */
    public abstract boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags);

    /**
     * Makes this object shared.
     *
     * @throws UnsupportedOperationException if the object is already {@linkplain #isShared shared}.
     * @since 20.2.0
     */
    public abstract void makeShared(DynamicObject object);

    /**
     * Checks whether this object is shared.
     *
     * @return {@code true} if the object is shared
     * @since 20.2.0
     */
    public abstract boolean isShared(DynamicObject object);

    /**
     * Ensure object shape is up-to-date.
     *
     * @return {@code true} if the object's shape was changed
     * @since 20.2.0
     */
    public abstract boolean updateShape(DynamicObject object);

    /**
     * Empties and resets the object to the given root shape, which must not contain any instance
     * properties.
     *
     * @param otherShape the desired shape
     * @return {@code true} if the object's shape was changed
     * @throws IllegalArgumentException if the shape contains instance properties
     * @since 20.2.0
     */
    public abstract boolean resetShape(DynamicObject object, Shape otherShape);

    /**
     * Gets a snapshot of the object's property keys, in insertion order. The returned array may
     * have been cached and must not be mutated.
     *
     * @return a read-only array of the object's property keys.
     * @since 20.2.0
     */
    public abstract Object[] getKeyArray(DynamicObject object);

    /**
     * Gets an array snapshot of the object's properties, in insertion order. The returned array may
     * have been cached and must not be mutated.
     *
     * @return a read-only array of the object's properties.
     * @since 20.2.0
     */
    public abstract Property[] getPropertyArray(DynamicObject object);
}
