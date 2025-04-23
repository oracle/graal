/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
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
        boolean hasMetaParents;
        Supplier<Object> getMetaParents;

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

        @ExportMessage
        boolean hasMetaParents() {
            return hasMetaParents;
        }

        @ExportMessage
        final Object getMetaParents() throws UnsupportedMessageException {
            if (!hasMetaParents || getMetaParents == null) {
                throw UnsupportedMessageException.create();
            }
            return getMetaParents.get();
        }

    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static final class Wrapper implements TruffleObject {

        final Object delegate;

        Wrapper(Object delegate) {
            this.delegate = delegate;
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

        Wrapper wrapper = new Wrapper(v);
        InteropLibrary wrapperLibrary = createLibrary(InteropLibrary.class, wrapper);
        v.hasMetaObject = true;
        v.getMetaObject = () -> testMeta;
        testMeta.isMetaObject = true;
        testMeta.isMetaInstance = (o) -> {
            // It is fair to use hasMetaObject/isMetaObject/getMetaObject here to e.g. get a foreign
            // subclass of the instance, test that to ensure there is no infinite recursion.
            InteropLibrary.getUncached().isMetaObject(o);
            if (InteropLibrary.getUncached().hasMetaObject(o)) {
                try {
                    InteropLibrary.getUncached().getMetaObject(o);
                } catch (UnsupportedMessageException e) {
                    throw new AssertionError(e);
                }
            }
            return o == v;
        };
        testMeta.getMetaQualifiedName = () -> "testQualifiedName";
        testMeta.getMetaSimpleName = () -> "testSimpleName";
        assertTrue(wrapperLibrary.hasMetaObject(wrapper));
        assertSame(testMeta, wrapperLibrary.getMetaObject(wrapper));
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
        assertFails(() -> l.isMetaObject(v), AssertionError.class);
        assertFails(() -> l.getMetaSimpleName(v), AssertionError.class);
        assertFails(() -> l.getMetaQualifiedName(v), AssertionError.class);
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

    @Test
    public void testMetaParents() throws UnsupportedMessageException {
        GetMetaObjectTest instance = new GetMetaObjectTest();
        MetaObjectTest v = new MetaObjectTest();
        MetaObjectTest parent = new MetaObjectTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);

        v.isMetaObject = false;
        v.isMetaInstance = null;
        v.getMetaQualifiedName = null;
        v.getMetaSimpleName = null;
        v.hasMetaParents = false;
        v.getMetaParents = null;
        assertFalse(l.hasMetaParents(v));
        assertFails(() -> l.getMetaParents(v), UnsupportedMessageException.class);

        v.isMetaObject = true;
        v.isMetaInstance = (o) -> o == instance;
        v.getMetaQualifiedName = () -> "testQualifiedName";
        v.getMetaSimpleName = () -> "testSimpleName";
        v.hasMetaParents = true;
        v.getMetaParents = () -> parent;
        assertTrue(l.hasMetaParents(v));
        assertEquals(parent, l.getMetaParents(v));

        v.isMetaObject = true;
        v.isMetaInstance = (o) -> o == instance;
        v.getMetaQualifiedName = () -> "testQualifiedName";
        v.getMetaSimpleName = () -> "testSimpleName";
        v.hasMetaParents = false;
        v.getMetaParents = () -> parent;
        assertFalse(l.hasMetaParents(v));
        assertFails(() -> l.getMetaParents(v), UnsupportedMessageException.class);

        v.isMetaObject = true;
        v.isMetaInstance = (o) -> o == instance;
        v.getMetaQualifiedName = () -> "testQualifiedName";
        v.getMetaSimpleName = () -> "testSimpleName";
        v.hasMetaParents = true;
        v.getMetaParents = null;
        assertTrue(l.hasMetaParents(v));
        assertFails(() -> l.getMetaParents(v), AssertionError.class);

        v.isMetaObject = false;
        v.isMetaInstance = (o) -> o == instance;
        v.getMetaQualifiedName = () -> "testQualifiedName";
        v.getMetaSimpleName = () -> "testSimpleName";
        v.hasMetaParents = true;
        v.getMetaParents = () -> parent;
        assertFails(() -> l.hasMetaParents(v), AssertionError.class);
        assertFails(() -> l.getMetaParents(v), AssertionError.class);
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

    @Test
    public void testValidScopeUsage() throws Exception {
        ScopeCached sc = new ScopeCached(5);
        InteropLibrary iop = createLibrary(InteropLibrary.class, sc);
        assertTrue(iop.hasMembers(sc));
        Object members = iop.getMembers(sc);
        assertNotNull(members);
        assertTrue(iop.hasScopeParent(sc));
        Object scParent = iop.getScopeParent(sc);
        assertNotNull(scParent);
        if (run == TestRun.CACHED) {
            checkInvalidUsage(() -> iop.hasMembers(scParent));
            checkInvalidUsage(() -> iop.getMembers(scParent));
            checkInvalidUsage(() -> iop.hasScopeParent(scParent));
            checkInvalidUsage(() -> iop.getScopeParent(scParent));
        }
    }

    private static void checkInvalidUsage(Callable<Object> call) throws Exception {
        boolean invalidUsage = false;
        try {
            call.call();
        } catch (AssertionError err) {
            assertTrue(err.getMessage(), err.getMessage().startsWith("Invalid library usage"));
            invalidUsage = true;
        }
        assertTrue(invalidUsage);
    }

    @ExportLibrary(InteropLibrary.class)
    static class ScopeCached implements TruffleObject {

        final long id;

        ScopeCached(long id) {
            this.id = id;
        }

        @ExportMessage
        boolean accepts(@Cached(value = "this.id") long cachedId) {
            return this.id == cachedId;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        boolean hasScopeParent() {
            return this.id > 0;
        }

        @ExportMessage
        @TruffleBoundary
        Object getScopeParent() throws UnsupportedMessageException {
            if (this.id > 0) {
                return new ScopeCached(id - 1);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new ScopeMembers(id);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "ScopeCached[" + id + "]";
        }

        @ExportLibrary(InteropLibrary.class)
        static final class ScopeMembers implements TruffleObject {

            private final long len;

            private ScopeMembers(long len) {
                this.len = len;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                if (0 <= index && index < len) {
                    return Long.toString(len - index);
                } else {
                    throw InvalidArrayIndexException.create(index);
                }
            }

            @ExportMessage
            long getArraySize() {
                return len;
            }

            @ExportMessage
            boolean isArrayElementReadable(long index) {
                return 0 <= index && index < len;
            }
        }
    }

    static class Members implements TruffleObject {
    }

    @ExportLibrary(InteropLibrary.class)
    static class ScopeTest implements TruffleObject {

        boolean hasLanguage;
        boolean isScope;
        boolean hasScopeParent;
        boolean hasMembers;
        Supplier<Class<? extends TruffleLanguage<?>>> getLanguage;
        Supplier<Object> getScopeParent;
        Supplier<Object> getMembers;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return hasLanguage;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return getLanguage.get();
        }

        @ExportMessage
        boolean isScope() {
            return isScope;
        }

        @ExportMessage
        final boolean hasScopeParent() {
            return hasScopeParent;
        }

        @ExportMessage
        Object getScopeParent() throws UnsupportedMessageException {
            if (getScopeParent == null) {
                throw UnsupportedMessageException.create();
            }
            return getScopeParent.get();
        }

        @ExportMessage
        final boolean hasMembers() {
            return hasMembers;
        }

        @ExportMessage
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
            if (getMembers == null) {
                throw UnsupportedMessageException.create();
            }
            return getMembers.get();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "Local";
        }
    }

    @Test
    public void testScope() throws InteropException {
        ScopeTest v = new ScopeTest();
        InteropLibrary l = createLibrary(InteropLibrary.class, v);

        v.hasLanguage = false;
        v.isScope = false;
        v.hasScopeParent = false;
        v.getScopeParent = null;
        v.hasMembers = false;
        v.getMembers = null;
        assertFalse(l.isScope(v));
        assertFalse(l.hasScopeParent(v));
        assertFalse(l.hasMembers(v));
        assertFails(() -> l.getScopeParent(v), UnsupportedMessageException.class);

        v.isScope = false;
        v.hasScopeParent = true;
        assertFalse(l.isScope(v));
        assertFails(() -> l.hasScopeParent(v), AssertionError.class);
        assertFails(() -> l.getScopeParent(v), AssertionError.class);

        v.isScope = true;
        v.hasScopeParent = false;
        v.hasMembers = true;
        v.getMembers = () -> new Members();
        assertFails(() -> l.isScope(v), AssertionError.class); // It does not have a language
        v.hasLanguage = true;
        v.getLanguage = () -> ProxyLanguage.class;
        assertTrue(l.isScope(v));

        v.hasMembers = false;
        v.getMembers = null;
        assertFails(() -> l.isScope(v), AssertionError.class);
        assertFalse(l.hasScopeParent(v));
        assertFails(() -> l.getScopeParent(v), UnsupportedMessageException.class);

        v.hasMembers = true;
        v.getMembers = () -> new Members();
        assertTrue(l.isScope(v));
        assertFalse(l.hasScopeParent(v));
        assertFails(() -> l.getScopeParent(v), UnsupportedMessageException.class);

        v.hasScopeParent = false;
        v.getScopeParent = () -> "No Scope";
        assertTrue(l.isScope(v));
        assertFails(() -> l.hasScopeParent(v), AssertionError.class);
        assertFails(() -> l.getScopeParent(v), AssertionError.class);

        v.hasScopeParent = true;
        v.getScopeParent = () -> "No Scope";
        assertTrue(l.isScope(v));
        assertFails(() -> l.hasScopeParent(v), AssertionError.class);
        assertFails(() -> l.getScopeParent(v), AssertionError.class);

        ScopeTest parentScope = new ScopeTest();
        v.getScopeParent = () -> parentScope;
        assertFails(() -> l.hasScopeParent(v), AssertionError.class);
        assertFails(() -> l.getScopeParent(v), AssertionError.class);

        parentScope.isScope = true;
        parentScope.hasMembers = true;
        parentScope.getMembers = () -> new Members();
        parentScope.hasLanguage = true;
        parentScope.getLanguage = () -> ProxyLanguage.class;
        v.getScopeParent = () -> parentScope;
        assertTrue(l.isScope(v));
        assertTrue(l.hasScopeParent(v));
        assertEquals(parentScope, l.getScopeParent(v));

        v.isScope = false;
        assertFalse(l.isScope(v));
        assertFails(() -> l.hasScopeParent(v), AssertionError.class);
        assertFails(() -> l.getScopeParent(v), AssertionError.class);

        v.isScope = true;
        v.hasScopeParent = false;
        v.getScopeParent = () -> parentScope;
        assertFails(() -> l.hasScopeParent(v), AssertionError.class);
        assertFails(() -> l.getScopeParent(v), AssertionError.class);
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("serial")
    static final class ExceptionTest extends AbstractTruffleException {

        boolean hasExceptionMessage;
        Supplier<Object> getExceptionMessage;
        ExceptionType exceptionType;
        Supplier<Integer> getExceptionExitStatus;
        Supplier<Boolean> isExceptionIncompleteSource;
        boolean hasExceptionCause;
        Supplier<Object> getExceptionCause;
        boolean hasExceptionStackTrace;
        Supplier<Object> getExceptionStackTrace;

        ExceptionTest() {
            hasExceptionMessage = true;
            getExceptionMessage = () -> "Test exception";
            exceptionType = ExceptionType.RUNTIME_ERROR;
        }

        @ExportMessage
        boolean hasExceptionMessage() {
            return hasExceptionMessage;
        }

        @ExportMessage
        Object getExceptionMessage() throws UnsupportedMessageException {
            if (getExceptionMessage == null) {
                throw UnsupportedMessageException.create();
            }
            return getExceptionMessage.get();
        }

        @ExportMessage
        ExceptionType getExceptionType() {
            return exceptionType;
        }

        @ExportMessage
        int getExceptionExitStatus() throws UnsupportedMessageException {
            if (getExceptionExitStatus == null) {
                throw UnsupportedMessageException.create();
            }
            return getExceptionExitStatus.get();
        }

        @ExportMessage
        boolean isExceptionIncompleteSource() throws UnsupportedMessageException {
            if (isExceptionIncompleteSource == null) {
                throw UnsupportedMessageException.create();
            }
            return isExceptionIncompleteSource.get();
        }

        @ExportMessage
        boolean hasExceptionCause() {
            return hasExceptionCause;
        }

        @ExportMessage
        Object getExceptionCause() throws UnsupportedMessageException {
            if (getExceptionCause == null) {
                throw UnsupportedMessageException.create();
            }
            return getExceptionCause.get();
        }

        @ExportMessage
        boolean hasExceptionStackTrace() {
            return hasExceptionStackTrace;
        }

        @ExportMessage
        Object getExceptionStackTrace() throws UnsupportedMessageException {
            if (getExceptionStackTrace == null) {
                throw UnsupportedMessageException.create();
            }
            return getExceptionStackTrace.get();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class StackFrameTest implements TruffleObject {

        boolean hasExecutableName;
        Supplier<Object> getExecutableName;
        boolean hasDeclaringMetaObject;
        Supplier<Object> getDeclaringMetaObject;

        StackFrameTest(String name) {
            hasExecutableName = true;
            getExecutableName = () -> name;
        }

        @ExportMessage
        boolean hasExecutableName() {
            return hasExecutableName;
        }

        @ExportMessage
        Object getExecutableName() throws UnsupportedMessageException {
            if (getExecutableName == null) {
                throw UnsupportedMessageException.create();
            }
            return getExecutableName.get();
        }

        @ExportMessage
        boolean hasDeclaringMetaObject() {
            return hasDeclaringMetaObject;
        }

        @ExportMessage
        Object getDeclaringMetaObject() throws UnsupportedMessageException {
            if (getDeclaringMetaObject == null) {
                throw UnsupportedMessageException.create();
            }
            return getDeclaringMetaObject.get();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class StackTraceTest implements TruffleObject {

        Supplier<Object[]> content;

        StackTraceTest(Object... frames) {
            content = () -> frames;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return content != null;
        }

        @ExportMessage
        long getArraySize() {
            return content.get().length;
        }

        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        boolean isArrayElementReadable(long index) {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            checkBounds(index);
            return content.get()[(int) index];
        }

        private void checkBounds(long index) throws InvalidArrayIndexException {
            if (index < 0 || index >= content.get().length) {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class IteratorTest implements TruffleObject {

        boolean isIterator;
        Supplier<Boolean> hasNext;
        Supplier<Object> next;

        IteratorTest(Object element) {
            isIterator = true;
            next = () -> element;
            hasNext = () -> true;
        }

        @ExportMessage
        boolean isIterator() {
            return isIterator;
        }

        @ExportMessage
        boolean hasIteratorNextElement() throws UnsupportedMessageException {
            if (hasNext == null) {
                throw UnsupportedMessageException.create();
            }
            return hasNext.get();
        }

        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException, UnsupportedMessageException {
            if (next == null) {
                throw UnsupportedMessageException.create();
            }
            if (next.get() == null) {
                throw StopIterationException.create();
            }
            return next.get();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        String toDisplayString(boolean allowSideEffects) {
            return getClass().getName();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class IterableTest implements TruffleObject {

        Supplier<Object> iterator;
        boolean hasIterator;

        IterableTest(Object iterator) {
            this.iterator = () -> iterator;
            this.hasIterator = true;
        }

        @ExportMessage
        boolean hasIterator() {
            return hasIterator;
        }

        @ExportMessage
        Object getIterator() throws UnsupportedMessageException {
            if (iterator == null) {
                throw UnsupportedMessageException.create();
            }
            return iterator.get();
        }
    }

    @Test
    public void testGetExceptionMessage() throws UnsupportedMessageException {
        ExceptionTest exceptionTest = new ExceptionTest();
        InteropLibrary exceptionLib = createLibrary(InteropLibrary.class, exceptionTest);
        assertEquals(exceptionTest.getExceptionMessage.get(), exceptionLib.getExceptionMessage(exceptionTest));
        exceptionTest.hasExceptionMessage = false;
        assertFails(() -> exceptionLib.getExceptionMessage(exceptionTest), AssertionError.class);
        exceptionTest.hasExceptionMessage = true;
        exceptionTest.getExceptionMessage = null;
        assertFails(() -> exceptionLib.getExceptionMessage(exceptionTest), AssertionError.class);
        exceptionTest.getExceptionMessage = () -> null;
        assertFails(() -> exceptionLib.getExceptionMessage(exceptionTest), NullPointerException.class);
        exceptionTest.getExceptionMessage = () -> 1;
        assertFails(() -> exceptionLib.getExceptionMessage(exceptionTest), AssertionError.class);
        TruffleObject empty = new TruffleObject() {
        };
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.getExceptionMessage(empty), UnsupportedMessageException.class);
    }

    @Test
    public void getExceptionExitStatus() throws UnsupportedMessageException {
        ExceptionTest exceptionTest = new ExceptionTest();
        InteropLibrary exceptionLib = createLibrary(InteropLibrary.class, exceptionTest);
        exceptionTest.exceptionType = ExceptionType.EXIT;
        exceptionTest.getExceptionExitStatus = () -> 255;
        assertEquals(exceptionTest.getExceptionExitStatus.get().intValue(), exceptionLib.getExceptionExitStatus(exceptionTest));
        exceptionTest.exceptionType = ExceptionType.RUNTIME_ERROR;
        assertFails(() -> exceptionLib.getExceptionExitStatus(exceptionTest), AssertionError.class);
        exceptionTest.exceptionType = ExceptionType.EXIT;
        exceptionTest.getExceptionExitStatus = null;
        assertFails(() -> exceptionLib.getExceptionExitStatus(exceptionTest), AssertionError.class);
        TruffleObject empty = new TruffleObject() {
        };
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.getExceptionExitStatus(empty), UnsupportedMessageException.class);
    }

    @Test
    public void isExceptionIncompleteSource() throws UnsupportedMessageException {
        ExceptionTest exceptionTest = new ExceptionTest();
        InteropLibrary exceptionLib = createLibrary(InteropLibrary.class, exceptionTest);
        exceptionTest.exceptionType = ExceptionType.PARSE_ERROR;
        exceptionTest.isExceptionIncompleteSource = () -> true;
        assertEquals(exceptionTest.isExceptionIncompleteSource.get().booleanValue(), exceptionLib.isExceptionIncompleteSource(exceptionTest));
        exceptionTest.exceptionType = ExceptionType.RUNTIME_ERROR;
        assertFails(() -> exceptionLib.isExceptionIncompleteSource(exceptionTest), AssertionError.class);
        exceptionTest.exceptionType = ExceptionType.PARSE_ERROR;
        exceptionTest.isExceptionIncompleteSource = null;
        assertFails(() -> exceptionLib.isExceptionIncompleteSource(exceptionTest), AssertionError.class);
        TruffleObject empty = new TruffleObject() {
        };
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.isExceptionIncompleteSource(empty), UnsupportedMessageException.class);
    }

    @Test
    public void getExceptionCause() throws UnsupportedMessageException {
        ExceptionTest exceptionTest = new ExceptionTest();
        InteropLibrary exceptionLib = createLibrary(InteropLibrary.class, exceptionTest);
        exceptionTest.hasExceptionCause = true;
        ExceptionTest cause = new ExceptionTest();
        exceptionTest.getExceptionCause = () -> cause;
        assertEquals(cause, exceptionLib.getExceptionCause(exceptionTest));
        exceptionTest.hasExceptionCause = false;
        assertFails(() -> exceptionLib.getExceptionCause(exceptionTest), AssertionError.class);
        exceptionTest.hasExceptionCause = true;
        exceptionTest.getExceptionCause = null;
        assertFails(() -> exceptionLib.getExceptionCause(exceptionTest), AssertionError.class);
        exceptionTest.getExceptionCause = () -> null;
        assertFails(() -> exceptionLib.getExceptionCause(exceptionTest), NullPointerException.class);
        TruffleObject empty = new TruffleObject() {
        };
        exceptionTest.getExceptionCause = () -> empty;
        assertFails(() -> exceptionLib.getExceptionCause(exceptionTest), AssertionError.class);
        exceptionTest.getExceptionCause = () -> new RuntimeException();
        assertFails(() -> exceptionLib.getExceptionCause(exceptionTest), AssertionError.class);
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.getExceptionCause(empty), UnsupportedMessageException.class);
    }

    @Test
    public void getExceptionStackTrace() throws UnsupportedMessageException {
        ExceptionTest exceptionTest = new ExceptionTest();
        InteropLibrary exceptionLib = createLibrary(InteropLibrary.class, exceptionTest);
        StackTraceTest stackTrace = new StackTraceTest(new StackFrameTest("main"));
        exceptionTest.hasExceptionStackTrace = true;
        exceptionTest.getExceptionStackTrace = () -> stackTrace;
        assertEquals(stackTrace, exceptionLib.getExceptionStackTrace(exceptionTest));

        exceptionTest.hasExceptionStackTrace = false;
        assertFails(() -> exceptionLib.getExceptionStackTrace(exceptionTest), AssertionError.class);
        exceptionTest.hasExceptionStackTrace = true;
        exceptionTest.getExceptionStackTrace = null;
        assertFails(() -> exceptionLib.getExceptionStackTrace(exceptionTest), AssertionError.class);
        exceptionTest.getExceptionStackTrace = () -> null;
        assertFails(() -> exceptionLib.getExceptionStackTrace(exceptionTest), AssertionError.class);
        TruffleObject empty = new TruffleObject() {
        };
        exceptionTest.getExceptionStackTrace = () -> empty;
        assertFails(() -> exceptionLib.getExceptionStackTrace(exceptionTest), AssertionError.class);
        StackTraceTest stackTraceWithEmptyFrame = new StackTraceTest(empty);
        exceptionTest.getExceptionStackTrace = () -> stackTraceWithEmptyFrame;
        exceptionLib.getExceptionStackTrace(exceptionTest);
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.getExceptionStackTrace(empty), UnsupportedMessageException.class);
    }

    @Test
    public void getexecutableName() throws UnsupportedMessageException {
        StackFrameTest stackFrameTest = new StackFrameTest("foo");
        InteropLibrary stackFrameLib = createLibrary(InteropLibrary.class, stackFrameTest);
        assertEquals(stackFrameTest.getExecutableName.get(), stackFrameLib.getExecutableName(stackFrameTest));
        stackFrameTest.hasExecutableName = false;
        assertFails(() -> stackFrameLib.getExecutableName(stackFrameTest), AssertionError.class);
        stackFrameTest.hasExecutableName = true;
        stackFrameTest.getExecutableName = null;
        assertFails(() -> stackFrameLib.getExecutableName(stackFrameTest), AssertionError.class);
        stackFrameTest.getExecutableName = () -> null;
        assertFails(() -> stackFrameLib.getExecutableName(stackFrameTest), NullPointerException.class);
        TruffleObject empty = new TruffleObject() {
        };
        stackFrameTest.getExecutableName = () -> empty;
        assertFails(() -> stackFrameLib.getExecutableName(stackFrameTest), AssertionError.class);
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.getExecutableName(empty), UnsupportedMessageException.class);
    }

    @Test
    public void getDeclaringMetaObject() throws UnsupportedMessageException {
        StackFrameTest stackFrameTest = new StackFrameTest("foo");
        MetaObjectTest metaObject = new MetaObjectTest();
        metaObject.isMetaObject = true;
        metaObject.getMetaQualifiedName = () -> "std";
        metaObject.getMetaSimpleName = () -> "std";
        metaObject.isMetaInstance = (i) -> false;
        stackFrameTest.hasDeclaringMetaObject = true;
        stackFrameTest.getDeclaringMetaObject = () -> metaObject;
        InteropLibrary stackFrameLib = createLibrary(InteropLibrary.class, stackFrameTest);
        assertEquals(metaObject, stackFrameLib.getDeclaringMetaObject(stackFrameTest));
        stackFrameTest.hasDeclaringMetaObject = false;
        assertFails(() -> stackFrameLib.getDeclaringMetaObject(stackFrameTest), AssertionError.class);
        stackFrameTest.hasDeclaringMetaObject = true;
        stackFrameTest.getDeclaringMetaObject = null;
        assertFails(() -> stackFrameLib.getDeclaringMetaObject(stackFrameTest), AssertionError.class);
        stackFrameTest.getDeclaringMetaObject = () -> null;
        assertFails(() -> stackFrameLib.getDeclaringMetaObject(stackFrameTest), AssertionError.class);
        TruffleObject empty = new TruffleObject() {
        };
        stackFrameTest.getDeclaringMetaObject = () -> empty;
        assertFails(() -> stackFrameLib.getDeclaringMetaObject(stackFrameTest), AssertionError.class);
        InteropLibrary objectLib = createLibrary(InteropLibrary.class, empty);
        assertFails(() -> objectLib.getDeclaringMetaObject(empty), UnsupportedMessageException.class);
    }

    @Test
    public void getIterator() throws UnsupportedMessageException {
        IterableTest iterableTest = new IterableTest(new IteratorTest(1));
        InteropLibrary iterableLib = createLibrary(InteropLibrary.class, iterableTest);
        assertEquals(iterableTest.iterator.get(), iterableLib.getIterator(iterableTest));
        iterableTest.hasIterator = false;
        assertFails(() -> iterableLib.getIterator(iterableTest), AssertionError.class);
        iterableTest.hasIterator = true;
        iterableTest.iterator = null;
        assertFails(() -> iterableLib.getIterator(iterableTest), AssertionError.class);
        iterableTest.iterator = () -> null;
        assertFails(() -> iterableLib.getIterator(iterableTest), AssertionError.class);
        TruffleObject empty = new TruffleObject() {
        };
        iterableTest.iterator = () -> empty;
        assertFails(() -> iterableLib.getIterator(iterableTest), AssertionError.class);
    }

    @Test
    public void hasIteratorNextElement() throws UnsupportedMessageException {
        IteratorTest iteratorTest = new IteratorTest(1);
        InteropLibrary iteratorLib = createLibrary(InteropLibrary.class, iteratorTest);
        assertEquals(true, iteratorLib.hasIteratorNextElement(iteratorTest));
        iteratorTest.isIterator = false;
        assertFails(() -> iteratorLib.hasIteratorNextElement(iteratorTest), AssertionError.class);
        iteratorTest.isIterator = true;
        iteratorTest.hasNext = null;
        assertFails(() -> iteratorLib.hasIteratorNextElement(iteratorTest), AssertionError.class);
    }

    @Test
    public void getIteratorNextElement() throws UnsupportedMessageException, StopIterationException {
        setupEnv(Context.create()); // we need no multi threaded context.
        IteratorTest iteratorTest = new IteratorTest(1);
        InteropLibrary iteratorLib = createLibrary(InteropLibrary.class, iteratorTest);
        assertEquals(iteratorTest.next.get(), iteratorLib.getIteratorNextElement(iteratorTest));
        iteratorTest.isIterator = false;
        assertFails(() -> iteratorLib.getIteratorNextElement(iteratorTest), AssertionError.class);
        iteratorTest.isIterator = true;
        iteratorTest.hasNext = () -> true;
        iteratorTest.next = Object::new;
        assertFails(() -> iteratorLib.getIteratorNextElement(iteratorTest), AssertionError.class);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HashTest implements TruffleObject {

        boolean hasHashEntries;
        Predicate<Object> readable;
        Predicate<Object> modifiable;
        Predicate<Object> insertable;
        Predicate<Object> removeable;
        Predicate<Object> writable;
        Predicate<Object> existing;
        Supplier<Long> size;
        Map<Object, Object> data;
        Supplier<Object> iterator;

        HashTest() {
            this.hasHashEntries = true;
            this.size = () -> 0L;
            this.data = new HashMap<>();
            this.iterator = () -> {
                IteratorTest it = new IteratorTest(null);
                it.hasNext = () -> false;
                it.next = () -> null;
                return it;
            };
        }

        @ExportMessage
        boolean hasHashEntries() {
            return hasHashEntries;
        }

        @ExportMessage
        long getHashSize() throws UnsupportedMessageException {
            if (size == null) {
                throw UnsupportedMessageException.create();
            } else {
                return size.get();
            }
        }

        @ExportMessage
        boolean isHashEntryReadable(Object key) {
            if (readable != null) {
                return readable.test(key);
            } else if (data != null) {
                return data.containsKey(key);
            } else {
                return false;
            }
        }

        @ExportMessage
        Object readHashValue(Object key) throws UnsupportedMessageException, UnknownKeyException {
            if (data == null) {
                throw UnsupportedMessageException.create();
            } else if (!data.containsKey(key)) {
                throw UnknownKeyException.create(key);
            } else {
                return data.get(key);
            }
        }

        @ExportMessage
        Object readHashValueOrDefault(Object key, Object defaultValue) throws UnsupportedMessageException {
            if (data == null) {
                throw UnsupportedMessageException.create();
            } else if (!data.containsKey(key)) {
                return defaultValue;
            } else {
                return data.get(key);
            }
        }

        @ExportMessage
        boolean isHashEntryModifiable(Object key) {
            if (modifiable != null) {
                return modifiable.test(key);
            } else if (data != null) {
                return data.containsKey(key);
            } else {
                return false;
            }
        }

        @ExportMessage
        boolean isHashEntryInsertable(Object key) {
            if (insertable != null) {
                return insertable.test(key);
            } else if (data != null) {
                return !data.containsKey(key);
            } else {
                return false;
            }
        }

        @ExportMessage
        void writeHashEntry(Object key, Object value) throws UnsupportedMessageException {
            if (data == null) {
                throw UnsupportedMessageException.create();
            } else {
                data.put(key, value);
            }
            size = () -> (long) data.size();
        }

        @ExportMessage
        boolean isHashEntryRemovable(Object key) {
            if (removeable != null) {
                return removeable.test(key);
            } else if (data != null) {
                return data.containsKey(key);
            } else {
                return false;
            }
        }

        @ExportMessage
        void removeHashEntry(Object key) throws UnsupportedMessageException, UnknownKeyException {
            if (data == null) {
                throw UnsupportedMessageException.create();
            } else if (!data.containsKey(key)) {
                throw UnknownKeyException.create(key);
            } else {
                data.remove(key);
                size = () -> (long) data.size();
            }
        }

        @ExportMessage
        public Object getHashEntriesIterator() throws UnsupportedMessageException {
            if (iterator == null) {
                throw UnsupportedMessageException.create();
            } else {
                return iterator.get();
            }
        }

        @ExportMessage
        public Object getHashKeysIterator() throws UnsupportedMessageException {
            if (iterator == null) {
                throw UnsupportedMessageException.create();
            } else {
                return iterator.get();
            }
        }

        @ExportMessage
        public Object getHashValuesIterator() throws UnsupportedMessageException {
            if (iterator == null) {
                throw UnsupportedMessageException.create();
            } else {
                return iterator.get();
            }
        }

        @ExportMessage
        public boolean isHashEntryWritable(Object key) {
            if (writable != null) {
                return writable.test(key);
            } else {
                return isHashEntryModifiable(key) || isHashEntryInsertable(key);
            }
        }

        @ExportMessage
        public boolean isHashEntryExisting(Object key) {
            if (existing != null) {
                return existing.test(key);
            } else {
                return isHashEntryReadable(key) || isHashEntryModifiable(key) || isHashEntryRemovable(key);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        String toDisplayString(boolean allowSideEffects) {
            return getClass().getName();
        }

    }

    @Test
    public void testGetHashSize() throws UnsupportedMessageException {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertEquals(hashTest.data.size(), hashLib.getHashSize(hashTest));
        hashTest.hasHashEntries = false;
        assertFails(() -> hashLib.getHashSize(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.size = null;
        assertFails(() -> hashLib.getHashSize(hashTest), AssertionError.class);
    }

    @Test
    public void testIsHashEntryReadable() {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertFalse(hashLib.isHashEntryReadable(hashTest, 1));
        hashTest.hasHashEntries = false;
        hashTest.readable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryReadable(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.readable = (k) -> true;
        hashTest.insertable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryReadable(hashTest, 1), AssertionError.class);
    }

    @Test
    public void testReadHashValue() throws UnsupportedMessageException, UnknownKeyException {
        setupEnv(Context.create()); // we need no multi threaded context.
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashTest.writeHashEntry(1, -1);
        assertEquals(-1, hashLib.readHashValue(hashTest, 1));
        assertFails(() -> hashLib.readHashValue(hashTest, null), NullPointerException.class);
        hashTest.hasHashEntries = false;
        assertFails(() -> hashLib.readHashValue(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.readable = (k) -> false;
        assertFails(() -> hashLib.readHashValue(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.readable = null;
        hashTest.data.put(1, new Object());
        assertFails(() -> hashLib.readHashValue(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.readable = (k) -> true;
        hashTest.data = null;
        assertFails(() -> hashLib.readHashValue(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.readable = null;
        hashTest.data = Collections.singletonMap(1, -1);
        assertFails(() -> hashLib.readHashValue(hashTest, 2), UnknownKeyException.class);
    }

    @Test
    public void testReadHashValueOrDefault() throws UnsupportedMessageException {
        setupEnv(Context.create()); // we need no multi threaded context.
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashTest.writeHashEntry(1, -1);
        assertEquals(-1, hashLib.readHashValueOrDefault(hashTest, 1, 0));
        assertEquals(-2, hashLib.readHashValueOrDefault(hashTest, 2, -2));
        assertFails(() -> hashLib.readHashValueOrDefault(hashTest, null, 0), NullPointerException.class);
        assertFails(() -> hashLib.readHashValueOrDefault(hashTest, 1, null), NullPointerException.class);
        hashTest.hasHashEntries = false;
        assertFails(() -> hashLib.readHashValueOrDefault(hashTest, 1, 0), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.data.put(1, new Object());
        assertFails(() -> hashLib.readHashValueOrDefault(hashTest, 1, 0), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.data = null;
        assertFails(() -> hashLib.readHashValue(hashTest, 1), UnsupportedMessageException.class);
    }

    @Test
    public void testIsHashEntryModifiable() {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertFalse(hashLib.isHashEntryModifiable(hashTest, 1));
        hashTest.hasHashEntries = false;
        hashTest.modifiable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryModifiable(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.modifiable = (k) -> true;
        hashTest.insertable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryModifiable(hashTest, 1), AssertionError.class);
    }

    @Test
    public void testIsHashEntryInsertable() {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertTrue(hashLib.isHashEntryInsertable(hashTest, 1));
        hashTest.hasHashEntries = false;
        hashTest.insertable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryInsertable(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.insertable = (k) -> true;
        hashTest.readable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryInsertable(hashTest, 1), AssertionError.class);
    }

    @Test
    public void testIsHashEntryExisting() {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertFalse(hashLib.isHashEntryExisting(hashTest, 1));
        hashTest.modifiable = (k) -> true;
        assertTrue(hashLib.isHashEntryExisting(hashTest, 1));
        hashTest.modifiable = null;
        hashTest.existing = (k) -> true;
        assertFails(() -> hashLib.isHashEntryExisting(hashTest, 1), AssertionError.class);
    }

    @Test
    public void testIsHashEntryWritable() {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertTrue(hashLib.isHashEntryWritable(hashTest, 1));
        hashTest.insertable = (k) -> false;
        hashTest.modifiable = (k) -> false;
        assertFalse(hashLib.isHashEntryWritable(hashTest, 1));
        hashTest.writable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryWritable(hashTest, 1), AssertionError.class);
    }

    @Test
    public void testWriteHashEntry() throws UnsupportedMessageException {
        setupEnv(Context.create()); // we need no multi threaded context.
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashTest.writeHashEntry(1, -1);
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, null, 1);
            return null;
        }, NullPointerException.class);
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, 1, null);
            return null;
        }, NullPointerException.class);
        hashTest.hasHashEntries = false;
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, 1, -1);
            return null;
        }, AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.insertable = (k) -> false;
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, 2, -1);
            return null;
        }, AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.insertable = null;
        hashTest.modifiable = (k) -> false;
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, 1, -1);
            return null;
        }, AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.insertable = (k) -> true;
        hashTest.data = null;
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, 1, -1);
            return null;
        }, UnsupportedMessageException.class);
        hashTest.hasHashEntries = true;
        hashTest.insertable = null;
        hashTest.modifiable = (k) -> true;
        hashTest.data = null;
        assertFails(() -> {
            hashLib.writeHashEntry(hashTest, 1, -1);
            return null;
        }, UnsupportedMessageException.class);
    }

    @Test
    public void testIsHashEntryRemovable() {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        assertFalse(hashLib.isHashEntryRemovable(hashTest, 1));
        hashTest.hasHashEntries = false;
        hashTest.removeable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryRemovable(hashTest, 1), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.removeable = (k) -> true;
        hashTest.insertable = (k) -> true;
        assertFails(() -> hashLib.isHashEntryRemovable(hashTest, 1), AssertionError.class);
    }

    @Test
    public void testRemoveHashEntry() throws UnsupportedMessageException, UnknownKeyException {
        setupEnv(Context.create()); // we need no multi threaded context.
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashTest.writeHashEntry(1, -1);
        hashLib.removeHashEntry(hashTest, 1);
        assertFails(() -> {
            hashLib.removeHashEntry(hashTest, null);
            return null;
        }, NullPointerException.class);
        hashTest.writeHashEntry(1, -1);
        hashTest.hasHashEntries = false;
        assertFails(() -> {
            hashLib.removeHashEntry(hashTest, 1);
            return null;
        }, AssertionError.class);
        hashTest.writeHashEntry(1, -1);
        hashTest.hasHashEntries = true;
        hashTest.removeable = (k) -> false;
        assertFails(() -> {
            hashLib.removeHashEntry(hashTest, 1);
            return null;
        }, AssertionError.class);
        hashTest.writeHashEntry(1, -1);
        hashTest.hasHashEntries = true;
        hashTest.removeable = (k) -> true;
        hashTest.data = null;
        assertFails(() -> {
            hashLib.removeHashEntry(hashTest, 1);
            return null;
        }, UnsupportedMessageException.class);
    }

    @Test
    public void testGetHashEntriesIterator() throws UnsupportedMessageException {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashLib.getHashEntriesIterator(hashTest);
        hashTest.hasHashEntries = false;
        assertFails(() -> hashLib.getHashEntriesIterator(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.iterator = null;
        assertFails(() -> hashLib.getHashEntriesIterator(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.iterator = () -> null;
        assertFails(() -> hashLib.getHashEntriesIterator(hashTest), AssertionError.class);
        hashTest.iterator = () -> new TruffleObject() {
        };
        assertFails(() -> hashLib.getHashEntriesIterator(hashTest), AssertionError.class);
    }

    @Test
    public void testGetHashKeysIterator() throws UnsupportedMessageException {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashLib.getHashKeysIterator(hashTest);
        hashTest.hasHashEntries = false;
        assertFails(() -> hashLib.getHashKeysIterator(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.iterator = null;
        assertFails(() -> hashLib.getHashKeysIterator(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.iterator = () -> null;
        assertFails(() -> hashLib.getHashKeysIterator(hashTest), AssertionError.class);
        hashTest.iterator = () -> new TruffleObject() {
        };
        assertFails(() -> hashLib.getHashKeysIterator(hashTest), AssertionError.class);
    }

    @Test
    public void testGetHashValuesIterator() throws UnsupportedMessageException {
        HashTest hashTest = new HashTest();
        InteropLibrary hashLib = createLibrary(InteropLibrary.class, hashTest);
        hashLib.getHashValuesIterator(hashTest);
        hashTest.hasHashEntries = false;
        assertFails(() -> hashLib.getHashValuesIterator(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.iterator = null;
        assertFails(() -> hashLib.getHashValuesIterator(hashTest), AssertionError.class);
        hashTest.hasHashEntries = true;
        hashTest.iterator = () -> null;
        assertFails(() -> hashLib.getHashValuesIterator(hashTest), AssertionError.class);
        hashTest.iterator = () -> new TruffleObject() {
        };
        assertFails(() -> hashLib.getHashValuesIterator(hashTest), AssertionError.class);
    }

    @Test
    public void testArityException() {
        assertNotNull(ArityException.create(0, 0, -1));
        assertNotNull(ArityException.create(0, 1, -1));
        assertNotNull(ArityException.create(0, 1, -1));
        assertNotNull(ArityException.create(0, -1, -1));

        assertNotNull(ArityException.create(0, 0, 1));
        assertNotNull(ArityException.create(0, 1, 2));
        assertNotNull(ArityException.create(0, 1, 3));
        assertNotNull(ArityException.create(1, -1, 0));
        assertNotNull(ArityException.create(2, -1, 1));
        assertNotNull(ArityException.create(0, Integer.MAX_VALUE - 1, Integer.MAX_VALUE));

        assertFails(() -> ArityException.create(0, 0, 0), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(0, 1, 0), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(1, 0, 2), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(0, 1, 1), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(0, -1, 0), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(0, -1, Integer.MAX_VALUE), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(2, -1, 2), IllegalArgumentException.class);
        assertFails(() -> ArityException.create(-1, -1, -1), IllegalArgumentException.class);

        assertEquals(0, ArityException.create(0, 0, -1).getExpectedMinArity());
        assertEquals(1, ArityException.create(1, 1, 2).getExpectedMinArity());
        assertEquals(2, ArityException.create(2, 2, 3).getExpectedMinArity());

        assertEquals(0, ArityException.create(0, 0, -1).getExpectedMaxArity());
        assertEquals(1, ArityException.create(1, 1, 2).getExpectedMaxArity());
        assertEquals(2, ArityException.create(2, 2, 3).getExpectedMaxArity());

        assertEquals(-1, ArityException.create(0, 0, -1).getActualArity());
        assertEquals(1, ArityException.create(0, 0, 1).getActualArity());
        assertEquals(0, ArityException.create(1, 2, 0).getActualArity());

        assertEquals("Arity error - expected: 0 actual: unknown", ArityException.create(0, 0, -1).getMessage());
        assertEquals("Arity error - expected: 0 actual: 1", ArityException.create(0, 0, 1).getMessage());
        assertEquals("Arity error - expected: 0-1 actual: 2", ArityException.create(0, 1, 2).getMessage());
        assertEquals("Arity error - expected: 0+ actual: unknown", ArityException.create(0, -1, -1).getMessage());
        assertEquals("Arity error - expected: 1+ actual: 0", ArityException.create(1, -1, 0).getMessage());
    }

    @Test
    public void testIsInvocableMemberWithReadSideEffects() throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        setupEnv(Context.create()); // we need no multi threaded context.
        var obj = new IsInvocableUnknown();
        InteropLibrary memberLib = createLibrary(InteropLibrary.class, obj);
        String memberName = IsInvocableUnknown.MEMBER_NAME;
        /*
         * If hasMemberReadSideEffects(), a language may not be able to determine, without side
         * effects, if the member is invocable, so the invariant that if invokeMember succeeds
         * isMemberInvocable must have returned true is lifted.
         */
        obj.invocable = false;
        assertFalse(memberLib.isMemberInvocable(obj, memberName));
        obj.readSideEffects = true;
        assertEquals(42, memberLib.invokeMember(obj, memberName));
        // Invariant contract violation
        obj.readSideEffects = false;
        assertFails(() -> memberLib.invokeMember(obj, memberName), AssertionError.class);

        obj.invocable = true;
        assertTrue(memberLib.isMemberInvocable(obj, memberName));
        obj.readSideEffects = true;
        assertEquals(42, memberLib.invokeMember(obj, memberName));
        obj.readSideEffects = false;
        assertEquals(42, memberLib.invokeMember(obj, memberName));
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class IsInvocableUnknown implements TruffleObject {

        static final String MEMBER_NAME = "getter";
        boolean invocable = false;
        boolean readSideEffects = true;

        @ExportMessage
        final boolean hasMembers() {
            return true;
        }

        @ExportMessage
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return ProxyArray.fromArray(MEMBER_NAME);
        }

        @ExportMessage
        final boolean isMemberReadable(String member) {
            return switch (member) {
                case MEMBER_NAME -> true;
                default -> false;
            };
        }

        @ExportMessage
        final boolean isMemberInvocable(String member) {
            return switch (member) {
                case MEMBER_NAME -> invocable;
                default -> false;
            };
        }

        @ExportMessage
        final boolean hasMemberReadSideEffects(String member) {
            return switch (member) {
                case MEMBER_NAME -> readSideEffects;
                default -> false;
            };
        }

        @ExportMessage
        final Object readMember(String member) throws UnknownIdentifierException {
            return switch (member) {
                case MEMBER_NAME -> ((ProxyExecutable) a -> 42);
                default -> throw UnknownIdentifierException.create(member);
            };
        }

        @ExportMessage
        final Object invokeMember(String member, @SuppressWarnings("unused") Object[] arguments) throws UnknownIdentifierException {
            return switch (member) {
                case MEMBER_NAME -> 42;
                default -> throw UnknownIdentifierException.create(member);
            };
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @TruffleBoundary
        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return getClass().getSimpleName();
        }
    }

    @Test
    public void testAllMemberSideEffects() throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        setupEnv(Context.create()); // we need no multi threaded context.
        var obj = new IsMemberAllUnknown();
        InteropLibrary memberLib = createLibrary(InteropLibrary.class, obj);
        String memberName = IsMemberAllUnknown.MEMBER_NAME;

        obj.isMember = false;
        assertEquals("isMemberInvocable", false, memberLib.isMemberInvocable(obj, memberName));
        assertEquals("isMemberReadable", false, memberLib.isMemberReadable(obj, memberName));
        assertEquals("isMemberWritable", false, memberLib.isMemberWritable(obj, memberName));
        assertEquals("isMemberRemovable", false, memberLib.isMemberRemovable(obj, memberName));

        obj.readSideEffects = true;
        assertEquals(42, memberLib.invokeMember(obj, memberName));
        assertEquals(42, memberLib.readMember(obj, memberName));
        memberLib.writeMember(obj, memberName, 42);
        memberLib.removeMember(obj, memberName);

        // Invariant contract violation
        obj.readSideEffects = false;
        assertFails(() -> memberLib.invokeMember(obj, memberName), AssertionError.class);
        assertFails(() -> memberLib.readMember(obj, memberName), AssertionError.class);
        assertFails(() -> {
            memberLib.writeMember(obj, memberName, 42);
            return null;
        }, AssertionError.class);
        assertFails(() -> {
            memberLib.removeMember(obj, memberName);
            return null;
        }, AssertionError.class);

        obj.isMember = true;
        assertEquals("isMemberInvocable", true, memberLib.isMemberInvocable(obj, memberName));
        assertEquals("isMemberReadable", true, memberLib.isMemberReadable(obj, memberName));
        assertEquals("isMemberWritable", true, memberLib.isMemberWritable(obj, memberName));
        assertEquals("isMemberRemovable", true, memberLib.isMemberRemovable(obj, memberName));

        for (boolean hasSideEffects : new boolean[]{true, false}) {
            obj.readSideEffects = hasSideEffects;
            assertEquals(42, memberLib.invokeMember(obj, memberName));
            assertEquals(42, memberLib.readMember(obj, memberName));
            memberLib.writeMember(obj, memberName, 42);
            memberLib.removeMember(obj, memberName);
        }

        obj.throwUnsupported = true;
        assertFails(() -> memberLib.invokeMember(obj, memberName), UnsupportedMessageException.class);
        assertFails(() -> memberLib.readMember(obj, memberName), UnsupportedMessageException.class);
        assertFails(() -> {
            memberLib.writeMember(obj, memberName, 42);
            return null;
        }, UnsupportedMessageException.class);
        assertFails(() -> {
            memberLib.removeMember(obj, memberName);
            return null;
        }, UnsupportedMessageException.class);
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class IsMemberAllUnknown implements TruffleObject {

        static final String MEMBER_NAME = "member";
        boolean isMember = false;
        boolean readSideEffects = true;
        boolean throwUnsupported = false;

        @ExportMessage
        final boolean hasMembers() {
            return true;
        }

        @ExportMessage
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return ProxyArray.fromArray(MEMBER_NAME);
        }

        @ExportMessage(name = "isMemberReadable")
        @ExportMessage(name = "isMemberRemovable")
        @ExportMessage(name = "isMemberModifiable")
        @ExportMessage(name = "isMemberInvocable")
        final boolean isMemberReadable(@SuppressWarnings("unused") String member) {
            return isMember;
        }

        @ExportMessage
        final boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage(name = "hasMemberReadSideEffects")
        @ExportMessage(name = "hasMemberWriteSideEffects")
        final boolean hasMemberReadSideEffects(String member) {
            return switch (member) {
                case MEMBER_NAME -> readSideEffects;
                default -> false;
            };
        }

        @ExportMessage
        final Object readMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
            if (throwUnsupported) {
                throw UnsupportedMessageException.create();
            }
            return switch (member) {
                case MEMBER_NAME -> 42;
                default -> throw UnknownIdentifierException.create(member);
            };
        }

        @ExportMessage
        final Object invokeMember(String member, @SuppressWarnings("unused") Object[] arguments) throws UnknownIdentifierException, UnsupportedMessageException {
            if (throwUnsupported) {
                throw UnsupportedMessageException.create();
            }
            return switch (member) {
                case MEMBER_NAME -> 42;
                default -> throw UnknownIdentifierException.create(member);
            };
        }

        @ExportMessage
        final void writeMember(String member, @SuppressWarnings("unused") Object value) throws UnknownIdentifierException, UnsupportedMessageException {
            if (throwUnsupported) {
                throw UnsupportedMessageException.create();
            }
            switch (member) {
                case MEMBER_NAME -> {
                }
                default -> throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        final void removeMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
            if (throwUnsupported) {
                throw UnsupportedMessageException.create();
            }
            switch (member) {
                case MEMBER_NAME -> {
                }
                default -> throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @TruffleBoundary
        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return getClass().getSimpleName();
        }
    }
}
