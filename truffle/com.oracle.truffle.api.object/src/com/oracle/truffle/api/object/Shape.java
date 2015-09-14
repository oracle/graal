/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import java.util.*;

import com.oracle.truffle.api.*;

/**
 * Shape objects create a mapping of Property objects to Locations. Shapes are immutable; adding or
 * deleting a property yields a new Shape which links to the old one. This allows inline caching to
 * simply check the identity of an object's Shape to determine if the cache is valid. There is one
 * exception to this immutability, the transition map, but that is used simply to assure that an
 * identical series of property additions and deletions will yield the same Shape object.
 *
 * @see DynamicObject
 * @see Property
 * @see Location
 */
public abstract class Shape {
    /**
     * Get a property entry by key.
     *
     * @param key the identifier to look up
     * @return a Property object, or null if not found
     */
    public abstract Property getProperty(Object key);

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * @param property the property to add
     * @return the new Shape
     */
    public abstract Shape addProperty(Property property);

    /**
     * An {@link Iterable} over the shape's properties in insertion order.
     */
    public abstract Iterable<Property> getProperties();

    /**
     * Get a list of properties that this Shape stores.
     *
     * @return list of properties
     */
    public abstract List<Property> getPropertyList(Pred<Property> filter);

    /**
     * Get a list of all properties that this Shape stores.
     *
     * @return list of properties
     */
    public abstract List<Property> getPropertyList();

    /**
     * Returns all (also hidden) property objects in this shape.
     *
     * @param ascending desired order ({@code true} for insertion order, {@code false} for reverse
     *            insertion order)
     */
    public abstract List<Property> getPropertyListInternal(boolean ascending);

    /**
     * Get a filtered list of property keys in insertion order.
     */
    public abstract List<Object> getKeyList(Pred<Property> filter);

    /**
     * Get a list of all property keys in insertion order.
     */
    public abstract List<Object> getKeyList();

    /**
     * Get all property keys in insertion order.
     */
    public abstract Iterable<Object> getKeys();

    /**
     * Get an assumption that the shape is valid.
     */
    public abstract Assumption getValidAssumption();

    /**
     * Check whether this shape is valid.
     */
    public abstract boolean isValid();

    /**
     * Get an assumption that the shape is a leaf.
     */
    public abstract Assumption getLeafAssumption();

    /**
     * Check whether this shape is a leaf in the transition graph, i.e. transitionless.
     */
    public abstract boolean isLeaf();

    /**
     * @return the parent shape or {@code null} if none.
     */
    public abstract Shape getParent();

    /**
     * Check whether the shape has a property with the given key.
     */
    public abstract boolean hasProperty(Object key);

    /**
     * Remove the given property from the shape.
     */
    public abstract Shape removeProperty(Property property);

    /**
     * Replace a property in the shape.
     */
    public abstract Shape replaceProperty(Property oldProperty, Property newProperty);

    /**
     * Get the last added property.
     */
    public abstract Property getLastProperty();

    public abstract int getId();

    /**
     * Append the property, relocating it to the next allocated location.
     */
    public abstract Shape append(Property oldProperty);

    /**
     * Obtain an {@link Allocator} instance for the purpose of allocating locations.
     */
    public abstract Allocator allocator();

    /**
     * Get number of properties in this shape.
     */
    public abstract int getPropertyCount();

    /**
     * Get the shape's operations.
     */
    public abstract ObjectType getObjectType();

    /**
     * Get the root shape.
     */
    public abstract Shape getRoot();

    /**
     * Check whether this shape is identical to the given shape.
     */
    public abstract boolean check(DynamicObject subject);

    /**
     * Get the shape's layout.
     */
    public abstract Layout getLayout();

    /**
     * Get the shape's custom data.
     */
    public abstract Object getData();

    /**
     * Get the shape's shared data.
     */
    public abstract Object getSharedData();

    /**
     * Query whether the shape has a transition with the given key.
     */
    public abstract boolean hasTransitionWithKey(Object key);

    /**
     * Clone off a separate shape with new shared data.
     */
    public abstract Shape createSeparateShape(Object sharedData);

    /**
     * Change the shape's type, yielding a new shape.
     */
    public abstract Shape changeType(ObjectType newOps);

    /**
     * Reserve the primitive extension array field.
     */
    public abstract Shape reservePrimitiveExtensionArray();

    /**
     * Create a new {@link DynamicObject} instance with this shape.
     */
    public abstract DynamicObject newInstance();

    /**
     * Create a {@link DynamicObjectFactory} for creating instances of this shape.
     */
    public abstract DynamicObjectFactory createFactory();

    /**
     * Get mutex object shared by related shapes, i.e. shapes with a common root.
     */
    public abstract Object getMutex();

    /**
     * Are these two shapes related, i.e. do they have the same root?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
     */
    public abstract boolean isRelated(Shape other);

    /**
     * Try to merge two related shapes to a more general shape that has the same properties and can
     * store at least the values of both shapes.
     *
     * @return this, other, or a new shape that is compatible with both shapes
     */
    public abstract Shape tryMerge(Shape other);

    /**
     * Utility class to allocate locations in an object layout.
     */
    public abstract static class Allocator {
        protected abstract Location locationForValue(Object value, boolean useFinal, boolean nonNull);

        /**
         * Create a new location compatible with the given initial value.
         *
         * @param value the initial value this location is going to be assigned
         */
        public final Location locationForValue(Object value) {
            return locationForValue(value, false, value != null);
        }

        /**
         * Create a new location compatible with the given initial value.
         *
         * @param value the initial value this location is going to be assigned
         * @param modifiers additional restrictions and semantics
         */
        public final Location locationForValue(Object value, EnumSet<LocationModifier> modifiers) {
            assert value != null || !modifiers.contains(LocationModifier.NonNull);
            return locationForValue(value, modifiers.contains(LocationModifier.Final), modifiers.contains(LocationModifier.NonNull));
        }

        protected abstract Location locationForType(Class<?> type, boolean useFinal, boolean nonNull);

        /**
         * Create a new location for a fixed type. It can only be assigned to values of this type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         */
        public final Location locationForType(Class<?> type) {
            return locationForType(type, false, false);
        }

        /**
         * Create a new location for a fixed type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         * @param modifiers additional restrictions and semantics
         */
        public final Location locationForType(Class<?> type, EnumSet<LocationModifier> modifiers) {
            return locationForType(type, modifiers.contains(LocationModifier.Final), modifiers.contains(LocationModifier.NonNull));
        }

        /**
         * Creates a new location from a constant value. The value is stored in the shape rather
         * than in the object.
         */
        public abstract Location constantLocation(Object value);

        /**
         * Creates a new declared location with a default value. A declared location only assumes a
         * type after the first set (initialization).
         */
        public abstract Location declaredLocation(Object value);

        /**
         * Reserves space for the given location, so that it will not be available to subsequently
         * allocated locations.
         */
        public abstract Allocator addLocation(Location location);

        /**
         * Creates an copy of this allocator state.
         */
        public abstract Allocator copy();
    }

    /**
     * Represents a predicate (boolean-valued function) of one argument.
     *
     * For Java 7 compatibility (equivalent to Predicate).
     *
     * @param <T> the type of the input to the predicate
     */
    public interface Pred<T> {
        /**
         * Evaluates this predicate on the given argument.
         *
         * @param t the input argument
         * @return {@code true} if the input argument matches the predicate, otherwise {@code false}
         */
        boolean test(T t);
    }
}
