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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;

public class LateBindNFITest extends NFITest {

    private static class BindAndExecuteNode extends TestRootNode {

        private final String signature;

        @Child Node bind = Message.createInvoke(1).createNode();
        @Child Node execute = Message.createExecute(1).createNode();

        BindAndExecuteNode(String signature) {
            this.signature = signature;
        }

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            TruffleObject symbol = (TruffleObject) frame.getArguments()[0];
            TruffleObject bound = (TruffleObject) ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
            return ForeignAccess.sendExecute(execute, bound, frame.getArguments()[1]);
        }
    }

    @Test
    public void testLateBind() {
        TruffleObject increment;
        try {
            increment = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "increment_SINT32");
        } catch (InteropException e) {
            throw new AssertionError(e);
        }

        Object ret = run(new BindAndExecuteNode("(sint32):sint32"), increment, 41);

        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }
}
