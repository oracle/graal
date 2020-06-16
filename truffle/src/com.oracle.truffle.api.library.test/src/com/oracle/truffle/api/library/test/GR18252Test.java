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
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

/*
 * Test for GR-18252.
 */
@SuppressWarnings("unused")
public class GR18252Test extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @ExportLibrary(DynamicDispatchLibrary.class)
    static class Data implements TruffleObject {
        private final Object value;
        private final Class<?> dispatchTarget;

        Data(Class<?> dispatchTarget, Object value) {
            this.dispatchTarget = dispatchTarget;
            this.value = value;
        }

        @ExportMessage
        public Class<?> dispatch() {
            return dispatchTarget;
        }
    }

    @GenerateLibrary
    abstract static class ALibrary extends Library {

        public abstract boolean is(Object receiver);

        @GenerateLibrary.Abstract(ifExported = "is")
        public Object get(Object receiver) {
            return null;
        }
    }

    @ExportLibrary(value = ALibrary.class, receiverType = Data.class)
    static class AMessages {

        @ExportMessage
        public static boolean is(Data receiver,
                        @Shared("profile") @Cached BranchProfile profile) {
            return false;
        }

        @ExportMessage
        public static Object get(Data receiver,
                        @Shared("profile") @Cached BranchProfile profile) {
            return null;
        }
    }

    @ExportLibrary(value = ALibrary.class, receiverType = Data.class)
    static class BMessages extends AMessages {

        @ExportMessage
        public static class Is {
            @Specialization
            public static boolean is(Data receiver,
                            @Cached BranchProfile p0,
                            @Cached BranchProfile p1) {
                return true;
            }
        }

        @ExportMessage
        public static Object get(Data receiver) {
            return receiver.value;
        }
    }

    /*
     * Asserts that this should not generate a shared cached warning.
     */
    @ExportLibrary(value = ALibrary.class, receiverType = Data.class)
    static class CMessagesNoWarn1 extends AMessages {

        @ExportMessage
        public static boolean is(Data receiver,
                        @Cached BranchProfile p0,
                        @Cached BranchProfile p1) {
            return true;
        }

    }

    /*
     * Asserts that this should not generate a shared cached warning.
     */
    @ExportLibrary(value = ALibrary.class, receiverType = Data.class)
    static class CMessagesNoWarn2 extends AMessages {

        @ExportMessage
        public static class Is {
            @Specialization
            public static boolean is(Data receiver,
                            @Cached BranchProfile profile,
                            @Cached BranchProfile profile1) {
                return true;
            }
        }
    }

    /*
     * Asserts that this should not generate a shared cached warning.
     */
    @ExportLibrary(value = ALibrary.class, receiverType = Data.class)
    static class CMessagesNoWarn3 extends BMessages {

        @ExportMessage
        public static Object get(Data receiver,
                        @Cached BranchProfile profile1) {
            return receiver.value;
        }
    }

    @Test
    public void testDispatchingToAIs() {
        Data dataA = new Data(AMessages.class, "value");
        assertEquals(false, createLibrary(ALibrary.class, dataA).is(dataA));
    }

    @Test
    public void testDispatchingToBIs() {
        Data dataB = new Data(BMessages.class, "value");
        assertEquals(true, createLibrary(ALibrary.class, dataB).is(dataB));
    }

    @Test
    public void testDispatchingToAGet() {
        Data dataA = new Data(AMessages.class, "value");
        assertEquals(null, createLibrary(ALibrary.class, dataA).get(dataA));
    }

    @Test
    public void testDispatchingToBGet() {
        Data dataB = new Data(BMessages.class, "value");
        assertEquals("value", createLibrary(ALibrary.class, dataB).get(dataB));
    }

}
