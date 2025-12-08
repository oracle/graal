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

package com.oracle.svm.webimage.jtt.api;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSUndefined;
import org.graalvm.webimage.api.JSValue;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JSStringTest {

    public static final String[] OUTPUT = new String[]{};

    private static final String HELLO_STRING = "Hello";
    private static final String WORLD_STRING = "World";
    private static final String HELLO_WORLD_STRING = "Hello World";
    private static final String JAVASCRIPT_STRING = "JavaScript";
    private static final int[] ARROWS_CODE_POINTS = new int[]{0x2190, 0x2191, 0x2192, 0x2193};
    private static final int[] MATH_CODE_POINTS = new int[]{0x2211, 0x221A, 0x03C0, 0x221E};
    private static final int[] CURRENCY_CODE_POINTS = new int[]{0x20AC, 0x00A5, 0x20B9, 0x0024};
    private static final String LONG_TEXT = "The quick brown fox jumps over the lazy dog.";

    public static void main(String[] args) {
        testAt();
        testCharAt();
        testCharCodeAt();
        testCodePointAt();
        testConcat();
        testEndsWith();
        testIncludes();
        testFromCharCode();
        testFromCodePoint();
        testIndexOf();
        testLastIndexOf();
        testIsWellFormed();
        testLength();
        testLocaleCompare();
        testMatchAll();
        testMatch();
        testNormalize();
        testPadEnd();
        testPadStart();
        testRaw();
        testRepeat();
        testReplace();
        testReplaceAll();
        testSearch();
        testSlice();
        testSplit();
        testStartsWith();
        testToLocaleLowerCase();
        testToLocaleUpperCase();
        testToLowerCase();
        testToUpperCase();
        testToWellFormed();
        testTrim();
        testSubstring();
    }

    public static void testAt() {
        JSString helloString = JSString.of(HELLO_STRING);
        JSString javaScriptString = JSString.of(JAVASCRIPT_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);
        JSString currencyString = JSString.fromCodePoint(CURRENCY_CODE_POINTS);

        assertEquals("H", helloString.at(0).asString());
        assertEquals("o", helloString.at(4).asString());
        assertEquals(JSUndefined.undefined(), JSValue.checkedCoerce(helloString.at(10), JSUndefined.class));
        assertEquals("o", helloString.at(-1).asString());
        assertEquals("S", javaScriptString.at(4).asString());
        assertEquals("i", javaScriptString.at(-3).asString());
        assertEquals("\u2191", arrowsString.at(1).asString());
        assertEquals("\u2192", arrowsString.at(-2).asString());
        assertEquals("\u2211", mathString.at(0).asString());
        assertEquals("\u221E", mathString.at(3).asString());
        assertEquals("\u20B9", currencyString.at(2).asString());
        assertEquals("\u0024", currencyString.at(-1).asString());
    }

    public static void testCharAt() {
        JSString helloString = JSString.of(HELLO_STRING);
        JSString javaScriptString = JSString.of(JAVASCRIPT_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);
        JSString currencyString = JSString.fromCodePoint(CURRENCY_CODE_POINTS);

        assertEquals("H", helloString.charAt(0).asString());
        assertEquals("o", helloString.charAt(4).asString());
        assertEquals("", helloString.charAt(-1).asString());
        assertEquals("S", javaScriptString.charAt(4).asString());
        assertEquals("t", javaScriptString.charAt(9).asString());
        assertEquals("", javaScriptString.charAt(10).asString());
        assertEquals("\u2190", arrowsString.charAt(0).asString());
        assertEquals("\u2192", arrowsString.charAt(2).asString());
        assertEquals("\u221A", mathString.charAt(1).asString());
        assertEquals("\u221E", mathString.charAt(3).asString());
        assertEquals("\u20B9", currencyString.charAt(2).asString());
        assertEquals("\u0024", currencyString.charAt(3).asString());
    }

    public static void testCharCodeAt() {
        JSString helloString = JSString.of(HELLO_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);
        JSString currencyString = JSString.fromCodePoint(CURRENCY_CODE_POINTS);

        assertEquals(72, helloString.charCodeAt(0));
        assertEquals(111, helloString.charCodeAt(4));
        assertEquals(-1, helloString.charCodeAt(-1));
        assertEquals(-1, helloString.charCodeAt(10));
        assertEquals(8592, arrowsString.charCodeAt(0));
        assertEquals(8594, arrowsString.charCodeAt(2));
        assertEquals(8730, mathString.charCodeAt(1));
        assertEquals(8734, mathString.charCodeAt(3));
        assertEquals(8377, currencyString.charCodeAt(2));
        assertEquals(36, currencyString.charCodeAt(3));
    }

    public static void testCodePointAt() {
        JSString helloString = JSString.of(HELLO_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);
        JSString currencyString = JSString.fromCodePoint(CURRENCY_CODE_POINTS);

        assertEquals(72, helloString.codePointAt(0));
        assertEquals(111, helloString.codePointAt(4));
        assertEquals(-1, helloString.codePointAt(-1));
        assertEquals(-1, helloString.codePointAt(10));
        assertEquals(8592, arrowsString.codePointAt(0));
        assertEquals(8594, arrowsString.codePointAt(2));
        assertEquals(8730, mathString.codePointAt(1));
        assertEquals(8734, mathString.codePointAt(3));
        assertEquals(8377, currencyString.codePointAt(2));
        assertEquals(36, currencyString.codePointAt(3));
    }

    public static void testConcat() {
        JSString comma = JSString.of(", ");
        JSString exclaim = JSString.of("!");
        JSString label = JSString.of("Arrows: ");
        JSString mathLabel = JSString.of("Math: ");
        JSString helloString = JSString.of(HELLO_STRING);
        JSString worldString = JSString.of(WORLD_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);

        assertEquals("Hello, World!", helloString.concat(comma, worldString, exclaim).asString());
        assertEquals("Arrows: \u2190\u2191\u2192\u2193", label.concat(arrowsString).asString());
        assertEquals("Math: \u2211\u221A\u03C0\u221E", mathLabel.concat(mathString).asString());
    }

    public static void testEndsWith() {
        JSString worldString = JSString.of(WORLD_STRING);
        JSString helloWorldString = JSString.of(HELLO_WORLD_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);

        assertTrue(helloWorldString.endsWith(worldString));
        assertFalse(helloWorldString.endsWith("world"));
        assertFalse(helloWorldString.endsWith(HELLO_STRING));
        assertTrue(helloWorldString.endsWith(HELLO_STRING, 5));
        assertTrue(arrowsString.endsWith(JSString.fromCodePoint(0x2192, 0x2193)));
        assertTrue(arrowsString.endsWith(JSString.fromCodePoint(0x2191), 2));
        assertTrue(mathString.endsWith(JSString.fromCodePoint(0x03C0, 0x221E)));
        assertTrue(mathString.endsWith(JSString.fromCodePoint(0x221A), 2));
        assertFalse(mathString.endsWith(JSString.fromCodePoint(0x221E), 3));
    }

    public static void testIncludes() {
        JSString worldString = JSString.of(WORLD_STRING);
        JSString helloWorldString = JSString.of(HELLO_WORLD_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);

        assertTrue(helloWorldString.includes(worldString));
        assertFalse(helloWorldString.includes("world"));
        assertTrue(helloWorldString.includes("lo"));
        assertFalse(helloWorldString.includes("lo", 5));
        assertTrue(arrowsString.includes(JSString.fromCodePoint(0x2191)));
        assertTrue(arrowsString.includes(JSString.fromCodePoint(0x2191), 1));
        assertTrue(mathString.includes(JSString.fromCodePoint(0x221A)));
        assertTrue(mathString.includes(JSString.fromCodePoint(0x03C0), 2));
    }

    public static void testFromCharCode() {
        assertEquals("", JSString.fromCharCode().asString());
        assertEquals("A", JSString.fromCharCode(65).asString());
        assertEquals("Hello", JSString.fromCharCode(72, 101, 108, 108, 111).asString());
        assertEquals("\u0024\u00A9\u00AE", JSString.fromCharCode(36, 169, 174).asString());
        assertEquals("\uD83D\uDE00", JSString.fromCharCode(0xD83D, 0xDE00).asString());
    }

    public static void testFromCodePoint() {
        assertEquals("", JSString.fromCodePoint().asString());
        assertEquals("A", JSString.fromCodePoint(65).asString());
        assertEquals("Hello", JSString.fromCodePoint(72, 101, 108, 108, 111).asString());
        assertEquals("\u0024\u00A9\u00AE", JSString.fromCodePoint(36, 169, 174).asString());
        assertEquals("\uD83D\uDE00", JSString.fromCodePoint(0x1F600).asString());
        assertEquals("\u03A9\uD83D\uDE80", JSString.fromCodePoint(0x03A9, 0x1F680).asString());
    }

    public static void testIndexOf() {
        JSString helloWorldString = JSString.of(HELLO_WORLD_STRING);
        JSString arrowsString = JSString.fromCodePoint(ARROWS_CODE_POINTS);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);

        assertEquals(6, helloWorldString.indexOf(WORLD_STRING));
        assertEquals(-1, helloWorldString.indexOf("world"));
        assertEquals(2, helloWorldString.indexOf("l"));
        assertEquals(9, helloWorldString.indexOf("l", 4));
        assertEquals(2, helloWorldString.indexOf("l"), -4);
        assertEquals(1, arrowsString.indexOf(JSString.fromCodePoint(0x2191)));
        assertEquals(2, arrowsString.indexOf(JSString.fromCodePoint(0x2192), 2));
        assertEquals(2, mathString.indexOf(JSString.fromCodePoint(0x03C0)));
        assertEquals(1, mathString.indexOf(JSString.fromCodePoint(0x221A), 1));
    }

    public static void testLastIndexOf() {
        JSString phrase1 = JSString.of(HELLO_STRING + " " + HELLO_STRING);
        JSString phrase2 = JSString.fromCodePoint(0x2190, 0x2191, 0x2192, 0x2193, 0x2190, 0x2191, 0x2192, 0x2193);

        assertEquals(6, phrase1.lastIndexOf(HELLO_STRING));
        assertEquals(0, phrase1.lastIndexOf(HELLO_STRING, 5));
        assertEquals(0, phrase1.lastIndexOf(HELLO_STRING, -5));
        assertEquals(9, phrase1.lastIndexOf("lo"));
        assertEquals(6, phrase2.lastIndexOf(JSString.fromCodePoint(0x2192)));
        assertEquals(6, phrase2.lastIndexOf(JSString.fromCodePoint(0x2192), 6));
    }

    public static void testIsWellFormed() {
        JSString helloWorldString = JSString.of(HELLO_WORLD_STRING);
        JSString mathString = JSString.fromCodePoint(MATH_CODE_POINTS);

        JSString highPlusAscii = JSString.fromCharCode(0xD800, 0x0041);
        JSString lowPlusAscii = JSString.fromCharCode(0xDC00, 0x0042);
        JSString reversedPair = JSString.fromCharCode(0xDC00, 0xD800);

        assertTrue(helloWorldString.isWellFormed());
        assertTrue(mathString.isWellFormed());
        assertFalse(highPlusAscii.isWellFormed());
        assertFalse(lowPlusAscii.isWellFormed());
        assertFalse(reversedPair.isWellFormed());
    }

    public static void testLength() {
        JSString text = JSString.of("Life, the universe and everything. Answer:");

        assertEquals(42, text.length());
    }

    public static void testLocaleCompare() {
        JSString a = JSString.of("a");
        JSString a2 = JSString.of("A");
        JSString a3 = JSString.of("\u00E4");
        JSString a4 = JSString.of("ae");
        JSObject caseSensitive = JSObject.create();
        caseSensitive.set("sensitivity", "case");

        JSObject accentSensitive = JSObject.create();
        accentSensitive.set("sensitivity", "accent");

        assertEquals(1, a2.localeCompare("a"));
        assertEquals(-1, a.localeCompare("A"));
        assertEquals(0, a2.localeCompare("A"));
        assertEquals(-1, a3.localeCompare("ae", "de"));
        assertEquals(-1, a.localeCompare("A", "en", caseSensitive));
        assertEquals(1, a2.localeCompare("a", "en", caseSensitive));
        assertEquals(-1, a.localeCompare(a2));
        assertEquals(-1, a3.localeCompare(a4, JSString.of("sv")));
        assertEquals(-1, a3.localeCompare("a", "en", accentSensitive));
        assertEquals(1, a.localeCompare(a3, JSString.of("en"), accentSensitive));
        assertEquals(0, a3.localeCompare("\u00E4", "en", accentSensitive));
    }

    public static void testMatchAll() {
        JSString phrase = JSString.of("Price: \u002412, Discount: \u00245, Tax: \u00242");
        JSObject iterator = phrase.matchAll(eval("/\\\u0024(\\d+)/g"));
        String result = iteratorToString(iterator);

        assertEquals("\u002412,12,\u00245,5,\u00242,2", result);
    }

    public static void testMatch() {
        JSString phrase = JSString.of("Hello 123 World 456");
        JSString mixed = JSString.of("Hello 123 World ABC xyz");

        String result1 = objToString(phrase.match("World"));
        String result2 = objToString(mixed.match(eval("/[A-Z]/g")));
        String result3 = objToString(phrase.match(JSString.of("\\d+")));
        String result4 = objToString(phrase.match(eval("/\\d+/g")));

        assertEquals("World", result1);
        assertEquals("H,W,A,B,C", result2);
        assertEquals("123", result3);
        assertEquals("123,456", result4);
        assertNull(phrase.match("XYZ"));
    }

    public static void testNormalize() {
        JSString composed = JSString.fromCodePoint(0x00E9);
        JSString decomposed = JSString.fromCodePoint(0x0065, 0x0301);
        JSString fullWidth = JSString.fromCodePoint(0xFF21);

        assertEquals("\u00E9", composed.asString());
        assertEquals("e\u0301", decomposed.asString());
        assertEquals("\u00E9", decomposed.normalize().asString());
        assertEquals("e\u0301", decomposed.normalize("NFD").asString());
        assertEquals("e\u0301", composed.normalize("NFD").asString());
        assertEquals("\u00E9", composed.normalize("NFC").asString());
        assertEquals("\u00E9", decomposed.normalize("NFC").asString());
        assertEquals("\uFF21", fullWidth.asString());
        assertEquals("A", fullWidth.normalize("NFKC").asString());
    }

    public static void testPadEnd() {
        JSString base = JSString.of("Hi");

        assertEquals("Hi   ", base.padEnd(5).asString());
        assertEquals("Hi***", base.padEnd(5, "*").asString());
        assertEquals("Hi*****", base.padEnd(7, JSString.of("*")).asString());
    }

    public static void testPadStart() {
        JSString base = JSString.of("Hi");

        assertEquals("   Hi", base.padStart(5).asString());
        assertEquals("---Hi", base.padStart(5, "-").asString());
        assertEquals("-----Hi", base.padStart(7, JSString.of("-")).asString());
    }

    public static void testRaw() {
        JSObject template = JSObject.create();
        template.set("raw", new String[]{"Line1\n", "Line2\t", "End"});

        assertEquals("Line1\nLine2\tEnd", JSString.raw(template).asString());
        assertEquals("Line1\nALine2\tBEnd", JSString.raw(template, "A", "B").asString());
    }

    public static void testRepeat() {
        JSString base = JSString.of("Echo");
        JSString helloString = JSString.of(HELLO_STRING);

        assertEquals("EchoEchoEcho", base.repeat(3).asString());
        assertEquals("HelloHelloHelloHello", helloString.repeat(4).asString());
    }

    public static void testReplace() {
        JSString phrase = JSString.of("foo bar foo");
        JSString digits = JSString.of("Price: 42");
        JSString helloWorldString = JSString.of(HELLO_WORLD_STRING);
        JSObject replacer1 = eval("(match) => '[' + match + ']'");
        JSValue replacer2 = fromJavaFunction((JSString match) -> JSString.of("(" + match.asString() + ")"));

        assertEquals("baz bar foo", phrase.replace(JSString.of("foo"), JSString.of("baz")).asString());
        assertEquals("baz bar foo", phrase.replace(eval("/foo/"), JSString.of("baz")).asString());
        assertEquals("World, Hello", helloWorldString.replace(eval("/(\\w+) (\\w+)/"), JSString.of("\u00242, \u00241")).asString());
        assertEquals("Price: [42]", digits.replace(eval("/\\d+/"), replacer1).asString());
        assertEquals("Price: (42)", digits.replace(eval("/\\d+/"), replacer2).asString());
        assertEquals("foo bar foo", phrase.replace(JSString.of("xyz"), JSString.of("baz")).asString());
        assertEquals("123 bar foo", phrase.replace(JSString.of("foo"), JSNumber.of(123)).asString());

        JSString multi = JSString.of("foo bar foo");
        assertEquals("baz bar baz", multi.replace(eval("/foo/g"), JSString.of("baz")).asString());
    }

    public static void testReplaceAll() {
        JSString multi = JSString.of("foo bar foo");

        assertEquals("baz bar baz", multi.replace(eval("/foo/g"), JSString.of("baz")).asString());
    }

    public static void testSearch() {
        JSString text = JSString.of("Find 42 here");

        assertEquals(5, text.search(eval("/\\d+/")));
        assertEquals(0, text.search("Find"));
        assertEquals(-1, text.search(eval("/find/")));
        assertEquals(0, text.search(eval("/find/i")));
        assertEquals(-1, text.search(eval("/^42/")));
        assertEquals(-1, text.search("XYZ"));
    }

    public static void testSlice() {
        JSString longText = JSString.of(LONG_TEXT);

        assertEquals("the lazy dog.", longText.slice(31).asString());
        assertEquals("dog.", longText.slice(-4).asString());
        assertEquals("quick brown fox", longText.slice(4, 19).asString());
        assertEquals("lazy", longText.slice(-9, -5).asString());
    }

    public static void testSplit() {
        JSString csv = JSString.of("red,green,blue,yellow");
        JSObject regexObject = eval("/,/");

        String result1 = objToString(csv.split(","));
        String result2 = objToString(csv.split(",", 2));
        String result3 = objToString(csv.split(regexObject));
        String result4 = objToString(csv.split(regexObject, 2));
        String result5 = objToString(csv.split(""));

        assertEquals("red,green,blue,yellow", result1);
        assertEquals("red,green", result2);
        assertEquals("red,green,blue,yellow", result3);
        assertEquals("red,green", result4);
        assertEquals("r,e,d,,,g,r,e,e,n,,,b,l,u,e,,,y,e,l,l,o,w", result5);
    }

    public static void testStartsWith() {
        JSString text = JSString.of("To be, or not to be, that is the question.");

        assertTrue(text.startsWith("To be"));
        assertFalse(text.startsWith("to be"));
        assertTrue(text.startsWith(JSString.of("To be")));
        assertFalse(text.startsWith(JSString.of("question")));
        assertTrue(text.startsWith("not", 10));
        assertFalse(text.startsWith("To", 3));
        assertTrue(text.startsWith(JSString.of("not"), 10));
        assertFalse(text.startsWith(JSString.of("To"), 3));
        assertFalse(text.startsWith("To", 100));
        assertFalse(text.startsWith(JSString.of("To"), 100));
    }

    public static void testToLocaleLowerCase() {
        JSString turkish = JSString.fromCodePoint(0x0130).concat(JSString.of("stanbul"));
        JSString english = JSString.of("HELLO WORLD");

        assertEquals("i\u0307stanbul", turkish.toLocaleLowerCase().asString());
        assertEquals("istanbul", turkish.toLocaleLowerCase("tr").asString());
        assertEquals("istanbul", turkish.toLocaleLowerCase(JSString.of("tr")).asString());
        assertEquals("hello world", english.toLocaleLowerCase().asString());
        assertEquals("hello world", english.toLocaleLowerCase("en").asString());
        assertEquals("hello world", english.toLocaleLowerCase(JSString.of("en")).asString());
    }

    public static void testToLocaleUpperCase() {
        JSString turkish = JSString.fromCodePoint(0x0069).concat(JSString.of("stanbul"));
        JSString english = JSString.of("hello world");

        assertEquals("ISTANBUL", turkish.toLocaleUpperCase().asString());
        assertEquals("\u0130STANBUL", turkish.toLocaleUpperCase("tr").asString());
        assertEquals("\u0130STANBUL", turkish.toLocaleUpperCase(JSString.of("tr")).asString());
        assertEquals("HELLO WORLD", english.toLocaleUpperCase().asString());
        assertEquals("HELLO WORLD", english.toLocaleUpperCase("en").asString());
        assertEquals("HELLO WORLD", english.toLocaleUpperCase(JSString.of("en")).asString());
    }

    public static void testToLowerCase() {
        JSString longText = JSString.of(LONG_TEXT);

        assertEquals("the quick brown fox jumps over the lazy dog.", longText.toLowerCase().asString());
    }

    public static void testToUpperCase() {
        JSString longText = JSString.of(LONG_TEXT);

        assertEquals("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG.", longText.toUpperCase().asString());
    }

    public static void testToWellFormed() {
        JSString str1 = JSString.of("ab").concat(JSString.fromCodePoint(0xD800));
        JSString str2 = str1.concat(JSString.of("c"));
        JSString str3 = JSString.fromCodePoint(0xDFFF).concat(JSString.of("ab"));
        JSString str4 = JSString.of("c").concat(JSString.fromCodePoint(0xDFFF)).concat(JSString.of("ab"));
        JSString str5 = JSString.of("abc");
        JSString str6 = JSString.of("ab").concat(JSString.fromCodePoint(0x1F604)).concat(JSString.of("c"));

        assertEquals("ab\uFFFD", str1.toWellFormed().asString());
        assertEquals("ab\uFFFDc", str2.toWellFormed().asString());
        assertEquals("\uFFFDab", str3.toWellFormed().asString());
        assertEquals("c\uFFFDab", str4.toWellFormed().asString());
        assertEquals("abc", str5.toWellFormed().asString());
        assertEquals("ab\uD83D\uDE04c", str6.toWellFormed().asString());
    }

    public static void testTrim() {
        JSString padded = JSString.of("   To be, or not to be   ");

        assertEquals("To be, or not to be", padded.trim().asString());
        assertEquals("To be, or not to be   ", padded.trimStart().asString());
        assertEquals("   To be, or not to be", padded.trimEnd().asString());
    }

    public static void testSubstring() {
        JSString text = JSString.of("JavaScript");

        assertEquals("Java", text.substring(0, 4).asString());
        assertEquals("Script", text.substring(4).asString());
        assertEquals("Java", text.substring(4, 0).asString());
        assertEquals("Script", text.substring(4, 100).asString());
        assertEquals("", text.substring(100, 200).asString());
        assertEquals("Java", text.substring(-4, 4).asString());
        assertEquals("JavaScript", text.substring(-10, 100).asString());
    }

    @JS.Coerce
    @JS(value = "return eval(script);")
    private static native JSObject eval(String script);

    @JS.Coerce
    @JS("return Array.from(it).toString();")
    private static native String iteratorToString(Object it);

    @JS.Coerce
    @JS("return it.toString();")
    private static native String objToString(Object it);

    @JS.Coerce
    @JS(value = "return function(args) { return javaFunc.apply(args); }")
    public static native <T, R> JSValue fromJavaFunction(Function<T, R> javaFunc);
}
