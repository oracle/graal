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
// NOTE: js prop prefix needs patching here if changed in the FieldFormatter
$t["java.lang.String"].prototype.toJSString = function () {
    return charArrayToString(this.$t["java.lang.String"].$f["value"]);
};

$t["java.lang.String"].prototype.valueOf = function () {
    return this.toJSString();
};

/**
 * Initializes the given Java string such that it has the same content as the given JavaScript string.
 */
function initializeJavaString(javaStr, javaScriptStr) {
    let charArray = $t["com.oracle.svm.webimage.functionintrinsics.JSConversion"].$m["createCharArray"](
        javaScriptStr.length
    );
    for (let i = 0; i < javaScriptStr.length; i++) {
        charArray[i] = javaScriptStr.charCodeAt(i);
    }
    stringCharConstructor(javaStr, charArray);
}

/**
 * Constructs a Java string from a JavaScript string.
 */
function toJavaString(str) {
    let javaStr = new $t["java.lang.String"]();
    initializeJavaString(javaStr, str);
    return javaStr;
}

/**
 * Constructs a Java string from a JavaScript string by setting the fields of the Java string manually.
 * This allows us to convert a JavaScript string to a Java string without making any calls into generated
 * code.
 * This function should only be used if it cannot be guaranteed that the image heap is initialized when calling
 * this function. This function only supports ASCII characters and the array does not have a hub.
 */
function toJavaStringManually(javaScriptStr) {
    let byteArray = new Int8Array(javaScriptStr.length);
    for (let i = 0; i < javaScriptStr.length; i++) {
        byteArray[i] = javaScriptStr.charCodeAt(i);
    }
    let javaStr = new $t["java.lang.String"]();
    javaStr.$t["java.lang.String"].$f["value"] = byteArray;
    return javaStr;
}

/**
 * List of Java/JavaScript string tuples. The Java strings are uninitialized. The JavaScript strings hold the value
 * for the corresponding Java strings.
 */
let lazilyInitializedJavaStrings = [];

/**
 * Constructs a Java string from a JavaScript string lazily. This means that the constructor is not yet called.
 * The intended usage of this function is to construct Java strings that are in the image heap. We cannot call into generated code
 * while the image heap is not fully reconstructed. Therefore, we delay calling the constructor until the heap initialization
 * is finished.
 */
function toJavaStringLazy(javaScriptString) {
    let javaStr = new $t["java.lang.String"]();
    lazilyInitializedJavaStrings.push([javaStr, javaScriptString]);
    return javaStr;
}

/**
 * Initializes the Java strings that were created by `toJavaStringLazy`.
 */
function initializeJavaStrings() {
    for (let i = 0; i < lazilyInitializedJavaStrings.length; i++) {
        let javaStr = lazilyInitializedJavaStrings[i][0];
        let javaScriptStr = lazilyInitializedJavaStrings[i][1];
        initializeJavaString(javaStr, javaScriptStr);
    }
    lazilyInitializedJavaStrings = [];
}
