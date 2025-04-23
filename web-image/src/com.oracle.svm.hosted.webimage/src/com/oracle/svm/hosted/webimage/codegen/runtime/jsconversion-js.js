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

/**
 * JS-backend specific implementation of conversion code.
 *
 * In the JS backend, the Java type hierarchy is modelled as a JS class
 * hierarchy and Java values are represented as instances of those classes.
 * Java long values are represented as instances of the dedicated Long64 class.
 */
class JSConversion extends Conversion {
    setJavaScriptNative(jsValueObject, jsNative) {
        $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["setJavaScriptNative"](
            jsValueObject,
            jsNative
        );
    }

    extractJavaScriptNative(jsValueObject) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["extractJavaScriptNative"](
            jsValueObject
        );
    }

    /*
     * Defines functionality that supports Java-to-JavaScript and JavaScript-to-Java call semantics.
     *
     * This file is included after all Java class definitions are emitted.
     */

    // Java-to-JavaScript conversions

    /**
     * Given a Java boxed Double, creates the corresponding JavaScript number value.
     *
     * Note: Java represents (in the generated code) primitive double values as JavaScript numbers,
     * hence no extra conversion is necessary.
     *
     * @param jldouble The java.lang.Double object
     * @return {*} A JavaScript Number value
     */
    extractJavaScriptNumber(jldouble) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["extractJavaScriptNumber"](jldouble);
    }

    /**
     * Given a Java String, creates the corresponding JavaScript string value.
     *
     * Note: the Java method called in this implementation will return (in the generated code)
     * an actual primitive Java string.
     *
     * @param jlstring The java.lang.String object
     * @return {*} A JavaScript String value
     */
    extractJavaScriptString(jlstring) {
        return jlstring.toJSString();
    }

    /**
     * Converts a Java array to a JavaScript array that contains JavaScript values
     * that correspond to the Java values of the input array.
     *
     * @param jarray A Java array
     * @returns {*} The resulting JavaScript array
     */
    extractJavaScriptArray(jarray) {
        const length = $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["lengthOf"](jarray);
        const jsarray = new Array(length);
        for (let i = 0; i < length; i++) {
            jsarray[i] = $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["javaToJavaScript"](
                jarray[i]
            );
        }
        return jsarray;
    }

    // JavaScript-to-Java conversions (standard Java classes)

    /**
     * Creates a java.lang.Boolean object from a JavaScript boolean value.
     */
    createJavaBoolean(b) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaBoolean"](b);
    }

    createJavaByte(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaByte"](x);
    }

    createJavaShort(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaShort"](x);
    }

    createJavaCharacter(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaCharacter"](x);
    }

    createJavaInteger(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaInteger"](x);
    }

    createJavaFloat(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaFloat"](x);
    }

    createJavaLong(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaLong"](x);
    }

    /**
     * Creates a java.lang.Boolean object from a JavaScript number value.
     */
    createJavaDouble(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJavaDouble"](x);
    }

    getHubKindOrdinal(hub) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["getKindOrdinal"](hub);
    }

    unboxBoolean(jlBoolean) {
        return jlBoolean.$t["java.lang.Boolean"].$m["booleanValue"](jlBoolean);
    }

    unboxByte(jlByte) {
        return jlByte.$t["java.lang.Byte"].$m["byteValue"](jlByte);
    }

    unboxShort(jlShort) {
        return jlShort.$t["java.lang.Short"].$m["shortValue"](jlShort);
    }

    unboxChar(jlChar) {
        return jlChar.$t["java.lang.Character"].$m["charValue"](jlChar);
    }

    unboxInt(jlInt) {
        return jlInt.$t["java.lang.Integer"].$m["intValue"](jlInt);
    }

    unboxFloat(jlFloat) {
        return jlFloat.$t["java.lang.Float"].$m["floatValue"](jlFloat);
    }

    unboxLong(jlLong) {
        return jlLong.$t["java.lang.Long"].$m["longValue"](jlLong);
    }

    unboxDouble(jlDouble) {
        return jlDouble.$t["java.lang.Double"].$m["doubleValue"](jlDouble);
    }

    getBoxedHub(jlClass) {
        return jlClass[runtime.symbol.boxedHub];
    }

    // JavaScript-to-Java conversions (JSValue classes)

    /**
     * Gets the Java singleton object that represents the JavaScript undefined value.
     */
    createJSUndefined() {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSUndefined"]();
    }

    /**
     * Wraps a JavaScript Boolean into a Java JSBoolean object.
     *
     * @param boolean The JavaScript boolean to wrap
     * @return {*} The Java JSBoolean object
     */
    createJSBoolean(boolean) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSBoolean"](boolean);
    }

    /**
     * Wraps a JavaScript Number into a Java JSNumber object.
     *
     * @param number The JavaScript number to wrap
     * @return {*} The Java JSNumber object
     */
    createJSNumber(number) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSNumber"](number);
    }

    /**
     * Wraps a JavaScript BigInt into a Java JSBigInt object.
     *
     * @param bigint The JavaScript BigInt value to wrap
     * @return {*} The Java JSBigInt object
     */
    createJSBigInt(bigint) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSBigInt"](bigint);
    }

    /**
     * Wraps a JavaScript String into a Java JSString object.
     *
     * @param string The JavaScript String value to wrap
     * @return {*} The Java JSString object
     */
    createJSString(string) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSString"](string);
    }

    /**
     * Wraps a JavaScript Symbol into a Java JSSymbol object.
     *
     * @param symbol The JavaScript Symbol value to wrap
     * @return {*} The Java JSSymbol object
     */
    createJSSymbol(symbol) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSSymbol"](symbol);
    }

    /**
     * Wraps a JavaScript object into a Java JSObject object.
     *
     * @param obj The JavaScript Object value to wrap
     * @returns {*} The Java JSObject object
     */
    createJSObject(obj) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createJSObject"](obj);
    }

    // Helper methods

    /**
     * Checks if the specified object (which may be a JavaScript value or a Java value) is an internal Java object.
     */
    isInternalJavaObject(obj) {
        return obj instanceof $t["java.lang.Object"];
    }

    isPrimitiveHub(hub) {
        return hub.$t["java.lang.Class"].$m["isPrimitive"](hub);
    }

    isJavaLangString(obj) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["isJavaLangString"](obj);
    }

    isJavaLangClass(obj) {
        return obj.constructor === $t["java.lang.Class"];
    }

    isInstance(obj, hub) {
        return isA(true, obj, hub);
    }

    getOrCreateProxyHandler(constructor) {
        if (!constructor.hasOwnProperty(runtime.symbol.javaProxyHandler)) {
            constructor[runtime.symbol.javaProxyHandler] = new JSProxyHandler(constructor);
        }
        return constructor[runtime.symbol.javaProxyHandler];
    }

    _getProxyHandlerArg(obj) {
        return obj.constructor;
    }

    /**
     * Converts a Java value to JavaScript.
     */
    javaToJavaScript(x) {
        return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["javaToJavaScript"](x);
    }

    throwClassCastExceptionImpl(javaObject, tpeNameJavaString) {
        $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["throwClassCastException"](
            javaObject,
            tpeNameJavaString
        );
    }

    /**
     * Converts the specified Java Proxy to the target JavaScript type, if possible.
     *
     * This method is meant to be called from Java Proxy object, either when implicit coercion is enabled,
     * or when the user explicitly invokes coercion on the Proxy object.
     *
     * @param proxyHandler handler for the proxy that must be converted
     * @param proxy the Java Proxy object that should be coerced
     * @param tpe target JavaScript type name (result of the typeof operator) or constructor function
     * @return {*} the resulting JavaScript value
     */
    coerceJavaProxyToJavaScriptType(proxyHandler, proxy, tpe) {
        const o = proxy[runtime.symbol.javaNative];
        switch (tpe) {
            case "boolean":
                // Due to Java booleans being numbers, the double-negation is necessary.
                return !!$t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptBoolean"](
                    o
                );
            case "number":
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptNumber"](o);
            case "bigint":
                const bs =
                    $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptBigInt"](o);
                return BigInt(bs);
            case "string":
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptString"](o);
            case "object":
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptObject"](o);
            case "function":
                const sam = proxyHandler._getSingleAbstractMethod(proxy);
                if (sam !== undefined) {
                    return (...args) => proxyHandler._applyWithObject(proxy, args);
                }
                this.throwClassCastException(o, tpe);
            case Uint8Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptUint8Array"](
                    o
                );
            case Int8Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptInt8Array"](
                    o
                );
            case Uint16Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m[
                    "coerceToJavaScriptUint16Array"
                ](o);
            case Int16Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptInt16Array"](
                    o
                );
            case Int32Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["coerceToJavaScriptInt32Array"](
                    o
                );
            case Float32Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m[
                    "coerceToJavaScriptFloat32Array"
                ](o);
            case BigInt64Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m[
                    "coerceToJavaScriptBigInt64Array"
                ](o);
            case Float64Array:
                return $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m[
                    "coerceToJavaScriptFloat64Array"
                ](o);
            default:
                this.throwClassCastException(o, tpe);
        }
    }

    /**
     * Coerce the specified JavaScript value to the specified Java type.
     *
     * See VM.as for the specification of this function.
     */
    coerceJavaScriptToJavaType(javaScriptValue, type) {
        let typeHub;
        if (typeof type === "string") {
            typeHub = runtime.hubs[type];
        } else if (typeof type === "object") {
            const javaType = type[runtime.symbol.javaNative];
            if (javaType !== undefined && javaType.constructor === runtime.classHub) {
                typeHub = javaType;
            }
        }
        if (typeHub === undefined) {
            throw new Error(
                "Cannot coerce JavaScript value with the requested type descriptor (use String or Java Class): " + type
            );
        }
        // Check if the current object is a Java Proxy, in which case no coercion is possible.
        let javaValue = javaScriptValue[runtime.symbol.javaNative];
        if (javaValue !== undefined) {
            const valueHub = runtime.hubOf(javaValue);
            if (runtime.isSupertype(typeHub, valueHub)) {
                return javaValue;
            } else {
                throw new Error("Cannot coerce Java Proxy of type '" + valueHub + "' to the type '" + typeHub + "'");
            }
        }
        // Do a normal conversion, and invoke the as method on the JSValue.
        javaValue = this.javaScriptToJava(javaScriptValue);
        return this.javaToJavaScript(javaValue.$t["org.graalvm.webimage.api.JSValue"].$m["as"](javaValue, typeHub));
    }
}

// Java Proxies

/**
 * Handler for JavaScript Proxies that wrap Java objects.
 */
class JSProxyHandler extends ProxyHandler {
    constructor(javaScriptConstructor) {
        super();
        this.javaScriptConstructor = javaScriptConstructor;
    }

    _getClassMetadata() {
        return this.javaScriptConstructor[runtime.symbol.classMeta];
    }

    _getClassName() {
        return this.javaScriptConstructor.name;
    }

    _linkMethodPrototype() {
        if (this.javaScriptConstructor !== $t["java.lang.Object"]) {
            const parentConstructor = Object.getPrototypeOf(this.javaScriptConstructor);
            const parentProxyHandler = conversion.getOrCreateProxyHandler(parentConstructor);
            Object.setPrototypeOf(this._getMethods(), parentProxyHandler._getMethods());
        }
    }

    _getSingleAbstractMethod(javaScriptJavaProxy) {
        const javaThis = javaScriptJavaProxy[runtime.symbol.javaNative];
        return javaThis.constructor[runtime.symbol.classMeta].singleAbstractMethod;
    }

    _createInstance(hub) {
        const javaScriptConstructor = hub[runtime.symbol.jsClass];
        return new javaScriptConstructor();
    }
}

const conversion = new JSConversion();

runtime.classHub = $t["java.lang.Class"];

runtime.hubOf = $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["hubOf"];

runtime.isSupertype = $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["isSupertype"];

vm.as = (...args) => conversion.coerceJavaScriptToJavaType(...args);
