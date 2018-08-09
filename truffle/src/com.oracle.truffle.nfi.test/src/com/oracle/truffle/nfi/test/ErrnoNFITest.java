/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.io.File;
import java.io.FileReader;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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
        @Child private Node executeSetErrno = Message.EXECUTE.createNode();

        private final TruffleObject getErrno = lookupAndBind("getErrno", "():sint32");
        @Child private Node executeGetErrno = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            ForeignAccess.sendExecute(executeSetErrno, setErrno, frame.getArguments()[0]);
            destroyErrno();
            return ForeignAccess.sendExecute(executeGetErrno, getErrno);
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

    @Test
    public void testErrnoCallback(@Inject(TestErrnoCallback.class) CallTarget target) {
        TestCallback callback = new TestCallback(0, (args) -> {
            destroyErrno();
            return null;
        });
        Object ret = target.call(42, callback);
        Assert.assertEquals(42, ret);
    }
}
