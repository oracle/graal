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

/**
 * A <code>JSObject</code> is a Java-side wrapper for JavaScript objects. The purpose of this class
 * is that a JavaScript object is not normally an instance of any Java class, and it therefore
 * cannot be represented as a data-type in Java programs. When the JavaScript code (invoked by the
 * method annotated with the {@link JS} annotation) returns a JavaScript object, that object gets
 * wrapped into a <code>JSObject</code> instance. The <code>JSObject</code> allows the Java code to
 * access the fields of the underlying JavaScript object using the <code>get</code> and
 * <code>set</code> methods.
 *
 * The Java {@link JSObject} instance that corresponds to the JavaScript object is called a <i>Java
 * mirror</i>. Vice versa, the JavaScript instance is a <i>JavaScript mirror</i> for that
 * {@link JSObject} instance.
 *
 *
 * <h3>Anonymous JavaScript objects</h3>
 *
 * Here are a few examples of creating and modifying anonymous JavaScript objects:
 *
 * <pre>
 * &#64;JS("return {x: x, y: y};")
 * public static native JSObject createPair(double x, double y);
 *
 * JSObject pair = createPair(3.2, 4.8);
 *
 * System.out.println(pair.get("x"));
 * System.out.println(pair.get("y"));
 * </pre>
 *
 * In this example, using the {@code JSObject} methods, the user can access the <code>x</code> and
 * <code>y</code> fields.
 *
 * <pre>
 * pair.set("x", 5.4);
 * System.out.println(pair.get("x"));
 * </pre>
 *
 * The code above sets a new value for the <code>x</code> field, and then prints the new value.
 *
 *
 * <h3>Anonymous JavaScript functions</h3>
 *
 * A {@code JSObject} can be a Java-side wrapper for a JavaScript {@code Function} object. The
 * JavaScript {@code Function} value can be returned by the JavaScript code of the method annotated
 * with the {@link JS} annotation.
 *
 * The Java program can then call the underlying function by calling the
 * {@link JSObject#call(Object, Object...)} method. If the underlying JavaScript object is not
 * callable, then calling {@code call} will result in an exception.
 *
 * The {@code call} method has the following signature:
 *
 * <pre>
 * public Object call(Object thisArgument, Object... arguments);
 * </pre>
 *
 * The {@code invoke} method has the following signature:
 *
 * <pre>
 * public Object invoke(Object... arguments);
 * </pre>
 *
 * The difference is that the method {@code call} takes an object for specifying {@code this} of the
 * JavaScript function, while {@code invoke} uses the underlying {@code JSObject} as the value of
 * {@code this}.
 *
 * <p>
 * Here is an example of how to use {@code JSObject} as a function:
 * </p>
 *
 * <pre>
 * public static void main(String[] args) {
 *     JSObject function = createFunction();
 *     Object result = function.invoke(JSNumber.of(1), JSNumber.of(2));
 *     System.out.println(result);
 * }
 *
 * &#64;JS("return (a, b) => { return a + b; };")
 * public static native JSObject createFunction();
 * </pre>
 *
 * <p>
 * The {@link JS}-annotated method {@code createFunction} in the preceding example creates a
 * function in JavaScript that adds together two numbers. The call to {@code createFunction} returns
 * a {@code JSObject} object, upon which users can call the {@code invoke} method to execute the
 * underlying JavaScript function. The result {@code JavaScript&lt;number; 3&lt;} is then printed.
 * </p>
 *
 *
 * <h3>Declaring JavaScript classes in Java</h3>
 *
 * The second purpose of {@link JSObject} is to declare JavaScript classes as classes in Java code,
 * in a way that makes the Java objects look-and-feel like native JavaScript objects. Users should
 * subclass the {@link JSObject} class when they need to define a JavaScript class whose fields and
 * methods can be accessed conveniently from Java, without the {@link JSObject#get(Object)} and
 * {@link JSObject#set(Object, Object)} methods.
 *
 * Directly exposing a Java object to JavaScript code means that the JavaScript code is able to
 * manipulate the data within the object (e.g. mutate fields, add new fields, or redefine existing
 * fields), which is not allowed by default for regular Java classes. Extending {@link JSObject}
 * furthermore allows the JavaScript code to instantiate objects of the {@link JSObject} subclass.
 * One of the use-cases for these functionalities are JavaScript frameworks that redefine properties
 * of JavaScript objects with custom getters and setters, with the goal of enabling data-binding or
 * reactive updates.
 *
 * In a subclass of {@link JSObject}, every JavaScript property directly corresponds to the Java
 * field of the same name. Consequently, all these properties point to native JavaScript values
 * rather than Java values, so bridge methods are generated that are called for each property access
 * and that convert native JavaScript values to their Java counterparts. The conversion rules are
 * the same as in a {@link JS}-annotated method. Furthermore, note that JavaScript code can violate
 * the Java type-safety by storing into some property a value that is not compatible with the
 * corresponding Java field. For this reason, the bridge methods also generate check-casts on every
 * access: if the JavaScript property that corresponds to the Java field does not contain a
 * compatible value, a {@link ClassCastException} is thrown.
 *
 * There are several restrictions imposed on {@link JSObject} subclasses:
 * <ul>
 * <li>Only public and protected fields are allowed to ensure encapsulation.</li>
 * <li>Instance fields must not be {@code final}. This restriction ensures that JavaScript code
 * cannot inadvertently change the property that corresponds to a final field.</li>
 * </ul>
 *
 * <b>Example:</b> consider the following <code>JSObject</code> subclass:
 *
 * <pre>
 * public class Point extends JSObject {
 *     protected double x;
 *     protected double y;
 *
 *     public Point(double x, double y) {
 *         this.x = x;
 *         this.y = y;
 *     }
 *
 *     public double absolute() {
 *         return Math.sqrt(x * x + y * y);
 *     }
 * }
 * </pre>
 *
 * The preceding Java class is effectively translated to the corresponding JavaScript class:
 *
 * <pre>
 * class Point {
 *     constructor(x, y){
 *         this.x=x;
 *         this.y=y;
 *     }
 *
 *     absolute() {
 *         // Java code that computes sqrt(x * x + y * y);
 *     }
 * }
 * </pre>
 *
 * The {@code Point} class can be used from Java as if it were a normal Java class:
 *
 * <pre>
 * Point p = new Point(0.3, 0.4);
 * System.out.println(p.x);
 * System.out.println(p.y);
 * System.out.println(p.absolute());
 * </pre>
 *
 * All accesses to the fields {@code x} and {@code y} are rewritten to accesses on the corresponding
 * JavaScript properties. JavaScript code may assign values to these properties that violate the
 * type of the corresponding Java fields, but a subsequent Java read of such a field will result in
 * a {@link ClassCastException}.
 *
 * <pre>
 * &#64;JS("p.x = s;")
 * public static native void corrupt(Point p, String s);
 *
 * corrupt(p, "whoops");
 * System.out.println(p.x); // Throws a ClassCastException.
 * </pre>
 *
 *
 * <h3>Passing {@code JSObject} subclasses between JavaScript and Java</h3>
 *
 * When an object of the {@link JSObject} subclass is passed from Java to JavaScript code using the
 * {@link JS} annotation, the object is converted from its Java representation to its JavaScript
 * representation. Changes in the JavaScript representation are reflected in the Java representation
 * and vice-versa.
 *
 * <b>Example:</b> the following code resets the {@code Point} object in JavaScript by calling the
 * {@code reset} method, and then prints {@code 0, 0}:
 *
 * <pre>
 * &#64;JS("p.x = x; p.y = y;")
 * public static native void reset(Point p, double x, double y);
 *
 * Point p0 = new Point(1.5, 2.5);
 * reset(p0, 0.0, 0.0);
 * System.out.println(p0.x + ", " + p0.y);
 * </pre>
 *
 * A {@link Class} object that represents {@link JSObject} can also be passed to JavaScript code.
 * The {@link Class} object is wrapped in a proxy, which can be used inside a {@code new} expression
 * to instantiate the object of the corresponding class from JavaScript.
 *
 * <b>Example:</b> the following code creates a {@code Point} object in JavaScript:
 *
 * <pre>
 * &#64;JS("return new pointType(x, y);")
 * public static Point create(Class<Point> pointType, double x, double y);
 *
 * Point p1 = create(Point.class, 1.25, 0.5);
 * System.out.println(p1.x + ", " + p1.y);
 * </pre>
 *
 * Note that creating an object in JavaScript and passing it to Java several times does not
 * guarantee that the same mirror instance is returned each time -- each time a JavaScript object
 * becomes a Java mirror, a different instance of the mirror may be returned.
 *
 *
 * <h3>Importing existing JavaScript classes</h3>
 *
 * The {@link JSObject} class allows access to properties of any JavaScript object using the
 * {@link JSObject#get(Object)} and {@link JSObject#set(Object, Object)} methods. In situations
 * where the programmer knows the relevant properties of a JavaScript object (for example, when
 * there is a class corresponding to the JavaScript object), the object's "blueprint" can be
 * "imported" to Java. To do this, the user declares a {@link JSObject} subclass that serves as a
 * facade to the JavaScript object. This subclass must be annotated with the {@link JS.Import}
 * annotation.
 *
 * The name of the declared class Java must match the name of the JavaScript class that is being
 * imported. The package name of the Java class is not taken into account -- the same JavaScript
 * class can be imported multiple times from within separate packages.
 *
 * The exposed JavaScript fields must be declared as {@code protected} or {@code public}. The
 * constructor parameters must match those of the JavaScript class, and its body must be empty.
 *
 * Here is an example of a class declared in JavaScript:
 *
 * <pre>
 * class Rectangle {
 *     constructor(width, height) {
 *         this.width = width;
 *         this.height = height;
 *     }
 * }
 * </pre>
 *
 * To import this class in Java code, we need the following declaration in Java:
 *
 * <pre>
 * &#64;JS.Import
 * public class Rectangle extends JSObject {
 *     protected int width;
 *     protected int height;
 *
 *     public Rectangle(int width, int height) {
 *     }
 * }
 * </pre>
 *
 * The fields declared in the {@code Rectangle} class are directly mapped to the properties of the
 * underlying JavaScript object. If the type of the property of the underlying JavaScript object
 * does not match the type of the field declared in Java, then a field-read in Java will throw a
 * {@link ClassCastException}.
 *
 * The {@code Rectangle} class can be instantiated from Java as follows:
 *
 * <pre>
 * Rectangle r = new Rectangle(640, 480);
 * System.out.println(r.width + "x" + r.height);
 * </pre>
 *
 * A JavaScript object whose {@code constructor} property matches the imported JavaScript class can
 * be converted to the declared Java class when the JavaScript code passes a value to Java. Here is
 * a code example that creates the {@code Rectangle} object in JavaScript, and passes it to Java:
 *
 * <pre>
 * &#64;JS("return new Rectangle(width, height);")
 * Rectangle createRectangle(int width, int height);
 * </pre>
 *
 * Another way to convert a JavaScript object to a Java facade is to call the
 * {@link JSObject#as(Class)} method to cast the {@link JSObject} instance to the proper subtype.
 *
 *
 * <h3>Exporting Java classes to JavaScript</h3>
 *
 * The users can annotate the exported classes with the {@link JS.Export} annotation to denote that
 * the {@link JSObject} subclass should be made available to JavaScript code. Exported classes can
 * be accessed using the JavaScript VM-instance API, using the `exports` property.
 *
 * <b>Example:</b> the following code exports a Java class:
 *
 * <pre>
 * package org.example;
 *
 * &#64;JS.Export
 * public class Randomizer extends JSObject {
 *     private Random rng = new Random(719513L);
 *
 *     public byte[] randomBytes(int length) {
 *         byte[] bytes = new byte[length];
 *         rng.nextBytes(bytes);
 *         return bytes;
 *     }
 * }
 * </pre>
 *
 * The exported class can then be used from JavaScript code as follows:
 *
 * <pre>
 * GraalVM.run([]).then(vm => {
 *   const Randomizer = vm.exports.org.example.Randomizer;
 *   const r = new Randomizer();
 *   const bytes = r.randomBytes(1024);
 * });
 * </pre>
 */
public class JSObject extends JSValue {

    /**
     * Creates an empty JavaScript object.
     *
     * @return an empty JavaScript object
     */
    @JS("return conversion.createAnonymousJavaScriptObject();")
    public static native JSObject create();

    @JS("return obj0 === obj1;")
    private static native JSBoolean referenceEquals(JSObject obj0, JSObject obj1);

    protected JSObject() {
    }

    @JS("return typeof this;")
    public native JSString typeofString();

    @Override
    public String typeof() {
        return typeofString().asString();
    }

    @Override
    @JS("return conversion.toProxy(toJavaString(this.toString()));")
    protected native String stringValue();

    /**
     * Returns the value of the key passed as the argument in the JavaScript object.
     *
     * @param key the object under which the returned value is placed in the JavaScript object
     * @return the value of the key passed as the argument in the JavaScript object
     */
    @JS("return this[key];")
    public native Object get(Object key);

    /**
     * Sets the value of the key passed as the argument in the JavaScript object.
     *
     * @param key the object under which the value should be placed in the JavaScript object
     * @param newValue the value that should be placed under the given key in the JavaScript object
     */
    @JS("this[key] = newValue;")
    public native void set(Object key, Object newValue);

    /**
     * Returns the array of property keys for this object.
     *
     * @return an array of all the keys that can be used with {@code get} on this object
     */
    @JS("return Object.keys(this);")
    public native Object keys();

    /**
     * Invoke the underlying JavaScript function, if this object is callable.
     *
     * @param args The array of Java arguments, which is converted to JavaScript and passed to the
     *            underlying JavaScript function
     * @return The result of the JavaScript function, converted to the corresponding Java value
     */
    @JS("return this.apply(this, conversion.extractJavaScriptArray(args[runtime.symbol.javaNative]));")
    public native Object invoke(Object... args);

    /**
     * Calls the underlying JavaScript function with the given value for the binding of {@code this}
     * in the function, if this object is callable.
     *
     * @param thisArg The value for the binding of {@code this} inside the JavaScript function
     * @param args The array of Java arguments, which is converted to JavaScript and passed to the
     *            underlying JavaScript function
     * @return The result of the JavaScript function, converted to the corresponding Java value
     */
    @JS("return this.apply(thisArg, conversion.extractJavaScriptArray(args[runtime.symbol.javaNative]));")
    public native Object call(Object thisArg, Object... args);

    private ClassCastException classCastException(String targetType) {
        return new ClassCastException(this + " cannot be coerced to '" + targetType + "'.");
    }

    @JS("if (this.constructor === Uint8Array) { this.hub = booleanArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native boolean[] extractBooleanArray();

    @Override
    public boolean[] asBooleanArray() {
        boolean[] array = extractBooleanArray();
        if (array != null) {
            return array;
        }
        throw classCastException("boolean[]");
    }

    @JS("if (this.constructor === Int8Array) { this.hub = byteArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native byte[] extractByteArray();

    @Override
    public byte[] asByteArray() {
        byte[] array = extractByteArray();
        if (array != null) {
            return array;
        }
        throw classCastException("byte[]");
    }

    @JS("if (this.constructor === Int16Array) { this.hub = shortArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native short[] extractShortArray();

    @Override
    public short[] asShortArray() {
        short[] array = extractShortArray();
        if (array != null) {
            return array;
        }
        throw classCastException("short[]");
    }

    @JS("if (this.constructor === Uint16Array) { this.hub = charArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native char[] extractCharArray();

    @Override
    public char[] asCharArray() {
        char[] array = extractCharArray();
        if (array != null) {
            return array;
        }
        throw classCastException("char[]");
    }

    @JS("if (this.constructor === Int32Array) { this.hub = intArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native int[] extractIntArray();

    @Override
    public int[] asIntArray() {
        int[] array = extractIntArray();
        if (array != null) {
            return array;
        }
        throw classCastException("int[]");
    }

    @JS("if (this.constructor === Float32Array) { this.hub = floatArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native float[] extractFloatArray();

    @Override
    public float[] asFloatArray() {
        float[] array = extractFloatArray();
        if (array != null) {
            return array;
        }
        throw classCastException("float[]");
    }

    @JS("if (this.constructor === BigInt64Array) { this.hub = longArrayHub; initComponentView(this); return conversion.toProxy(this); } else { return null; };")
    private native long[] extractLongArray();

    @Override
    public long[] asLongArray() {
        long[] array = extractLongArray();
        if (array != null) {
            return array;
        }
        throw classCastException("long[]");
    }

    @JS("if (this.constructor === Float64Array) { this.hub = doubleArrayHub; return conversion.toProxy(this); } else { return null; };")
    private native double[] extractDoubleArray();

    @Override
    public double[] asDoubleArray() {
        double[] array = extractDoubleArray();
        if (array != null) {
            return array;
        }
        throw classCastException("double[]");
    }

    @JS("return conversion.tryExtractFacadeClass(this, cls);")
    private native <T> T extractFacadeClass(Class<?> cls);

    @SuppressWarnings("unchecked")
    @Override
    public <T> T as(Class<T> cls) {
        Object facade = extractFacadeClass(cls);
        if (facade != null) {
            return (T) facade;
        }
        return super.as(cls);
    }

    public boolean equalsJavaScript(JSObject that) {
        return referenceEquals(this, that).asBoolean();
    }
}
