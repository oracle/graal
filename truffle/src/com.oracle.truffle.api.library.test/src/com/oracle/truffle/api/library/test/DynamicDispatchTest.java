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
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@RunWith(Parameterized.class)
@SuppressWarnings("unused")
public class DynamicDispatchTest extends AbstractParametrizedLibraryTest {

    //

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED);
    }

    static class NonDispatch {

    }

    @ExportLibrary(DynamicDispatchLibrary.class)
    static class DynamicDispatch {

        Class<?> dispatch;

        DynamicDispatch(Class<?> dispatch) {
            this.dispatch = dispatch;
        }

        @ExportMessage
        protected final Class<?> dispatch() {
            return dispatch;
        }
    }

    @ExportLibrary(value = TestDispatchLibrary.class)
    static class NonFinalDispatch {

        @ExportMessage
        String m0() {
            return "m0_non_final";
        }

    }

    @ExportLibrary(TestDispatchLibrary.class)
    static final class FinalDispatch {

        @SuppressWarnings("static-method")
        @ExportMessage
        String m0() {
            return "m0_final";
        }

    }

    @ExportLibrary(value = TestDispatchLibrary.class, receiverType = DynamicDispatch.class)
    static class DynamicDispatchTarget1 {

        @ExportMessage
        static String m0(DynamicDispatch dispatch) {
            return "m0_dynamic_dispatch_target1";
        }

    }

    @ExportLibrary(value = TestDispatchLibrary.class, receiverType = DynamicDispatch.class)
    static final class TestDispatchDefaultExport {

        @ExportMessage
        static String m0(DynamicDispatch receiver) {
            return "m0_default_dynamic_dispatch";
        }
    }

    @GenerateLibrary
    @DefaultExport(TestDispatchDefaultExport.class)
    abstract static class TestDispatchLibrary extends Library {
        public String m0(Object receiver) {
            return "m0";
        }
    }

    @Test
    public void testFinalDispatch() {
        Object finalDispatch = new FinalDispatch();
        Object nonFinalDispatch = new NonFinalDispatch();
        Object dynamicDispatch = new DynamicDispatch(null);

        // Test FinalDispatch
        TestDispatchLibrary lib = createLibrary(TestDispatchLibrary.class, finalDispatch);
        assertTrue(lib.accepts(finalDispatch));
        assertFalse(lib.accepts(nonFinalDispatch));
        assertFalse(lib.accepts(dynamicDispatch));

        assertEquals("m0_final", lib.m0(finalDispatch));
        assertAssertionError(() -> lib.m0(nonFinalDispatch));
        assertAssertionError(() -> lib.m0(dynamicDispatch));
    }

    @Test
    public void testNonFinalDispatch() {
        Object finalDispatch = new FinalDispatch();
        Object nonFinalDispatch = new NonFinalDispatch();
        Object dynamicDispatch = new DynamicDispatch(null);

        // Test FinalDispatch
        TestDispatchLibrary lib = createLibrary(TestDispatchLibrary.class, nonFinalDispatch);
        assertFalse(lib.accepts(finalDispatch));
        assertTrue(lib.accepts(nonFinalDispatch));
        assertFalse(lib.accepts(dynamicDispatch));

        assertAssertionError(() -> lib.m0(finalDispatch));
        assertEquals("m0_non_final", lib.m0(nonFinalDispatch));
        assertAssertionError(() -> lib.m0(dynamicDispatch));
    }

    @Test
    public void testInvalidDynamicDispatch() {
        Object incompatibleDispatch = new DynamicDispatch(FinalDispatch.class);
        assertAssertionError(() -> createLibrary(TestDispatchLibrary.class, incompatibleDispatch));
    }

    @Test
    public void testDispatchChanges() {
        DynamicDispatch object = new DynamicDispatch(null);
        TestDispatchLibrary lib = createLibrary(TestDispatchLibrary.class, object);
        assertTrue(lib.accepts(object));
        assertEquals("m0_default_dynamic_dispatch", lib.m0(object));

        assertFalse(lib.accepts(new Object()));
        assertFalse(lib.accepts(new DynamicDispatch(FinalDispatch.class)));

        // change dispatch to valid dispatch
        // this shoudl work
        object.dispatch = DynamicDispatchTarget1.class;
        assertFalse(lib.accepts(object));
        lib = createLibrary(TestDispatchLibrary.class, object);
        assertTrue(lib.accepts(object));

        // change dispatch to invalid default dispatch
        object.dispatch = TestDispatchDefaultExport.class;
        assertFalse(lib.accepts(object));
        assertAssertionError(() -> createLibrary(TestDispatchLibrary.class, object),
                        "Dynamic dispatch from receiver class 'com.oracle.truffle.api.library.test.DynamicDispatchTest$DynamicDispatch' " +
                                        "to default export 'com.oracle.truffle.api.library.test.DynamicDispatchTest$TestDispatchDefaultExport' detected. " +
                                        "Use null instead to dispatch to a default export.");

        // change dispatch to incompatible dispatch
        object.dispatch = FinalDispatch.class;
        assertAssertionError(() -> createLibrary(TestDispatchLibrary.class, object),
                        "Receiver class com.oracle.truffle.api.library.test.DynamicDispatchTest$DynamicDispatch " +
                                        "was dynamically dispatched to incompatible exports com.oracle.truffle.api.library.test.DynamicDispatchTest$FinalDispatch. " +
                                        "Expected receiver class com.oracle.truffle.api.library.test.DynamicDispatchTest$FinalDispatch.");
    }

    @Test
    public void testDynamicDispatch() {
        Object finalDispatch = new FinalDispatch();
        Object nonFinalDispatch = new NonFinalDispatch();
        Object dynamicDispatch = new DynamicDispatch(DynamicDispatchTarget1.class);

        // Test FinalDispatch
        TestDispatchLibrary lib = createLibrary(TestDispatchLibrary.class, dynamicDispatch);
        assertFalse(lib.accepts(finalDispatch));
        assertFalse(lib.accepts(nonFinalDispatch));
        assertTrue(lib.accepts(dynamicDispatch));

        assertAssertionError(() -> lib.m0(finalDispatch));
        assertAssertionError(() -> lib.m0(nonFinalDispatch));
        assertEquals("m0_dynamic_dispatch_target1", lib.m0(dynamicDispatch));
    }

    @GenerateLibrary(dynamicDispatchEnabled = false)
    abstract static class TestDisabledDispatchLibrary extends Library {
        public String m0(Object receiver) {
            return "m0";
        }
    }

    @ExpectError("Using explicit receiver types is only supported for default exports or types that export DynamicDispatchLibrary.%n" +
                    "Note that dynamic dispatch is disabled for the exported library 'TestDisabledDispatchLibrary'.%")
    @ExportLibrary(value = TestDisabledDispatchLibrary.class, receiverType = DynamicDispatch.class)
    abstract static class DisabledDynamicDispatchError1 {
        @ExportMessage
        static String m0(DynamicDispatch receiver) {
            throw new AssertionError();
        }
    }

    @ExportLibrary(value = TestDisabledDispatchLibrary.class)
    static class DisabledDynamicDispatch extends DynamicDispatch {

        DisabledDynamicDispatch(Class<?> dispatch) {
            super(dispatch);
        }

        @ExportMessage
        String m0() {
            return "m0_export";
        }
    }

    @Test
    public void testDisabledDispatch() {
        DisabledDynamicDispatch dynamicDispatch = new DisabledDynamicDispatch(DisabledDynamicDispatch.class);
        TestDisabledDispatchLibrary lib = createLibrary(TestDisabledDispatchLibrary.class, dynamicDispatch);
        assertEquals("m0_export", lib.m0(dynamicDispatch));
    }

    @ExportLibrary(value = TestDispatchLibrary.class)
    static class ErrorNonFinalDispatch1 {

        @ExportMessage
        String m0() {
            return "m0_non_final";
        }

    }

    @ExpectError("@ExportLibrary cannot be used for other libraries if the DynamicDispatchLibrary library is exported. %")
    @ExportLibrary(value = TestDispatchLibrary.class)
    @ExportLibrary(value = DynamicDispatchLibrary.class)
    static final class ErrorFinalDispatch1 {
        @SuppressWarnings("static-method")
        @ExportMessage
        String m0() {
            return "m0_non_final";
        }
    }

    @ExpectError("@ExportLibrary cannot be used for other libraries if the DynamicDispatchLibrary library is exported. %")
    @ExportLibrary(value = TestDispatchLibrary.class)
    static final class ErrorDynamicDispatch1 extends DynamicDispatch {

        ErrorDynamicDispatch1(Class<?> dispatch) {
            super(dispatch);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        String m0() {
            return "m0_non_final";
        }
    }

    // test that cast cannot be override
    @ExportLibrary(DynamicDispatchLibrary.class)
    @SuppressWarnings("static-method")
    @ExpectError("No message 'cast' found for library DynamicDispatchLibrary.")
    static final class ErrorOverrideCast1 {

        @ExportMessage
        Class<?> dispatch() {
            return null;
        }

        @ExportMessage
        Object cast() {
            return null;
        }
    }

    @GenerateLibrary
    abstract static class TestOtherLibrary extends Library {
        public String m1(Object receiver) {
            return "m1";
        }
    }

    @Test
    public void testOtherLibraryDefault() {
        Object dynamicDispatch = new DynamicDispatch(DynamicDispatchTarget1.class);

        TestOtherLibrary lib = createLibrary(TestOtherLibrary.class, dynamicDispatch);
        assertEquals("m1", lib.m1(dynamicDispatch));
    }

}
