/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public class ForeignAccessSingleThreadedTest implements ForeignAccess.Factory, TruffleObject {
    ForeignAccess fa;
    private int cnt;

    @Before
    public void initInDifferentThread() throws InterruptedException {
        Thread t = new Thread("Initializer") {
            @Override
            public void run() {
                fa = ForeignAccess.create(ForeignAccessSingleThreadedTest.this);
            }
        };
        t.start();
        t.join();
    }

    @Test(expected = AssertionError.class)
    public void accessNodeFromWrongThread() {
        Node n = Message.IS_EXECUTABLE.createNode();
        Object ret = ForeignAccess.sendIsExecutable(n, this);
        fail("Should throw an exception: " + ret);
    }

    @After
    public void noCallsToFactory() {
        assertEquals("No calls to accessMessage or canHandle", 0, cnt);
    }

    @Override
    public boolean canHandle(TruffleObject obj) {
        cnt++;
        return true;
    }

    @Override
    public CallTarget accessMessage(Message tree) {
        cnt++;
        return null;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return fa;
    }
}
