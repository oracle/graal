/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.graalvm.home.Version;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIState;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.nfi.test.parser.backend.TestClosure;
import com.oracle.truffle.nfi.test.parser.backend.TestSignature.TestFunction;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class ExceptionNFITest extends NFITest {

    static final TestCallback NOOP = new TestCallback(0, (args) -> {
        return 0;
    });

    static class MyException extends RuntimeException {
        private static final long serialVersionUID = 5984933885288699021L;

        static final TestCallback THROW = new TestCallback(0, (args) -> {
            throw new MyException();
        });
    }

    static class SuccessException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        static final TestCallback THROW = new TestCallback(0, (args) -> {
            throw new SuccessException();
        });
    }

    public class NativeJustCall extends SendExecuteNode {

        public NativeJustCall() {
            super("native_just_call", "(():void) : void");
        }
    }

    public class NativeJustCall2 extends SendExecuteNode {

        public NativeJustCall2() {
            super("native_just_call_2", "(():void, ():void) : void");
        }
    }

    public static class NativeStringCallback extends SendExecuteNode {

        public NativeStringCallback() {
            super("native_string_callback", "(():string) : string");
        }
    }

    public static class NativeNullableCallback extends SendExecuteNode {

        public NativeNullableCallback() {
            super("callback_null_ret", "(():NULLABLE) : STRING");
        }
    }

    public static class NativeNullPointer extends SendExecuteNode {

        public NativeNullPointer() {
            super("return_null", "() : POINTER");
        }
    }

    private static Object recursiveCallback(CallTarget target) {
        Object[] callback = new Object[1];
        callback[0] = new TestCallback(0, (args) -> target.call(callback[0]));
        return callback[0];
    }

    public class NativeCheckException extends SendExecuteNode {

        public NativeCheckException() {
            super("native_check_exception", "(env, ():void, ():void) : void");
        }
    }

    /**
     * Checks that Java -> native -> Java (throws) propagates the Java exception through the native
     * boundary.
     */
    @Test(expected = MyException.class)
    public void testExceptionPropagation(@Inject(NativeJustCall.class) CallTarget target) {
        target.call(MyException.THROW);
    }

    @Test
    public void testPendingExceptionDoesNotLeak(@Inject(NativeJustCall.class) CallTarget target) {
        RuntimeException first = new MyException();
        RuntimeException second = new SuccessException();
        Assert.assertSame(first, Assert.assertThrows(RuntimeException.class, () -> target.call(new TestCallback(0, args -> {
            throw first;
        }))));
        int[] calls = {0};
        target.call(new TestCallback(0, args -> {
            calls[0]++;
            return 0;
        }));
        Assert.assertEquals(1, calls[0]);
        Assert.assertSame(second, Assert.assertThrows(RuntimeException.class, () -> target.call(new TestCallback(0, args -> {
            throw second;
        }))));
        target.call(NOOP);
    }

    /**
     * Checks that a stack overflow while entering a native closure is propagated and does not leave
     * the thread in a state that prevents later NFI calls.
     */
    @Test
    public void testStackOverflowDuringCallback(@Inject(NativeJustCall.class) CallTarget target) {
        assumeStackOverflowSupported();
        Assert.assertThrows(StackOverflowError.class, () -> target.call(recursiveCallback(target)));
        target.call(NOOP);
    }

    /*
     * Keep different return types in separate tests: unsupported signatures skip the entire test,
     * including during target injection. For example, Panama supports numeric and pointer returns
     * but not string or nullable returns.
     */
    @Test
    public void testStackOverflowDuringStringCallbackReturnMarshalling(
                    @Inject(NativeStringCallback.class) CallTarget stringTarget,
                    @Inject(NativeNullPointer.class) CallTarget nullPointer) throws UnsupportedMessageException {
        assumeStackOverflowSupported();
        Assert.assertThrows(StackOverflowError.class, () -> stringTarget.call(recursiveCallback(stringTarget)));

        Assert.assertTrue("isNull", UNCACHED_INTEROP.isNull(nullPointer.call()));

        Object result = stringTarget.call(new TestCallback(0, (args) -> "ok"));
        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(result));
        Assert.assertEquals("different", UNCACHED_INTEROP.asString(result));
    }

    @Test
    public void testStackOverflowDuringNullableCallbackReturnMarshalling(@Inject(NativeNullableCallback.class) CallTarget target) {
        assumeStackOverflowSupported();
        Assert.assertThrows(StackOverflowError.class, () -> target.call(recursiveCallback(target)));
    }

    @Test
    public void testStackOverflowDuringPointerCallbackReturnMarshalling() throws UnsupportedMessageException {
        testStackOverflowDuringNumericCallback("POINTER");
    }

    @Test
    public void testStackOverflowDuringIntCallbackReturnMarshalling() throws UnsupportedMessageException {
        testStackOverflowDuringNumericCallback("SINT32");
    }

    @Test
    public void testStackOverflowDuringFloatCallbackReturnMarshalling() throws UnsupportedMessageException {
        testStackOverflowDuringNumericCallback("FLOAT");
    }

    @Test
    public void testStackOverflowDuringDoubleCallbackReturnMarshalling() throws UnsupportedMessageException {
        testStackOverflowDuringNumericCallback("DOUBLE");
    }

    @Test
    public void testStackOverflowDuringFP80CallbackReturnMarshalling() throws UnsupportedMessageException {
        Assume.assumeTrue(IS_AMD64 && !IS_WINDOWS);
        testStackOverflowDuringNumericCallback("FP80");
    }

    @Test
    public void testStackOverflowDuringFP128CallbackReturnMarshalling() throws UnsupportedMessageException {
        Assume.assumeTrue(IS_AARCH64 && !IS_WINDOWS && !IS_DARWIN);
        testStackOverflowDuringNumericCallback("FP128");
    }

    private static void testStackOverflowDuringNumericCallback(String type) throws UnsupportedMessageException {
        assumeStackOverflowSupported();
        CallTarget target = new SendExecuteNode("call_closure_noarg_" + type, String.format("(():%s) : %s", type, type)).getCallTarget();
        Assert.assertThrows(StackOverflowError.class, () -> target.call(recursiveCallback(target)));

        Object result = target.call(new TestCallback(0, (args) -> 42L));
        if (type.equals("POINTER")) {
            Assert.assertFalse("isNull", UNCACHED_INTEROP.isNull(result));
            Assert.assertEquals(42L, UNCACHED_INTEROP.asPointer(result));
        } else {
            Assert.assertTrue("isNumber", UNCACHED_INTEROP.isNumber(result));
            Assert.assertEquals("return value", 42.0, UNCACHED_INTEROP.asDouble(result), 0.0);
        }
    }

    @Test
    public void testNumericCallbackExceptionReturnsZero() {
        for (String type : new String[]{"SINT8", "UINT8", "SINT16", "UINT16", "SINT32", "UINT32", "SINT64", "UINT64", "POINTER", "FLOAT", "DOUBLE"}) {
            checkCallbackExceptionReturnsZero(type);
        }
    }

    @Test
    public void testFP80CallbackExceptionReturnsZero() {
        Assume.assumeTrue(IS_AMD64 && !IS_WINDOWS);
        checkCallbackExceptionReturnsZero("FP80");
    }

    @Test
    public void testFP128CallbackExceptionReturnsZero() {
        Assume.assumeTrue(IS_AARCH64 && !IS_WINDOWS && !IS_DARWIN);
        checkCallbackExceptionReturnsZero("FP128");
    }

    private static void checkCallbackExceptionReturnsZero(String type) {
        // The native wrapper checks the result before the pending exception is rethrown in Java.
        CallTarget checkZero = new SendExecuteNode("check_closure_zero_" + type, String.format("(():%s) : void", type)).getCallTarget();
        Assert.assertThrows(MyException.class, () -> checkZero.call(MyException.THROW));
    }

    private static void assumeStackOverflowSupported() {
        Assume.assumeFalse("Panama upcalls can terminate the VM on stack overflow", "panama".equals(TEST_BACKEND));
    }

    @Test
    public void testBackendExceptionPrecedenceAndCleanup() throws Exception {
        Source source = Source.newBuilder("nfi", "with test (():sint32):sint32", "exceptionPrecedence").internal(true).build();
        Object signature = runWithPolyglot.getTruffleTestEnv().parseInternal(source).call();
        RuntimeException callbackFailure = new RuntimeException("callback");
        RuntimeException backendFailure = new RuntimeException("backend");
        TestFunction failingBackend = args -> {
            UNCACHED_INTEROP.execute(((TestClosure) args[0]).executable);
            throw backendFailure;
        };
        TestCallback throwing = new TestCallback(0, args -> {
            throw callbackFailure;
        });
        Assert.assertSame(callbackFailure, Assert.assertThrows(RuntimeException.class,
                        () -> SignatureLibrary.getUncached().call(signature, failingBackend, throwing)));
        int[] calls = {0};
        TestCallback successful = new TestCallback(0, args -> {
            calls[0]++;
            return 42;
        });
        TestFunction successfulBackend = args -> UNCACHED_INTEROP.execute(((TestClosure) args[0]).executable);
        Assert.assertEquals(42, SignatureLibrary.getUncached().call(signature, successfulBackend, successful));
        Assert.assertEquals(1, calls[0]);
        Assert.assertSame(backendFailure, Assert.assertThrows(RuntimeException.class,
                        () -> SignatureLibrary.getUncached().call(signature, failingBackend, successful)));
        Assert.assertEquals(42, SignatureLibrary.getUncached().call(signature, successfulBackend, successful));
        Assert.assertEquals(3, calls[0]);
    }

    @Test
    public void testSuccessfulCallbackWithPendingException() {
        CallTarget target = new SendExecuteNode("native_call_after_exception", "(():void, ():sint32, (sint32):void):void").getCallTarget();
        int[] observed = {-1};
        TestCallback success = new TestCallback(0, args -> 42);
        TestCallback observe = new TestCallback(1, args -> {
            observed[0] = (int) args[0];
            return 0;
        });
        Assert.assertThrows(MyException.class, () -> target.call(MyException.THROW, success, observe));
        Assert.assertEquals(42, observed[0]);
    }

    /**
     * The earlier pending exception is restored after the second callback.
     */
    @Test
    public void testFirstExceptionPreserved(@Inject(NativeJustCall2.class) CallTarget target) {
        RuntimeException first = new MyException();
        TestCallback fail = new TestCallback(0, args -> {
            throw first;
        });
        Assert.assertSame(first, Assert.assertThrows(MyException.class, () -> target.call(fail, SuccessException.THROW)));
        target.call(NOOP, NOOP);
    }

    @Test
    public void testPanamaOuterExceptionBarrier() throws Throwable {
        Assume.assumeTrue("Requires the Panama backend", "panama".equals(TEST_BACKEND));
        Class<?> languageClass = Class.forName("com.oracle.truffle.nfi.backend.panama.PanamaNFILanguage");
        Object language = ReflectionUtils.invokeStatic(languageClass, "get", new Class<?>[]{Node.class}, new Object[]{null});
        NFIState state = (NFIState) ReflectionUtils.invoke(language, "getNFIState");
        Class<?> closureClass = Class.forName("com.oracle.truffle.nfi.backend.panama.PanamaClosure");
        RuntimeException first = new MyException();
        RuntimeException second = new SuccessException();
        MethodHandle failing = MethodHandles.throwException(int.class, RuntimeException.class).bindTo(second);
        MethodHandle protectedCall = (MethodHandle) ReflectionUtils.invokeStatic(closureClass, "protectUpcall",
                        new Class<?>[]{MethodHandle.class, languageClass}, failing, language);
        try {
            state.setPendingException(first);
            Assert.assertEquals(0, (int) protectedCall.invokeExact());
            Assert.assertSame(first, state.getPendingException());
            state.clearPendingException();
            Assert.assertEquals(0, (int) protectedCall.invokeExact());
            Assert.assertSame(second, state.getPendingException());
        } finally {
            state.clearPendingException();
        }
    }

    @Test
    public void testSuccessfulCallbackAfterException() {
        CallTarget target = new SendExecuteNode("native_call_after_exception", "(():void, ():sint32, (sint32):void):void").getCallTarget();
        CallTarget nested = new SendExecuteNode("native_just_call", "(():void):void").getCallTarget();
        int[] observed = {-1};
        TestCallback success = new TestCallback(0, args -> {
            nested.call(NOOP);
            return 42;
        });
        TestCallback observe = new TestCallback(1, args -> {
            observed[0] = (int) args[0];
            return 0;
        });
        Assert.assertThrows(MyException.class, () -> target.call(MyException.THROW, success, observe));
        Assert.assertEquals(42, observed[0]);
    }

    @Test
    public void testNestedArgumentFailureAfterException() {
        CallTarget target = new SendExecuteNode("native_call_after_exception", "(():void, ():sint32, (sint32):void):void").getCallTarget();
        CallTarget nested = new SendExecuteNode("native_just_call", "(():void):void").getCallTarget();
        RuntimeException first = new MyException();
        TestCallback fail = new TestCallback(0, args -> {
            throw first;
        });
        AssertionError[] argumentFailure = {null};
        TestCallback success = new TestCallback(0, args -> {
            argumentFailure[0] = Assert.assertThrows(AssertionError.class, () -> nested.call());
            return 42;
        });
        int[] observed = {-1};
        TestCallback observe = new TestCallback(1, args -> {
            observed[0] = (int) args[0];
            return 0;
        });
        Assert.assertSame(first, Assert.assertThrows(MyException.class, () -> target.call(fail, success, observe)));
        Assert.assertNotNull("The successful callback must reach the nested call", argumentFailure[0]);
        Assert.assertTrue("The nested call must fail on its own arguments", argumentFailure[0].getCause() instanceof ArityException);
        Assert.assertEquals(42, observed[0]);
    }

    @Test
    public void testObjectCallbackAfterException() {
        Assume.assumeFalse("Panama does not support object handles", "panama".equals(TEST_BACKEND));
        for (String type : new String[]{"OBJECT", "NULLABLE"}) {
            CallTarget target = new SendExecuteNode("native_object_after_exception", String.format("(env, ():void, ():%s, %s, (sint32):void):void", type, type)).getCallTarget();
            Object value = new TestCallback(0, args -> 0);
            TestCallback success = new TestCallback(0, args -> value);
            int[] observed = {-1};
            TestCallback observe = new TestCallback(1, args -> {
                observed[0] = (int) args[0];
                return 0;
            });
            Assert.assertThrows(MyException.class, () -> target.call(MyException.THROW, success, value, observe));
            Assert.assertEquals(type, 1, observed[0]);
        }
    }

    @Test
    public void testStringCallbackAfterException() {
        Assume.assumeFalse("Panama string callbacks use different ownership", "panama".equals(TEST_BACKEND));
        CallTarget target = new SendExecuteNode("native_string_after_exception", "(():void, ():string, (sint32):void):void").getCallTarget();
        int[] observed = {-1};
        TestCallback success = new TestCallback(0, args -> "callback result");
        TestCallback observe = new TestCallback(1, args -> {
            observed[0] = (int) args[0];
            return 0;
        });
        Assert.assertThrows(MyException.class, () -> target.call(MyException.THROW, success, observe));
        Assert.assertEquals(1, observed[0]);
    }

    @Test
    public void testNativeExceptionFlagRestoredAfterCallback() {
        Assume.assumeFalse("Panama does not support environment pointers", "panama".equals(TEST_BACKEND));
        CallTarget target = new SendExecuteNode("native_exception_flag_after_callback", "(env, ():void, ():void, (sint32, sint32):void):void").getCallTarget();
        CallTarget check = new SendExecuteNode("native_exception_check", "(env):sint32").getCallTarget();
        int[] observed = {-1, -1, -1};
        TestCallback success = new TestCallback(0, args -> {
            observed[0] = (int) check.call();
            return 0;
        });
        TestCallback observe = new TestCallback(2, args -> {
            observed[1] = (int) args[0];
            observed[2] = (int) args[1];
            return 0;
        });
        Assert.assertThrows(MyException.class, () -> target.call(MyException.THROW, success, observe));
        Assert.assertArrayEquals(new int[]{0, 1, 1}, observed);
        Assert.assertEquals(0, check.call());
    }

    @Test
    public void testStackOverflowAfterPendingException() {
        assumeStackOverflowSupported();
        CallTarget target = new SendExecuteNode("native_call_after_exception", "(():void, ():sint32, (sint32):void):void").getCallTarget();
        CallTarget recursive = new SendExecuteNode("call_closure_noarg_SINT32", "(():sint32):sint32").getCallTarget();
        boolean[] overflow = {false};
        Object[] callback = new Object[1];
        callback[0] = new TestCallback(0, args -> {
            try {
                return recursive.call(callback[0]);
            } catch (StackOverflowError error) {
                overflow[0] = true;
                throw error;
            }
        });
        int[] observed = {-1};
        TestCallback observe = new TestCallback(1, args -> {
            observed[0] = (int) args[0];
            return 0;
        });
        Assert.assertThrows(MyException.class, () -> target.call(MyException.THROW, callback[0], observe));
        Assert.assertTrue("The recursive callback must reach stack overflow", overflow[0]);
        Assert.assertEquals(0, observed[0]);
    }

    /**
     * First call throws, so second call is skipped.
     */
    @Test(expected = MyException.class)
    public void testCheckException(@Inject(NativeCheckException.class) CallTarget target) {
        /*
         * The exceptionCheck API was introduced in 24.0.0.
         */
        Assume.assumeTrue(Version.getCurrent().compareTo(24, 0) >= 0);
        target.call(MyException.THROW, SuccessException.THROW);
    }

    /**
     * First call doesn't throw, so we catch the exception from the second.
     */
    @Test(expected = SuccessException.class)
    public void testCheckNoException(@Inject(NativeCheckException.class) CallTarget target) {
        /*
         * The exceptionCheck API was introduced in 24.0.0.
         */
        Assume.assumeTrue(Version.getCurrent().compareTo(24, 0) >= 0);
        target.call(NOOP, SuccessException.THROW);
    }
}
