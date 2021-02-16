/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

public class ExportDelegationTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED);
    }

    @Test
    public void testNoExports() {
        Object receiver = new ExportDelegationNoExports(new Object());
        ExportDelegationLibrary lib = createLibrary(ExportDelegationLibrary.class, receiver);

        assertEquals("m0_default", lib.m0(receiver));
        assertEquals("m1_default_a0", lib.m1(receiver, "a0"));
        assertEquals("m2_default_a0_a1", lib.m2(receiver, "a0", "a1"));
        lib.m3(receiver);
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationNoExports {

        final Object delegate;

        ExportDelegationNoExports(Object delegate) {
            this.delegate = delegate;
        }

    }

    @Test
    public void testSingleExports() {
        Object receiver = new ExportDelegationSingleExports(new Object());
        ExportDelegationLibrary lib = createLibrary(ExportDelegationLibrary.class, receiver);

        assertEquals("m0_single_exports", lib.m0(receiver));
        assertEquals("m1_default_a0", lib.m1(receiver, "a0"));
        assertEquals("m2_default_a0_a1", lib.m2(receiver, "a0", "a1"));
        lib.m3(receiver);
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationSingleExports {

        final Object delegate;

        ExportDelegationSingleExports(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        public String m0() {
            return "m0_single_exports";
        }

    }

    @Test
    public void testDoubleExports() {
        Object receiver = new ExportDelegationDoubleExports(new Object());
        ExportDelegationLibrary lib = createLibrary(ExportDelegationLibrary.class, receiver);

        assertEquals("m0_default", lib.m0(receiver));
        assertEquals("m1_double_exports_a0", lib.m1(receiver, "a0"));
        assertEquals("m2_double_exports_a0_a1", lib.m2(receiver, "a0", "a1"));
        lib.m3(receiver);
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationDoubleExports {

        final Object delegate;

        ExportDelegationDoubleExports(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        public String m1(String arg0) {
            return "m1_double_exports_" + arg0;
        }

        @ExportMessage
        public String m2(String arg0, String arg1) {
            return "m2_double_exports_" + arg0 + "_" + arg1;
        }

    }

    @Test
    public void testDelegationWithReflection() throws UnsupportedMessageException {
        Object delegate = 42;
        Object receiver = new ExportDelegationWithReflection(delegate);
        ExportDelegationLibrary lib = createLibrary(ExportDelegationLibrary.class, receiver);
        InteropLibrary interop = createLibrary(InteropLibrary.class, receiver);

        assertEquals("m0_default", lib.m0(receiver));
        assertEquals("m1_double_exports_a0", lib.m1(receiver, "a0"));
        assertEquals("m2_default_a0_a1", lib.m2(receiver, "a0", "a1"));
        lib.m3(receiver);

        assertTrue(interop.isNumber(receiver));
        assertEquals(42, interop.asInt(receiver));
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    @ExportLibrary(value = ReflectionLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationWithReflection implements TruffleObject {

        final Object delegate;

        ExportDelegationWithReflection(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        public String m1(String arg0) {
            return "m1_double_exports_" + arg0;
        }

    }

    @Test
    public void testDelegationWithThisCachedLibrary() {
        Object delegate = new ExportDelegationSingleExports(new Object());
        Object receiver = new ExportDelegationWithThisCachedLibrary(delegate);
        ExportDelegationLibrary lib = createLibrary(ExportDelegationLibrary.class, receiver);

        assertEquals("m0_single_exports", lib.m0(receiver));
        assertEquals("m1_m0_single_exports_a0", lib.m1(receiver, "a0"));
        assertEquals("m2_default_a0_a1", lib.m2(receiver, "a0", "a1"));
        lib.m3(receiver);
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationWithThisCachedLibrary {

        final Object delegate;

        ExportDelegationWithThisCachedLibrary(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        public String m1(String arg0, @CachedLibrary("this") ExportDelegationLibrary library) {
            return "m1_" + library.m0(this) + "_" + arg0;
        }
    }

    @Test
    public void testExportDelegationWithTwoLibraries() {
        Object delegate = new ExportDelegationSingleExports(new Object());
        Object receiver = new ExportDelegationWithTwoLibraries(delegate);
        ExportDelegationLibrary exportLib = createLibrary(ExportDelegationLibrary.class, receiver);
        OtherDelegationLibrary otherLib = createLibrary(OtherDelegationLibrary.class, receiver);

        assertEquals("m0_single_exports", exportLib.m0(receiver));
        assertEquals("m1_a0", exportLib.m1(receiver, "a0"));
        assertEquals("m2_default_a0_a1", exportLib.m2(receiver, "a0", "a1"));
        exportLib.m3(receiver);

        assertEquals("otherm0_default", otherLib.m0(receiver));
        assertEquals("m1_a0", otherLib.m1(receiver, "a0"));
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    @ExportLibrary(value = OtherDelegationLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationWithTwoLibraries {

        final Object delegate;

        ExportDelegationWithTwoLibraries(Object delegate) {
            this.delegate = delegate;
        }

        // exports from ExportDelegationLibrary and OtherDelegationLibrary
        @ExportMessage
        public String m1(String arg0) {
            return "m1_" + arg0;
        }
    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    @ExportLibrary(value = OtherDelegationLibrary.class, delegateTo = "delegate")
    public static class ExportDelegationWithTwoLibrariesAndCachedLibrary {

        final Object delegate;

        ExportDelegationWithTwoLibrariesAndCachedLibrary(Object delegate) {
            this.delegate = delegate;
        }

        // exports from ExportDelegationLibrary and OtherDelegationLibrary
        @ExportMessage
        public String m1(String arg0, @CachedLibrary("this") ExportDelegationLibrary exportLib,
                        @CachedLibrary("this") OtherDelegationLibrary otherLib) {
            return "m1_" + exportLib.m0(this) + "_" + otherLib.m0(this) + "_" + arg0;
        }

    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public abstract class ExportDelegationPrimitive {

        final int delegate = 0;

        @ExportMessage
        public String m1(String arg0) {
            return "m1_" + arg0;
        }

    }

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class ExportDelegationLibrary extends Library {

        public String m0(Object receiver) {
            return "m0_default";
        }

        public String m1(Object receiver, String arg0) {
            return "m1_default_" + arg0;
        }

        public String m2(Object receiver, String arg0, String arg1) {
            return "m2_default_" + arg0 + "_" + arg1;
        }

        public void m3(Object receiver) {
        }
    }

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class OtherDelegationLibrary extends Library {

        public String m0(Object receiver) {
            return "otherm0_default";
        }

        public String m1(Object receiver, String arg0) {
            return "otherm1_default_" + arg0;
        }

    }

    @ExpectError("The delegation variable with name 'delegate' could not be found in type 'ExportDelegationError1'. " +
                    "Declare a field 'final Object delegate' in 'ExportDelegationError1' to resolve this problem.")
    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public abstract class ExportDelegationError1 {

    }

    @ExpectError("The delegation variable with name 'delegate' in type 'ExportDelegationError2' must be have the modifier final. Make the variable final to resolve the problem.")
    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public abstract class ExportDelegationError2 {

        Object delegate;

    }

    @ExpectError("The delegation variable with name 'delegate' in type 'ExportDelegationError3' is not visible in package 'com.oracle.truffle.api.library.test'. " +
                    "Increase the visibility to resolve this problem.")
    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public abstract class ExportDelegationError3 {

        @SuppressWarnings("unused") private final Object delegate = null;

    }

    @ExportLibrary(value = ExportDelegationLibrary.class, delegateTo = "delegate")
    public abstract class ExportDelegationError5 {

        final Object delegate = null;

        @ExpectError("Exporting a custom accepts method is currently not supported when export delegation is used in @ExportLibrary. " +
                        "Remove delegateTo from all exports or remove the accepts export to resolve this.")
        @ExportMessage
        public boolean accepts() {
            return false;
        }

    }

}
