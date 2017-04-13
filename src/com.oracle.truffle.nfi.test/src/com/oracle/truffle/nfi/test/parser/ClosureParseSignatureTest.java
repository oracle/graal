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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.nfi.types.NativeFunctionTypeMirror;
import com.oracle.truffle.nfi.types.NativeSignature;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.nfi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror.Kind;
import com.oracle.truffle.nfi.types.Parser;
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
        NativeSignature closureArgSig = Parser.parseSignature(String.format("(%s):void", closureSig));
        Assert.assertThat("return type", closureArgSig.getRetType(), isSimpleType(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 1, closureArgSig.getArgTypes().size());
        checkClosureType(closureArgSig.getArgTypes().get(0), validator);

        NativeSignature closureRetSig = Parser.parseSignature(String.format("() : %s", closureSig));
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
