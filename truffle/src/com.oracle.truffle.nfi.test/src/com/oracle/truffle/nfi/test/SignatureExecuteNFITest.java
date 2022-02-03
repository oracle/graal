/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.test.SignatureExecuteNFITestFactory.DoDirectExecuteNodeGen;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class SignatureExecuteNFITest extends NFITest {

    abstract static class DoDirectExecute extends Node {

        abstract Object execute(Object symbol, Object signature, Object arg);

        @Specialization(limit = "3")
        Object doBindAndExecute(Object symbol, Object signature, Object arg,
                        @CachedLibrary("signature") SignatureLibrary signatures) {
            try {
                return signatures.call(signature, symbol, arg);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(ex);
            }
        }
    }

    public static class DirectExecuteNode extends NFITestRootNode {

        @Child DoDirectExecute directExecute = DoDirectExecuteNodeGen.create();

        @Override
        public final Object executeTest(VirtualFrame frame) throws InteropException {
            return directExecute.execute(frame.getArguments()[0], frame.getArguments()[1], frame.getArguments()[2]);
        }
    }

    @Test
    public void testDirectExecute(@Inject(DirectExecuteNode.class) CallTarget callTarget) {
        Object increment;
        try {
            increment = UNCACHED_INTEROP.readMember(testLibrary, "increment_SINT32");
        } catch (InteropException e) {
            throw new AssertionError(e);
        }

        Source sigSource = Source.newBuilder("nfi", "(sint32):sint32", "signature").internal(true).build();
        CallTarget sigTarget = runWithPolyglot.getTruffleTestEnv().parseInternal(sigSource);
        Object signature = sigTarget.call();

        Object ret = callTarget.call(increment, signature, 41);

        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }
}
