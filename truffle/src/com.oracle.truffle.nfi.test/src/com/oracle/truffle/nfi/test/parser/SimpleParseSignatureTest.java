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

import com.oracle.truffle.nfi.types.NativeSignature;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.nfi.types.Parser;
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
        return Parser.parseSignature(String.format(format, type, type));
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
