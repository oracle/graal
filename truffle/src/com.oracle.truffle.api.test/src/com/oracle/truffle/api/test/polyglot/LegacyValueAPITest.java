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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import static com.oracle.truffle.tck.tests.ValueAssert.assertValue;
import java.util.Map;
import java.util.function.Consumer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings({"deprecation", "truffle-abstract-export"})
public class LegacyValueAPITest {

    private static Context context;
    private static Context secondaryContext;

    @BeforeClass
    public static void setUp() {
        context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        secondaryContext = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
    }

    @AfterClass
    public static void tearDown() {
        context.close();
        secondaryContext.close();
    }

    @Test
    public void testObjectCoercion() {
        LegacyMembersAndInvocable invocable = new LegacyMembersAndInvocable();
        invocable.invokeMember = "foo";
        invocable.invocableResult = "foobarbaz";

        objectCoercionTest(invocable, Map.class, (v) -> {
            Value value = context.asValue(v);
            assertTrue(value.canInvokeMember("foo"));
            assertEquals("foobarbaz", value.invokeMember("foo").asString());
        }, false);

    }

    @SuppressWarnings({"unchecked"})
    private static <T> void objectCoercionTest(Object value, Class<T> expectedType, Consumer<T> validator, boolean valueTest) {
        Value coerce = context.asValue(new ValueAPITest.CoerceObject()).getMember("coerce");
        T result = (T) context.asValue(value).as(Object.class);
        if (result != null) {
            assertTrue("expected " + expectedType + " but was " + result.getClass(), expectedType.isInstance(result));
        } else if (value != null) {
            fail("expected " + expectedType + " but was null");
        }

        if (validator == null) {
            assertEquals(value, result);
        } else {
            validator.accept(result);
            coerce.execute(value, validator);
        }

        if (valueTest) {
            assertValueInContexts(context.asValue(value));
            assertValueInContexts(context.asValue(result));
        }
    }

    private static void assertValueInContexts(Value value) {
        assertValue(value);
        assertValue(secondaryContext.asValue(value));
    }

    @ExportLibrary(InteropLibrary.class)
    static final class LegacyMembersAndInvocable implements TruffleObject {

        String invokeMember;
        Object invocableResult;

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean internal) {
            return new LegacyKeysArray(new String[]{invokeMember});
        }

        @ExportMessage
        Object invokeMember(String member, @SuppressWarnings("unused") Object[] arguments) throws UnknownIdentifierException {
            if (member.equals(invokeMember)) {
                return invocableResult;
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        boolean isMemberInvocable(String member) {
            return invokeMember.equals(member);
        }

        @ExportLibrary(InteropLibrary.class)
        static final class LegacyKeysArray implements TruffleObject {

            private final String[] keys;

            LegacyKeysArray(String[] keys) {
                this.keys = keys;
            }

            @SuppressWarnings("static-method")
            @ExportMessage
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            boolean isArrayElementReadable(long index) {
                return index >= 0 && index < keys.length;
            }

            @ExportMessage
            long getArraySize() {
                return keys.length;
            }

            @ExportMessage
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                try {
                    return keys[(int) index];
                } catch (IndexOutOfBoundsException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw InvalidArrayIndexException.create(index);
                }
            }
        }
    }

}
