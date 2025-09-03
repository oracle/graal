/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.codegen.phase;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Replaces unsafe accesses ({@link RawStoreNode} and {@link RawLoadNode}) on the null object (an
 * i64 zero value) with an access at an absolute address ({@link UnsafeMemoryStoreNode} or
 * {@link UnsafeMemoryLoadNode}).
 * <p>
 * These raw store and load nodes seem to have an implicit guarantee that the receiver object is
 * either a non-null object or an i64 zero value. We rely on this guarantee to make sure unsafe
 * accesses with a null-receiver never happen through these raw store and load nodes.
 * <p>
 * Because this is for single-threaded JS code, we can ignore the need for memory barriers or
 * certain memory ordering encoded in the original nodes.
 */
public class NullUnsafeAccessPhase extends BasePhase<CoreProviders> {
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        // Replace RawStoreNodes with equivalent UnsafeMemoryStoreNodes
        for (RawStoreNode node : graph.getNodes().filter(RawStoreNode.class)) {
            if (canOptimize(node)) {
                UnsafeMemoryStoreNode unsafeStore = graph.add(new UnsafeMemoryStoreNode(node.offset(), node.value(), node.accessKind(), node.getLocationIdentity()));
                unsafeStore.setStateAfter(node.stateAfter());
                graph.replaceFixedWithFixed(node, unsafeStore);
            }
        }

        // Replace RawLoadNodes with equivalent UnsafeMemoryLoadNodes
        for (RawLoadNode node : graph.getNodes().filter(RawLoadNode.class)) {
            if (canOptimize(node)) {
                UnsafeMemoryLoadNode unsafeLoad = graph.add(new UnsafeMemoryLoadNode(node.offset(), node.accessKind(), node.getLocationIdentity()));
                graph.replaceFixedWithFixed(node, unsafeLoad);
            }
        }
    }

    /**
     * Accesses can be optimized if the receiver object is {@code null} (this is represented as an
     * i64 zero value, actual null object receivers are not allowed).
     */
    protected static boolean canOptimize(UnsafeAccessNode node) {
        ValueNode object = node.object();
        JavaKind kind = object.getStackKind();
        JavaConstant javaConstant = object.asJavaConstant();
        if (javaConstant != null && javaConstant.isDefaultForKind() && kind == JavaKind.Long) {
            /*
             * Such accesses should not appear because accesses of object kinds are null checked and
             * paths with these nodes optimized away. This is just here as a sanity check to catch
             * errors early.
             */
            assert node.accessKind() != JavaKind.Object : "Accessing object reference in raw memory";
            return true;
        } else {
            // This validates the implicit guarantee mentioned above
            assert object.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp objectStamp && objectStamp.nonNull() : object.stamp(NodeView.DEFAULT);
            return false;
        }
    }
}
