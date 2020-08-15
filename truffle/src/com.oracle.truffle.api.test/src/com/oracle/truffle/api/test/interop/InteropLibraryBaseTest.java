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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

@RunWith(Parameterized.class)
public abstract class InteropLibraryBaseTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    protected final void assertNotNull(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isNull(value));
    }

    protected final void assertNoArray(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.hasArrayElements(value));
        assertFalse(lib.isArrayElementInsertable(value, 0L));
        assertFalse(lib.isArrayElementModifiable(value, 0L));
        assertFalse(lib.isArrayElementReadable(value, 0L));
        assertFalse(lib.isArrayElementRemovable(value, 0L));
        assertUnsupported(() -> lib.readArrayElement(value, 0));
        assertUnsupported(() -> lib.getArraySize(value));
        assertUnsupported(() -> lib.removeArrayElement(value, 0));
        assertUnsupported(() -> lib.writeArrayElement(value, 0, ""));
    }

    protected final void assertNoNative(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isPointer(value));
        assertUnsupported(() -> lib.asPointer(value));
        lib.toNative(value); // must not fail.
    }

    protected final void assertNoObject(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.hasMembers(value));
        assertFalse(lib.isMemberReadable(value, "foo"));
        assertFalse(lib.isMemberModifiable(value, "foo"));
        assertFalse(lib.isMemberInsertable(value, "foo"));
        assertFalse(lib.isMemberRemovable(value, "foo"));
        assertFalse(lib.isMemberInvocable(value, "foo"));
        assertFalse(lib.isMemberInternal(value, "foo"));
        assertUnsupported(() -> lib.getMembers(value));
        assertUnsupported(() -> lib.readMember(value, "foo"));
        assertUnsupported(() -> lib.writeMember(value, "foo", "bar"));
        assertUnsupported(() -> lib.removeMember(value, "foo"));
        assertUnsupported(() -> lib.invokeMember(value, "foo"));
    }

    protected final void assertNoBoolean(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isBoolean(value));
        assertUnsupported(() -> lib.asBoolean(value));
    }

    protected final void assertNoMetaObject(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isMetaObject(value));
        assertUnsupported(() -> lib.getMetaQualifiedName(value));
        assertUnsupported(() -> lib.getMetaSimpleName(value));
    }

    protected final void assertNoDate(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isDate(value));
        assertUnsupported(() -> lib.asDate(value));
    }

    protected final void assertNoTime(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isTime(value));
        assertUnsupported(() -> lib.asTime(value));
    }

    protected final void assertNoTimeZone(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isTimeZone(value));
        assertUnsupported(() -> lib.asTimeZone(value));
    }

    protected final void assertNoDuration(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isDuration(value));
        assertUnsupported(() -> lib.asDuration(value));
    }

    protected final void assertNoSourceLocation(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.hasSourceLocation(value));
        assertUnsupported(() -> lib.getSourceLocation(value));
    }

    protected final void assertNoLanguage(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.hasLanguage(value));
        assertUnsupported(() -> lib.getLanguage(value));
    }

    protected final void assertNoIdentity(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.hasIdentity(value));
    }

    protected final void assertHasNoMetaObject(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.hasMetaObject(value));
        assertUnsupported(() -> lib.getMetaObject(value));
    }

    protected final void assertNoString(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isString(value));
        assertUnsupported(() -> lib.asString(value));
    }

    protected final void assertNoNumber(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isNumber(value));
        assertFalse(lib.fitsInByte(value));
        assertFalse(lib.fitsInShort(value));
        assertFalse(lib.fitsInInt(value));
        assertFalse(lib.fitsInLong(value));
        assertFalse(lib.fitsInFloat(value));
        assertFalse(lib.fitsInDouble(value));

        assertUnsupported(() -> lib.asByte(value));
        assertUnsupported(() -> lib.asShort(value));
        assertUnsupported(() -> lib.asInt(value));
        assertUnsupported(() -> lib.asLong(value));
        assertUnsupported(() -> lib.asFloat(value));
        assertUnsupported(() -> lib.asDouble(value));
    }

    protected final void assertNotExecutable(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isExecutable(value));
        assertUnsupported(() -> lib.execute(value));
    }

    protected final void assertNotInstantiable(Object value) {
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        assertFalse(lib.isInstantiable(value));
        assertUnsupported(() -> lib.instantiate(value));
    }

    @FunctionalInterface
    protected interface InteropCallable {

        void call() throws InteropException;

    }

    protected static void assertUnsupported(InteropCallable r) {
        try {
            r.call();
            fail("unsupported message expected");
        } catch (InteropException e) {
            assertTrue(e.getClass().getName(), e instanceof UnsupportedMessageException);
        }
    }

}
