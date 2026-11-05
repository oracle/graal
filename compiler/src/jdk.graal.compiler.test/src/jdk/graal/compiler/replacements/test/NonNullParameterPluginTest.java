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
package jdk.graal.compiler.replacements.test;

import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.replacements.NonNullParameterPlugin;
import jdk.vm.ci.meta.JavaKind;

public class NonNullParameterPluginTest extends GraalCompilerTest {
    private static NonNullParameterPlugin nonNullPlugin() {
        BitSet marked = new BitSet();
        marked.set(0);
        return new NonNullParameterPlugin(marked);
    }

    @Test
    public void nullableParameterIsStrengthened() {
        ParameterNode result = (ParameterNode) nonNullPlugin().interceptParameter(null, 0, StampPair.createSingle(StampFactory.object()));
        Assert.assertEquals(0, result.index());
        Assert.assertEquals(StampFactory.objectNonNull(), result.stamp(NodeView.DEFAULT));
        Assert.assertNull(result.uncheckedStamp());
    }

    @Test
    public void alreadyNonNullParameterNeedsNoReplacement() {
        Assert.assertNull(nonNullPlugin().interceptParameter(null, 0, StampPair.createSingle(StampFactory.objectNonNull())));
        Stamp typedNonNull = StampFactory.objectNonNull(TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(String.class)));
        Assert.assertNull(nonNullPlugin().interceptParameter(null, 0, StampPair.createSingle(typedNonNull)));
    }

    @Test
    public void trustedAndUncheckedTypesArePreserved() {
        TypeReference type = TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(String.class));
        Stamp typed = StampFactory.object(type);
        ParameterNode result = (ParameterNode) nonNullPlugin().interceptParameter(null, 0, StampPair.create(typed, typed));
        Assert.assertEquals(StampFactory.objectNonNull(type), result.stamp(NodeView.DEFAULT));
        Assert.assertEquals(StampFactory.objectNonNull(type), result.uncheckedStamp());
    }

    @Test
    public void unmarkedParametersAreUnchanged() {
        Assert.assertNull(nonNullPlugin().interceptParameter(null, 1, StampPair.createSingle(StampFactory.object())));
        Assert.assertNull(nonNullPlugin().interceptParameter(null, 1, StampPair.createSingle(StampFactory.forKind(JavaKind.Int))));
    }

    @Test
    public void primitiveParameterIsRejected() {
        GraalError error = Assert.assertThrows(GraalError.class,
                        () -> nonNullPlugin().interceptParameter(null, 0, StampPair.createSingle(StampFactory.forKind(JavaKind.Int))));
        Assert.assertTrue(error.getMessage().contains("Non-Object parameter annotated with @NonNullParameter?"));
    }
}
