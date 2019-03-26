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

import com.oracle.truffle.nfi.spi.types.NativeSignature;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import java.util.ArrayList;
import java.util.Collection;
import org.hamcrest.core.Every;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleParseSignatureTest extends ParseSignatureTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (NativeSimpleType type : NativeSimpleType.values()) {
            ret.add(new Object[]{type});
        }
        return ret;
    }

    @Parameter public NativeSimpleType type;

    protected NativeSignature parse(String format) {
        return parseSignature(String.format(format, type, type));
    }

    @Test
    public void testNoArgs() {
        NativeSignature signature = parse("():%s");
        Assert.assertThat("return type", signature.getRetType(), isSimpleType(type));
        Assert.assertEquals("argument count", 0, signature.getArgTypes().size());
    }

    @Test
    public void testOneArg() {
        NativeSignature signature = parse("(%s):void");
        Assert.assertThat("return type", signature.getRetType(), isSimpleType(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 1, signature.getArgTypes().size());
        Assert.assertThat("argument types", signature.getArgTypes(), Every.everyItem(isSimpleType(type)));
    }

    @Test
    public void testTwoArgs() {
        NativeSignature signature = parse("(%s,%s):void");
        Assert.assertThat("return type", signature.getRetType(), isSimpleType(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 2, signature.getArgTypes().size());
        Assert.assertThat("argument types", signature.getArgTypes(), Every.everyItem(isSimpleType(type)));
    }

    @Test
    public void testArrayArg() {
        NativeSignature signature = parse("([%s]):void");
        Assert.assertThat("return type", signature.getRetType(), isSimpleType(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 1, signature.getArgTypes().size());
        Assert.assertThat("argument types", signature.getArgTypes(), Every.everyItem(isArrayType(isSimpleType(type))));
    }

    @Test
    public void testArrayRet() {
        NativeSignature signature = parse("():[%s]");
        Assert.assertEquals("argument count", 0, signature.getArgTypes().size());
        Assert.assertThat("return type", signature.getRetType(), isArrayType(isSimpleType(type)));
    }
}
