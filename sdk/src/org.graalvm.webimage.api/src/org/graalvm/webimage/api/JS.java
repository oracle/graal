/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.webimage.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Replaces the body of a Java method with JavaScript code. The JavaScript code is specified by the
 * {@link #value()} of this annotation. A method with this annotation can be made {@code native},
 * since its Java body is not used at run-time (this is the convention).
 *
 * <b>Examples:</b>
 *
 * <pre>
 * {@code @JS("console.log('User message: '.concat(message));")}
 * public static native void reportToUser(String message);
 *
 * {@code @JS("return obj.hashCode();")}
 * public static native Integer hashCode(Object obj);
 * </pre>
 *
 * The data types in JavaScript are different from the data types in Java, so this annotation
 * defines a mapping between the Java and the JavaScript data types. The mapping is 1:1, meaning
 * that each Java data type maps to exactly one JavaScript, and vice versa. This mapping determines
 * how Java arguments are converted to JavaScript, and how JavaScript return values are converted
 * back to Java values.
 *
 * The following Java and JavaScript type pairs correspond to each other:
 *
 * <ul>
 * <li>Java {@code null} value and the JavaScript {@code null} value.</li>
 * <li>Java {@link JSUndefined} and JavaScript {@code Undefined}.</li>
 * <li>Java {@link JSBoolean} and JavaScript {@code Boolean}.</li>
 * <li>Java {@link JSNumber} and JavaScript {@code Number}.</li>
 * <li>Java {@link JSBigInt} and JavaScript {@code BigInt}.</li>
 * <li>Java {@link JSString} and JavaScript {@code String}.</li>
 * <li>Java {@link JSSymbol} and JavaScript {@code Symbol}.</li>
 * <li>Java {@link JSObject} and JavaScript {@code Object}.</li>
 * <li>Any other Java class and the corresponding JavaScript {@code Proxy} for that class.</li>
 * </ul>
 *
 * The Java class {@link JSValue} is the common supertype of {@link JSUndefined}, {@link JSBoolean},
 * {@link JSNumber}, {@link JSBigInt}, {@link JSString}, {@link JSSymbol} and {@link JSObject}. It
 * defined conversion methods such as {@link JSValue#asInt()} and {@link JSValue#asString()}, which
 * allow converting certain {@link JSValue} objects to corresponding Java objects (for example,
 * {@link JSNumber} can be converted to most Java numeric types).
 *
 * Other Java classes are transformed to JavaScript {@code Proxy} objects, which expose JavaScript
 * keys that correspond to methods of the underlying Java object. Such an object behaves as if it
 * were a regular JavaScript object, but its internal behavior is defined by the corresponding Java
 * code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface JS {
    /**
     * The default arguments string is an empty string used to denote the default value of the
     * <code>args</code> member.
     *
     * It is an error for the user of the <code>JS</code> annotation to specify an empty-string name
     * of an argument, hence this special value is used to denote the defaults.
     */
    String DEFAULT_ARGUMENTS = "";

    /**
     * JavaScript statements that form the body of this method. When returning a value, the
     * {@code return} keyword <strong>must not</strong> be omitted. The JavaScript code can use
     * parameters using their names specified in {@link #args()}. In non-static methods, it can also
     * access the receiver using {@code this} keyword.
     *
     * @return a snippet of JavaScript source code
     */
    String value();

    /**
     * If specified, overrides parameter names seen by the JavaScript code. By default, Web Image
     * will try to infer the original argument names in the method header from the method bytecode.
     * If that is not possible, the argument names must be specified here. The target method must be
     * compiled with <code>javac -parameters</code> in order for the argument names to be available
     * in the bytecode.
     *
     * The {@code this} parameter cannot be renamed.
     *
     * @return the new parameter names (their count must match the number of parameters), or
     *         {@code DEFAULT_ARGUMENTS} if the original names should be used (this is the default)
     */
    String[] args() default {DEFAULT_ARGUMENTS};

    /**
     * When this annotation is used together with the {@link JS} annotation, the arguments and
     * return values of the respective method undergo implicit Java-to-JavaScript and
     * JavaScript-to-Java conversions.
     *
     * When the {@link JS.Coerce} annotation is present, Java-value arguments to
     * {@link JS}-annotated methods are implicitly converted (coerced) to the corresponding
     * JavaScript type (and more broadly, they are implicitly converted when JavaScript code calls a
     * Java method that has the {@link JS.Coerce} annotation, and that method returns a value).
     * These conversions are done in a way such that no information is dropped -- for example, a
     * Java {@code byte} value is coerced to a JavaScript {@code Number} value, which is a wider
     * type.
     *
     * The following Java types are coerced to JavaScript values as follows:
     *
     * <ul>
     * <li>{@code boolean} and {@link java.lang.Boolean} are converted to a JavaScript
     * {@code Boolean}.</li>
     * <li>{@code byte} (and {@link java.lang.Byte}), {@code short} (and {@link java.lang.Short}),
     * {@code char} (and {@link java.lang.Character}), {@code int} (and {@link java.lang.Integer}),
     * {@code float} (and {@link java.lang.Float}), {@code double} (and {@link java.lang.Double}),
     * are converted to a JavaScript {@code Number}.</li>
     * <li>{@code long}, {@link java.lang.Long} and {@link java.math.BigInteger} are converted to a
     * JavaScript {@code BigInt}.</li>
     * <li>{@link java.lang.String} is converted to a JavaScript {@code String}.</li>
     * <li>Any functional-interface object (whose class implements exactly one single abstract
     * method as defined by the {@link java.lang.FunctionalInterface} annotation) is converted to a
     * JavaScript {@code Function}.</li>
     * <li>A primitive array is converted to corresponding JavaScript typed arrays. Concretely, an
     * array of type {@code boolean[]} is converted to a JavaScript {@code Uint8Array}, a
     * {@code byte[]} array to a JavaScript {@code Int8Array}, a {@code short[]} array to a
     * JavaScript {@code Int16Array}, a {@code char[]} array to a JavaScript {@code Uint16Array}, a
     * {@code int[]} array to a JavaScript {@code Int32Array}, a {@code float[]} array to a
     * JavaScript {@code Float32Array}, a {@code long[]} array to a JavaScript
     * {@code BigInt64Array}, and a {@code double[]} array to a JavaScript {@code Float64Array}.
     * </li>
     * <li>All other values are not coerced -- subtypes of the Java {@link JSValue} class are
     * converted to corresponding JavaScript values, and other objects are converted to JavaScript
     * proxies.</li>
     * </ul>
     *
     * When the {@link JS.Coerce} annotation is present, JavaScript return values of
     * {@link JS}-annotated methods, which originate from JavaScript code, are implicitly converted
     * (coerced) to the corresponding Java values (and more broadly, they are implicitly converted
     * when JavaScript code passes JavaScript arguments to a Java method that has {@link JS.Coerce}
     * annotation). The conversion is driven by the type that Java expects. For example, if the
     * return type of a {@link JS}-annotated method is {@code double}, then a JavaScript
     * {@code Number} will be converted to a {@code double}. If the return type is {@code int}, then
     * the JavaScript {@code Number} will be converted to an {@code int}, even though this may
     * truncate the original JavaScript value. However, only certain JavaScript values can be
     * converted to certain Java types -- for example, a JavaScript {@code String} is not converted
     * to a Java {@code int}, and the attempt to return a JavaScript {@code String} from a
     * {@link JS}-annotated method whose return type is {@code int} will throw a
     * {@link ClassCastException}.
     *
     * The following JavaScript types are coerced to the expected Java types as follows:
     *
     * <ul>
     * <li>JavaScript {@code Boolean} can be coerced to a Java {@code boolean} and
     * {@link java.lang.Boolean}.</li>
     * <li>JavaScript {@code Number} and {@code BigInt} can be coerced to a Java {@code byte} (and
     * {@link java.lang.Byte}), {@code short} (and {@link java.lang.Short}), {@code char} (and
     * {@link java.lang.Character}), {@code int} (and {@link java.lang.Integer}), {@code long} (and
     * {@link java.lang.Long}), {@code float} (and {@link java.lang.Float}), and {@code double} (and
     * {@link java.lang.Double}), {@link java.math.BigInteger} and
     * {@link java.math.BigDecimal}.</li>
     * <li>JavaScript {@code String} can be coerced to a Java {@link java.lang.String}.</li>
     * <li>JavaScript typed array can be coerced to a corresponding Java primitive array, if there
     * is one. Concretely, a JavaScript {@code UInt8Array} can be coerced to a {@code boolean[]}. A
     * JavaScript {@code Int8Array} can be coerced to a {@code byte[]} array. A JavaScript
     * {@code Int16Array} can be coerced to a {@code short[]}, a {@code Uint16Array} can be coerced
     * to a {@code char[]}, a {@code Int32Array} can be coerced to a {@code int[]}, a
     * {@code Float32Array} can be coerced to a {@code float[]}, a {@code BigInt64Array} can be
     * coerced to a {@code long[]}, and a {@code Float64Array} can be coerced to a
     * {@code double[]}.</li>
     * <li>JavaScript {@code Object} can be coerced to any Java class that is a subclass of
     * {@link JSObject} if that class <i>conforms</i> to that JavaScript object.</li>
     * <li>All other values are not coerced -- they are converted to the corresponding Java
     * {@link JSValue} class, with the exception of JavaScript {@code Proxy} objects that wrap Java
     * objects (those are converted back to the original Java objects). A mismatch with the
     * user-ascribed type will cause a {@link ClassCastException}.</li>
     * </ul>
     *
     * When an object is mutable, the coercions maintain the property that the state-change in Java
     * is reflected in the corresponding JavaScript object, and vice versa. For immutable objects,
     * the object identity is not preserved when the object is passed from Java to JavaScript and
     * then back again (for example, if Java passes a {@code java.lang.Integer} to JavaScript, and
     * JavaScript returns that object back, the returned integer object may not be reference-equal
     * to the original integer object).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @interface Coerce {
    }

    /**
     * Denotes that the annotated class represents a JavaScript class from the global scope. The
     * annotated class must be a <b>direct</b> subclass of the {@link JSObject} class.
     *
     * For more information, see {@link JSObject} documentation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @interface Import {
        /**
         * Denotes the name of the JavaScript class that is being imported. The default value is an
         * empty string, which means that the name of the JavaScript class corresponds to the name
         * of the Java class.
         *
         * Example usage:
         *
         * <pre>
         * &#64;JS.Import("HTMLDocument")
         * public class HTMLDocumentImpl extends JSObject {
         * }
         * </pre>
         *
         * @return Name of the JavaScript class that the annotated Java class should correspond to.
         */
        String value() default "";
    }

    /**
     * Denotes that the annotated Java class should be exported from the VM class. The annotated
     * class must be a subclass of the {@link JSObject} class.
     *
     * A {@link JSObject} subclass may not be included in the image if the closed-world analysis
     * concludes that the class is not used by the Java code of the application. However, exported
     * {@link JSObject} subclasses are always included in the image, and are accessible from the
     * JavaScript code (see {@link JSObject} documentation).
     *
     * Exported classes should be used with care. Since these classes and all their methods are
     * unconditionally included in the image, this annotation should only be used in cases in which
     * the class is supposed to be <b>instantiated</b> from JavaScript. If the {@link JSObject}
     * subclass is only instantiated in Java and passed to JavaScript, then there is no need to use
     * the {@link JS.Export} annotation. Furthermore, the authors of exported classes should reduce
     * the amount of code that is reachable from the methods of an exported class.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @interface Export {
    }

    /**
     * A snippet of JavaScript code that must be included in the image.
     *
     * The specified JavaScript code is meant to contain JavaScript declarations that are necessary
     * for the annotated Java class to work properly. The JavaScript code runs before any other code
     * in the image.
     *
     * If the class annotated with this annotation is included in the image, then the specified
     * JavaScript code is executed when the image code is loaded. If the analysis concludes that
     * this class should not be included in the image, then specified JavaScript code may also not
     * be included.
     *
     * This annotation can be applied to the same class more than once.
     *
     * See also {@link JS.Code.Include} for a way to include a JavaScript file instead of a
     * JavaScript snippet.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @interface Code {
        String value();

        /**
         * Designates the path to the JavaScript file with the code that must be included in the
         * image. This annotation is similar to {@link JS.Code}, but the content is a path to a
         * JavaScript resource, rather than the JavaScript code itself.
         *
         * The conditions for inclusions in the image are the same as in {@link JS.Code}, but the
         * difference is that if multiple included classes specify the same path, then the
         * respective JavaScript code is included only once (before any of the classes that include
         * that JavaScript file).
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE})
        @Repeatable(Include.Group.class)
        @interface Include {
            String value();

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @interface Group {
                Include[] value();
            }

        }
    }
}
