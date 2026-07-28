/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HotSpotFieldLocationIdentity.KLASS_ACCESS_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HotSpotFieldLocationIdentity.KLASS_MISC_FLAGS_LOCATION;

import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.replacements.test.MethodSubstitutionTest;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.reflect.Reflection;
import jdk.vm.ci.meta.JavaKind;

/**
 * As of JDK 15 {@code java.lang.Class::isHidden()} was added.
 *
 * @see "https://openjdk.java.net/jeps/371"
 * @see "https://bugs.openjdk.java.net/browse/JDK-8238358"
 */
@AddExports("java.base/jdk.internal.reflect")
public class ClassReplacementsTest extends MethodSubstitutionTest {

    @SuppressWarnings("all")
    public static boolean isHidden(Class<?> clazz) {
        return clazz.isHidden();
    }

    public static int getModifiers(Class<?> clazz) {
        return Reflection.getClassAccessFlags(clazz);
    }

    @Test
    public void testIsHidden() {
        StructuredGraph graph = parseEager("isHidden", StructuredGraph.AllowAssumptions.YES);

        ReadNode read = findRead(graph, KLASS_MISC_FLAGS_LOCATION);
        Assert.assertNotNull(read);
        Assert.assertEquals(Byte.SIZE, read.getAccessBits());
        IntegerStamp accessStamp = (IntegerStamp) read.getAccessStamp(NodeView.DEFAULT);
        Assert.assertEquals(Byte.SIZE, accessStamp.getBits());
        Assert.assertEquals(JavaKind.Int, accessStamp.getStackKind());
        Assert.assertEquals(0, accessStamp.unsignedLowerBound());
        Assert.assertEquals(0xff, accessStamp.unsignedUpperBound());

        Supplier<Runnable> lambda = () -> () -> System.out.println("run");

        for (Class<?> c : new Class<?>[]{getClass(), Cloneable.class, int[].class, String[][].class, lambda.getClass(), lambda.get().getClass()}) {
            test("isHidden", c);
        }
    }

    @Test
    public void testGetClassAccessFlagsRead() {
        StructuredGraph graph = parseEager("getModifiers", StructuredGraph.AllowAssumptions.YES);
        ReadNode read = findRead(graph, KLASS_ACCESS_FLAGS_LOCATION);
        Assert.assertNotNull(read);
        Assert.assertEquals(Short.SIZE, read.getAccessBits());
        IntegerStamp accessStamp = (IntegerStamp) read.getAccessStamp(NodeView.DEFAULT);
        Assert.assertEquals(Short.SIZE, accessStamp.getBits());
        Assert.assertEquals(JavaKind.Int, accessStamp.getStackKind());
        Assert.assertEquals(0, accessStamp.unsignedLowerBound());
        Assert.assertEquals(0xffff, accessStamp.unsignedUpperBound());

        test("getModifiers", getClass());
    }

    private static ReadNode findRead(StructuredGraph graph, HotSpotReplacementsUtil.HotSpotFieldLocationIdentity location) {
        for (ReadNode read : graph.getNodes().filter(ReadNode.class)) {
            if (read.getLocationIdentity().equals(location)) {
                return read;
            }
        }
        return null;
    }
}
