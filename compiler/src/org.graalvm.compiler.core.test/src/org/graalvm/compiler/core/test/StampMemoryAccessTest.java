/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.lang.reflect.Field;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;

/**
 * Tests {@link MemoryAccessProvider#readObjectConstant} and
 * {@link MemoryAccessProvider#readPrimitiveConstant} by way of {@link Stamp#readConstant}.
 */
public class StampMemoryAccessTest extends GraalCompilerTest {

    long longField1 = Double.doubleToRawLongBits(Double.MAX_VALUE);
    long longField2 = Double.doubleToRawLongBits(Double.MIN_VALUE);

    @Test
    public void testReadPrimitiveOutOfBounds() {
        MemoryAccessProvider memory = getConstantReflection().getMemoryAccessProvider();
        Stamp stamp = StampFactory.forKind(JavaKind.Long);
        JavaConstant objectBase = getSnippetReflection().forObject("");
        assertTrue(stamp.readConstant(memory, objectBase, 128) == null);
        JavaConstant arrayBase = getSnippetReflection().forObject(new int[]{});
        assertTrue(stamp.readConstant(memory, arrayBase, 128) == null);
    }

    @Test
    public void testReadObjectOutOfBounds() {
        MemoryAccessProvider memory = getConstantReflection().getMemoryAccessProvider();
        Stamp stamp = StampFactory.forKind(JavaKind.Object);
        JavaConstant objectBase = getSnippetReflection().forObject("");
        assertTrue(stamp.readConstant(memory, objectBase, 128) == null);
        JavaConstant arrayBase = getSnippetReflection().forObject(new int[]{});
        assertTrue(stamp.readConstant(memory, arrayBase, 128) == null);
    }

    /**
     * Checks that unaligned reads are not constant folded.
     */
    @Test
    public void testReadPrimitiveUnaligned() throws Exception {
        MemoryAccessProvider memory = getConstantReflection().getMemoryAccessProvider();
        Object object = this;
        JavaConstant objectBase = getSnippetReflection().forObject(object);
        Field f = getClass().getDeclaredField("longField1");
        long baseDisplacement = UNSAFE.objectFieldOffset(f);
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void && kind.getByteCount() > 1) {
                for (long offset = 1; offset < kind.getByteCount(); offset++) {
                    long displacement = baseDisplacement + offset;
                    Stamp stamp = StampFactory.forKind(kind);
                    Assert.assertEquals(null, stamp.readConstant(memory, objectBase, displacement));
                }
            }
        }
    }
}
