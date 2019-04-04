/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.nfi.spi.types.NativeFunctionTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeSignature;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror.Kind;
import org.junit.Assert;
import org.junit.Test;

public class ClosureParseSignatureTest extends ParseSignatureTest {

    private interface Validator {

        void validateSignature(NativeSignature signature);
    }

    private static void checkClosureType(NativeTypeMirror type, Validator validator) {
        Assert.assertEquals(Kind.FUNCTION, type.getKind());
        validator.validateSignature(((NativeFunctionTypeMirror) type).getSignature());
    }

    private static void testWithClosure(String closureSig, Validator validator) {
        NativeSignature closureArgSig = parseSignature(String.format("(%s):void", closureSig));
        Assert.assertThat("return type", closureArgSig.getRetType(), isSimpleType(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 1, closureArgSig.getArgTypes().size());
        checkClosureType(closureArgSig.getArgTypes().get(0), validator);

        NativeSignature closureRetSig = parseSignature(String.format("() : %s", closureSig));
        Assert.assertEquals("argument count", 0, closureRetSig.getArgTypes().size());
        checkClosureType(closureRetSig.getRetType(), validator);
    }

    @Test
    public void testClosureNoArgs() {
        testWithClosure("():void", sig -> {
            Assert.assertThat("return type", sig.getRetType(), isSimpleType(NativeSimpleType.VOID));
            Assert.assertEquals("argument count", 0, sig.getArgTypes().size());
        });
    }

    @Test
    public void testClosureOneArg() {
        testWithClosure("(float):double", sig -> {
            Assert.assertThat("return type", sig.getRetType(), isSimpleType(NativeSimpleType.DOUBLE));
            Assert.assertEquals("argument count", 1, sig.getArgTypes().size());
            Assert.assertThat("argument type", sig.getArgTypes().get(0), isSimpleType(NativeSimpleType.FLOAT));
        });
    }

    @Test
    public void testClosureVarargs() {
        testWithClosure("(string, ...sint32):double", sig -> {
            Assert.assertThat("return type", sig.getRetType(), isSimpleType(NativeSimpleType.DOUBLE));
            Assert.assertEquals("argument count", 2, sig.getArgTypes().size());
            Assert.assertEquals("fixed argument count", 1, sig.getFixedArgCount());
            Assert.assertTrue(sig.isVarargs());
        });
    }

    @Test
    public void testNestedClosure() {
        Validator inner = sig -> {
            Assert.assertThat("return type", sig.getRetType(), isSimpleType(NativeSimpleType.VOID));
            Assert.assertEquals("argument count", 0, sig.getArgTypes().size());
        };
        testWithClosure("(():void) : ():void", sig -> {
            Assert.assertEquals("argument count", 1, sig.getArgTypes().size());
            checkClosureType(sig.getRetType(), inner);
            checkClosureType(sig.getArgTypes().get(0), inner);
        });
    }
}
