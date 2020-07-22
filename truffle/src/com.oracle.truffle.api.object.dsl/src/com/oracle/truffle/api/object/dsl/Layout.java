/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.ObjectType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Annotate an interface with {@link Layout} to generate an implementation of the interface which
 * uses object-model properties. {@link Layout} allows you to use the object-model in a similar way
 * to writing a normal Java class for statically declared and implementation-specific fields. Most
 * methods generated from an {@link Layout}-annotated interface are suitable for use on the
 * fast-path.
 *
 * The name of the interface should end with 'Layout'.
 *
 * {@codesnippet rectlayout}
 *
 * The generated class is named with the name of the interface and then {@code -Impl}. A singleton
 * instance of the interface, {@code -Impl.INSTANCE} is available as a static field in the class.
 *
 * {@codesnippet rectlayoutimpl}
 *
 * <h2>Factory method</h2>
 *
 * A factory method named {@code create-} and then the name of the layout creates instances of the
 * layout. It returns a {@link DynamicObject}, not an instance of the interface.
 *
 * {@codesnippet rectlayoutcreate}
 *
 * <h3>Alternative constructor method</h3>
 *
 * As an alternative to the {@code create-} factory, you can declare a method named {@code build},
 * which returns the arguments packed for {@link DynamicObjectFactory#newInstance(Object...)}.
 *
 * {@codesnippet rectlayoutbuild}
 *
 * This is particularly useful when the {@code DynamicObjectFactory} is not statically known or some
 * generic allocation node taking {@code Object[]} arguments is used to create objects.
 *
 * {@codesnippet rectlayoutbuildinstance}
 *
 * <h2>Guards</h2>
 *
 * Guards can tell you if an object is using layout. Guards are defined for {@link DynamicObject},
 * the more general {@link Object} which first checks if the arguments is a {@link DynamicObject},
 * and {@link ObjectType}, which you can get through the shape of a {@link DynamicObject}. To add a
 * guard, define the method in your interface.
 *
 *
 * {@codesnippet rectlayoutguards}
 *
 * <h2>Properties</h2>
 *
 * To add properties, define a getter and setter, and add a parameter to the factory method.
 *
 * {@codesnippet rectlayoutprops}
 *
 * If you don't define a setter, the property will be final. This may improve the performance of the
 * property.
 *
 * <h2>Nullable Properties</h2>
 *
 * By default, properties are non-nullable, which means that they always need an instance of an
 * object and they cannot be assigned the value {@code null}. This has performance benefits in the
 * implementation of the object-model.
 *
 * To make a property nullable so that you can assign {@code null} to it, annotate the constructor
 * parameter with {@link Nullable}.
 *
 * {@codesnippet nullable}
 *
 * <h2>Volatile Properties</h2>
 *
 * To define a property with volatile semantics, in the sense of the Java Language Specification
 * section 8.3.1.4, annotate the constructor parameter with {@link Volatile}. A property annotated
 * as volatile also allows you to define atomic operation methods in your layout interface for that
 * property. Methods available are {@code compareAndSet}, in the sense of
 * {@link AtomicReference#compareAndSet}, and {@code getAndSet}, in the sense of
 * {@link AtomicReference#getAndSet}.
 *
 * {@codesnippet volatile}
 *
 * Volatile properties generally have lower performance than the default non-volatile properties.
 *
 * <h2>Semi-Final Properties</h2>
 *
 * It is possible to define a 'back-door' and unsafe setter for otherwise-final properties by
 * appending {@code -Unsafe} to the setter name.
 *
 * {@codesnippet semifinal}
 *
 * Final and semi-final properties may be assumed by a dynamic compiler to not change for a given
 * instance of an object after it is constructed. Unsafe setters are therefore unsafe as a
 * modification to the property could be ignored by the dynamic compiler. You should only use unsafe
 * setters if you have reasoned that it is not possible for the dynamic compiler to compile a
 * reference to the object and the property before the unsafe setter is used. One use-case is
 * closing cycles in class graphs, such as the classic class-of-class-is-class problem, where you
 * normally want the class property to be final for performance but just as the graph is created
 * this one cycle needs to be closed.
 *
 * Errors due to the incorrect use of unsafe getters are likely to be non-deterministic and
 * difficult to isolate. Consider making properties temporarily non-final with a conventional getter
 * if stale value are experienced in dynamically compiled code.
 *
 * <h2>Shape Properties</h2>
 *
 * A shape property is a property that is shared between many objects and does not frequently
 * change. One intended use-case is a property to store the class of an object, which is likely
 * shared between many objects and likely does not change after the object is created.
 *
 * Shape properties should be cached against an object's shape as there is an extra level of
 * indirection used to look up their value for an object. They may save space as they are not stored
 * for all instances.
 *
 * It is important to note that changing a shape-property for an existing object is both not a
 * fast-path operation, and depending on the design of your interpreter is likely to invalidate
 * caches.
 *
 * When shape properties are used there is an extra level of indirection, in that a
 * {@link DynamicObjectFactory} (referred to as the shape, because it is the shape that the factory
 * object contains that is used to look up shape properties) is created by the layout and then used
 * when creating new instances. As shape properties are set and changed, multiple factories will be
 * created and it is up to the user to store and supply these as needed.
 *
 * Consider the example of a Java-style object, with a class and a hash code. The class would be a
 * shape property, as many objects will share the same class, and the hash code will be a normal
 * property.
 *
 * Shape properties are created by parameters in the method that creates the shape. The factory
 * method then accepts an instance of a factory when creating the object, which is how the instance
 * knows the value of the class property to use. A getter for a shape property can be defined as
 * normal.
 *
 * {@codesnippet javaobject}
 *
 * When we load our Java interpreter we need to set the class property of the {@code Class} object
 * to be itself. This means in this one isolated, slow-path, case we need to change a shape property
 * for an object that is already allocated. Getters for shape properties can be defined for the
 * {@link DynamicObjectFactory}, and for the {@link ObjectType}.
 *
 * Setters for shape properties are more complex, and they are not intended to be used in the fast
 * path. Setters can be defined on a {@link DynamicObjectFactory}, in which case they return a new
 * factory, or on a {@link DynamicObject}, in which they they change the shape of the object. This
 * is a slow-path operation and is likely to invalidate caches in your interpreter.
 *
 * {@codesnippet shapesetters}
 *
 * Apply this to our example with Java classes:
 *
 * {@codesnippet closecycle}
 *
 * <h2>Layout Inheritance</h2>
 *
 * Inheritance of layout interfaces allows you to model classical class inheritance, such as in a
 * language like Java. Use normal interface inheritance to make one layout inherit from another. You
 * then need to add the parameters for super-layouts at the beginning of sub-layout constructor
 * methods.
 *
 * Inherited shape properties work in a similar way.
 *
 * {@codesnippet inheritanceinterfaces}
 *
 * {@codesnippet inheritanceuse}
 *
 * <h2>Custom Object-Type Superclass</h2>
 *
 * Generated layouts use custom {@link ObjectType} subclasses internally. The default base class
 * that is inherited from is simply {@link ObjectType}. You can change this with the
 * {@link #objectTypeSuperclass} property on the {@link Layout} annotation.
 *
 * <h2>Implicit Casts</h2>
 *
 * {@code IntToLong} and {@code IntToDouble} implicit cast flags can be set in the generated layout
 * by setting {@link #implicitCastIntToLong} or {@link #implicitCastIntToDouble}. This can only be
 * done in base layouts, not subclassed layouts.
 *
 * <h2>Custom Identifiers</h2>
 *
 * By default, internal {@link HiddenKey} identifiers with descriptive names will be created for
 * your properties automatically. You can also specify the identifiers to use by defining them as
 * public static final fields in the interface.
 *
 * {@codesnippet customid}
 *
 * @since 0.12
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Layout {
    /** @since 0.12 */
    Class<? extends ObjectType> objectTypeSuperclass() default ObjectType.class;

    /** @since 0.12 */
    boolean implicitCastIntToLong() default false;

    /** @since 0.12 */
    boolean implicitCastIntToDouble() default false;

    /**
     * Non-instantiable class representing default value for {@link #dispatch()}.
     *
     * @since 20.0
     */
    final class DispatchDefaultValue {
        private DispatchDefaultValue() {
        }
    }

    /**
     * Generate override of {@link ObjectType#dispatch()} method in the generated inner *Type class.
     *
     * @since 20.0
     */
    Class<?> dispatch() default DispatchDefaultValue.class;
}

class Snippets {

    // BEGIN: rectlayout
    @Layout
    private interface RectLayout {

    }

    // END: rectlayout

    private static class RectLayoutImpl {
        public static final RectLayoutImpl INSTANCE = new RectLayoutImpl();
    }

    static Object rectLayoutImpl() {
        return
        // BEGIN: rectlayoutimpl
        RectLayoutImpl.INSTANCE;
        // END: rectlayoutimpl
    }

    interface CreateSnippets {
        // BEGIN: rectlayoutcreate
        DynamicObject createRect(int x, int y, int width, int height);

        // END: rectlayoutcreate

        // BEGIN: rectlayoutbuild
        Object[] build(int x, int y, int width, int height);

        // END: rectlayoutbuild
    }

    static class BuildSnippets {
        static class RectLayoutImpl implements CreateSnippets {
            public DynamicObject createRect(int x, int y, int width, int height) {
                return null;
            }

            public Object[] build(int x, int y, int width, int height) {
                return null;
            }

            public static final CreateSnippets INSTANCE = new RectLayoutImpl();
        }

        DynamicObject createRect(Object type, int x, int y, int width, int height) {
            // BEGIN: rectlayoutbuildinstance
            DynamicObjectFactory factory = getCachedFactory(type);
            return factory.newInstance(RectLayoutImpl.INSTANCE.build(x, y, width, height));
            // END: rectlayoutbuildinstance
        }

        private static DynamicObjectFactory getCachedFactory(Object type) {
            return (DynamicObjectFactory) type;
        }
    }

    interface InterfaceSnippets {
        // BEGIN: rectlayoutguards
        boolean isRect(DynamicObject object);

        boolean isRect(Object object);

        boolean isRect(ObjectType objectType);

        // END: rectlayoutguards

        // BEGIN: rectlayoutprops
        DynamicObject createRect(int x, int y, int width, int height);

        int getX(DynamicObject object);

        void setX(DynamicObject object, int value);

        int getWidth(DynamicObject object);

        void setWidth(DynamicObject object, int value);

        // END: rectlayoutprops

        // BEGIN: nullable
        DynamicObject createObject(@Nullable Object nullableProperty);

        // END: nullable

        // BEGIN: volatile
        boolean compareAndSetWidth(DynamicObject object,
                        int expectedValue, int newValue);

        int getAndSet(DynamicObject object, int value);

        // END: volatile

        // BEGIN: semifinal
        void setValueUnsafe(DynamicObject object, Object value);

        // END: semifinal

        class JavaClass {
        }

        // BEGIN: javaobject
        @Layout
        interface JavaObjectLayout {

            DynamicObjectFactory createJavaObjectShape(JavaClass klass);

            DynamicObject createJavaObject(DynamicObjectFactory factory, int hashCode);

            JavaClass getKlass(DynamicObjectFactory factory);

            JavaClass getKlass(ObjectType objectType);

            JavaClass getKlass(DynamicObject object);

            int getHashCode(DynamicObject object);

        }

        // END: javaobject

        // BEGIN: shapesetters
        DynamicObjectFactory setKlass(DynamicObjectFactory factory, JavaClass value);

        void setKlass(DynamicObject object, JavaClass value);
        // END: shapesetters
    }

    static class JavaObjectImpl {

        static final JavaObjectImpl INSTANCE = new JavaObjectImpl();

        @SuppressWarnings("unused")
        Object createJavaObject(Object x, Object y) {
            return null;
        }

        @SuppressWarnings("unused")
        Object createJavaObjectShape(Object x) {
            return null;
        }

        @SuppressWarnings("unused")
        void setKlass(Object x, Object y) {
        }

    }

    Object defaultHashCode() {
        return null;
    }

    void closeCycle() {
        Object
        // BEGIN: closecycle
        javaClassObject = JavaObjectImpl.INSTANCE.createJavaObject(
                        JavaObjectImpl.INSTANCE.createJavaObjectShape(null),
                        defaultHashCode());

        JavaObjectImpl.INSTANCE.setKlass(javaClassObject, javaClassObject);
        // END: closecycle
    }

    // BEGIN: inheritanceinterfaces
    @Layout
    interface BaseLayout {

        DynamicObject createBase(int a);

        boolean isBase(DynamicObject object);

        int getA(DynamicObject object);

        void setA(DynamicObject object, int value);

    }

    @Layout
    interface SuperLayout extends BaseLayout {

        DynamicObject createSuper(int a, int b);

        int getB(DynamicObject object);

        void setB(DynamicObject object, int value);
    }

    // END: inheritanceinterfaces

    static class BaseImpl {

        static final BaseImpl INSTANCE = new BaseImpl();

        @SuppressWarnings("unused")
        boolean isBase(Object x) {
            return false;
        }

        @SuppressWarnings("unused")
        Object getA(Object x) {
            return null;
        }

    }

    static class SuperImpl {

        static final SuperImpl INSTANCE = new SuperImpl();

        @SuppressWarnings("unused")
        DynamicObject createSuper(int x, int y) {
            return null;
        }

    }

    void inheritanceUse() {
        // BEGIN: inheritanceuse
        DynamicObject object = SuperImpl.INSTANCE.createSuper(14, 2);
        BaseImpl.INSTANCE.isBase(object);
        BaseImpl.INSTANCE.getA(object);
        // END: inheritanceuse
    }

    // Checkstyle: stop
    // BEGIN: customid
    @Layout
    interface CustomIdentifierLayout {

        public static final String A_IDENTIFIER = "A";

        DynamicObject createCustomIdentifier(int a);

    }
    // END: customid
    // Checkstyle: resume

}
