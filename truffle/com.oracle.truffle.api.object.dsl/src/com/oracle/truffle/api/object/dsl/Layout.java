/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link Layout} annotation is attached to an interface that describes an object layout with
 * properties. The interface has a factory method, guards, getters and setters. From this a class is
 * generated that implements these methods and provides very efficient static access to these
 * properties in a {@link DynamicObject}.
 *
 * <pre>
 * {@literal@}Layout
 * public interface RectLayout {
 *    ...
 * }
 * </pre>
 *
 * The properties are defined by getters and setter method pairs. They should both take a
 * {@link DynamicObject}, and the setter should take a value. The type of this value should match
 * the type of the return value of the getter. This defines an {@code int} property called
 * {@code width}.
 *
 * <pre>
 * int getWidth(DynamicObject object);
 *
 * void setWidth(DynamicObject object, int value);
 * </pre>
 *
 * A constructor accepts a value for each property, returning a new {@link DynamicObject}. There
 * should be as many parameters as there are properties.
 *
 * Setters are optional. A property without a setter is final.
 *
 * <pre>
 * DynamicObject createRect(int x, int y, int width, int height);
 * </pre>
 *
 * Guards can tell you if an object has this layout.
 *
 * <pre>
 * boolean isRect(DynamicObject object);
 *
 * boolean isRect(Object object);
 *
 * boolean isRect(ObjectType objectType);
 * </pre>
 *
 * To access the implementation of the interface, use the {@code INSTANCE} static final field of the
 * generated {@code ...Impl} class.
 *
 * <pre>
 * RectLayout rectLayout = RectLayoutImpl.INSTANCE;
 * </pre>
 *
 * <p>
 * <strong>Nullability</strong>
 * </p>
 *
 * Properties are non-nullable by default - they cannot contain null values and attempting to set
 * them to null in the constructor method or a setter is an assertion failure.
 *
 * Properties can be marked as nullable by annotating the relevant constructor parameters with
 * {@link Nullable}. Properties with primitive types cannot be nullable.
 *
 * <pre>
 * DynamicObject createWidget({@literal@}Nullable Object foo);
 * </pre>
 *
 * <p>
 * <strong>Volatility</strong>
 * </p>
 *
 * Properties can have volatile semantics on read and write operations by annotating the relevant
 * constructor parameters with {@link Volatile}.
 *
 * Volatile is not supported for shape properties.
 *
 * <pre>
 * DynamicObject createWidget({@literal@}Volatile Object foo);
 * </pre>
 *
 * <p>
 * <strong>Compare-and-set and get-and-set operations</strong>
 * </p>
 *
 * If a property is marked with {@link Volatile}, methods providing compare-and-set and get-and-set
 * can be added by declaring the method headers.
 *
 * <pre>
 * boolean compareAndSetFoo(DynamicObject widget, Object expected_foo, Object foo);
 *
 * Object getAndSet(DynamicObject widget, Object foo);
 * </pre>
 *
 * <p>
 * <strong>Semi-Final Properties</strong>
 * </p>
 *
 * Properties without setters are final, and can be optimized more effectively by the compiler. Some
 * properties need to be modified just once very soon after construction (such as closing a cycle).
 * If you can guarantee that a final reference to the object will not be included in compiled code
 * between construction and needing to set the property, you can define only an unsafe setter, which
 * allows the property to be set while still treating it as final.
 *
 * Unsafe setters have 'Unsafe' after their name.
 *
 * <pre>
 * void setWidthUnsafe(DynamicObject object, int value);
 * </pre>
 *
 * <p>
 * <strong>Inheritance</strong>
 * </p>
 *
 * One layout can inherit properties from another by having one interface annotated with
 * {@link Layout} extend another.
 *
 * <pre>
 * {@literal@}Layout
 * public interface RectLayout {
 *
 *     DynamicObject createRect(int x, int y, int width, int height);
 *
 *     boolean isRect(DynamicObject object);
 *
 *     int getX(DynamicObject object);
 *     ...
 *
 * }
 *
 * {@literal@}Layout
 * public interface ColouredRectLayout extends RectLayout {
 *
 *     DynamicObject createRect(int x, int y, int width, int height, Colour colour);
 *
 *     boolean isColouredRect(DynamicObject object);
 *
 *     Colour getColour(DynamicObject object);
 *     ...
 * }
 * </pre>
 *
 * The inheriting layout must have the properties of the inherited layout in its create method.
 * Inherited properties and guards are available from the base-interface as normal in Java.
 *
 * Instances of the {@code ColouredRectLayout} layout will pass the {@code isRect} guard (so like
 * {@code instanceof}, and properties inherited from {@code RectLayout} can be accessed using
 * {@code RectLayoutImpl.INSTANCE} as well as {@code ColouredRectLayoutImpl.INSTANCE}.
 *
 * <p>
 * <strong>Shape Properties</strong>
 * </p>
 *
 * You may wish to store some properties where the values are common to many objects and do not
 * frequently change in the shape, instead of in the instances. This way you can guard against the
 * value of that property by guarding against the shape.
 *
 * Guest language class references can be implemented using shape properties.
 *
 * To create a shape property you define an additional method, {@code createFooShape} that accepts
 * the shape properties as arguments.
 *
 * <pre>
 * DynamicObjectFactory createFooShape(Object myShapeProperty);
 * </pre>
 *
 * The {@code create} method then takes this factory as well as non-shape properties as before.
 *
 * <pre>
 * DynamicObject createRect(DynamicObjectFactory factory, int x, int y, int width, int height);
 * </pre>
 *
 * Getters and getters for shape properties are defined and used as normal, although the performance
 * of the setter will be much reduced, and the getter will use additional indirection so you may
 * want to cache the result (against the shape).
 *
 * You can also use the getter and setter against the {@code DynamicObjectFactory} that
 * {@code createFooShape} returns, with the setter returning a new factory. This allows objects to
 * be created with modified shape properties, and is much more efficient than using the instance
 * setter after creating the object.
 *
 * Finally, the getters can be used against an {@code ObjectType}.
 *
 * Shape properties cannot be semi-final.
 *
 * <p>
 * <strong>Object Type Superclass</strong>
 * </p>
 *
 * By default the superclass of the generated object type is {@link ObjectType}. You can change
 * this, perhaps to override methods in it, using the {@link #objectTypeSuperclass} property. This
 * can't be used if you are inheriting another layout, as the DSL needs to inherit the object type
 * of the inherited shape.
 *
 * The class used should have empty constructor that is protected or more visible.
 *
 * <p>
 * <strong>Processing</strong>
 * </p>
 *
 * {@link Layout} annotations are processed by {@link OMProcessor}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Layout {

    String objectTypeSuperclass() default "ObjectType";

}
