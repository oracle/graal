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
 * WasmGC-backend specific implementation of conversion code.
 *
 * In the WasmGC backend, all Java values are originally Wasm values, with
 * objects being references to Wasm structs. How those values are represented
 * in Java is governed by the "WebAssembly JavaScript Interface".
 * Java Objects are represented as opaque JS objects, these objects do not have
 * any properties of their own nor are they extensible (though identity with
 * regards to the === operator is preserved), only when passing them back to
 * Wasm code can they be manipulated.
 * Java long values are represented as JS BigInt values.
 *
 * Unlike the JS backend, Java code compiled to WasmGC cannot directly be passed
 * JS objects as arguments due to Wasm's type safety. Instead, JS values are
 * first wrapped in WasmExtern, a custom Java class with some special handling
 * to have it store and externref value in one of its fields.
 *
 * To support JSValue instances, there are Java factory methods for each
 * subclass exported under convert.create.*, which create a new instance of the
 * type and associate it with the given JavaScript value.
 * Instead of having the JS code store the JS native value directly in the
 * WasmGC object (which won't work because they are immutable on the JS side),
 * the JS value is first wrapped in a WasmExtern, and then passed to Java, where
 * that WasmExtern value is stored in a hidden field of the JSValue instance.
 */
class WasmGCConversion extends Conversion {
    constructor() {
        super();
        this.proxyHandlers = new WeakMap();
    }

    #wrapExtern(jsObj) {
        return getExport("extern.wrap")(jsObj);
    }

    #unwrapExtern(javaObj) {
        return getExport("extern.unwrap")(javaObj);
    }

    handleJSError(jsError) {
        if (jsError instanceof WebAssembly.Exception) {
            // Wasm exceptions can be rethrown as-is. They will be caught in Wasm code
            throw jsError;
        } else {
            // Have Java code wrap the JS error in a Java JSError instance and throw it.
            getExport("convert.throwjserror")(this.javaScriptToJava(jsError));
        }
    }

    extractJavaScriptNumber(jldouble) {
        return getExport("unbox.double")(jldouble);
    }

    extractJavaScriptString(jlstring) {
        return charArrayToString(proxyCharArray(getExport("string.tochars")(jlstring)));
    }

    extractJavaScriptArray(jarray) {
        const length = getExport("array.length")(jarray);
        const jsarray = new Array(length);
        for (let i = 0; i < length; i++) {
            jsarray[i] = this.javaToJavaScript(getExport("array.object.read")(jarray, i));
        }
        return jsarray;
    }

    createJavaBoolean(x) {
        return getExport("box.boolean")(x);
    }

    createJavaByte(x) {
        return getExport("box.byte")(x);
    }

    createJavaShort(x) {
        return getExport("box.short")(x);
    }

    createJavaCharacter(x) {
        return getExport("box.char")(x);
    }

    createJavaInteger(x) {
        return getExport("box.int")(x);
    }

    createJavaFloat(x) {
        return getExport("box.float")(x);
    }

    createJavaLong(x) {
        return getExport("box.long")(x);
    }

    createJavaDouble(x) {
        return getExport("box.double")(x);
    }

    getHubKindOrdinal(hub) {
        return getExport("class.getkindordinal")(hub);
    }

    getBoxedHub(jlClass) {
        return getExport("class.getboxedhub")(jlClass);
    }

    unboxBoolean(jlBoolean) {
        return getExport("unbox.boolean")(jlBoolean);
    }

    unboxByte(jlByte) {
        return getExport("unbox.byte")(jlByte);
    }

    unboxShort(jlShort) {
        return getExport("unbox.short")(jlShort);
    }

    unboxChar(jlChar) {
        return getExport("unbox.char")(jlChar);
    }

    unboxInt(jlInt) {
        return getExport("unbox.int")(jlInt);
    }

    unboxFloat(jlFloat) {
        return getExport("unbox.float")(jlFloat);
    }

    unboxLong(jlLong) {
        return getExport("unbox.long")(jlLong);
    }

    unboxDouble(jlDouble) {
        return getExport("unbox.double")(jlDouble);
    }

    createJSUndefined() {
        return getExport("convert.create.jsundefined")();
    }

    createJSBoolean(boolean) {
        return getExport("convert.create.jsboolean")(this.#wrapExtern(boolean));
    }

    createJSNumber(number) {
        return getExport("convert.create.jsnumber")(this.#wrapExtern(number));
    }

    createJSBigInt(bigint) {
        return getExport("convert.create.jsbigint")(this.#wrapExtern(bigint));
    }

    createJSString(string) {
        return getExport("convert.create.jsstring")(this.#wrapExtern(string));
    }

    createJSSymbol(symbol) {
        return getExport("convert.create.jssymbol")(this.#wrapExtern(symbol));
    }

    createJSObject(obj) {
        return getExport("convert.create.jsobject")(this.#wrapExtern(obj));
    }

    isInternalJavaObject(obj) {
        return getExport("extern.isjavaobject")(obj);
    }

    isPrimitiveHub(hub) {
        return getExport("class.isprimitive")(hub);
    }

    isJavaLangString(obj) {
        return getExport("convert.isjavalangstring")(obj);
    }

    isJavaLangClass(obj) {
        return getExport("convert.isjavalangclass")(obj);
    }

    isInstance(obj, hub) {
        return getExport("object.isinstance")(obj, hub);
    }

    getOrCreateProxyHandler(clazz) {
        if (!this.proxyHandlers.has(clazz)) {
            this.proxyHandlers.set(clazz, new WasmGCProxyHandler(clazz));
        }
        return this.proxyHandlers.get(clazz);
    }

    _getProxyHandlerArg(obj) {
        return getExport("object.getclass")(obj);
    }

    javaToJavaScript(x) {
        let effectiveJavaObject = x;

        /*
         * When catching exceptions in JavaScript, exceptions thrown from Java
         * aren't caught as Java objects, but as WebAssembly.Exception objects.
         * Instead of having to do special handling whenever we catch an
         * exception in JS, converting to JavaScript first unwraps the original
         * Java Throwable before converting.
         */
        if (x instanceof WebAssembly.Exception && x.is(getExport("tag.throwable"))) {
            effectiveJavaObject = x.getArg(getExport("tag.throwable"), 0);
        }

        return this.#unwrapExtern(getExport("convert.javatojavascript")(effectiveJavaObject));
    }

    throwClassCastExceptionImpl(javaObject, tpeNameJavaString) {
        getExport("convert.throwClassCastException")(javaObject, tpeNameJavaString);
    }

    coerceJavaProxyToJavaScriptType(proxyHandler, proxy, tpe) {
        const o = proxy[runtime.symbol.javaNative];
        switch (tpe) {
            case "boolean":
                // Due to Java booleans being numbers, the double-negation is necessary.
                return !!this.#unwrapExtern(getExport("convert.coerce.boolean")(o));
            case "number":
                return this.#unwrapExtern(getExport("convert.coerce.number")(o));
            case "bigint":
                const bs = this.#unwrapExtern(getExport("convert.coerce.bigint")(o));
                return BigInt(bs);
            case "string":
                return this.#unwrapExtern(getExport("convert.coerce.string")(o));
            case "object":
                return this.#unwrapExtern(getExport("convert.coerce.object")(o));
            case "function":
                const sam = proxyHandler._getSingleAbstractMethod(proxy);
                if (sam !== undefined) {
                    return (...args) => proxyHandler._applyWithObject(proxy, args);
                }
                this.throwClassCastException(o, tpe);
            case Uint8Array:
            case Int8Array:
            case Uint16Array:
            case Int16Array:
            case Int32Array:
            case Float32Array:
            case BigInt64Array:
            case Float64Array:
                // TODO GR-60603 Support array coercion
                throw new Error("Coercion to arrays is not supported yet");
            default:
                this.throwClassCastException(o, tpe);
        }
    }
}

const METADATA_PREFIX = "META.";
const SAM_PREFIX = "SAM.";
const METADATA_SEPARATOR = " ";

class WasmGCProxyHandler extends ProxyHandler {
    #classMetadata = null;

    constructor(clazz) {
        super();
        this.clazz = clazz;
    }

    #lookupClass(name) {
        const clazz = getExport("conversion.classfromencoding")(toJavaString(name));
        if (!clazz) {
            throw new Error("Failed to lookup class " + name);
        }

        return clazz;
    }

    _getClassMetadata() {
        if (!this.#classMetadata) {
            this.#classMetadata = new ClassMetadata({}, this.#extractSingleAbstractMethod(), this.#createMethodTable());
        }
        return this.#classMetadata;
    }

    #decodeMetadata(exports, name, prefix) {
        if (name.startsWith(prefix)) {
            const parts = name.slice(prefix.length).split(METADATA_SEPARATOR);
            if (parts.length < 3) {
                throw new Error("Malformed metadata: " + name);
            }
            const classId = parts[0];

            if (this.#lookupClass(classId) == this.clazz) {
                const methodName = parts[1];
                const returnTypeId = parts[2];
                const argTypeIds = parts.slice(3);

                return [
                    methodName,
                    mmeta(
                        exports[name],
                        this.#lookupClass(returnTypeId),
                        ...argTypeIds.map((i) => this.#lookupClass(i))
                    ),
                ];
            }
        }

        return undefined;
    }

    #extractSingleAbstractMethod() {
        const exports = getExports();

        for (const name in exports) {
            const meta = this.#decodeMetadata(exports, name, SAM_PREFIX);
            if (meta !== undefined) {
                return meta[1];
            }
        }

        return undefined;
    }

    #createMethodTable() {
        const exports = getExports();
        const methodTable = {};

        for (const name in exports) {
            const meta = this.#decodeMetadata(exports, name, METADATA_PREFIX);
            if (meta !== undefined) {
                let methodName = meta[0];

                if (methodName === "<init>") {
                    methodName = runtime.symbol.ctor;
                }

                if (!methodTable.hasOwnProperty(methodName)) {
                    methodTable[methodName] = [];
                }

                methodTable[methodName].push(meta[1]);
            }
        }

        return methodTable;
    }

    _getClassName() {
        return conversion.extractJavaScriptString(getExport("class.getname")(this.clazz));
    }

    _linkMethodPrototype() {
        // Link the prototype chain of the superclass' proxy handler, to include super methods.
        if (!getExport("class.isjavalangobject")(this.clazz)) {
            const parentClass = getExport("class.superclass")(this.clazz);
            const parentProxyHandler = conversion.getOrCreateProxyHandler(parentClass);
            Object.setPrototypeOf(this._getMethods(), parentProxyHandler._getMethods());
        }
    }

    _createInstance(hub) {
        return getExport("unsafe.create")(hub);
    }
}

const conversion = new WasmGCConversion();
