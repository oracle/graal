/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ForeignAccessMultiThreadedTest implements ForeignAccess.Factory, TruffleObject {
    ForeignAccess fa;
    private int cnt;

    @Before
    public void initInDifferentThread() throws InterruptedException {
        Thread t = new Thread("Initializer") {
            @Override
            public void run() {
                fa = ForeignAccess.create(ForeignAccessMultiThreadedTest.this);
            }
        };
        t.start();
        t.join();
    }

    @Test
    public void accessNodeFromOtherThread() {
        Node n = Message.IS_EXECUTABLE.createNode();
        ForeignAccess.sendIsExecutable(n, this);
        // access from different thread allowed.
        assertEquals(2, cnt);
    }

    @Override
    public boolean canHandle(TruffleObject obj) {
        cnt++;
        return true;
    }

    @Override
    public CallTarget accessMessage(Message tree) {
        cnt++;
        if (tree == Message.IS_EXECUTABLE) {
            return Truffle.getRuntime().createCallTarget(new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return Boolean.FALSE;
                }
            });
        }
        return null;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return fa;
    }
}
