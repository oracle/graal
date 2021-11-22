/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Type guards are the most common form of guards used in Truffle languages. By default all type
 * checks and casts use the default method of checking and casting a type in Java. A default type
 * check is performed using the <code>instanceof</code> operator, and a default cast is done using a
 * standard Java cast conversion. However, for some types, this behavior needs to be customized or
 * extended. Therefore, operations can decide to use type systems. If used, type systems are applied
 * to operation classes using the {@link TypeSystemReference} annotation. Only one type system can
 * be active for a single operation. Type systems are inherited from operation superclasses so that
 * the most concrete type system reference is used. If no type system reference is found in the type
 * hierarchy, all type checks and casts are kept default.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;TypeSystem
 * public static class ExampleTypeSystem {
 *
 *     &#64;TypeCast(Undefined.class)
 *     public static Undefined asUndefined(Object value) {
 *         return Undefined.INSTANCE;
 *     }
 *
 *     &#64;TypeCheck(Undefined.class)
 *     public static boolean isUndefined(Object value) {
 *         return value == Undefined.INSTANCE;
 *     }
 *
 *     &#64;ImplicitCast
 *     public static double castInt(int value) {
 *         return value;
 *     }
 * }
 *
 * &#64;TypeSystemReference(ExampleTypeSystem.class)
 * public abstract class BaseNode extends Node {
 *     abstract Object execute();
 * }
 *
 * public static final class Undefined {
 *     public static final Undefined INSTANCE = new Undefined();
 * }
 * </pre>
 *
 * The example type system declared in here defines a special type check and cast for the type
 * Undefined using methods annotated with {@link TypeCheck} and {@link TypeCast}. Undefined is a
 * singleton type and therefore, only a single instance of it exists at a time. For singleton types
 * the type check can be implemented using an identity comparison and instead of casting a value to
 * Undefined we can implement it by returning a constant value. Both the singleton check and cast
 * are considered more efficient than the default check and cast behavior.
 * <p>
 * Multiple distinct Java types can be used to represent values of the same semantic guest language
 * type. For example our JavaScript implementation uses the Java types <code>int</code> and
 * <code>double</code> to represent the JavaScript numeric type. Whenever a type needs to be checked
 * for the type numeric we need to check the value for each representation type. Defining a
 * specialization with two numeric parameters would normally require us to specify four
 * specializations, for each combination of the representation type. However, in Java, an
 * <code>int</code> type has the interesting property of always being representable with a double
 * value without losing precision. Type systems allows the user to specify such relationships using
 * implicit casts such as the <code>castInt</code> method declared in the example After declaring an
 * implicit cast from <code>int</code> to <code>double</code> we can specify a single specialization
 * with double type guards to implicitly represent all the cases of the JavaScript numeric type.
 * Whenever the DSL implementation needs to cast a value from <code>int</code> to
 * <code>double</code> then the implicit cast method is called. Specializations with
 * <code>int</code> type guards can be declared before specializations with <code>double</code> type
 * guards. If an int specialization is declared after the <code>double</code> specialization then
 * the <code>int</code> specialization is unreachable due to the implicit cast. The requirements for
 * implicit casts can vary between guest languages. Therefore, no implicit cast is enabled by
 * default or if no type system is referenced.
 * <p>
 * If multiple implicit casts are declared for a single target type then their types are checked in
 * declaration order. Languages implementations are encouraged to optimize their implicit cast
 * declaration order by sorting them starting with the most frequently used type.
 *
 * @see TypeCast
 * @see TypeCheck
 * @see ImplicitCast
 * @since 0.8 or earlier
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface TypeSystem {

    /**
     * The list of types as child elements of the {@link TypeSystem}. Each precedes its super type.
     *
     * @since 0.8 or earlier
     */
    Class<?>[] value() default {};

}
