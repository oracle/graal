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
package jdk.graal.compiler.core.test.ea;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.ArrayCloneCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.CacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.IndexedCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.LoadCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.NewInitializedArrayCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.UnsafeLoadCacheEntry;
import jdk.vm.ci.meta.JavaKind;

public class ReadEliminationCacheEntryTest extends GraalCompilerTest {

    static class Fields {
        int first;
        int second;
    }

    public static int snippet(Object object, Object otherObject, int index, int otherIndex) {
        return object == otherObject ? index : otherIndex;
    }

    private LocationIdentity location(String name) throws NoSuchFieldException {
        return new FieldLocationIdentity(getMetaAccess().lookupJavaField(Fields.class.getDeclaredField(name)));
    }

    private static List<CacheEntry<?>> entries(ValueNode object, LocationIdentity identity, ValueNode index, JavaKind kind) {
        return List.of(new LoadCacheEntry(object, identity), new ArrayCloneCacheEntry(object, identity),
                        new NewInitializedArrayCacheEntry(object, identity), new UnsafeLoadCacheEntry(object, index, identity, kind),
                        new IndexedCacheEntry(object, identity, index, kind));
    }

    @Test
    public void testEqualityAndLookup() throws NoSuchFieldException {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        List<CacheEntry<?>> keys = entries(graph.getParameter(0), location("first"), graph.getParameter(2), JavaKind.Int);
        List<CacheEntry<?>> equalKeys = entries(graph.getParameter(0), location("first"), graph.getParameter(2), JavaKind.Int);
        EconomicMap<CacheEntry<?>, Integer> cache = EconomicMap.create();
        for (int i = 0; i < keys.size(); i++) {
            cache.put(keys.get(i), i);
        }
        Assert.assertEquals(keys.size(), cache.size());
        EconomicMap<CacheEntry<?>, Integer> copy = EconomicMap.create(Equivalence.DEFAULT, cache);
        for (int i = 0; i < keys.size(); i++) {
            CacheEntry<?> key = keys.get(i);
            Assert.assertFalse(key.equals(null));
            Assert.assertFalse(key.equals(new Object()));
            Assert.assertEquals(key, key);
            Assert.assertEquals(key, equalKeys.get(i));
            Assert.assertEquals(equalKeys.get(i), key);
            Assert.assertEquals(key.hashCode(), equalKeys.get(i).hashCode());
            CacheEntry<?> duplicate = key.duplicateWithObject(key.object);
            Assert.assertEquals(key, duplicate);
            Assert.assertEquals(equalKeys.get(i), duplicate);
            Assert.assertEquals(key.hashCode(), duplicate.hashCode());
            Assert.assertEquals(Integer.valueOf(i), cache.get(equalKeys.get(i)));
            Assert.assertEquals(Integer.valueOf(i), copy.get(duplicate));
            for (int j = 0; j < keys.size(); j++) {
                Assert.assertEquals(i == j, key.equals(equalKeys.get(j)));
                Assert.assertEquals(i == j, equalKeys.get(j).equals(key));
            }
        }
    }

    @Test
    public void testUnregisteredPhiReceiver() throws NoSuchFieldException {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        ValuePhiNode phi = new ValuePhiNode(StampFactory.object(), graph.add(new MergeNode()));
        Assert.assertTrue(phi.isUnregistered());
        List<CacheEntry<?>> keys = entries(phi, location("first"), graph.getParameter(2), JavaKind.Int);
        EconomicMap<CacheEntry<?>, Integer> cache = EconomicMap.create();
        int[] hashes = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            hashes[i] = keys.get(i).hashCode();
            cache.put(keys.get(i), i);
        }
        EconomicMap<CacheEntry<?>, Integer> copy = EconomicMap.create(Equivalence.DEFAULT, cache);
        graph.addWithoutUnique(phi);
        Assert.assertTrue(phi.isAlive());
        List<CacheEntry<?>> registeredKeys = entries(phi, location("first"), graph.getParameter(2), JavaKind.Int);
        for (int i = 0; i < keys.size(); i++) {
            Assert.assertEquals(hashes[i], keys.get(i).hashCode());
            Assert.assertEquals(keys.get(i), registeredKeys.get(i));
            Assert.assertEquals(hashes[i], registeredKeys.get(i).hashCode());
            Assert.assertEquals(Integer.valueOf(i), cache.get(registeredKeys.get(i)));
            Assert.assertEquals(Integer.valueOf(i), copy.get(registeredKeys.get(i)));
        }
    }

    @Test
    public void testKeyComponents() throws NoSuchFieldException {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        ValueNode object = graph.getParameter(0);
        ValueNode otherObject = graph.getParameter(1);
        ValueNode index = graph.getParameter(2);
        ValueNode otherIndex = graph.getParameter(3);
        LocationIdentity identity = location("first");
        List<CacheEntry<?>> keys = entries(object, identity, index, JavaKind.Int);
        List<CacheEntry<?>> differentObjects = entries(otherObject, identity, index, JavaKind.Int);
        List<CacheEntry<?>> differentLocations = entries(object, location("second"), index, JavaKind.Int);
        List<CacheEntry<?>> differentIndexes = entries(object, identity, otherIndex, JavaKind.Int);
        List<CacheEntry<?>> differentKinds = entries(object, identity, index, JavaKind.Long);
        for (int i = 0; i < keys.size(); i++) {
            CacheEntry<?> key = keys.get(i);
            Assert.assertNotEquals(key, differentObjects.get(i));
            Assert.assertNotEquals(key, differentLocations.get(i));
            Assert.assertEquals(differentObjects.get(i), key.duplicateWithObject(otherObject));
            boolean hasIndexAndKind = key instanceof UnsafeLoadCacheEntry || key instanceof IndexedCacheEntry;
            Assert.assertEquals(!hasIndexAndKind, key.equals(differentIndexes.get(i)));
            Assert.assertEquals(!hasIndexAndKind, key.equals(differentKinds.get(i)));
        }
    }

    @Test
    public void testNullIdentitiesAndObjects() throws NoSuchFieldException {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.NO);
        List<CacheEntry<?>> keys = List.of(new LoadCacheEntry(null, null), new ArrayCloneCacheEntry(null, null),
                        new NewInitializedArrayCacheEntry(null, null), new UnsafeLoadCacheEntry(null, null, location("first"), JavaKind.Int),
                        new IndexedCacheEntry(null, null, graph.getParameter(2), JavaKind.Int));
        for (CacheEntry<?> key : keys) {
            CacheEntry<?> equalKey = key.duplicateWithObject(null);
            Assert.assertEquals(key, key);
            Assert.assertEquals(key, equalKey);
            Assert.assertEquals(equalKey, key);
            Assert.assertEquals(key.hashCode(), equalKey.hashCode());
            Assert.assertFalse(key.equals(null));
            Assert.assertNotEquals(key, key.duplicateWithObject(graph.getParameter(0)));
        }
    }
}
