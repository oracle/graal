/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.parser.backend.TestSignature;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import static org.hamcrest.CoreMatchers.is;
import org.hamcrest.core.Every;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
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

    public class ParseNoArgs extends ParseSignatureNode {

        public ParseNoArgs() {
            super("():%s", type);
        }
    }

    @Test
    public void testNoArgs(@Inject(ParseNoArgs.class) CallTarget parse) {
        TestSignature signature = getSignature(parse, 0);
        Assert.assertEquals("return type", signature.retType, type);
        Assert.assertEquals("argument count", 0, signature.argTypes.size());
    }

    public class ParseOneArg extends ParseSignatureNode {

        public ParseOneArg() {
            super("(%s):void", type);
        }
    }

    @Test
    public void testOneArg(@Inject(ParseOneArg.class) CallTarget parse) {
        TestSignature signature = getSignature(parse, 1);
        Assert.assertEquals("return type", signature.retType, NativeSimpleType.VOID);
        Assert.assertEquals("argument count", 1, signature.argTypes.size());
        Assert.assertThat("argument types", signature.argTypes, Every.everyItem(is(type)));
    }

    public class ParseTwoArgs extends ParseSignatureNode {

        public ParseTwoArgs() {
            super("(%s, %s):void", type, type);
        }
    }

    @Test
    public void testTwoArgs(@Inject(ParseTwoArgs.class) CallTarget parse) {
        TestSignature signature = getSignature(parse, 2);
        Assert.assertEquals("return type", signature.retType, NativeSimpleType.VOID);
        Assert.assertEquals("argument count", 2, signature.argTypes.size());
        Assert.assertThat("argument types", signature.argTypes, Every.everyItem(is(type)));
    }

    public class ParseArrayArg extends ParseSignatureNode {

        public ParseArrayArg() {
            super("([%s]):void", type);
        }
    }

    @Test
    public void testArrayArg(@Inject(ParseArrayArg.class) CallTarget parse) {
        TestSignature signature = getSignature(parse, 1);
        Assert.assertThat("return type", signature.retType, is(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 1, signature.argTypes.size());
        Assert.assertThat("argument types", signature.argTypes, Every.everyItem(isArrayType(type)));
    }

    public class ParseArrayRet extends ParseSignatureNode {

        public ParseArrayRet() {
            super("():[%s]", type);
        }
    }

    @Test
    public void testArrayRet(@Inject(ParseArrayRet.class) CallTarget parse) {
        TestSignature signature = getSignature(parse, 0);
        Assert.assertEquals("argument count", 0, signature.argTypes.size());
        Assert.assertThat("return type", signature.retType, isArrayType(type));
    }
}
