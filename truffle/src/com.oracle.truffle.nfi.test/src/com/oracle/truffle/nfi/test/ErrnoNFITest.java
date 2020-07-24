/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileReader;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class ErrnoNFITest extends NFITest {

    @TruffleBoundary
    private static void destroyErrno() {
        // do something that's very likely to make the JVM override errno
        try (FileReader r = new FileReader(new File("/file/that/does/not/exist"))) {
            r.read();
        } catch (Exception ex) {
            // ignore
        }
    }

    public static class TestVirtualErrno extends NFITestRootNode {

        private final TruffleObject setErrno = lookupAndBind("setErrno", "(sint32):void");
        @Child InteropLibrary setErrnoInterop = getInterop(setErrno);

        private final TruffleObject getErrno = lookupAndBind("getErrno", "():sint32");
        @Child InteropLibrary getErrnoInterop = getInterop(getErrno);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            setErrnoInterop.execute(setErrno, frame.getArguments()[0]);
            destroyErrno();
            return getErrnoInterop.execute(getErrno);
        }
    }

    @Test
    public void testVirtualErrno(@Inject(TestVirtualErrno.class) CallTarget target) {
        Object ret = target.call(8472);
        Assert.assertEquals(8472, ret);
    }

    public static class TestErrnoCallback extends SendExecuteNode {

        public TestErrnoCallback() {
            super("errnoCallback", "(sint32, ():void):sint32");
        }
    }

    private static final TestCallback callback = new TestCallback(0, (args) -> {
        destroyErrno();
        return null;
    });

    @Test
    public void testErrnoCallback(@Inject(TestErrnoCallback.class) CallTarget target) {
        Object ret = target.call(42, callback);
        Assert.assertEquals(42, ret);
    }
}
