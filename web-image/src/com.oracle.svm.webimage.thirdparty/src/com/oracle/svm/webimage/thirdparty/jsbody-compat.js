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
/*
 * Defines functions to support the JavaScriptBody semantics.
 *
 * This file is included after all Java class definitions are emitted.
 */

/*
 * The JavaScript TCK expects that enums expose a toString function.
 * Some images may not contain an Enum definition, there we can't (and don't need to) add the toString function.
 */
if (typeof $t["java.lang.Enum"] !== "undefined") {
    /**
     * jsbody expects that enums have their own toString function
     */
    $t["java.lang.Enum"].prototype.toString = function () {
        return this.$t["java.lang.Enum"].$f["name"].toJSString();
    };
}

function isJavaObject(o) {
    return o instanceof $t["java.lang.Object"];
}

/** This is a class method of all Java classes. It uses the class method specified with the method parameter.
 *  Returns the method wrapped in a lambda that converts its arguments and result as specified by convArgs and convReturn.
 *  Example usage: CLASS_NAME.methodForJS('MANGLED_METHOD', $$$x, $$$A, $$$B, $$$C)(ARG1, ARG2, ARG3)
 */
$t["java.lang.Object"].methodForJS = function (method, convReturn, ...convArgs) {
    return (...argsArray) => {
        // Convert each argument in argsArray according to the corresponding element in convArgs.
        let i = 0;
        for (const arg of argsArray) {
            argsArray[i] = convArgs[i](arg);
            i++;
        }
        // Call the method with the converted arguments and convert its result.
        return convReturn(method.apply(this, argsArray));
    };
};

// This is an instance method of all Java objects. It uses the selfToMethod function to reference the instance method on the object.
// Returns the method wrapped in a lambda that converts its arguments (including receiver) and result as specified by convArgs and convReturn.
// Receiver is always converted as reference object ($$$A) - it is not expected to be in convArgs.
// Example usage: OBJECT_EXPRESSION.methodForJS('MANGLED_METHOD', $$$x, $$$A, $$$B, $$$C)(ARG1, ARG2, ARG3)
$t["java.lang.Object"].prototype.methodForJS = function (selfToMethod, convReturn, ...convArgs) {
    return (...argsArray) => {
        // Convert the receiver - always a reference, never primitive.
        let shiftedArg = $$$A(this);
        // Convert each argument in argsArray according to the corresponding element in convArgs.
        // At the same time, add the receiver to the beginning of the list.
        let i = 0;
        for (const arg of argsArray) {
            argsArray[i] = shiftedArg;
            shiftedArg = convArgs[i++](arg);
        }
        argsArray.push(shiftedArg);
        // Call the method with the converted arguments and convert its result.
        return convReturn(selfToMethod(this).apply(this, argsArray));
    };
};

function convertObjectToJavaForJavaScriptBody(o) {
    if (o === undefined || o === null) {
        return null;
    }
    if (typeof o === "string") {
        return toJavaString(o);
    }
    if (Array.isArray(o)) {
        let res = $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["createJavaArray"](o.length);
        for (let i = 0; i < o.length; i++) {
            res[i] = convertObjectToJavaForJavaScriptBody(o[i]);
        }
        return res;
    }
    if (Long64.instanceof(o)) {
        return $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["toDouble"](Long64.toNumber(o));
    }
    if (typeof o === "boolean") {
        return $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["toBoolean"](o);
    }
    if (typeof o === "number") {
        return $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["toDouble"](o);
    }
    if (typeof o === "bigint") {
        return $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["toBigInt"](
            toJavaString(o.toString())
        );
    }
    if (isJavaObject(o)) {
        return o;
    }
    return $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["createJavaScriptBodyObject"](o);
}

function toJSArray(a) {
    let res = new Array(a.length);
    for (let i = 0; i < a.length; i++) {
        res[i] = $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["convertObjectToJS"](a[i]);
    }
    return res;
}

// Conversions from Java to JS. Used for methodForJS convReturn.
const $$$x = (x) => x;
const $$$z = (x) => !!x; // boolean
const $$$b = $$$x;
const $$$c = $$$x;
const $$$s = $$$x;
const $$$l = $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["convertObjectToJS"]; // object
const $$$a = $$$l;
const $$$d = $$$x;
const $$$f = $$$x;
const $$$i = $$$x;
const $$$j = Long64.toNumber; // long

// Conversions from JS to Java. Used for methodForJS convArgs.
const $$$Z = (x) => +x; // boolean
const $$$B = (x) => (toInt(x) << 24) >> 24; // byte
const $$$C = (x) => toInt(x) & 0xffff; // char
const $$$S = (x) => (toInt(x) << 16) >> 16; // short
const $$$A =
    $t["com.oracle.svm.webimage.thirdparty.JavaScriptBodyConversion"].$m["convertObjectToJavaForJavaScriptBody"]; // object, array
const $$$D = (x) => x; // double
const $$$V = (x) => x; // void
const $$$F = Math.fround; // float
const $$$I = toInt; // int
const $$$J = toLong; // long
