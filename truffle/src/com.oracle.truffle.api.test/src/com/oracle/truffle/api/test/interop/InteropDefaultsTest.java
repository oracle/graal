/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

@SuppressWarnings("deprecation")
public class InteropDefaultsTest extends InteropLibraryBaseTest {

    public static class TestInterop1 {
    }

    @Test
    public void testBooleanDefault() throws InteropException {
        assertBoolean(true, true);
        assertBoolean(false, false);
    }

    private void assertBoolean(Object v, boolean expected) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isBoolean(v));
        assertEquals(expected, library.asBoolean(v));
        assertEquals(v.toString(), library.toDisplayString(v));

        // assert boolean
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoString(v);
        assertNoNumber(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    @Test
    public void testByteDefault() throws InteropException {
        assertNumber(Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((byte) 0, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE, true, true, true, true, true, true);
    }

    @Test
    public void testShortDefault() throws InteropException {
        assertNumber(Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((short) (Byte.MIN_VALUE - 1), false, true, true, true, true, true);
        assertNumber((short) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((short) 0, true, true, true, true, true, true);
        assertNumber((short) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((short) (Byte.MAX_VALUE + 1), false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE, false, true, true, true, true, true);
    }

    @Test
    public void testIntDefault() throws InteropException {
        assertNumber(Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber(Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((int) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber(Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((int) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0, true, true, true, true, true, true);
        assertNumber((int) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((int) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber(1 << 24, false, false, true, true, true, true);
        assertNumber((1 << 24) + 1, false, false, true, true, false, true);
        assertNumber(1 << 25, false, false, true, true, true, true);
        assertNumber(Integer.MAX_VALUE, false, false, true, true, true, true);
    }

    @Test
    public void testLongDefault() throws InteropException {
        assertNumber(Long.MIN_VALUE, false, false, false, true, true, true);
        assertNumber((long) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((long) Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber((long) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((long) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0L, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((long) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber(1L << 24, false, false, true, true, true, true);
        assertNumber((1L << 24) + 1, false, false, true, true, false, true);
        assertNumber(1L << 25, false, false, true, true, true, true);
        assertNumber((1L << 53) + 1, false, false, false, true, false, false);
        assertNumber(1L << 54, false, false, false, true, true, true);
        assertNumber(Long.MAX_VALUE, false, false, false, true, true, true);
    }

    @Test
    public void testFloatDefault() throws InteropException {
        assertNumber(Float.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((float) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE - 1, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((float) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0f, false, false, false, false, true, true);
        assertNumber(0.0f, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((float) Integer.MAX_VALUE, false, false, false, false, true, true);
        assertNumber((float) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Float.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Float.NaN, false, false, false, false, true, true);
        assertNumber(Float.MIN_VALUE, false, false, false, false, true, true);
        assertNumber(Float.MIN_NORMAL, false, false, false, false, true, true);
        assertNumber(Float.MAX_VALUE, false, false, false, false, true, true);
    }

    @Test
    public void testDoubleDefault() throws InteropException {
        assertNumber(Double.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((double) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((double) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((double) Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0d, false, false, false, false, true, true);
        assertNumber(0.0d, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((double) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber((double) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Double.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Double.NaN, false, false, false, false, true, true);
        assertNumber(Double.MIN_VALUE, false, false, false, false, false, true);
        assertNumber(Double.MIN_NORMAL, false, false, false, false, false, true);
        assertNumber(Double.MAX_VALUE, false, false, false, false, false, true);
    }

    @Test
    public void testStringDefaults() throws InteropException {
        assertString("foo", "foo");
        assertString("bar", "bar");
    }

    @Test
    public void testCharacterDefaults() throws InteropException {
        assertString('a', "a");
        assertString('b', "b");
    }

    private void assertString(Object v, String expectedString) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isString(v));
        assertEquals(expectedString, library.asString(v));

        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        // assert string
        assertNoNumber(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    private void assertNumber(Object v, boolean supportsByte, boolean supportsShort,
                    boolean supportsInt, boolean supportsLong, boolean supportsFloat, boolean supportsDouble) throws InteropException {

        Object expectedValue = v;

        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        assertTrue(l.isNumber(v));

        assertEquals(supportsByte, l.fitsInByte(v));
        assertEquals(supportsShort, l.fitsInShort(v));
        assertEquals(supportsInt, l.fitsInInt(v));
        assertEquals(supportsLong, l.fitsInLong(v));
        assertEquals(supportsFloat, l.fitsInFloat(v));
        assertEquals(supportsDouble, l.fitsInDouble(v));

        if (supportsByte) {
            assertEquals(((Number) expectedValue).byteValue(), l.asByte(v));
        } else {
            assertUnsupported(() -> l.asByte(v));
        }
        if (supportsShort) {
            assertEquals(((Number) expectedValue).shortValue(), l.asShort(v));
        } else {
            assertUnsupported(() -> l.asShort(v));
        }
        if (supportsInt) {
            assertEquals(((Number) expectedValue).intValue(), l.asInt(v));
        } else {
            assertUnsupported(() -> l.asInt(v));
        }
        if (supportsLong) {
            assertEquals(((Number) expectedValue).longValue(), l.asLong(v));
        } else {
            assertUnsupported(() -> l.asLong(v));
        }
        if (supportsFloat) {
            assertEquals(((Number) expectedValue).floatValue(), l.asFloat(v), 0);
        } else {
            assertUnsupported(() -> l.asFloat(v));
        }
        if (supportsDouble) {
            assertEquals(((Number) expectedValue).doubleValue(), l.asDouble(v), 0);
        } else {
            assertUnsupported(() -> l.asDouble(v));
        }

        assertEquals(v.toString(), l.toDisplayString(v));

        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoString(v);
        // assert number
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    @Test
    public void testObjectDefaults() {
        AtomicBoolean toStringInvoked = new AtomicBoolean();

        Object v = new TruffleObject() {
            @Override
            public String toString() {
                toStringInvoked.set(true);
                return super.toString();
            }
        };
        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        String expectedToString = v.toString();
        toStringInvoked.set(false);
        assertEquals(expectedToString, l.toDisplayString(v));
        assertFalse(toStringInvoked.get());
        assertNoTypes(v);
    }

    public static class MetaDataLegacyObject implements TruffleObject {

        final SourceSection section;
        final Object metaObject;

        MetaDataLegacyObject(SourceSection section, Object metaObject) {
            this.section = section;
            this.metaObject = metaObject;
        }

    }

    public static class MetaDataLegacyOnlyLangauge implements TruffleObject {

        MetaDataLegacyOnlyLangauge() {
        }

    }

    @Test
    public void testMetaDataLegacyBehavior() throws InteropException {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object instanceof MetaDataLegacyObject || object instanceof MetaDataLegacyOnlyLangauge;
            }

            @Override
            protected SourceSection findSourceLocation(LanguageContext c, Object value) {
                if (value instanceof MetaDataLegacyObject) {
                    return ((MetaDataLegacyObject) value).section;
                }
                return null;
            }

            @Override
            protected Object findMetaObject(LanguageContext c, Object value) {
                if (value instanceof MetaDataLegacyObject) {
                    return ((MetaDataLegacyObject) value).metaObject;
                }
                return null;
            }

            @Override
            protected String toString(LanguageContext c, Object value) {
                if (value instanceof MetaDataLegacyObject) {
                    return "MetaDataLegacyObject";
                }
                return super.toString(c, value);
            }

        });
        SourceSection section = Source.newBuilder(ProxyLanguage.ID, "", "").build().createUnavailableSection();
        Object v1 = new MetaDataLegacyObject(section, "meta-object");
        InteropLibrary libV1 = createLibrary(InteropLibrary.class, v1);

        assertTrue(libV1.hasLanguage(v1));
        assertSame(ProxyLanguage.class, libV1.getLanguage(v1));
        assertTrue(libV1.hasMetaObject(v1));
        Object metaObject = libV1.getMetaObject(v1);
        InteropLibrary metaObjectInterop = createLibrary(InteropLibrary.class, metaObject);
        assertTrue(metaObjectInterop.isMetaObject(metaObject));
        assertTrue(metaObjectInterop.isMetaInstance(metaObject, v1));
        assertEquals("meta-object", metaObjectInterop.toDisplayString(metaObject));
        assertEquals("meta-object", metaObjectInterop.getMetaSimpleName(metaObject));
        assertEquals("meta-object", metaObjectInterop.getMetaQualifiedName(metaObject));
        assertTrue(libV1.hasSourceLocation(v1));
        assertSame(section, libV1.getSourceLocation(v1));
        assertEquals("MetaDataLegacyObject", libV1.toDisplayString(v1));

        assertNoBoolean(v1);
        assertNotNull(v1);
        assertNoObject(v1);
        assertNoArray(v1);
        assertNoString(v1);
        assertNoNumber(v1);
        assertNoNative(v1);
        assertNotExecutable(v1);
        assertNotInstantiable(v1);
        assertNoMetaObject(v1);
        // has meta-object
        assertNoDate(v1);
        assertNoTime(v1);
        assertNoTimeZone(v1);
        assertNoDuration(v1);
        // has source section
        // has language

        Object v2 = new MetaDataLegacyOnlyLangauge();
        InteropLibrary libV2 = createLibrary(InteropLibrary.class, v2);
        assertTrue(libV2.hasLanguage(v2));
        assertSame(ProxyLanguage.class, libV2.getLanguage(v2));
        assertFalse(libV2.hasMetaObject(v2));
        assertFails(() -> libV2.getMetaObject(v2), UnsupportedMessageException.class);
        assertFalse(libV2.hasSourceLocation(v2));
        assertFails(() -> libV2.getSourceLocation(v2), UnsupportedMessageException.class);
        assertEquals(v2.toString(), libV2.toDisplayString(v2));

        assertNoBoolean(v2);
        assertNotNull(v2);
        assertNoObject(v2);
        assertNoArray(v2);
        assertNoString(v2);
        assertNoNumber(v2);
        assertNoNative(v2);
        assertNotExecutable(v2);
        assertNotInstantiable(v2);
        assertNoMetaObject(v2);
        assertHasNoMetaObject(v2);
        assertNoDate(v2);
        assertNoTime(v2);
        assertNoTimeZone(v2);
        assertNoDuration(v2);
        assertNoSourceLocation(v2);
        // has language
    }

    private void assertNoTypes(Object v) {
        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoString(v);
        assertNoNumber(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

}
