/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 ** <p>
 * It is recommended that you use the {@link CachedLibrary} annotation in Truffle DSL nodes. You can
 * also use the library either {@linkplain #getUncached() without caching} or create a
 * {@linkplain LibraryFactory#create(Object) manually} or
 * {@linkplain LibraryFactory#createDispatched(int) automatically} dispatched cached library. Cached
 * libraries must be adopted before use.
 *
 * The cached library instances dispatch by object {@linkplain Shape shape} and, if applicable,
 * automatically by property key.
 *
 * <p>
 * Property keys are compared using object identity ({@code ==}) first and then
 * {@link Object#equals(Object)}. It is therefore recommended to use the same string/key instances
 * for each access of the property in order to avoid pulling in {@code equals}. Keys must not be
 * {@code null}.
 *
 * <p>
 * Note: cached library nodes may not profile the class of the object parameter; it is therefore the
 * caller's responsibility to do any desired profiling and ensure accurate type information.
 *
 * <h3>Usage examples:</h3>
 *
 * <pre>
 * &#64;Specialization(limit = "3")
 * static Object read(DynamicObject receiver, Object key,
 *                 &#64;CachedLibrary("receiver") DynamicObjectLibrary objLib) {
 *     return objLib.getOrDefault(receiver, key, NULL_VALUE);
 * }
 * </pre>
 *
 * <pre>
 * &#64;ExportMessage
 * Object readMember(String name,
 *                 &#64;CachedLibrary("this") DynamicObjectLibrary objLib) throws UnknownIdentifierException {
 *     Object result = objLib.getOrDefault(this, name, null);
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
     * <h3>Usage example:</h3>
     *
     * <pre>
     * &#64;Specialization(limit = "3")
     * static Object read(DynamicObject receiver, Object key,
     *                 &#64;CachedLibrary("receiver") DynamicObjectLibrary objLib) {
     *     return objLib.getOrDefault(receiver, key, NULL_VALUE);
     * }
     * </pre>
     *
     * @param key the property key
     * @param defaultValue value to be returned if the property does not exist
     * @return the property's value if it exists, else {@code defaultValue}.
     * @since 20.2.0
     */
    public abstract Object getOrDefault(DynamicObject object, Object key, Object defaultValue);

    /**
     * Gets the value of an existing property or returns the provided default value if no such
     * property exists.
     *
     * @param key the property key
     * @param defaultValue the value to be returned if the property does not exist
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
     * @param key the property key
     * @param defaultValue the value to be returned if the property does not exist
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
     * @param key the property key
     * @param defaultValue the value to be returned if the property does not exist
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
     * Sets the value of an existing property or adds a new property if no such property exists.
     *
     * A newly added property will have flags 0; flags of existing properties will not be changed.
     * Use {@link #putWithFlags} to set property flags as well.
     *
     * <h3>Usage example:</h3>
     *
     * <pre>
     * &#64;ExportMessage
     * Object writeMember(String member, Object value,
     *                 &#64;CachedLibrary("this") DynamicObjectLibrary objLib) {
     *     objLib.put(this, member, value);
     * }
     * </pre>
     *
     * @param key the property key
     * @param value the value to be set
     * @see #putInt(DynamicObject, Object, int)
     * @see #putDouble(DynamicObject, Object, double)
     * @see #putLong(DynamicObject, Object, long)
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
     * exists, its flags will be updated before the value is set.
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
     * Adds a property with a constant value or replaces an existing one. If the property already
     * exists, its flags will be updated.
     *
     * The constant value is stored in the shape rather than the object instance and a new shape
     * will be allocated if it does not already exist.
     *
     * A typical use case for this method is setting the initial default value of a declared, but
     * yet uninitialized, property. This defers storage allocation and type speculation until the
     * first actual value is set.
     *
     * <p>
     * Warning: this method will lead to a shape transition every time a new value is set and should
     * be used sparingly (with at most one constant value per property) since it could cause an
     * excessive amount of shapes to be created.
     * <p>
     * Note: the value will be strongly referenced from the shape and should be a value type or
     * light-weight object without any references to guest language objects in order to prevent
     * potential memory leaks.
     *
     * <h3>Usage example:</h3>
     *
     * <pre>
     * // declare property
     * objLib.putConstant(receiver, key, NULL_VALUE, 0);
     *
     * // initialize property
     * objLib.put(receiver, key, value);
     * </pre>
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
     * @param key the property key
     * @return {@code true} if the property was removed or {@code false} if property was not found
     * @since 20.2.0
     */
    public abstract boolean removeKey(DynamicObject object, Object key);

    /**
     * Sets the object's dynamic type identifier. What this type represents is completely up to the
     * language. For example, it could be a guest-language class.
     *
     * The type object is strongly referenced from the shape. It is important that this be a
     * singleton or light-weight object without any references to guest language objects in order to
     * keep the memory footprint low and prevent potential memory leaks.
     *
     * Type objects are always compared by object identity, never {@code equals}.
     *
     * <p>
     * Note: For compatibility reasons, the type needs to be an instance of
     * {@link com.oracle.truffle.api.object.ObjectType ObjectType}. This restriction will be lifted
     * in a future version.
     *
     * @param type an instance of {@link com.oracle.truffle.api.object.ObjectType}.
     * @return {@code true} if the type (and the object's shape) changed
     * @since 20.2.0
     * @see #getDynamicType(DynamicObject)
     */
    public abstract boolean setDynamicType(DynamicObject object, Object type);

    /**
     * Gets the dynamic type identifier currently associated with this object. What this type
     * represents is completely up to the language. For example, it could be a guest-language class.
     *
     * @return the object type
     * @since 20.2.0
     * @see #setDynamicType(DynamicObject, Object)
     * @see Shape#getDynamicType()
     */
    public abstract Object getDynamicType(DynamicObject object);

    /**
     * Returns {@code true} if this object contains a property with the given key.
     *
     * <h3>Usage example:</h3>
     *
     * <pre>
     * &#64;ExportMessage
     * boolean isMemberReadable(String name,
     *                 &#64;CachedLibrary("this") DynamicObjectLibrary objLib) {
     *     return objLib.containsKey(this, name);
     * }
     * </pre>
     *
     * @param key the property key
     * @return {@code true} if the object contains a property with this key, else {@code false}
     * @since 20.2.0
     */
    public abstract boolean containsKey(DynamicObject object, Object key);

    /**
     * Gets the language-specific object shape flags previously set using
     * {@link DynamicObjectLibrary#setShapeFlags(DynamicObject, int)} or
     * {@link Shape.Builder#shapeFlags(int)}. If no shape flags were explicitly set, the default of
     * 0 is returned.
     *
     * These flags may be used to tag objects that possess characteristics that need to be queried
     * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
     *
     * <h3>Usage example:</h3>
     *
     * <pre>
     * &#64;ExportMessage
     * Object writeMember(String member, Object value,
     *                 &#64;CachedLibrary("this") DynamicObjectLibrary objLib)
     *                 throws UnsupportedMessageException {
     *     if ((objLib.getShapeFlags(receiver) & FROZEN) != 0) {
     *         throw UnsupportedMessageException.create();
     *     }
     *     objLib.put(this, member, value);
     * }
     * </pre>
     *
     * @return shape flags
     * @see #setShapeFlags(DynamicObject, int)
     * @see Shape.Builder#shapeFlags(int)
     * @see Shape#getFlags()
     * @since 20.2.0
     */
    public abstract int getShapeFlags(DynamicObject object);

    /**
     * Sets language-specific object shape flags, changing the object's shape if need be.
     *
     * These flags may be used to tag objects that possess characteristics that need to be queried
     * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
     *
     * Only the lowest 8 bits (i.e. values in the range 0 to 255) are allowed, the remaining bits
     * are currently reserved.
     *
     * <h3>Usage example:</h3>
     *
     * <pre>
     * &#64;Specialization(limit = "3")
     * static void preventExtensions(DynamicObject receiver,
     *                 &#64;CachedLibrary("receiver") DynamicObjectLibrary objLib) {
     *     objLib.setShapeFlags(receiver, objLib.getShapeFlags(receiver) | FROZEN);
     * }
     * </pre>
     *
     * @param flags the flags to set; must be in the range from 0 to 255 (inclusive).
     * @return {@code true} if the object's shape changed, {@code false} if no change was made.
     * @throws IllegalArgumentException if the flags are not in the allowed range.
     * @see #getShapeFlags(DynamicObject)
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    public abstract boolean setShapeFlags(DynamicObject object, int flags);

    /**
     * Gets a {@linkplain Property property descriptor} for the requested property key. Returns
     * {@code null} if the object contains no such property.
     *
     * @return {@link Property} if the property exists, else {@code null}
     * @since 20.2.0
     */
    public abstract Property getProperty(DynamicObject object, Object key);

    /**
     * Gets the property flags associated with the requested property key. Returns the
     * {@code defaultValue} if the object contains no such property. If the property exists but no
     * flags were explicitly set, returns the default of 0.
     *
     * <p>
     * Convenience method equivalent to:
     *
     * <pre>
     * Property property = getProperty(object, key);
     * return property != null ? property.getFlags() : defaultValue;
     * </pre>
     *
     * @param key the property key
     * @param defaultValue value to return if no such property exists
     * @return the property flags if the property exists, else {@code defaultValue}
     * @see #getProperty(DynamicObject, Object)
     * @since 20.2.0
     */
    public final int getPropertyFlagsOrDefault(DynamicObject object, Object key, int defaultValue) {
        Property property = getProperty(object, key);
        return property != null ? property.getFlags() : defaultValue;
    }

    /**
     * Sets the property flags associated with the requested property.
     *
     * @param key the property key
     * @return {@code true} if the property was found and its flags were changed, else {@code false}
     * @since 20.2.0
     */
    public abstract boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags);

    /**
     * Marks this object as shared.
     *
     * Makes the object use a shared variant of the {@link Shape}, to allow safe usage of this
     * object between threads. Objects with a shared {@link Shape} will not reuse storage locations
     * for other fields. In combination with careful synchronization on writes, this can prevent
     * reading out-of-thin-air values.
     *
     * @throws UnsupportedOperationException if the object is already {@linkplain #isShared shared}.
     * @see #isShared(DynamicObject)
     * @since 20.2.0
     */
    public abstract void markShared(DynamicObject object);

    /**
     * Checks whether this object is marked as shared.
     *
     * @return {@code true} if the object is shared
     * @see #markShared(DynamicObject)
     * @see Shape#isShared()
     * @since 20.2.0
     */
    public abstract boolean isShared(DynamicObject object);

    /**
     * Ensures the object's shape is up-to-date. If the object's shape has been marked as
     * {@linkplain Shape#isValid() invalid}, this method will attempt to bring the object into a
     * valid shape again. If the object's shape is already {@linkplain Shape#isValid() valid}, this
     * method will have no effect.
     *
     * This method does not need to be called normally; all the messages in this library will work
     * on invalid shapes as well, but it can be useful in some cases to avoid such shapes being
     * cached which can cause unnecessary cache polymorphism and invalidations.
     *
     * @return {@code true} if the object's shape was changed, otherwise {@code false}.
     * @since 20.2.0
     */
    public abstract boolean updateShape(DynamicObject object);

    /**
     * Empties and resets the object to the given root shape, which must not contain any instance
     * properties (but may contain properties with a constant value).
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
     * Properties with a {@link HiddenKey} are not included.
     *
     * <h3>Usage example:</h3>
     *
     * The example below shows how the returned keys array could be translated to an interop array
     * for use with InteropLibrary.
     *
     * <pre>
     * &#64;ExportMessage
     * Object getMembers(
     *                 &#64;CachedLibrary("this") DynamicObjectLibrary objLib) {
     *     return new Keys(objLib.getKeyArray(this));
     * }
     *
     * &#64;ExportLibrary(InteropLibrary.class)
     * static final class Keys implements TruffleObject {
     *
     *     &#64;CompilationFinal(dimensions = 1) final Object[] keys;
     *
     *     Keys(Object[] keys) {
     *         this.keys = keys;
     *     }
     *
     *     &#64;ExportMessage
     *     boolean hasArrayElements() {
     *         return true;
     *     }
     *
     *     &#64;ExportMessage
     *     Object readArrayElement(long index) throws InvalidArrayIndexException {
     *         if (!isArrayElementReadable(index)) {
     *             throw InvalidArrayIndexException.create(index);
     *         }
     *         return keys[(int) index];
     *     }
     *
     *     &#64;ExportMessage
     *     long getArraySize() {
     *         return keys.length;
     *     }
     *
     *     &#64;ExportMessage
     *     boolean isArrayElementReadable(long index) {
     *         return index >= 0 && index < keys.length;
     *     }
     * }
     * </pre>
     *
     * @return a read-only array of the object's property keys.
     * @since 20.2.0
     */
    public abstract Object[] getKeyArray(DynamicObject object);

    /**
     * Gets an array snapshot of the object's properties, in insertion order. The returned array may
     * have been cached and must not be mutated.
     *
     * Properties with a {@link HiddenKey} are not included.
     *
     * Similar to {@link #getKeyArray} but allows the properties' flags to be queried simultaneously
     * which may be relevant for quick filtering.
     *
     * @return a read-only array of the object's properties.
     * @see #getKeyArray(DynamicObject)
     * @since 20.2.0
     */
    public abstract Property[] getPropertyArray(DynamicObject object);
}
