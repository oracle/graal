/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.utilities.TriState;

@SuppressWarnings("deprecation")
public class InteropAssertionsTest extends InteropLibraryBaseTest {

    @ExportLibrary(InteropLibrary.class)
    static class TestStringWrapper implements TruffleObject {

        final String string;

        TestStringWrapper(String string) {
            this.string = string;
        }

        @ExportMessage
        boolean isString() {
            return true;
        }

        @ExportMessage
        String asString() {
            return string;
        }
    }

    static class TestEmpty implements TruffleObject {
    }

    @ExportLibrary(InteropLibrary.class)
    static class ToDisplayStringTest implements TruffleObject {

        Object toDisplayString;

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return toDisplayString;
        }
    }

    @Test
    public void testToDisplayString() {
        ToDisplayStringTest v = new ToDisplayStringTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);

        v.toDisplayString = "myString";
        assertSame(v.toDisplayString, l.toDisplayString(v));

        v.toDisplayString = new TestStringWrapper("myString");
        assertSame(v.toDisplayString, l.toDisplayString(v));

        v.toDisplayString = null;
        assertFails(() -> l.toDisplayString(v, true), NullPointerException.class);

        v.toDisplayString = new TestEmpty();
        assertFails(() -> l.toDisplayString(v, true), AssertionError.class);
    }

    @ExportLibrary(InteropLibrary.class)
    static class SourceSectionTest implements TruffleObject {

        Supplier<SourceSection> sourceSection;
        boolean hasSourceSection;

        @ExportMessage
        boolean hasSourceLocation() {
            return hasSourceSection;
        }

        @ExportMessage
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            if (sourceSection == null) {
                throw UnsupportedMessageException.create();
            }
            return sourceSection.get();
        }
    }

    @Test
    public void testSourceLocation() throws UnsupportedMessageException {
        SourceSectionTest v = new SourceSectionTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        SourceSection section = Source.newBuilder(ProxyLanguage.ID, "", "").build().createUnavailableSection();

        assertFails(() -> l.getSourceLocation(null), NullPointerException.class);

        v.hasSourceSection = false;
        v.sourceSection = null;
        assertFalse(l.hasSourceLocation(v));
        assertFails(() -> l.getSourceLocation(v), UnsupportedMessageException.class);

        v.hasSourceSection = true;
        v.sourceSection = () -> section;
        assertTrue(l.hasSourceLocation(v));
        assertSame(section, l.getSourceLocation(v));

        v.hasSourceSection = true;
        v.sourceSection = null;
        assertFails(() -> l.hasSourceLocation(v), AssertionError.class);
        assertFails(() -> l.getSourceLocation(v), AssertionError.class);

        v.hasSourceSection = true;
        v.sourceSection = () -> null;
        assertFails(() -> l.hasSourceLocation(v), AssertionError.class);
        assertFails(() -> l.getSourceLocation(v), AssertionError.class);

        v.hasSourceSection = false;
        v.sourceSection = () -> section;
        assertFails(() -> l.hasSourceLocation(v), AssertionError.class);
        assertFails(() -> l.getSourceLocation(v), AssertionError.class);

    }

    @ExportLibrary(InteropLibrary.class)
    static class GetLanguageTest implements TruffleObject {

        Supplier<Class<? extends TruffleLanguage<?>>> getLanguage;
        boolean hasLanguage;

        @ExportMessage
        boolean hasLanguage() {
            return hasLanguage;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() throws UnsupportedMessageException {
            if (getLanguage == null) {
                throw UnsupportedMessageException.create();
            }
            return getLanguage.get();
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "";
        }
    }

    @Test
    public void testGetLanguage() throws UnsupportedMessageException {
        GetLanguageTest v = new GetLanguageTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        Class<? extends TruffleLanguage<?>> testLanguage = ProxyLanguage.class;

        assertFails(() -> l.getSourceLocation(null), NullPointerException.class);

        v.hasLanguage = false;
        v.getLanguage = null;
        assertFalse(l.hasLanguage(v));
        assertFails(() -> l.getLanguage(v), UnsupportedMessageException.class);

        v.hasLanguage = true;
        v.getLanguage = () -> testLanguage;
        assertTrue(l.hasLanguage(v));
        assertSame(testLanguage, l.getLanguage(v));

        v.hasLanguage = true;
        v.getLanguage = null;
        assertFails(() -> l.hasLanguage(v), AssertionError.class);
        assertFails(() -> l.getLanguage(v), AssertionError.class);

        v.hasLanguage = true;
        v.getLanguage = () -> null;
        assertFails(() -> l.hasLanguage(v), AssertionError.class);
        assertFails(() -> l.getLanguage(v), AssertionError.class);

        v.hasLanguage = false;
        v.getLanguage = () -> testLanguage;
        assertFails(() -> l.hasLanguage(v), AssertionError.class);
        assertFails(() -> l.getLanguage(v), AssertionError.class);

    }

    @ExportLibrary(InteropLibrary.class)
    static class GetMetaObjectTest implements TruffleObject {

        Supplier<Object> getMetaObject;
        boolean hasMetaObject;

        @ExportMessage
        boolean hasMetaObject() {
            return hasMetaObject;
        }

        @ExportMessage
        Object getMetaObject() throws UnsupportedMessageException {
            if (getMetaObject == null) {
                throw UnsupportedMessageException.create();
            }
            return getMetaObject.get();
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class MetaObjectTest implements TruffleObject {

        Supplier<Object> getMetaQualifiedName;
        Supplier<Object> getMetaSimpleName;
        Function<Object, Boolean> isMetaInstance;
        boolean isMetaObject;

        @ExportMessage
        boolean isMetaObject() {
            return isMetaObject;
        }

        @ExportMessage
        final Object getMetaQualifiedName() throws UnsupportedMessageException {
            if (getMetaQualifiedName == null) {
                throw UnsupportedMessageException.create();
            }
            return getMetaQualifiedName.get();
        }

        @ExportMessage
        final Object getMetaSimpleName() throws UnsupportedMessageException {
            if (getMetaSimpleName == null) {
                throw UnsupportedMessageException.create();
            }
            return getMetaSimpleName.get();
        }

        @ExportMessage
        final boolean isMetaInstance(Object instance) throws UnsupportedMessageException {
            if (isMetaInstance == null) {
                throw UnsupportedMessageException.create();
            }
            return isMetaInstance.apply(instance);
        }

    }

    @Test
    public void testGetMetaObject() throws UnsupportedMessageException {
        GetMetaObjectTest v = new GetMetaObjectTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        MetaObjectTest testMeta = new MetaObjectTest();
        testMeta.isMetaObject = true;
        testMeta.isMetaInstance = (o) -> o == v;
        testMeta.getMetaQualifiedName = () -> "testQualifiedName";
        testMeta.getMetaSimpleName = () -> "testSimpleName";

        v.hasMetaObject = false;
        v.getMetaObject = null;
        assertFalse(l.hasMetaObject(v));
        assertFails(() -> l.getMetaObject(v), UnsupportedMessageException.class);

        v.hasMetaObject = true;
        v.getMetaObject = () -> testMeta;
        assertTrue(l.hasMetaObject(v));
        assertSame(testMeta, l.getMetaObject(v));

        v.hasMetaObject = true;
        v.getMetaObject = null;
        assertFails(() -> l.hasMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaObject(v), AssertionError.class);

        v.hasMetaObject = true;
        v.getMetaObject = () -> null;
        assertFails(() -> l.hasMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaObject(v), AssertionError.class);

        v.hasMetaObject = false;
        v.getMetaObject = () -> testMeta;
        assertFails(() -> l.hasMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaObject(v), AssertionError.class);

        v.hasMetaObject = true;
        v.getMetaObject = () -> testMeta;
        testMeta.getMetaQualifiedName = null;
        assertFails(() -> l.hasMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaObject(v), AssertionError.class);

        v.hasMetaObject = true;
        v.getMetaObject = () -> testMeta;
        testMeta.getMetaQualifiedName = () -> "testQualifiedName";
        testMeta.getMetaSimpleName = null;
        assertFails(() -> l.hasMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaObject(v), AssertionError.class);

        v.hasMetaObject = true;
        v.getMetaObject = () -> testMeta;
        testMeta.getMetaSimpleName = () -> "testSimpleName";
        testMeta.isMetaInstance = (o) -> false;
        assertFails(() -> l.hasMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaObject(v), AssertionError.class);

    }

    @Test
    public void testMetaObject() throws UnsupportedMessageException {
        GetMetaObjectTest instance = new GetMetaObjectTest();
        MetaObjectTest v = new MetaObjectTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);

        v.isMetaObject = false;
        v.isMetaInstance = null;
        v.getMetaQualifiedName = null;
        v.getMetaSimpleName = null;
        assertFalse(l.isMetaObject(v));
        assertFails(() -> l.getMetaQualifiedName(v), UnsupportedMessageException.class);
        assertFails(() -> l.getMetaSimpleName(v), UnsupportedMessageException.class);
        assertFails(() -> l.isMetaInstance(v, v), UnsupportedMessageException.class);

        v.isMetaObject = true;
        v.isMetaInstance = (o) -> o == instance;
        v.getMetaQualifiedName = () -> "testQualifiedName";
        v.getMetaSimpleName = () -> "testSimpleName";
        assertTrue(l.isMetaObject(v));
        assertEquals("testQualifiedName", l.getMetaQualifiedName(v));
        assertEquals("testSimpleName", l.getMetaSimpleName(v));
        assertTrue(l.isMetaInstance(v, instance));
        assertFalse(l.isMetaInstance(v, new GetMetaObjectTest()));
        assertFails(() -> l.isMetaInstance(v, new Object()), ClassCastException.class);

        v.isMetaObject = true;
        v.isMetaInstance = null;
        v.getMetaQualifiedName = null;
        v.getMetaSimpleName = null;
        assertFails(() -> l.isMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaSimpleName(v), AssertionError.class);
        assertFails(() -> l.getMetaQualifiedName(v), AssertionError.class);
        assertFails(() -> l.isMetaInstance(v, v), AssertionError.class);

        v.isMetaObject = true;
        v.isMetaInstance = (o) -> o == instance;
        v.getMetaQualifiedName = () -> new Object();
        v.getMetaSimpleName = () -> new Object();
        assertFails(() -> l.isMetaObject(v), ClassCastException.class);
        assertFails(() -> l.getMetaSimpleName(v), ClassCastException.class);
        assertFails(() -> l.getMetaQualifiedName(v), ClassCastException.class);
        assertFails(() -> l.isMetaInstance(v, new Object()), ClassCastException.class);

        v.isMetaObject = true;
        v.isMetaInstance = (o) -> o == instance;
        TestStringWrapper testQualifiedName = new TestStringWrapper("testQualifiedName");
        TestStringWrapper testSimpleName = new TestStringWrapper("testSimpleName");
        v.getMetaQualifiedName = () -> testQualifiedName;
        v.getMetaSimpleName = () -> testSimpleName;
        assertTrue(l.isMetaObject(v));
        assertSame(testQualifiedName, l.getMetaQualifiedName(v));
        assertSame(testSimpleName, l.getMetaSimpleName(v));
    }

    @FunctionalInterface
    interface IsSameOrUndefined {

        TriState isSameOrUndefined(Object receiver, Object other);

    }

    @FunctionalInterface
    interface IdentityHashCode {

        int identityHashCode(Object receiver) throws UnsupportedMessageException;

    }

    @ExportLibrary(InteropLibrary.class)
    static class IdentityTest implements TruffleObject {

        IsSameOrUndefined isSameOrUndefined;
        IdentityHashCode identityHashCode;

        @ExportMessage
        TriState isIdenticalOrUndefined(Object other) {
            if (isSameOrUndefined == null) {
                return TriState.UNDEFINED;
            }
            return isSameOrUndefined.isSameOrUndefined(this, other);
        }

        @ExportMessage
        int identityHashCode() throws UnsupportedMessageException {
            if (identityHashCode == null) {
                throw UnsupportedMessageException.create();
            }
            return identityHashCode.identityHashCode(this);
        }

    }

    @Test
    public void testIsSame() {
        IdentityTest v0 = new IdentityTest();
        IdentityTest v1 = new IdentityTest();
        InteropLibrary l0 = createLibrary(InteropLibrary.class, v0);
        InteropLibrary l1 = createLibrary(InteropLibrary.class, v1);

        // correct usage
        v0.isSameOrUndefined = (r, o) -> TriState.valueOf(o == v0);
        v0.identityHashCode = (r) -> System.identityHashCode(r);
        v1.isSameOrUndefined = (r, o) -> TriState.valueOf(o == v1);
        v1.identityHashCode = (r) -> System.identityHashCode(r);
        assertTrue(l0.isIdentical(v0, v0, l0));
        assertTrue(l1.isIdentical(v1, v1, l1));
        assertFalse(l0.isIdentical(v0, v1, l1));

        // missing identity hash code
        v0.isSameOrUndefined = (r, o) -> TriState.valueOf(o == v0);
        v0.identityHashCode = null;
        assertFails(() -> l0.isIdentical(v0, v1, l1), AssertionError.class);

        // symmetry violated
        v0.isSameOrUndefined = (r, o) -> {
            if (o == v1) {
                return TriState.TRUE;
            }
            return TriState.UNDEFINED;
        };
        v0.identityHashCode = (r) -> System.identityHashCode(r);
        assertFails(() -> l0.isIdentical(v0, v1, l1), AssertionError.class);

        // reflexivity violated
        v0.isSameOrUndefined = (r, o) -> {
            if (o == v0) {
                return TriState.FALSE;
            }
            return TriState.UNDEFINED;
        };
        v0.identityHashCode = (r) -> System.identityHashCode(r);
        assertFails(() -> l0.isIdentical(v0, v0, l0), AssertionError.class);

        // invalid identity hash code
        v0.isSameOrUndefined = (r, o) -> TriState.valueOf(o == v0 || o == v1);
        v0.identityHashCode = (r) -> 42;

        v1.isSameOrUndefined = (r, o) -> TriState.valueOf(o == v0 || o == v1);
        v1.identityHashCode = (r) -> 43;
        assertFails(() -> l0.isIdentical(v0, v1, l1), AssertionError.class);

        // fix invalid identity hash code
        v1.identityHashCode = (r) -> 42;
        assertTrue(l0.isIdentical(v0, v1, l1));
    }

}
