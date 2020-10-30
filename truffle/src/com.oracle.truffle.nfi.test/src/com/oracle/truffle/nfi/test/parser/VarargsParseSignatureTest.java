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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.nfi.test.parser.backend.TestSignature;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class VarargsParseSignatureTest extends ParseSignatureTest {

    private static void testVarargs(CallTarget parse, int expectedArgCount, int expectedFixedArgCount) {
        TestSignature signature = getSignature(parse, expectedArgCount);
        Assert.assertEquals("argument count", expectedArgCount, signature.argTypes.size());
        if (expectedArgCount == expectedFixedArgCount) {
            Assert.assertTrue("not varargs", signature.fixedArgCount == TestSignature.NOT_VARARGS);
        } else {
            Assert.assertEquals("fixed argument count", expectedFixedArgCount, signature.fixedArgCount);
        }
    }

    public class ParseFixedArgs extends ParseSignatureNode {

        public ParseFixedArgs() {
            super("(float, double) : void");
        }
    }

    @Test
    public void testFixedArgs(@Inject(ParseFixedArgs.class) CallTarget parse) {
        testVarargs(parse, 2, 2);
    }

    public class ParseNoFixedArgs extends ParseSignatureNode {

        public ParseNoFixedArgs() {
            super("(...float, double) : void");
        }
    }

    @Test
    public void testNoFixedArgs(@Inject(ParseNoFixedArgs.class) CallTarget parse) {
        testVarargs(parse, 2, 0);
    }

    public class ParseTwoFixedArgs extends ParseSignatureNode {

        public ParseTwoFixedArgs() {
            super("(object, pointer, ...float, double) : void");
        }
    }

    @Test
    public void testTwoFixedArgs(@Inject(ParseTwoFixedArgs.class) CallTarget parse) {
        testVarargs(parse, 4, 2);
    }

    public class ParseOneVararg extends ParseSignatureNode {

        public ParseOneVararg() {
            super("(string, ...sint32) : void");
        }
    }

    @Test
    public void testOneVararg(@Inject(ParseOneVararg.class) CallTarget parse) {
        testVarargs(parse, 2, 1);
    }

    public class ParseTwoVarargs extends ParseSignatureNode {

        public ParseTwoVarargs() {
            super("(string, ...object, uint32) : void");
        }
    }

    @Test
    public void testTwoVarargs(@Inject(ParseTwoVarargs.class) CallTarget parse) {
        testVarargs(parse, 3, 1);
    }
}
