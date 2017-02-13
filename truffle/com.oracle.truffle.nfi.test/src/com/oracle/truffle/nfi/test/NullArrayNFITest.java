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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import java.util.ArrayList;
import java.util.Collection;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NullArrayNFITest extends NFITest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        ret.add(new Object[]{NativeSimpleType.UINT8});
        ret.add(new Object[]{NativeSimpleType.SINT8});
        ret.add(new Object[]{NativeSimpleType.UINT16});
        ret.add(new Object[]{NativeSimpleType.SINT16});
        ret.add(new Object[]{NativeSimpleType.UINT32});
        ret.add(new Object[]{NativeSimpleType.SINT32});
        ret.add(new Object[]{NativeSimpleType.UINT64});
        ret.add(new Object[]{NativeSimpleType.SINT64});
        ret.add(new Object[]{NativeSimpleType.FLOAT});
        ret.add(new Object[]{NativeSimpleType.DOUBLE});
        return ret;
    }

    @Parameter public NativeSimpleType nativeType;

    @Test
    public void testNullArray() {
        TruffleObject function = lookupAndBind("null_array_" + nativeType, String.format("([%s]):string", nativeType));
        Object ret = sendExecute(function, JavaInterop.asTruffleObject(null));
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isBoxed", JavaInterop.isBoxed(obj));
        Assert.assertEquals("return value", "null", JavaInterop.unbox(obj));
    }
}
