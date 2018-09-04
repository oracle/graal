/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class LateBindNFITest extends NFITest {

    public static class BindAndExecuteNode extends NFITestRootNode {

        @Child Node bind = Message.INVOKE.createNode();
        @Child Node execute = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            TruffleObject symbol = (TruffleObject) frame.getArguments()[0];
            Object signature = frame.getArguments()[1];
            TruffleObject bound = (TruffleObject) ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
            return ForeignAccess.sendExecute(execute, bound, frame.getArguments()[2]);
        }
    }

    private static void testLateBind(CallTarget callTarget, Object symbol, Object signature) {
        TruffleObject increment;
        try {
            increment = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, symbol);
        } catch (InteropException e) {
            throw new AssertionError(e);
        }

        Object ret = callTarget.call(increment, signature, 41);

        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    @Test
    public void testLateBind(@Inject(BindAndExecuteNode.class) CallTarget callTarget) {
        testLateBind(callTarget, "increment_SINT32", "(sint32):sint32");
    }

    @Test
    public void testLateBindBoxed(@Inject(BindAndExecuteNode.class) CallTarget callTarget) {
        testLateBind(callTarget, new BoxedPrimitive("increment_SINT32"), new BoxedPrimitive("(sint32):sint32"));
    }
}
