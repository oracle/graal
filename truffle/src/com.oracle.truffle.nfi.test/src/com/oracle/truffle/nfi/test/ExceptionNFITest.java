/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.home.Version;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.nfi.test.interop.TestCallback;
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

    /**
     * Last exception wins.
     */
    @Test(expected = SuccessException.class)
    public void testExceptionOverride(@Inject(NativeJustCall2.class) CallTarget target) {
        target.call(MyException.THROW, SuccessException.THROW);
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
