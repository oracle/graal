package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.DynamicDispatch;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.ExpectError;

@RunWith(Parameterized.class)
@SuppressWarnings("unused")
public class AcceptsTest extends AbstractLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED);
    }

    static class NonDispatch {

    }

    static class Dispatch implements DynamicDispatch {
        private final Class<?> dispatch;

        Dispatch(Class<?> dispatch) {
            this.dispatch = dispatch;
        }

        public Class<?> dispatch() {
            return dispatch;
        }
    }

    @GenerateLibrary
    abstract static class DispatchLibrary extends Library {

        public String m0(Object receiver) {
            return "m0";
        }

    }

    @ExpectError("The annotated type 'DispatchError1' is not specified using @DefaultExport in the library 'DispatchLibrary'. Using explicit receiver classes is only supported for default exports or receiver types that implement DynamicDispatch.")
    @ExportLibrary(value = DispatchLibrary.class, receiverClass = NonDispatch.class)
    static class DispatchError1 {
    }

    @ExpectError("@ExportLibrary cannot be used on types that implement DynamicDispatch. They are mutually exclusive.")
    @ExportLibrary(DispatchLibrary.class)
    static class DispatchError2 implements DynamicDispatch {

        public Class<?> dispatch() {
            return null;
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class DispatchValid1 {

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class DispatchValid2 {

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid2.class);
            return "d";
        }
    }

    @Test
    public void testNullDispatch() {
        Dispatch d = new Dispatch(null);
        assertAssertionError(() -> createLibrary(DispatchLibrary.class, d));
    }

    @Test
    public void testValidAccepts() {
        Dispatch d1 = new Dispatch(DispatchValid1.class);
        Dispatch d2 = new Dispatch(DispatchValid2.class);
        Dispatch d3 = new Dispatch(CustomAccepts1.class);
        Dispatch d4 = new Dispatch(CustomAccepts2.class);
        Dispatch d5 = new Dispatch(CustomAccepts3.class);

        List<Object> ds = Arrays.asList(d1, d2, d3, d4, d5);

        for (Object d : ds) {
            DispatchLibrary l = createLibrary(DispatchLibrary.class, d);
            assertTrue(l.accepts(d));

            for (Object otherDispatch : ds) {
                if (otherDispatch != d) {
                    assertFalse(l.accepts(otherDispatch));
                    assertAssertionError(() -> l.m0(d));
                }
            }
            assertEquals("d", l.m0(d));
            assertAssertionError(() -> l.m0(""));
        }
    }

    @Test
    public void testInvalidAccepts() {
        Dispatch d1 = new Dispatch(InvalidAccepts1.class);
        Dispatch d2 = new Dispatch(InvalidAccepts2.class);
        Dispatch d3 = new Dispatch(InvalidAccepts3.class);
        InvalidAccepts4 d4 = new InvalidAccepts4();
        Dispatch d5 = new Dispatch(InvalidAccepts5.class);

        List<Object> values = Arrays.asList(d1, d2, d3, d4, d5);
        for (Object d : values) {
            DispatchLibrary l = createLibrary(DispatchLibrary.class, d);
            assertAssertionError(() -> l.accepts(d));
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class CustomAccepts1 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return receiver instanceof Dispatch && ((Dispatch) receiver).dispatch == CustomAccepts1.class;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class CustomAccepts2 {

        @ExportMessage
        static boolean accepts(Dispatch receiver) {
            return receiver.dispatch == CustomAccepts2.class;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class CustomAccepts3 {

        @ExportMessage
        static boolean accepts(Object receiver, @Cached("receiver.getClass()") Class<?> receiverClass) {
            return receiver instanceof Dispatch && ((Dispatch) receiver).dispatch == CustomAccepts3.class;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts1 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return receiver instanceof Dispatch;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d1";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts2 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return true;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d1";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts3 {

        @ExportMessage
        static boolean accepts(Dispatch receiver) {
            return true;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d1";
        }
    }

    @ExportLibrary(DispatchLibrary.class)
    static class InvalidAccepts4 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return true;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") InvalidAccepts4 receiver) {
            return "d1";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts5 {

        @ExportMessage
        static class AcceptsNode extends Node {
            @Specialization
            static boolean accepts(Dispatch receiver) {
                return true;
            }
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d1";
        }
    }

    private static void assertAssertionError(Runnable r) {
        try {
            r.run();
            fail();
        } catch (AssertionError e) {
        }
    }

}
