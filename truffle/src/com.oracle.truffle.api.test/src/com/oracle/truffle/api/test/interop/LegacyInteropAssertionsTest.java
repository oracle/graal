/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.interop.InteropAssertionsTest.LanguageObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.util.concurrent.Callable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Legacy API test from the old version of InteropAssertionsTest.
 */
@SuppressWarnings({"deprecation", "truffle-abstract-export"})
public class LegacyInteropAssertionsTest extends InteropLibraryBaseTest {

    @Test
    public void testValidScopeUsage() throws Exception {
        LegacyScopeCached sc = new LegacyScopeCached(5);
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
    static class LegacyScopeCached extends LanguageObject {

        final long id;

        LegacyScopeCached(long id) {
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
                return new LegacyScopeCached(id - 1);
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
            return new LegacyScopeMembers(id);
        }

        @Override
        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "ScopeCached[" + id + "]";
        }

        @ExportLibrary(InteropLibrary.class)
        static final class LegacyScopeMembers extends LanguageObject {

            private final long len;

            private LegacyScopeMembers(long len) {
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

    static class LegacyMembers implements TruffleObject {
    }

    @Test
    public void testIsInvocableMemberWithReadSideEffects() throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        setupEnv(Context.create()); // we need no multi threaded context.
        var obj = new LegacyIsInvocableUnknown();
        InteropLibrary memberLib = createLibrary(InteropLibrary.class, obj);
        String memberName = LegacyIsInvocableUnknown.MEMBER_NAME;
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
    static class LegacyIsInvocableUnknown implements TruffleObject {

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
        var obj = new LegacyIsMemberAllUnknown();
        InteropLibrary memberLib = createLibrary(InteropLibrary.class, obj);
        String memberName = LegacyIsMemberAllUnknown.MEMBER_NAME;

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
    static class LegacyIsMemberAllUnknown implements TruffleObject {

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
