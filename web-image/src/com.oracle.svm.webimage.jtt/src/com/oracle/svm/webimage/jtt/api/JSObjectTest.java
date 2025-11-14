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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSSymbol;
import org.graalvm.webimage.api.JSValue;
import org.graalvm.webimage.api.ThrownFromJavaScript;

public class JSObjectTest {

    public static void main(String[] args) {
        testPrototypeInheritance();
        testCreateWithProperties();
        testDefineProperties();
        testDefinePropertyVariants();
        testEntries();
        testFreeze();
        testFromEntries();
        testGetOwnPropertyDescriptor();
        testGetOwnPropertyNames();
        testGroupBy();
        testHasOwn();
        testIsEquality();
        testIsExtensibleAndPreventExtensions();
        testIsFrozenAndFreeze();
        testPrototypeChain();
        testSealAndMutation();
        testKeysAndValues();
        testPreventExtensions();
        testPropertyIsEnumerable();
        testPrototypeMethodBinding();
        testToLocaleString();
        testValueOf();
        testValues();
    }

    public static void testPrototypeInheritance() {
        JSObject proto = JSObject.create();
        proto.set("greet", fromJavaFunction((String name) -> "Hello, " + name));

        JSObject obj = JSObject.create(proto);
        JSValue fun = (JSValue) obj.get("greet");
        String greeting = apply(fun, null, "Alice");

        assertEquals("Hello, Alice", greeting);
    }

    public static void testCreateWithProperties() {
        JSObject proto = JSObject.create();
        proto.set("greet", fromJavaFunction((String name) -> "Hello, " + name));
        JSObject nameDescriptor = JSObject.create();
        set(nameDescriptor, JSString.of("Bob"), false, true);
        JSObject properties = JSObject.create();
        properties.set("name", nameDescriptor);

        JSObject obj = JSObject.create(proto, properties);

        assertThrows(ThrownFromJavaScript.class, () -> obj.set("name", "Alice"));
    }

    public static void testDefineProperties() {
        JSObject target = JSObject.create();
        JSObject descriptors = JSObject.create();
        JSObject nameDescriptor = JSObject.create();
        set(nameDescriptor, JSString.of("Alice"), true, true);
        descriptors.set("name", nameDescriptor);
        JSObject versionDescriptor = JSObject.create();
        set(versionDescriptor, JSNumber.of(26), false, false);
        descriptors.set("age", versionDescriptor);

        JSObject result = JSObject.defineProperties(target, descriptors);
        String name1 = result.get("name", String.class);
        int age = result.get("age", Integer.class);
        result.set("name", JSString.of("Bob"));

        assertEquals("Alice", name1);
        assertEquals(26, age);
        assertThrows(ThrownFromJavaScript.class, () -> result.set("age", JSNumber.of(21)));
        assertEquals("Bob", result.get("name", String.class));
    }

    public static void testDefinePropertyVariants() {
        JSObject obj1 = JSObject.create();
        JSObject obj2 = JSObject.create();
        JSObject descriptor = JSObject.create();
        set(descriptor, JSString.of("Alice"), false, true);

        JSObject.defineProperty(obj1, JSString.of("name"), descriptor);
        JSObject.defineProperty(obj2, "name", descriptor);

        assertThrows(ThrownFromJavaScript.class, () -> obj1.set("name", "NotAlice"));
        assertThrows(ThrownFromJavaScript.class, () -> obj2.set("name", "StillNotAlice"));
        assertEquals("Alice", obj1.get("name", String.class));
        assertEquals("Alice", obj2.get("name", String.class));
    }

    public static void testEntries() {
        JSObject obj = JSObject.create();
        obj.set("language", "JavaScript");
        obj.set("version", "ES2025");

        String result = objToString(JSObject.entries(obj));

        assertEquals("language,JavaScript,version,ES2025", result);
    }

    public static void testFreeze() {
        JSObject obj = JSObject.create();
        obj.set("name", "Alice");

        JSObject.freeze(obj);

        assertThrows(ThrownFromJavaScript.class, () -> obj.set("name", "Changed"));
        assertEquals("Alice", obj.get("name", String.class));
    }

    public static void testFromEntries() {
        JSObject entries1 = createArray("framework", "GraalVM");
        JSObject entries2 = createArray("mode", "native");
        JSObject entries = createArray(entries1, entries2);

        JSObject result = JSObject.fromEntries(entries);
        String keys = objToString(result.keys());

        assertEquals("framework,mode", keys);
        assertEquals("GraalVM", result.get("framework"));
        assertEquals("native", result.get("mode"));
    }

    public static void testGetOwnPropertyDescriptor() {
        JSObject obj = JSObject.create();
        obj.set("name", "Alice");

        JSObject descriptor = JSObject.getOwnPropertyDescriptor(obj, "name");

        assertEquals("Alice", descriptor.get("value", String.class));
        assertTrue(descriptor.get("writable", Boolean.class));
        assertTrue(descriptor.get("enumerable", Boolean.class));
        assertTrue(descriptor.get("configurable", Boolean.class));
    }

    public static void testGetOwnPropertyNames() {
        JSObject obj = JSObject.create();
        obj.set("x", 1);
        obj.set("y", 2);

        String result = objToString(JSObject.getOwnPropertyNames(obj));
        assertEquals("x,y", result);
    }

    public static void testGroupBy() {
        JSObject item1 = createItem("asparagus", "vegetable", 5);
        JSObject item2 = createItem("bananas", "fruit", 0);
        JSObject item3 = createItem("cherries", "fruit", 5);
        JSObject item4 = createItem("goat", "meat", 23);
        JSObject item5 = createItem("fish", "meat", 22);
        JSObject items = createArray(item1, item2, item3, item4, item5);

        JSValue groupByType = fromJavaFunction((JSObject item) -> item.get("type"));
        JSObject grouped = JSObject.groupBy(items, groupByType);

        assertEquals("asparagus", grouped.get("vegetable", JSObject.class).get(0, JSObject.class).get("name"));
        assertEquals("bananas", grouped.get("fruit", JSObject.class).get(0, JSObject.class).get("name"));
        assertEquals("cherries", grouped.get("fruit", JSObject.class).get(1, JSObject.class).get("name"));
        assertEquals("goat", grouped.get("meat", JSObject.class).get(0, JSObject.class).get("name"));
        assertEquals("fish", grouped.get("meat", JSObject.class).get(1, JSObject.class).get("name"));
    }

    public static void testHasOwn() {
        JSObject obj = JSObject.create();
        obj.set("x", 10);
        JSSymbol sym = JSSymbol.forKey("x");
        obj.set(sym, 42);
        JSSymbol missing = JSSymbol.forKey("y");

        assertTrue(JSObject.hasOwn(obj, "x"));
        assertFalse(JSObject.hasOwn(obj, "y"));
        assertTrue(JSObject.hasOwn(obj, JSString.of("x")));
        assertFalse(JSObject.hasOwn(obj, JSString.of("y")));
        assertTrue(JSObject.hasOwn(obj, sym));
        assertFalse(JSObject.hasOwn(obj, missing));
    }

    public static void testIsEquality() {
        JSObject obj1 = JSObject.create();
        obj1.set("value", 1);
        JSObject obj2 = JSObject.create();
        obj2.set("value", 1);

        assertTrue(JSObject.is(JSString.of("hello"), JSString.of("hello")));
        assertFalse(JSObject.is(JSString.of("5"), JSNumber.of(5)));
        assertTrue(JSObject.is(JSNumber.of(Double.NaN), JSNumber.of(Double.NaN)));
        assertFalse(JSObject.is(JSNumber.of(0.0), JSNumber.of(-0.0)));
        assertTrue(JSObject.is(JSBoolean.of(true), JSBoolean.of(true)));
        assertFalse(JSObject.is(obj1, obj2));
        assertTrue(JSObject.is(obj1, obj1));
    }

    public static void testIsExtensibleAndPreventExtensions() {
        JSObject obj = createTestObject();

        boolean result1 = JSObject.isExtensible(obj);
        JSObject.preventExtensions(obj);
        boolean result2 = JSObject.isExtensible(obj);

        assertTrue(result1);
        assertFalse(result2);
        assertThrows(ThrownFromJavaScript.class, () -> obj.set("newProp", "test"));
        assertFalse(JSObject.hasOwn(obj, "newProp"));
    }

    public static void testIsFrozenAndFreeze() {
        JSObject obj = createTestObject();

        boolean result1 = JSObject.isFrozen(obj);
        JSObject.freeze(obj);
        boolean result2 = JSObject.isFrozen(obj);

        assertFalse(result1);
        assertTrue(result2);
        assertThrows(ThrownFromJavaScript.class, () -> obj.set("name", "Bob"));
        assertThrows(ThrownFromJavaScript.class, () -> obj.set("newProp", "test"));
        assertEquals("Alice", obj.get("name"));
        assertFalse(JSObject.hasOwn(obj, "newProp"));
    }

    public static void testPrototypeChain() {
        JSObject proto = JSObject.create();
        JSObject obj = JSObject.create();
        JSObject.setPrototypeOf(obj, proto);

        assertTrue(proto.isPrototypeOf(obj));
        assertFalse(obj.isPrototypeOf(proto));
    }

    public static void testSealAndMutation() {
        JSObject obj = createTestObject();

        boolean result1 = JSObject.isSealed(obj);
        JSObject.seal(obj);
        boolean result2 = JSObject.isSealed(obj);
        obj.set("name", "Bob");

        assertFalse(result1);
        assertTrue(result2);
        assertThrows(ThrownFromJavaScript.class, () -> obj.set("newProp", "test"));
        assertEquals("Bob", obj.get("name", String.class));
        assertFalse(JSObject.hasOwn(obj, "newProp"));
    }

    public static void testKeysAndValues() {
        JSObject obj = createTestObject();
        obj.set("age", "27");
        obj.set("active", "true");

        String keys = objToString(JSObject.keys(obj));
        String values = objToString(JSObject.values(obj));

        assertEquals("name,age,active", keys);
        assertEquals("Alice,27,true", values);
    }

    public static void testPreventExtensions() {
        JSObject obj = createTestObject();

        boolean result1 = JSObject.isExtensible(obj);
        JSObject.preventExtensions(obj);
        boolean result2 = JSObject.isExtensible(obj);

        assertTrue(result1);
        assertFalse(result2);
        assertThrows(ThrownFromJavaScript.class, () -> obj.set("newProp", "test"));
        assertFalse(JSObject.hasOwn(obj, "newProp"));
    }

    public static void testPropertyIsEnumerable() {
        JSObject obj = JSObject.create();
        obj.set("visible", "yes");
        JSObject descriptors = JSObject.create();
        JSObject hiddenDescriptor = JSObject.create();
        hiddenDescriptor.set("value", "no");
        hiddenDescriptor.set("enumerable", JSBoolean.of(false));
        descriptors.set("hidden", hiddenDescriptor);
        JSObject.defineProperties(obj, descriptors);
        JSSymbol sym = JSSymbol.forKey("secret");
        obj.set(sym, "classified");
        JSObject symbolDescriptor = JSObject.create();
        symbolDescriptor.set("value", "classified");
        symbolDescriptor.set("enumerable", JSBoolean.of(false));
        JSObject symbolDescriptors = JSObject.create();
        symbolDescriptors.set(sym, symbolDescriptor);
        JSObject.defineProperties(obj, symbolDescriptors);

        assertTrue(obj.propertyIsEnumerable("visible"));
        assertFalse(obj.propertyIsEnumerable("hidden"));
        assertFalse(obj.propertyIsEnumerable("missing"));
        assertTrue(obj.propertyIsEnumerable(JSString.of("visible")));
        assertFalse(obj.propertyIsEnumerable(JSString.of("hidden")));
        assertFalse(obj.propertyIsEnumerable(JSString.of("missing")));
        assertFalse(obj.propertyIsEnumerable(sym));
    }

    public static void testPrototypeMethodBinding() {
        JSObject obj = createTestObject();

        JSObject proto = JSObject.create();
        proto.set("describe", fromJSArgs("return 'I am ' + String(this.name);"));
        JSObject result = JSObject.setPrototypeOf(obj, proto);
        JSValue describeFn = (JSValue) result.get("describe");
        String description = JSValue.checkedCoerce(apply(describeFn, result), String.class);

        assertEquals("I am Alice", description);
    }

    public static void testToLocaleString() {
        JSObject obj = createTestObject();
        obj.set("region", "Austria");

        assertEquals("[object Object]", obj.toLocaleString());
    }

    public static void testValueOf() {
        JSObject obj = JSObject.create();
        obj.set("id", 42);

        JSObject result = obj.valueOf().as(JSObject.class);
        int value = result.get("id", Integer.class);

        assertEquals(42, value);
    }

    public static void testValues() {
        JSObject obj = createTestObject();
        obj.set("age", "27");
        obj.set("active", "true");

        String values = objToString(JSObject.values(obj));

        assertEquals("Alice,27,true", values);
    }

    private static void set(JSObject obj, JSValue value, boolean writable, boolean configurable) {
        obj.set("value", value);
        obj.set("writable", JSBoolean.of(writable));
        obj.set("enumerable", JSBoolean.of(true));
        obj.set("configurable", JSBoolean.of(configurable));
    }

    private static JSObject createItem(String name, String type, int quantity) {
        JSObject obj = JSObject.create();
        obj.set("name", name);
        obj.set("type", type);
        obj.set("quantity", quantity);
        return obj;
    }

    private static JSObject createTestObject() {
        JSObject obj = JSObject.create();
        obj.set("name", "Alice");
        return obj;
    }

    @JS.Coerce
    @JS(value = "return function(args) { return javaFunc.apply(args); }")
    public static native <T, R> JSValue fromJavaFunction(Function<T, R> javaFunc);

    @JS.Coerce
    @JS(value = "return Function.apply(null, args);")
    public static native JSValue fromJSArgs(String... args);

    @SafeVarargs
    @JS.Coerce
    @JS(value = "return fun.apply(thisArg, args);")
    public static native <T, R, Q> R apply(JSValue fun, Q thisArg, T... args);

    @JS.Coerce
    @JS("return it.toString();")
    private static native String objToString(Object it);

    @JS(value = "return Array.from(values);")
    public static native JSObject createArray(Object... values);
}
