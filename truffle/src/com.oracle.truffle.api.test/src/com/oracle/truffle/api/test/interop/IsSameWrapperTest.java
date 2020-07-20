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
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;

public class IsSameWrapperTest extends InteropLibraryBaseTest {

    @ExportLibrary(InteropLibrary.class)
    static class IdentityBoolean implements TruffleObject {

        final boolean b;

        IdentityBoolean(boolean b) {
            this.b = b;
        }

        @ExportMessage
        boolean isBoolean() {
            return true;
        }

        @ExportMessage
        boolean asBoolean() {
            return b;
        }

        @ExportMessage
        TriState isIdenticalOrUndefined(Object other) {
            if (other instanceof IdentityBoolean) {
                boolean otherBoolean = ((IdentityBoolean) other).b;
                return TriState.valueOf(this.b == otherBoolean);
            }
            return TriState.UNDEFINED;
        }

        @ExportMessage
        int identityHashCode() {
            return b ? 1 : 0;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static class IdentityBooleanWrapper implements TruffleObject {

        final Object delegate;

        IdentityBooleanWrapper(Object delegate) {
            this.delegate = delegate;
        }

    }

    @Test
    public void test() throws UnsupportedMessageException {
        Object[] values = new Object[]{
                        true,
                        false,
                        new IdentityBoolean(true),
                        new IdentityBoolean(true),
                        new IdentityBoolean(false),
                        new IdentityBoolean(false),
        };

        int actualTrueCount = 0;
        int actualFalseCount = 0;
        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                Object left = values[i];
                Object right = values[j];
                InteropLibrary leftLib = createLibrary(InteropLibrary.class, left);
                InteropLibrary rightLib = createLibrary(InteropLibrary.class, right);
                boolean expectedResult = true;
                if (!leftLib.hasIdentity(left) || !rightLib.hasIdentity(right)) {
                    expectedResult = false;
                } else {
                    expectedResult = leftLib.asBoolean(left) == rightLib.asBoolean(right);
                }
                if (expectedResult) {
                    actualTrueCount++;
                } else {
                    actualFalseCount++;
                }

                Object leftWrapper = new IdentityBooleanWrapper(left);
                Object rightWrapper = new IdentityBooleanWrapper(right);
                InteropLibrary leftWrapperLib = createLibrary(InteropLibrary.class, leftWrapper);
                InteropLibrary rightWrapperLib = createLibrary(InteropLibrary.class, rightWrapper);

                assertEquals(expectedResult, leftLib.isIdentical(left, right, rightLib));
                assertEquals(expectedResult, rightLib.isIdentical(right, left, leftLib));
                assertEquals(expectedResult, leftWrapperLib.isIdentical(leftWrapper, right, rightLib));
                assertEquals(expectedResult, leftWrapperLib.isIdentical(leftWrapper, rightWrapper, rightWrapperLib));
                assertEquals(expectedResult, leftLib.isIdentical(left, rightWrapper, rightWrapperLib));

                if (leftLib.hasIdentity(left)) {
                    assertEquals(leftLib.asBoolean(left) ? 1 : 0, leftLib.identityHashCode(left));
                    assertEquals(leftLib.asBoolean(left) ? 1 : 0, leftWrapperLib.identityHashCode(leftWrapper));
                } else {
                    assertFails(() -> leftLib.identityHashCode(left), UnsupportedMessageException.class);
                    assertFails(() -> leftWrapperLib.identityHashCode(leftWrapper), UnsupportedMessageException.class);
                }

                if (leftLib.hasIdentity(left) && rightLib.hasIdentity(right)) {
                    if (expectedResult) {
                        assertEquals(leftLib.identityHashCode(left), rightLib.identityHashCode(right));
                        assertEquals(leftLib.identityHashCode(left), rightWrapperLib.identityHashCode(rightWrapper));
                        assertEquals(leftWrapperLib.identityHashCode(leftWrapper), rightLib.identityHashCode(right));
                        assertEquals(leftWrapperLib.identityHashCode(leftWrapper), rightWrapperLib.identityHashCode(rightWrapper));
                    } else {
                        assertNotEquals(leftLib.identityHashCode(left), rightLib.identityHashCode(right));
                        assertNotEquals(leftLib.identityHashCode(left), rightWrapperLib.identityHashCode(rightWrapper));
                        assertNotEquals(leftWrapperLib.identityHashCode(leftWrapper), rightLib.identityHashCode(right));
                        assertNotEquals(leftWrapperLib.identityHashCode(leftWrapper), rightWrapperLib.identityHashCode(rightWrapper));
                    }

                }
            }
        }
        assertEquals(8, actualTrueCount);
        assertEquals(28, actualFalseCount);
    }

}
