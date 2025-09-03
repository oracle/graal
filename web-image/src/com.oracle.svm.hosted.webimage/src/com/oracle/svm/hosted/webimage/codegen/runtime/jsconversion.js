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
 * Code dealing with moving values between the Java and JavaScript world.
 *
 * This code handles Java values:
 * - All Java values except for objects and longs are represented as JS Number values.
 * - Object and long representation depends on the backend used.
 * Variables and arguments representing Java values are usually marked explicitly
 * (e.g. by being named something like javaObject or jlstring, which stands for
 * java.lang.String).
 *
 * Java values can be used to call Java methods directly without additional
 * conversions, which is the basis of the functionality this class provides.
 * It facilitates calling Java methods from JavaScript by first performing the
 * necessary conversions or coercions before the Java call is executed. In the
 * reverse direction, it helps Java code execute JS code (e.g. through the @JS
 * annotation) by converting or coercing Java values into appropriate JS values.
 */
class Conversion {
    /**
     * Associates the given Java object with the given JS value.
     */
    setJavaScriptNative(javaObject, jsNative) {
        throw new Error("Unimplemented: Conversion.javaScriptNative");
    }

    /**
     * Returns the JS value associated with the given Java object or null if there is no associated value.
     */
    extractJavaScriptNative(javaObject) {
        throw new Error("Unimplemented: Conversion.extractJavaScriptNative");
    }

    // Java-to-JavaScript conversions

    /**
     * Given a Java boxed Double, creates the corresponding JavaScript number value.
     *
     * @param jldouble The java.lang.Double object
     * @return {*} A JavaScript Number value
     */
    extractJavaScriptNumber(jldouble) {
        throw new Error("Unimplemented: Conversion.extractJavaScriptNumber");
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
        throw new Error("Unimplemented: Conversion.extractJavaScriptString");
    }

    /**
     * Converts a Java array to a JavaScript array that contains JavaScript values
     * that correspond to the Java values of the input array.
     *
     * @param jarray A Java array
     * @returns {*} The resulting JavaScript array
     */
    extractJavaScriptArray(jarray) {
        throw new Error("Unimplemented: Conversion.extractJavaScriptArray");
    }

    // JavaScript-to-Java conversions (standard Java classes)

    /**
     * Creates a java.lang.Boolean object from a JavaScript boolean value.
     */
    createJavaBoolean(b) {
        throw new Error("Unimplemented: Conversion.createJavaBoolean");
    }

    /**
     * Creates a java.lang.Byte object from a JavaScript number value.
     */
    createJavaByte(x) {
        throw new Error("Unimplemented: Conversion.createJavaByte");
    }

    /**
     * Creates a java.lang.Short object from a JavaScript number value.
     */
    createJavaShort(x) {
        throw new Error("Unimplemented: Conversion.createJavaShort");
    }

    /**
     * Creates a java.lang.Character object from a JavaScript number value.
     */
    createJavaCharacter(x) {
        throw new Error("Unimplemented: Conversion.createJavaCharacter");
    }

    /**
     * Creates a java.lang.Integer object from a JavaScript number value.
     */
    createJavaInteger(x) {
        throw new Error("Unimplemented: Conversion.createJavaInteger");
    }

    /**
     * Creates a java.lang.Float object from a JavaScript number value.
     */
    createJavaFloat(x) {
        throw new Error("Unimplemented: Conversion.createJavaFloat");
    }

    /**
     * Creates a java.lang.Long object from a JavaScript number value.
     */
    createJavaLong(x) {
        throw new Error("Unimplemented: Conversion.createJavaLong");
    }

    /**
     * Creates a java.lang.Double object from a JavaScript number value.
     */
    createJavaDouble(x) {
        throw new Error("Unimplemented: Conversion.createJavaDouble");
    }

    /**
     * Gets the JavaKind ordinal for the given hub, as expected by `boxIfNeeded`.
     */
    getHubKindOrdinal(hub) {
        throw new Error("Unimplemented: Conversion.getHubKindOrdinal");
    }

    /**
     * Box the given value if the specified type is primitive.
     *
     * The parameter type is the enum index as defined in jdk.vm.ci.meta.JavaKind.
     * The following is a summary:
     *
     *      0 - Boolean
     *      1 - Byte
     *      2 - Short
     *      3 - Char
     *      4 - Int
     *      5 - Float
     *      6 - Long
     *      7 - Double
     *      8 - Object
     *
     * @param {number=} type
     */
    boxIfNeeded(javaValue, type) {
        switch (type) {
            case 0:
                return this.createJavaBoolean(javaValue);
            case 1:
                return this.createJavaByte(javaValue);
            case 2:
                return this.createJavaShort(javaValue);
            case 3:
                return this.createJavaCharacter(javaValue);
            case 4:
                return this.createJavaInteger(javaValue);
            case 5:
                return this.createJavaFloat(javaValue);
            case 6:
                return this.createJavaLong(javaValue);
            case 7:
                return this.createJavaDouble(javaValue);
            default:
                return javaValue;
        }
    }

    /**
     * Unbox the given value if the specified type is primitive.
     *
     * See documentation for `boxIfNeeded`.
     */
    unboxIfNeeded(javaObject, type) {
        switch (type) {
            case 0:
                return this.unboxBoolean(javaObject);
            case 1:
                return this.unboxByte(javaObject);
            case 2:
                return this.unboxShort(javaObject);
            case 3:
                return this.unboxChar(javaObject);
            case 4:
                return this.unboxInt(javaObject);
            case 5:
                return this.unboxFloat(javaObject);
            case 6:
                return this.unboxLong(javaObject);
            case 7:
                return this.unboxDouble(javaObject);
            default:
                return javaObject;
        }
    }

    unboxBoolean(jlBoolean) {
        throw new Error("Unimplemented: Conversion.unboxBoolean");
    }

    unboxByte(jlByte) {
        throw new Error("Unimplemented: Conversion.unboxByte");
    }

    unboxShort(jlShort) {
        throw new Error("Unimplemented: Conversion.unboxShort");
    }

    unboxChar(jlChar) {
        throw new Error("Unimplemented: Conversion.unboxChar");
    }

    unboxInt(jlInt) {
        throw new Error("Unimplemented: Conversion.unboxInt");
    }

    unboxFloat(jlFloat) {
        throw new Error("Unimplemented: Conversion.unboxFloat");
    }

    unboxLong(jlLong) {
        throw new Error("Unimplemented: Conversion.unboxLong");
    }

    unboxDouble(jlDouble) {
        throw new Error("Unimplemented: Conversion.unboxDouble");
    }

    /**
     * Gets the boxed counterpart of the given primitive hub.
     */
    getBoxedHub(jlClass) {
        throw new Error("Unimplemented: Conversion.getBoxedHub");
    }

    // JavaScript-to-Java conversions (JSValue classes)

    /**
     * Gets the Java singleton object that represents the JavaScript undefined value.
     */
    createJSUndefined() {
        throw new Error("Unimplemented: Conversion.createJSUndefined");
    }

    /**
     * Wraps a JavaScript Boolean into a Java JSBoolean object.
     *
     * @param boolean The JavaScript boolean to wrap
     * @return {*} The Java JSBoolean object
     */
    createJSBoolean(boolean) {
        throw new Error("Unimplemented: Conversion.createJSBoolean");
    }

    /**
     * Wraps a JavaScript Number into a Java JSNumber object.
     *
     * @param number The JavaScript number to wrap
     * @return {*} The Java JSNumber object
     */
    createJSNumber(number) {
        throw new Error("Unimplemented: Conversion.createJSNumber");
    }

    /**
     * Wraps a JavaScript BigInt into a Java JSBigInt object.
     *
     * @param bigint The JavaScript BigInt value to wrap
     * @return {*} The Java JSBigInt object
     */
    createJSBigInt(bigint) {
        throw new Error("Unimplemented: Conversion.createJSBigInt");
    }

    /**
     * Wraps a JavaScript String into a Java JSString object.
     *
     * @param string The JavaScript String value to wrap
     * @return {*} The Java JSString object
     */
    createJSString(string) {
        throw new Error("Unimplemented: Conversion.createJSString");
    }

    /**
     * Wraps a JavaScript Symbol into a Java JSSymbol object.
     *
     * @param symbol The JavaScript Symbol value to wrap
     * @return {*} The Java JSSymbol object
     */
    createJSSymbol(symbol) {
        throw new Error("Unimplemented: Conversion.createJSSymbol");
    }

    /**
     * Wraps a JavaScript object into a Java JSObject object.
     *
     * @param obj The JavaScript Object value to wrap
     * @returns {*} The Java JSObject object
     */
    createJSObject(obj) {
        throw new Error("Unimplemented: Conversion.createJSObject");
    }

    // Helper methods

    /**
     * Checks if the specified object (which may be a JavaScript value or a Java value) is an internal Java object.
     */
    isInternalJavaObject(obj) {
        throw new Error("Unimplemented: Conversion.isInternalJavaObject");
    }

    isPrimitiveHub(hub) {
        throw new Error("Unimplemented: Conversion.isPrimitiveHub");
    }

    isJavaLangString(obj) {
        throw new Error("Unimplemented: Conversion.isJavaLangString");
    }

    isJavaLangClass(obj) {
        throw new Error("Unimplemented: Conversion.isJavaLangClassHub");
    }

    isInstance(obj, hub) {
        throw new Error("Unimplemented: Conversion.isInstance");
    }

    /**
     * Copies own fields from source to destination.
     *
     * Existing fields in the destination are overwritten.
     */
    copyOwnFields(src, dst) {
        for (let name of Object.getOwnPropertyNames(src)) {
            dst[name] = src[name];
        }
    }

    /**
     * Creates an anonymous JavaScript object, and does the mirror handshake.
     */
    createAnonymousJavaScriptObject() {
        const x = {};
        const jsObject = this.createJSObject(x);
        x[runtime.symbol.javaNative] = jsObject;
        return x;
    }

    /**
     * Obtains or creates the proxy handler for the given Java class
     */
    getOrCreateProxyHandler(arg) {
        throw new Error("Unimplemented: Conversion.getOrCreateProxyHandler");
    }

    /**
     * For proxying the given object returns value that should be passed to
     * getOrCreateProxyHandler.
     */
    _getProxyHandlerArg(obj) {
        throw new Error("Unimplemented: Conversion._getProxyHandlerArg");
    }

    /**
     * Creates a proxy that intercepts messages that correspond to Java method calls and Java field accesses.
     *
     * @param obj The Java object to create a proxy for
     * @return {*} The proxy around the Java object
     */
    toProxy(obj) {
        let proxyHandler = this.getOrCreateProxyHandler(this._getProxyHandlerArg(obj));
        // The wrapper is a temporary object that allows having the non-identifier name of the target function.
        // We declare the property as a function, to ensure that it is constructable, so that the Proxy handler's construct method is callable.
        let targetWrapper = {
            ["Java Proxy"]: function (key) {
                if (key === runtime.symbol.javaNative) {
                    return obj;
                }
                return undefined;
            },
        };

        return new Proxy(targetWrapper["Java Proxy"], proxyHandler);
    }

    /**
     * Converts a JavaScript value to the corresponding Java representation.
     *
     * The exact rules of the mapping are documented in the Java JS annotation class.
     *
     * This method is only meant to be called from the conversion code generated for JS-annotated methods.
     *
     * @param x The JavaScript value to convert
     * @return {*} The Java representation of the JavaScript value
     */
    javaScriptToJava(x) {
        // Step 1: check null, which is mapped 1:1 to null in Java.
        if (x === null) {
            return null;
        }

        // Step 2: check undefined, which is a singleton in Java.
        if (x === undefined) {
            return this.createJSUndefined();
        }

        // Step 3: check if the javaNative property is set.
        // This covers objects that already have Java counterparts (for example, Java proxies).
        const javaValue = x[runtime.symbol.javaNative];
        if (javaValue !== undefined) {
            return javaValue;
        }

        // Step 4: use the JavaScript type to select the appropriate Java representation.
        const tpe = typeof x;
        switch (tpe) {
            case "boolean":
                return this.createJSBoolean(x);
            case "number":
                return this.createJSNumber(x);
            case "bigint":
                return this.createJSBigInt(x);
            case "string":
                return this.createJSString(x);
            case "symbol":
                return this.createJSSymbol(x);
            case "object":
            case "function":
                // We know this is a normal object created in JavaScript,
                // otherwise it would have a runtime.symbol.javaNative property,
                // and the conversion would have returned in Step 3.
                return this.createJSObject(x);
            default:
                throw new Error("unexpected type: " + tpe);
        }
    }

    /**
     * Maps each JavaScript value in the input array to a Java value.
     * See {@code javaScriptToJava}.
     */
    eachJavaScriptToJava(javaScriptValues) {
        const javaValues = new Array(javaScriptValues.length);
        for (let i = 0; i < javaScriptValues.length; i++) {
            javaValues[i] = this.javaScriptToJava(javaScriptValues[i]);
        }
        return javaValues;
    }

    /**
     * Converts a Java value to JavaScript.
     */
    javaToJavaScript(x) {
        throw new Error("Unimplemented: Conversion.javaToJavaScript");
    }

    throwClassCastExceptionImpl(javaObject, tpeNameJavaString) {
        throw new Error("Unimplemented: Conversion.throwClassCastExceptionImpl");
    }

    throwClassCastException(javaObject, tpe) {
        let tpeName;
        if (typeof tpe === "string") {
            tpeName = tpe;
        } else if (typeof tpe === "function") {
            tpeName = tpe.name;
        } else {
            tpeName = tpe.toString();
        }
        this.throwClassCastExceptionImpl(javaObject, toJavaString(tpeName));
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
        throw new Error("Unimplemented: Conversion.coerceJavaProxyToJavaScriptType");
    }

    /**
     * Try to convert the JavaScript object to a Java facade class, or return null.
     *
     * @param obj JavaScript object whose Java facade class we search for
     * @param cls target Java class in the form of its JavaScript counterpart
     * @return {*} the mirror instance wrapped into a JavaScript Java Proxy, or null
     */
    tryExtractFacadeClass(obj, cls) {
        const facades = runtime.findFacadesFor(obj.constructor);
        const rawJavaHub = cls[runtime.symbol.javaNative];
        const internalJavaClass = rawJavaHub[runtime.symbol.jsClass];
        if (facades.has(internalJavaClass)) {
            const rawJavaMirror = new internalJavaClass();
            // Note: only one-way handshake, since the JavaScript object could be recast to a different Java facade class.
            this.setJavaScriptNative(rawJavaMirror, obj);
            return this.toProxy(rawJavaMirror);
        } else {
            return null;
        }
    }

    /**
     * Coerce the specified JavaScript value to the specified Java type.
     *
     * See VM.as for the specification of this function.
     */
    coerceJavaScriptToJavaType(javaScriptValue, type) {
        throw new Error("Unimplemented: Conversion.coerceJavaScriptToJavaType");
    }
}

/**
 * Handle for proxying Java objects.
 *
 * Client JS code never directly sees Java object, instead they see proxies
 * using this handler. The handler is generally specialized per type.
 * It provides access to the underlying Java methods.
 *
 * It also supports invoking the proxy, which calls the single abstract method
 * in the Java object if available, and for Class objects the new operator
 * works, creating a Java object and invoking a matching constructor.
 *
 * The backends provide method metadata describing the Java methods available
 * to the proxy. At runtime, when a method call is triggered (a method is
 * accessed and called, the proxy itself is invoked, or a constructor is called),
 * the proxy will find a matching implementation based on the types of the
 * passed arguments.
 * Arguments and return values are automatically converted to and from Java
 * objects respectively, but no coercion is done.
 */
class ProxyHandler {
    constructor() {
        this._initialized = false;
        this._methods = {};
        this._staticMethods = {};
        this._javaConstructorMethod = null;
    }

    ensureInitialized() {
        if (!this._initialized) {
            this._initialized = true;
            // Function properties derived from accessible Java methods.
            this._createProxyMethods();
            // Default function properties.
            this._createDefaultMethods();
        }
    }

    _getMethods() {
        this.ensureInitialized();
        return this._methods;
    }

    _getStaticMethods() {
        this.ensureInitialized();
        return this._staticMethods;
    }

    _getJavaConstructorMethod() {
        this.ensureInitialized();
        return this._javaConstructorMethod;
    }

    /**
     * Returns a ClassMetadata instance for the class this proxy handler represents.
     */
    _getClassMetadata() {
        throw new Error("Unimplemented: ProxyHandler._getClassMetadata");
    }

    _getMethodTable() {
        const classMeta = this._getClassMetadata();
        if (classMeta === undefined) {
            return undefined;
        }
        return classMeta.methodTable;
    }

    /**
     * String that can be printed as part of the toString and valueOf functions.
     */
    _getClassName() {
        throw new Error("Unimplemented: ProxyHandler._getClassName");
    }

    /**
     * Link the methods object to the prototype chain of the methods object of the superclass' proxy handler.
     */
    _linkMethodPrototype() {
        throw new Error("Unimplemented: ProxyHandler._linkMethodPrototype");
    }

    _createProxyMethods() {
        // Create proxy methods for the current class.
        const methodTable = this._getMethodTable();
        if (methodTable === undefined) {
            return;
        }

        const proxyHandlerThis = this;
        for (const name in methodTable) {
            const overloads = methodTable[name];
            const instanceOverloads = [];
            const staticOverloads = [];
            for (const m of overloads) {
                if (m.isStatic) {
                    staticOverloads.push(m);
                } else {
                    instanceOverloads.push(m);
                }
            }
            if (instanceOverloads.length > 0) {
                this._methods[name] = function (...javaScriptArgs) {
                    // Note: the 'this' value is bound to the Proxy object.
                    return proxyHandlerThis._invokeProxyMethod(name, instanceOverloads, this, ...javaScriptArgs);
                };
            }
            if (staticOverloads.length > 0) {
                this._staticMethods[name] = function (...javaScriptArgs) {
                    // Note: the 'this' value is bound to the Proxy object.
                    return proxyHandlerThis._invokeProxyMethod(name, staticOverloads, null, ...javaScriptArgs);
                };
            }
        }
        if (methodTable[runtime.symbol.ctor] !== undefined) {
            const overloads = methodTable[runtime.symbol.ctor];
            this._javaConstructorMethod = function (javaScriptJavaProxy, ...javaScriptArgs) {
                // Note: the 'this' value is bound to the Proxy object.
                return proxyHandlerThis._invokeProxyMethod("<init>", overloads, javaScriptJavaProxy, ...javaScriptArgs);
            };
        } else {
            this._javaConstructorMethod = function (javaScriptJavaProxy, ...javaScriptArgs) {
                throw new Error(
                    "Cannot invoke the constructor. Make sure that the constructors are explicitly added to the image."
                );
            };
        }

        this._linkMethodPrototype();
    }

    /**
     * Checks whether the given argument values can be used to call the method identified by the given metdata class.
     */
    _conforms(args, metadata) {
        if (metadata.paramHubs.length !== args.length) {
            return false;
        }
        for (let i = 0; i < args.length; i++) {
            const arg = args[i];
            let paramHub = metadata.paramHubs[i];
            if (paramHub === null) {
                // A null parameter hub means that the type-check always passes.
                continue;
            }
            if (conversion.isPrimitiveHub(paramHub)) {
                // A primitive hub must be replaced with the hub of the corresponding boxed type.
                paramHub = conversion.getBoxedHub(paramHub);
            }
            if (!conversion.isInstance(arg, paramHub)) {
                return false;
            }
        }
        return true;
    }

    _unboxJavaArguments(args, metadata) {
        // Precondition -- method metadata refers to a method with a correct arity.
        for (let i = 0; i < args.length; i++) {
            const paramHub = metadata.paramHubs[i];
            args[i] = conversion.unboxIfNeeded(args[i], conversion.getHubKindOrdinal(paramHub));
        }
    }

    _createDefaultMethods() {
        if (!this._methods.hasOwnProperty("toString")) {
            // The check must use hasOwnProperty, because toString always exists in the prototype.
            this._methods["toString"] = () => "[Java Proxy: " + this._getClassName() + "]";
        } else {
            const javaToString = this._methods["toString"];
            this._methods[runtime.symbol.toString] = javaToString;
            this._methods["toString"] = function () {
                // The `this` value must be bound to the proxy instance.
                //
                // The `toString` method is used often in JavaScript, and treated specially.
                // If its return type is a Java String, then that string is converted to a JavaScript string.
                // In other words, if the result of the call is a JavaScript proxy (see _invokeProxyMethod return value),
                // then proxies that represent java.lang.String are converted to JavaScript strings.
                const javaScriptResult = javaToString.call(this);
                if (typeof javaScriptResult === "function" || typeof javaScriptResult === "object") {
                    const javaResult = javaScriptResult[runtime.symbol.javaNative];
                    if (javaResult !== undefined && conversion.isJavaLangString(javaResult)) {
                        return conversion.extractJavaScriptString(javaResult);
                    } else {
                        return javaScriptResult;
                    }
                } else {
                    return javaScriptResult;
                }
            };
        }

        // Override Java methods that return valueOf.
        // JavaScript requires that valueOf returns a JavaScript primitive (in this case, string).
        this._methods["valueOf"] = () => "[Java Proxy: " + this._getClassName() + "]";

        const proxyHandlerThis = this;
        const asProperty = function (tpe) {
            // Note: this will be bound to the Proxy object.
            return conversion.coerceJavaProxyToJavaScriptType(proxyHandlerThis, this, tpe);
        };
        if (!("$as" in this._methods)) {
            this._methods["$as"] = asProperty;
        }
        this._methods[runtime.symbol.javaScriptCoerceAs] = asProperty;

        const vmProperty = vm;
        if (!("$vm" in this._methods)) {
            this._methods["$vm"] = vmProperty;
        }
    }

    _loadMethod(target, key) {
        const member = this._getMethods()[key];
        if (member !== undefined) {
            return member;
        }
    }

    _methodNames() {
        return Object.keys(this._getMethods());
    }

    _invokeProxyMethod(name, overloads, javaScriptJavaProxy, ...javaScriptArgs) {
        // For static methods, javaScriptThis is set to null.
        const isStatic = javaScriptJavaProxy === null;
        const javaThis = isStatic ? null : javaScriptJavaProxy[runtime.symbol.javaNative];
        const javaArgs = conversion.eachJavaScriptToJava(javaScriptArgs);
        for (let i = 0; i < overloads.length; i++) {
            const metadata = overloads[i];
            if (this._conforms(javaArgs, metadata)) {
                // Where necessary, perform unboxing of Java arguments.
                this._unboxJavaArguments(javaArgs, metadata);
                let javaResult;
                try {
                    if (isStatic) {
                        javaResult = metadata.method.call(null, ...javaArgs);
                    } else {
                        javaResult = metadata.method.call(null, javaThis, ...javaArgs);
                    }
                } catch (error) {
                    throw conversion.javaToJavaScript(error);
                }
                if (javaResult === undefined) {
                    // This only happens when the return type is void.
                    return undefined;
                }
                // If necessary, box the Java return value.
                const retHub = metadata.returnHub;
                javaResult = conversion.boxIfNeeded(javaResult, conversion.getHubKindOrdinal(retHub));
                const javaScriptResult = conversion.javaToJavaScript(javaResult);
                return javaScriptResult;
            }
        }
        const methodName = name !== null ? "method '" + name + "'" : "single abstract method";
        throw new Error("No matching signature for " + methodName + " and argument list '" + javaScriptArgs + "'");
    }

    /**
     * The Java type hierarchy is not modelled in the proxy and the proxied
     * object has no prototype.
     */
    getPrototypeOf(target) {
        return null;
    }

    /**
     * Modifying the prototype of the proxied object is not allowed.
     */
    setPrototypeOf(target, prototype) {
        return false;
    }

    /**
     * Proxied objects are not extensible in any way.
     */
    isExtensible(target) {
        return false;
    }

    /**
     * We allow calling Object.preventExtensions on the proxy.
     * However, it won't do anything, the proxy handler already prevents extensions.
     */
    preventExtensions(target) {
        return true;
    }

    getOwnPropertyDescriptor(target, key) {
        const value = this._loadMethod(target, key);
        if (value === undefined) {
            return undefined;
        }
        return {
            value: value,
            writable: false,
            enumerable: false,
            configurable: false,
        };
    }

    /**
     * Defining properties on the Java object is not allowed.
     */
    defineProperty(target, key, descriptor) {
        return false;
    }

    has(target, key) {
        return this._loadMethod(target, key) !== undefined;
    }

    get(target, key) {
        if (key === runtime.symbol.javaNative) {
            return target(runtime.symbol.javaNative);
        } else {
            const javaObject = target(runtime.symbol.javaNative);
            // TODO GR-60603 Deal with arrays in WasmGC backend
            if (Array.isArray(javaObject) && typeof key === "string") {
                const index = Number(key);
                if (0 <= index && index < javaObject.length) {
                    return conversion.javaToJavaScript(javaObject[key]);
                } else if (key === "length") {
                    return javaObject.length;
                }
            }
        }
        return this._loadMethod(target, key);
    }

    set(target, key, value, receiver) {
        const javaObject = target(runtime.symbol.javaNative);
        // TODO GR-60603 Deal with arrays in WasmGC backend
        if (Array.isArray(javaObject)) {
            const index = Number(key);
            if (0 <= index && index < javaObject.length) {
                javaObject[key] = conversion.javaScriptToJava(value);
                return true;
            }
        }
        return false;
    }

    /**
     * Deleting properties on the Java object is not allowed.
     */
    deleteProperty(target, key) {
        return false;
    }

    ownKeys(target) {
        return this._methodNames();
    }

    apply(target, javaScriptThisArg, javaScriptArgs) {
        // We need to convert the Proxy's target function to the Java Proxy.
        const javaScriptJavaProxy = conversion.toProxy(target(runtime.symbol.javaNative));
        // Note: the JavaScript this argument for the apply method is never exposed to Java, so we just ignore it.
        return this._applyWithObject(javaScriptJavaProxy, javaScriptArgs);
    }

    _getSingleAbstractMethod(javaScriptJavaProxy) {
        return this._getClassMetadata().singleAbstractMethod;
    }

    _applyWithObject(javaScriptJavaProxy, javaScriptArgs) {
        const sam = this._getSingleAbstractMethod(javaScriptJavaProxy);
        if (sam === undefined) {
            throw new Error("Java Proxy is not a functional interface, so 'apply' cannot be called from JavaScript.");
        }
        return this._invokeProxyMethod(null, [sam], javaScriptJavaProxy, ...javaScriptArgs);
    }

    /**
     * Create uninitialized instance of given Java type.
     */
    _createInstance(hub) {
        throw new Error("Unimplemented: ProxyHandler._createInstance");
    }

    construct(target, argumentsList) {
        const javaThis = target(runtime.symbol.javaNative);
        // This is supposed to be a proxy handler for java.lang.Class objects
        // and javaThis is supposed to be some Class instance.
        if (!conversion.isJavaLangClass(javaThis)) {
            throw new Error(
                "Cannot invoke the 'new' operator. The 'new' operator can only be used on Java Proxies that represent the 'java.lang.Class' type."
            );
        }
        // Allocate the Java object from Class instance
        const javaInstance = this._createInstance(javaThis);
        // Lookup constructor method of the target class.
        // This proxy handler is for java.lang.Class while javaThis is a
        // java.lang.Class instance for some object type for which we want to
        // lookup the constructor.
        const instanceProxyHandler = conversion.getOrCreateProxyHandler(conversion._getProxyHandlerArg(javaInstance));
        const javaConstructorMethod = instanceProxyHandler._getJavaConstructorMethod();
        // Convert the Java instance to JS (usually creates a proxy)
        const javaScriptInstance = conversion.javaToJavaScript(javaInstance);
        // Call the Java constructor method.
        javaConstructorMethod(javaScriptInstance, ...argumentsList);
        return javaScriptInstance;
    }
}
