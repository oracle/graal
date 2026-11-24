/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.util.ConstantReflectionUtil;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Tests reading string contents through constant reflection rather than accessing the original
 * string object in the compiler's runtime.
 */
public class ConstantReflectionUtilTest extends GraalCompilerTest {

    /**
     * Covers Latin-1, UTF-16, and surrogate code units, including unpaired surrogates that must not
     * be replaced while reading the string contents.
     */
    @Test
    public void testReadString() {
        String[] strings = {"", "__jsvml_expf8_ha_l9", "\u0000\u007f\u0080\u00ff", "\u0100\u20ac\uffff", "\ud83d\ude00", "\ud800", "\udc00"};
        for (String string : strings) {
            JavaConstant constant = getConstantReflection().forString(string);
            Assert.assertEquals(string, ConstantReflectionUtil.readString(getMetaAccess(), getConstantReflection(), constant));
        }
    }

    @Test
    public void testReadStringInvalidConstant() {
        Assert.assertNull(ConstantReflectionUtil.readString(getMetaAccess(), getConstantReflection(), null));
        Assert.assertNull(ConstantReflectionUtil.readString(getMetaAccess(), getConstantReflection(), JavaConstant.NULL_POINTER));
        Assert.assertNull(ConstantReflectionUtil.readString(getMetaAccess(), getConstantReflection(), JavaConstant.INT_0));
        Assert.assertNull(ConstantReflectionUtil.readString(getMetaAccess(), getConstantReflection(), getSnippetReflection().forObject(new Object())));
    }
}
