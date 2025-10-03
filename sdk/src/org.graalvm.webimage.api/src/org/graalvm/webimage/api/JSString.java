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
 * Java representation of a JavaScript {@code String} value.
 */
public final class JSString extends JSValue {

    JSString() {
    }

    @JS("return conversion.extractJavaScriptString(s[runtime.symbol.javaNative]);")
    public static native JSString of(String s);

    @Override
    public String typeof() {
        return "string";
    }

    @JS("return conversion.toProxy(toJavaString(this));")
    private native String javaString();

    @Override
    protected String stringValue() {
        return javaString();
    }

    @Override
    public String asString() {
        return javaString();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof JSString) {
            return this.javaString().equals(((JSString) that).javaString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return javaString().hashCode();
    }

    @JS.Coerce
    @JS(value = "return String.fromCharCode(codeUnit);")
    public static native JSString fromCharCode(int codeUnit);

    @JS.Coerce
    @JS(value = "return String.fromCharCode(...codeUnits);")
    public static native JSString fromCharCode(int... codeUnits);

    @JS.Coerce
    @JS(value = "return String.fromCodePoint(codeUnit);")
    public static native JSString fromCodePoint(int codeUnit);

    @JS.Coerce
    @JS(value = "return String.fromCodePoint(...codeUnits);")
    public static native JSString fromCodePoint(int... codeUnits);

    @JS(value = "return String.raw(template);")
    public static native JSString raw(JSObject template);

    @JS(value = """
            const args = [];
            for (let i = 0; i < substitutions.length; i++) {
                args.push(substitutions[i]);
            }
            return String.raw(template, ...args);
            """)
    public static native JSString raw(JSObject template, Object... substitutions);

    @JS.Coerce
    @JS(value = "return this.at(index);")
    public native JSValue at(int index);

    @JS.Coerce
    @JS(value = "return this.charAt(index);")
    public native JSString charAt(int index);

    @JS.Coerce
    @JS(value = """
            const code = this.charCodeAt(index);
            return Number.isNaN(code) ? -1 : code;
            """)
    public native int charCodeAt(int index);

    @JS.Coerce
    @JS(value = """
            const code = this.codePointAt(index);
            return code === undefined ? -1 : code;
            """)
    public native int codePointAt(int index);

    @JS.Coerce
    @JS(value = "return this.concat.apply(this, strings);")
    public native JSString concat(JSString... strings);

    @JS.Coerce
    @JS(value = "return this.endsWith(searchString);")
    public native boolean endsWith(String searchString);

    @JS.Coerce
    @JS(value = "return this.endsWith(searchString, endPosition);")
    public native boolean endsWith(String searchString, int endPosition);

    @JS.Coerce
    @JS(value = "return this.endsWith(searchString);")
    public native boolean endsWith(JSString searchString);

    @JS.Coerce
    @JS(value = "return this.endsWith(searchString, endPosition);")
    public native boolean endsWith(JSString searchString, int endPosition);

    @JS.Coerce
    @JS(value = "return this.includes(searchString);")
    public native boolean includes(String searchString);

    @JS.Coerce
    @JS(value = "return this.includes(searchString, position);")
    public native boolean includes(String searchString, int position);

    @JS.Coerce
    @JS(value = "return this.includes(searchString);")
    public native boolean includes(JSString searchString);

    @JS.Coerce
    @JS(value = "return this.includes(searchString, position);")
    public native boolean includes(JSString searchString, int position);

    @JS.Coerce
    @JS(value = "return this.indexOf(searchString);")
    public native int indexOf(String searchString);

    @JS.Coerce
    @JS(value = "return this.indexOf(searchString, position);")
    public native int indexOf(String searchString, int position);

    @JS.Coerce
    @JS(value = "return this.indexOf(searchString);")
    public native int indexOf(JSString searchString);

    @JS.Coerce
    @JS(value = "return this.indexOf(searchString, position);")
    public native int indexOf(JSString searchString, int position);

    @JS.Coerce
    @JS(value = "return this.isWellFormed();")
    public native boolean isWellFormed();

    @JS.Coerce
    @JS(value = "return this.lastIndexOf(searchString);")
    public native int lastIndexOf(String searchString);

    @JS.Coerce
    @JS(value = "return this.lastIndexOf(searchString, position);")
    public native int lastIndexOf(String searchString, int position);

    @JS.Coerce
    @JS(value = "return this.lastIndexOf(searchString);")
    public native int lastIndexOf(JSString searchString);

    @JS.Coerce
    @JS(value = "return this.lastIndexOf(searchString, position);")
    public native int lastIndexOf(JSString searchString, int position);

    @JS.Coerce
    @JS(value = "return this.localeCompare(compareString);")
    public native int localeCompare(String compareString);

    @JS.Coerce
    @JS(value = "return this.localeCompare(compareString, locales);")
    public native int localeCompare(String compareString, String locales);

    @JS.Coerce
    @JS(value = "return this.localeCompare(compareString, locales, options);")
    public native int localeCompare(String compareString, String locales, Object options);

    @JS.Coerce
    @JS(value = "return this.localeCompare(compareString);")
    public native int localeCompare(JSString compareString);

    @JS.Coerce
    @JS(value = "return this.localeCompare(compareString, locales);")
    public native int localeCompare(JSString compareString, JSString locales);

    @JS.Coerce
    @JS(value = "return this.localeCompare(compareString, locales, options);")
    public native int localeCompare(JSString compareString, JSString locales, Object options);

    @JS.Coerce
    @JS(value = "return this.match(regexp);")
    public native JSObject match(Object regexp);

    @JS.Coerce
    @JS(value = "return this.matchAll(regexp);")
    public native JSObject matchAll(Object regexp);

    @JS.Coerce
    @JS(value = "return this.normalize();")
    public native JSString normalize();

    @JS.Coerce
    @JS(value = "return this.normalize(form);")
    public native JSString normalize(String form);

    @JS.Coerce
    @JS(value = "return this.padEnd(targetLength);")
    public native JSString padEnd(int targetLength);

    @JS.Coerce
    @JS(value = "return this.padEnd(targetLength, padString);")
    public native JSString padEnd(int targetLength, String padString);

    @JS.Coerce
    @JS(value = "return this.padEnd(targetLength, padString);")
    public native JSString padEnd(int targetLength, JSString padString);

    @JS.Coerce
    @JS(value = "return this.padStart(targetLength);")
    public native JSString padStart(int targetLength);

    @JS.Coerce
    @JS(value = "return this.padStart(targetLength, padString);")
    public native JSString padStart(int targetLength, String padString);

    @JS.Coerce
    @JS(value = "return this.padStart(targetLength, padString);")
    public native JSString padStart(int targetLength, JSString padString);

    @JS.Coerce
    @JS(value = "return this.repeat(count);")
    public native JSString repeat(int count);

    @JS.Coerce
    @JS(value = "return this.replace(pattern, replacement);")
    public native JSString replace(Object pattern, Object replacement);

    @JS.Coerce
    @JS(value = "return this.replaceAll(pattern, replacement);")
    public native JSString replaceAll(Object pattern, Object replacement);

    @JS.Coerce
    @JS(value = "return this.search(regexp);")
    public native int search(Object regexp);

    @JS.Coerce
    @JS(value = "return this.slice(indexStart);")
    public native JSString slice(int indexStart);

    @JS.Coerce
    @JS(value = "return this.slice(indexStart, indexEnd);")
    public native JSString slice(int indexStart, int indexEnd);

    @JS.Coerce
    @JS(value = "return this.split(separator);")
    public native JSObject split(String separator);

    @JS.Coerce
    @JS(value = "return this.split(separator);")
    public native JSObject split(JSObject separator);

    @JS.Coerce
    @JS(value = "return this.split(separator, limit);")
    public native JSObject split(String separator, int limit);

    @JS.Coerce
    @JS(value = "return this.split(separator, limit);")
    public native JSObject split(JSObject separator, int limit);

    @JS.Coerce
    @JS(value = "return this.startsWith(searchString);")
    public native boolean startsWith(String searchString);

    @JS.Coerce
    @JS(value = "return this.startsWith(searchString);")
    public native boolean startsWith(JSString searchString);

    @JS.Coerce
    @JS(value = "return this.startsWith(searchString, position);")
    public native boolean startsWith(String searchString, int position);

    @JS.Coerce
    @JS(value = "return this.startsWith(searchString, position);")
    public native boolean startsWith(JSString searchString, int position);

    @JS.Coerce
    @JS(value = "return this.toLocaleLowerCase();")
    public native JSString toLocaleLowerCase();

    @JS.Coerce
    @JS(value = "return this.toLocaleLowerCase(locales);")
    public native JSString toLocaleLowerCase(String locales);

    @JS.Coerce
    @JS(value = "return this.toLocaleLowerCase(locales);")
    public native JSString toLocaleLowerCase(JSString locales);

    @JS.Coerce
    @JS(value = "return this.toLocaleUpperCase();")
    public native JSString toLocaleUpperCase();

    @JS.Coerce
    @JS(value = "return this.toLocaleUpperCase(locales);")
    public native JSString toLocaleUpperCase(String locales);

    @JS.Coerce
    @JS(value = "return this.toLocaleUpperCase(locales);")
    public native JSString toLocaleUpperCase(JSString locales);

    @JS.Coerce
    @JS(value = "return this.toLowerCase();")
    public native JSString toLowerCase();

    @JS.Coerce
    @JS(value = "return this.toUpperCase();")
    public native JSString toUpperCase();

    @JS.Coerce
    @JS(value = "return this.toWellFormed();")
    public native JSString toWellFormed();

    @JS.Coerce
    @JS(value = "return this.trim();")
    public native JSString trim();

    @JS.Coerce
    @JS(value = "return this.trimEnd();")
    public native JSString trimEnd();

    @JS.Coerce
    @JS(value = "return this.trimRight();")
    public native JSString trimRight();

    @JS.Coerce
    @JS(value = "return this.trimStart();")
    public native JSString trimStart();

    @JS.Coerce
    @JS(value = "return this.trimLeft();")
    public native JSString trimLeft();

    @JS.Coerce
    @JS(value = "return this.valueOf();")
    public native JSString valueOf();

    @JS.Coerce
    @JS(value = "return this.length;")
    public native int length();
}
