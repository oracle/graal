/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

public class TStringOpsCalcStringAttributesReturnValuesInSyncTest {

    @Test
    public void testBMP() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> c = Class.forName("com.oracle.truffle.api.strings.TSCodeRange");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_7BIT, "CR_7BIT");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_8BIT, "CR_8BIT");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_16BIT, "CR_16BIT");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_VALID, "CR_VALID");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_BROKEN, "CR_BROKEN");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_VALID_MULTIBYTE, "CR_VALID_MULTIBYTE");
        checkField(c, LIRGeneratorTool.CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE, "CR_BROKEN_MULTIBYTE");
    }

    private static void checkField(Class<?> tsCodeRangeClass, int value, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = tsCodeRangeClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        Assert.assertEquals(value, field.getInt(null));
    }
}
