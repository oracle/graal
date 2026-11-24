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

package jdk.graal.compiler.hotspot.test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.nodes.HotSpotCompressionNode;
import jdk.graal.compiler.hotspot.nodes.type.HotSpotNarrowOopStamp;
import jdk.graal.compiler.hotspot.nodes.type.KlassPointerStamp;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup.SpeculationContextObject.Visitor;
import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

public class HotSpotStampTest extends GraalCompilerTest {
    private static final CompressEncoding ENCODING = new CompressEncoding(0, 3);

    @Test
    public void bottomArrayType() {
        ObjectStamp ints = new ObjectStamp(getMetaAccess().lookupJavaType(int[].class), false, false, false, true);
        ObjectStamp bytes = new ObjectStamp(getMetaAccess().lookupJavaType(byte[].class), false, false, false, true);
        AbstractObjectStamp arrays = (AbstractObjectStamp) ints.meet(bytes);
        Assert.assertTrue(arrays.isBottomArrayType());
        Assert.assertTrue(HotSpotNarrowOopStamp.compressed(arrays, ENCODING).isBottomArrayType());
        Assert.assertFalse(HotSpotNarrowOopStamp.compressed(ints, ENCODING).isBottomArrayType());
        Assert.assertFalse(HotSpotNarrowOopStamp.compressed((ObjectStamp) StampFactory.object(), ENCODING).isBottomArrayType());
        Assert.assertFalse(StampFactory.forInteger(32).isBottomArrayType());
    }

    private static List<Object> visit(Stamp stamp) {
        List<Object> values = new ArrayList<>();
        Visitor visitor = (Visitor) Proxy.newProxyInstance(Visitor.class.getClassLoader(), new Class<?>[]{Visitor.class}, (proxy, method, arguments) -> {
            values.add(arguments[0]);
            return null;
        });
        stamp.accept(visitor);
        return values;
    }

    @Test
    public void klassVisitor() {
        KlassPointerStamp klass = KlassPointerStamp.klass();
        List<Object> uncompressed = visit(klass);
        List<Object> compressed = visit(klass.compressed(ENCODING));
        Assert.assertEquals(2, uncompressed.size());
        Assert.assertEquals(4, compressed.size());
        Assert.assertEquals(uncompressed, compressed.subList(0, 2));
        Assert.assertEquals(ENCODING.getBase(), compressed.get(2));
        Assert.assertEquals(ENCODING.getShift(), compressed.get(3));
        Assert.assertNotEquals(uncompressed, visit(KlassPointerStamp.klassNonNull()));
        Assert.assertNotEquals(compressed, visit(klass.compressed(new CompressEncoding(8, 3))));
        Assert.assertNotEquals(compressed, visit(klass.compressed(new CompressEncoding(0, 4))));
    }

    @Test
    public void narrowObjectConstants() {
        Stamp stamp = HotSpotNarrowOopStamp.compressed((ObjectStamp) StampFactory.object(), ENCODING);
        HotSpotConstant object = (HotSpotConstant) getSnippetReflection().forObject(new Object());
        Assert.assertTrue(stamp.isCompatible(object.compress()));
        Assert.assertTrue(stamp.isCompatible(HotSpotCompressedNullConstant.COMPRESSED_NULL));
        Assert.assertFalse(stamp.isCompatible(object));
        Assert.assertFalse(stamp.isCompatible(JavaConstant.NULL_POINTER));
        Assert.assertFalse(stamp.isCompatible(JavaConstant.forInt(1)));
        Assert.assertFalse(stamp.isCompatible(JavaConstant.forDouble(1)));
        Assert.assertFalse(stamp.isCompatible(klassConstant()));
        Assert.assertFalse(stamp.isCompatible(((HotSpotConstant) klassConstant()).compress()));
    }

    private Constant klassConstant() {
        return ((HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(String.class)).klass();
    }

    @Test
    public void compressibleConstants() {
        HotSpotCompressionNode node = new HotSpotCompressionNode(CompressionOp.Compress,
                        new ParameterNode(0, StampPair.createSingle(StampFactory.object())), ENCODING);
        HotSpotConstant object = (HotSpotConstant) getSnippetReflection().forObject(new Object());
        for (Constant constant : new Constant[]{JavaConstant.NULL_POINTER, object, klassConstant()}) {
            Assert.assertTrue(node.isCompressible(constant));
            Constant compressed = node.convert(constant, getConstantReflection());
            Assert.assertFalse(node.isCompressible(compressed));
            Assert.assertEquals(constant, node.reverse(compressed, getConstantReflection()));
        }
        Assert.assertFalse(node.isCompressible(JavaConstant.forInt(1)));
        Assert.assertFalse(node.isCompressible(JavaConstant.forDouble(1)));
    }

    @Test
    public void klassConstantRepresentation() {
        KlassPointerStamp klass = KlassPointerStamp.klass();
        KlassPointerStamp narrow = klass.compressed(ENCODING);
        Constant constant = klassConstant();
        Constant compressed = ((HotSpotConstant) constant).compress();
        Assert.assertTrue(klass.isCompatible(constant));
        Assert.assertTrue(narrow.isCompatible(compressed));
        Assert.assertFalse(klass.isCompatible(compressed));
        Assert.assertFalse(narrow.isCompatible(constant));
        Assert.assertTrue(klass.isCompatible(JavaConstant.NULL_POINTER));
        Assert.assertTrue(narrow.isCompatible(HotSpotCompressedNullConstant.COMPRESSED_NULL));
        Assert.assertFalse(klass.isCompatible(HotSpotCompressedNullConstant.COMPRESSED_NULL));
        Assert.assertFalse(narrow.isCompatible(JavaConstant.NULL_POINTER));
        Assert.assertFalse(klass.isCompatible(JavaConstant.forInt(0)));
        Assert.assertFalse(narrow.isCompatible(JavaConstant.forInt(0)));
    }
}
