/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.functionintrinsics;

import java.lang.reflect.Constructor;
import java.math.BigInteger;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBigInt;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSError;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSSymbol;
import org.graalvm.webimage.api.JSUndefined;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.JSExceptionSupport;
import com.oracle.svm.webimage.JSNameGenerator;
import com.oracle.svm.webimage.annotation.JSRawCall;
import com.oracle.svm.webimage.annotation.WebImage;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.JavaKind;

/**
 * Contains static methods that are responsible for converting values between the JS and Java
 * representation to support the {@link JS} annotation.
 * <p>
 * Backend specific functionality is implemented in subclasses and the static methods delegate to
 * the image singleton.
 */
public abstract class JSConversion {

    static {
        JSNameGenerator.registerReservedSymbols("$as", "$vm");
    }

    @Fold
    static JSConversion instance() {
        return ImageSingletons.lookup(JSConversion.class);
    }

    @WasmExport(value = "class.getkindordinal", comment = "Ordinal of the JavaKind the given class represents")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static int getKindOrdinal(Class<?> clazz) {
        if (clazz == Boolean.TYPE) {
            return JavaKind.Boolean.ordinal();
        }
        if (clazz == Byte.TYPE) {
            return JavaKind.Byte.ordinal();
        }
        if (clazz == Short.TYPE) {
            return JavaKind.Short.ordinal();
        }
        if (clazz == Character.TYPE) {
            return JavaKind.Char.ordinal();
        }
        if (clazz == Integer.TYPE) {
            return JavaKind.Int.ordinal();
        }
        if (clazz == Long.TYPE) {
            return JavaKind.Long.ordinal();
        }
        if (clazz == Float.TYPE) {
            return JavaKind.Float.ordinal();
        }
        if (clazz == Double.TYPE) {
            return JavaKind.Double.ordinal();
        }
        if (clazz == Void.TYPE) {
            return JavaKind.Void.ordinal();
        }

        return JavaKind.Object.ordinal();
    }

    // Creation of JSValue objects

    /**
     * Wraps a JavaScript value into a Java {@link JSValue} object.
     * <p>
     * Creates an instance of the given subclass of {@link JSValue} using the default no-argument
     * constructor and associates it with the given JavaScript value.
     * <p>
     * Uses reflection since the constructor is package-private to prevent user code from it because
     * {@link JSValue} and its subclasses are exposed as part of the API.
     *
     * @param clazz The subclass of {@link JSValue} that should be instantiated to hold the value.
     * @param jsNativeObject Not a real Java object. Instead, this is a JavaScript value that should
     *            be associated with the Java object using
     *            {@link #setJavaScriptNative(JSValue, Object)}.
     */
    private static <T extends JSValue> T createJSValue(Class<T> clazz, Object jsNativeObject) {
        try {
            Constructor<T> defaultCtor = clazz.getDeclaredConstructor();
            defaultCtor.setAccessible(true);
            T instance = defaultCtor.newInstance();
            setJavaScriptNative(instance, jsNativeObject);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere("Failed to create " + clazz.getName() + " object", e);
        }
    }

    /**
     * Gets the Java singleton object that represents the JavaScript undefined value.
     */
    @WasmExport(value = "convert.create.jsundefined")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static JSUndefined createJSUndefined() {
        return JSValue.undefined();
    }

    @WasmExport(value = "convert.create.jsboolean")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static JSBoolean createJSBoolean(Object jsBoolean) {
        return createJSValue(JSBoolean.class, jsBoolean);
    }

    @WasmExport(value = "convert.create.jsnumber")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static JSNumber createJSNumber(Object jsNumber) {
        return createJSValue(JSNumber.class, jsNumber);
    }

    @WasmExport(value = "convert.create.jsbigint")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static JSBigInt createJSBigInt(Object jsBigInt) {
        return createJSValue(JSBigInt.class, jsBigInt);
    }

    @WasmExport(value = "convert.create.jsstring")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static JSString createJSString(Object jsString) {
        return createJSValue(JSString.class, jsString);
    }

    @WasmExport(value = "convert.create.jssymbol")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static JSSymbol createJSSymbol(Object jsSymbol) {
        return createJSValue(JSSymbol.class, jsSymbol);
    }

    @WasmExport(value = "convert.create.jsobject")
    public static JSObject createJSObject(Object jsObject) {
        return createJSValue(JSObject.class, jsObject);
    }

    // Extractors: Java-to-JavaScript converters.

    /**
     * Helper method used to create a JavaScript {@code number} from a boxed {@code double}.
     *
     * Note: the {@code double} primitive type in Java corresponds directly to {@code number} inside
     * the generated JavaScript code. Raw JavaScript callers hence need no further conversions.
     */
    @WasmExport("unbox.double")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static double extractJavaScriptNumber(Double d) {
        return d;
    }

    /**
     * Helper method used to create a JavaScript {@code string} from a Java {@code String}.
     *
     * Note: this method returns a raw JavaScript string.
     */
    @JSRawCall
    @JS("return conversion.extractJavaScriptString(s);")
    public static native Object extractJavaScriptString(String s);

    /**
     * Associates the given Java object with the given JS value.
     */
    public abstract void setJavaScriptNativeImpl(JSValue self, Object jsNative);

    public static void setJavaScriptNative(JSValue self, Object jsNative) {
        instance().setJavaScriptNativeImpl(self, jsNative);
    }

    /**
     * Extracts the JS-native value, which represents the JavaScript counterpart of the specified
     * Java object, and can be passed to JavaScript code.
     */
    public abstract Object extractJavaScriptNativeImpl(JSValue self);

    public static Object extractJavaScriptNative(JSValue self) {
        return instance().extractJavaScriptNativeImpl(self);
    }

    @JSRawCall
    @JS("return undefined;")
    private static native Object javaScriptUndefined();

    @JSRawCall
    @JS("return conversion.toProxy(self);")
    public static native Object extractJavaScriptProxy(Object self);

    /**
     * Extracts the value from a proxy object created by {@link #extractJavaScriptProxy(Object)} (or
     * {@code conversion.toProxy}) in JavaScript.
     *
     * @return The underlying Java object or {@code null} if the object is not a proxy.
     */
    @JSRawCall
    @JS("const javaNative = proxy[runtime.symbol.javaNative]; return javaNative === undefined ? null : javaNative;")
    public static native Object unproxy(Object proxy);

    // Constructors: JavaScript-to-Java converters.

    /**
     * Helper method used to create a boxed {@code boolean} from a JavaScript {@code boolean}.
     */
    @WasmExport("box.boolean")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Boolean createJavaBoolean(boolean b) {
        return b;
    }

    /**
     * Helper method used to create a boxed byte from a JavaScript number.
     */
    @WasmExport("box.byte")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Byte createJavaByte(byte b) {
        return b;
    }

    /**
     * Helper method used to create a boxed short from a JavaScript number.
     */
    @WasmExport("box.short")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Short createJavaShort(short s) {
        return s;
    }

    /**
     * Helper method used to create a boxed char from a JavaScript number.
     */
    @WasmExport("box.char")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Character createJavaCharacter(char c) {
        return c;
    }

    /**
     * Helper method used to create a boxed int from a JavaScript number.
     */
    @WasmExport("box.int")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Integer createJavaInteger(int i) {
        return i;
    }

    /**
     * Helper method used to create a boxed float from a JavaScript number.
     */
    @WasmExport("box.float")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Float createJavaFloat(float f) {
        return f;
    }

    /**
     * Helper method used to create a boxed long from a JavaScript number.
     */
    @WasmExport("box.long")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Long createJavaLong(long l) {
        return l;
    }

    /**
     * Helper method used to create a boxed double from a JavaScript number.
     */
    @WasmExport("box.double")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Double createJavaDouble(double d) {
        return d;
    }

    // Java-to-JavaScript coercions.

    /**
     * Coerces a Java {@link Boolean} object to a JavaScript boolean.
     */
    @WasmExport("convert.coerce.boolean")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static boolean coerceToJavaScriptBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return ((Boolean) obj).booleanValue();
        }
        throw throwClassCastException(obj, "boolean");
    }

    /**
     * Coerces a Java non-{@link Boolean} boxed primitive to a JavaScript number.
     */
    @WasmExport("convert.coerce.number")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static double coerceToJavaScriptNumber(Object obj) {
        if (obj instanceof Double || obj instanceof Long || obj instanceof Integer || obj instanceof Float || obj instanceof Short || obj instanceof Byte) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof Character) {
            return (Character) obj;
        }
        throw throwClassCastException(obj, "number");
    }

    /**
     * Coerces a Java non-{@link Boolean} boxed primitive or a {@link BigInteger} to a JavaScript
     * bigint.
     *
     * Note: the return value is a JavaScript string, which the JavaScript caller should convert to
     * a bigint value.
     */
    @WebImage.OmitClosureReturnType
    @WasmExport("convert.coerce.bigint")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Object coerceToJavaScriptBigInt(Object obj) {
        if (obj instanceof Double || obj instanceof Long || obj instanceof Integer || obj instanceof Float || obj instanceof Short || obj instanceof Byte) {
            String s = ((Long) ((Number) obj).longValue()).toString();
            return extractJavaScriptString(s);
        }
        if (obj instanceof Character) {
            return extractJavaScriptString(Long.valueOf((Character) obj).toString());
        }
        if (obj instanceof BigInteger) {
            return extractJavaScriptString(obj.toString());
        }
        throw throwClassCastException(obj, "number");
    }

    /**
     * Coerces a Java {@link String} to a JavaScript string.
     */
    @WasmExport("convert.coerce.string")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Object coerceToJavaScriptString(Object obj) {
        if (obj instanceof String str) {
            return extractJavaScriptString(str);
        }
        throw throwClassCastException(obj, "string");
    }

    /**
     * Coerces a Java array to the corresponding typed array.
     *
     * All other Java classes cannot be coerced. This method must not be called with {@link JSValue}
     * subclasses -- only Java objects that become Java Proxies are valid arguments.
     */
    @WasmExport("convert.coerce.object")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static Object coerceToJavaScriptObject(Object obj) {
        if (obj instanceof boolean[] || obj instanceof byte[] || obj instanceof char[] || obj instanceof short[] || obj instanceof int[] || obj instanceof float[] || obj instanceof long[] ||
                        obj instanceof double[]) {
            return obj;
        }
        throw throwClassCastException(obj, "object");
    }

    /**
     * Coerces a Java {@code boolean[]} object to a JavaScript Uint8Array.
     */
    public static Object coerceToJavaScriptUint8Array(Object obj) {
        if (obj instanceof boolean[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Uint8Array");
    }

    /**
     * Coerces a Java {@code byte[]} object to a JavaScript Int8Array.
     */
    public static Object coerceToJavaScriptInt8Array(Object obj) {
        if (obj instanceof byte[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Int8Array");
    }

    /**
     * Coerces a Java {@code char[]} object to a JavaScript Uint16Array.
     */
    public static Object coerceToJavaScriptUint16Array(Object obj) {
        if (obj instanceof char[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Uint16Array");
    }

    /**
     * Coerces a Java {@code short[]} object to a JavaScript Int16Array.
     */
    public static Object coerceToJavaScriptInt16Array(Object obj) {
        if (obj instanceof short[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Int16Array");
    }

    /**
     * Coerces a Java {@code int[]} object to a JavaScript Int32Array.
     */
    public static Object coerceToJavaScriptInt32Array(Object obj) {
        if (obj instanceof int[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Int32Array");
    }

    /**
     * Coerces a Java {@code float[]} object to a JavaScript Float32Array.
     */
    public static Object coerceToJavaScriptFloat32Array(Object obj) {
        if (obj instanceof float[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Float32Array");
    }

    /**
     * Coerces a Java {@code long[]} object to a JavaScript BigInt64Array.
     */
    public static Object coerceToJavaScriptBigInt64Array(Object obj) {
        if (obj instanceof long[]) {
            return obj;
        }
        throw throwClassCastException(obj, "BigInt64Array");
    }

    public static char[] createCharArray(int length) {
        return new char[length];
    }

    /**
     * Coerces a Java {@code double[]} object to a JavaScript Float64Array.
     */
    public static Object coerceToJavaScriptFloat64Array(Object obj) {
        if (obj instanceof double[]) {
            return obj;
        }
        throw throwClassCastException(obj, "Float64Array");
    }

    // Various other helper methods.

    /**
     * Returns the length of the specified array.
     *
     * This method is intended to be called from JavaScript.
     */
    public static int lengthOf(Object[] array) {
        return array.length;
    }

    /**
     * Returns the hub of the specified object.
     */
    public static Class<?> hubOf(Object x) {
        return x.getClass();
    }

    /**
     * Checks if the first class is the same as or the supertype of the second class.
     */
    public static boolean isSupertype(Class<?> x, Class<?> y) {
        return x.isAssignableFrom(y);
    }

    /**
     * Checks if the object is a {@link String}.
     */
    @WasmExport("convert.isjavalangstring")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static boolean isJavaLangString(Object x) {
        return x instanceof String;
    }

    @JSRawCall
    @JS("return conversion.isInternalJavaObject(obj) ? obj : toJavaString(obj.toString());")
    private static native Object asJavaObjectOrString(Object obj);

    /**
     * Helper method that throws a Java {@link ClassCastException}.
     *
     * @param obj Java or JavaScript value that was supposed to be converted
     * @param tpe Target Java class for the conversion
     */
    public static ClassCastException throwClassCastExceptionForClass(Object obj, Class<?> tpe) {
        throw new ClassCastException("'" + asJavaObjectOrString(obj) + "' cannot be coerced to '" + tpe.getSimpleName() + "'.");
    }

    /**
     * Helper method that throws a Java {@link ClassCastException}.
     *
     * @param obj Java or JavaScript value that was supposed to be converted
     * @param tpe Target JavaScript type for the conversion
     */
    @WasmExport(value = "convert.throwClassCastException")
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    public static ClassCastException throwClassCastException(Object obj, String tpe) {
        throw new ClassCastException("'" + asJavaObjectOrString(obj) + "' cannot be coerced to a JavaScript '" + tpe + "'.");
    }

    /**
     * Converts a Java value to the corresponding JavaScript value.
     *
     * The conversion table is documented in the {@link JS} annotation.
     *
     * This method will not perform any type coercions (i.e. no implicit conversions).
     *
     * @param x Java value to convert
     * @return The corresponding JavaScript value
     */
    @WasmExport(value = "convert.javatojavascript")
    public static Object javaToJavaScript(Object x) {
        // Step 1: check the null value, which is mapped directly to null in JavaScript.
        if (x == null) {
            return null;
        }

        // Step 2: check if the value is the JSUndefined singleton.
        if (x == JSValue.undefined()) {
            return javaScriptUndefined();
        }

        // Step 3: check if a JavaScript value is associated with this object.
        // This covers:
        // - all JSValue subclasses that represent non-singleton primitive data types
        // - all subclasses of JSObject
        if (x instanceof JSValue jsValue) {
            Object javaScriptValue = extractJavaScriptNative(jsValue);
            if (javaScriptValue != null) {
                return javaScriptValue;
            }
        }

        // Step 4: for all other Java classes, create an Web Image Proxy object.
        return extractJavaScriptProxy(x);
    }

    /**
     * Converts a JavaScript value to the corresponding Java value.
     *
     * The conversion table is documented in the {@link JS} annotation.
     *
     * The implementation of this method calls the JavaScript method with the same name.
     *
     * @param x The JavaScript value to convert
     * @return The corresponding Java value
     */
    @JSRawCall
    @JS("return conversion.javaScriptToJava(x);")
    public static native Object javaScriptToJava(Object x);

    /**
     * Coerces a regular Java object to its corresponding {@code JSValue} representation, if
     * possible.
     *
     * This method does the implicit coercions that are performed on Java arguments when calling
     * {@link JS}-annotated methods.
     *
     * @param x The Java value
     * @return The corresponding Java value that is the "closest" in JavaScript
     */
    public static Object coerceJavaToJavaScript(Object x) {
        // Step 1: check if the object is null.
        if (x == null) {
            return null;
        }

        // Step 2: check if the object is already a JavaScript value.
        if (x instanceof JSValue) {
            return x;
        }

        // Step 3: check if the object is a convertible numeric type.
        if (x instanceof Number num) {
            if (x instanceof BigInteger bigInt) {
                return JSBigInt.of(bigInt);
            }

            if (x instanceof Long l) {
                return JSBigInt.of(l);
            }

            if (x instanceof Integer || x instanceof Float || x instanceof Double || x instanceof Byte || x instanceof Short) {
                return JSNumber.of(num.doubleValue());
            }
        }

        // Step 4: check if the object is a String.
        if (x instanceof String str) {
            return JSString.of(str);
        }

        // Step 5: check if the object is a Boolean.
        if (x instanceof Boolean bool) {
            return JSBoolean.of(bool);
        }

        // Step 6: check if the object is a character.
        if (x instanceof Character c) {
            return JSNumber.of(c);
        }

        // Step 7: check if the object is a primitive array.
        Class<?> cls = x.getClass();
        if (cls.isArray() && cls.getComponentType().isPrimitive()) {
            // Note: in this case, x is also a valid JavaScript object, because Java primitive
            // arrays are encoded as JavaScript typed arrays.
            // TODO GR-60603 This is not the case for the WasmGC backend
            return createJSObject(x);
        }

        // No coercion rule applies, return the original object.
        return x;
    }

    /**
     * Coerces a Java object (which may be a {@code JSValue}) to the {@code target} class.
     *
     * {@code JSValue} objects are coerced to the specified class. Other values are just
     * type-checked against the specified class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T coerceJavaScriptToJava(Object x, Class<T> target) {
        if (x == null) {
            return null;
        } else if (x instanceof JSValue jsValue) {
            return jsValue.as(target);
        } else if (target.isAssignableFrom(x.getClass())) {
            return (T) x;
        } else {
            throw throwClassCastExceptionForClass(x, target);
        }
    }

    /**
     * Ensures that objects that are thrown in {@link JS}-annotated methods are a subclass of
     * {@link Throwable}. If a {@link Throwable} exception is thrown, it gets simply rethrown.
     * Otherwise, the exception object is converted to Java according ot the conversion rules
     * specified by {@link JS} and wrapped into a {@link JSError}.
     *
     * @param excp thrown object. Due to JavaScript semantics this can be an arbitrary type.
     * @throws Throwable the original {@link Throwable} or {@link JSError} which warps the converted
     *             thrown JavaScript object
     */
    public static void handleJSError(Object excp) throws Throwable {
        if (JSExceptionSupport.isThrowable(excp)) {
            // this handles thrown ClassCastExceptions from JSConversion
            throw (Throwable) excp;
        } else {
            Object obj = javaScriptToJava(excp);

            // The object may have been behind a proxy
            if (obj instanceof Throwable t) {
                throw t;
            }

            throw new JSError(obj);
        }
    }
}
