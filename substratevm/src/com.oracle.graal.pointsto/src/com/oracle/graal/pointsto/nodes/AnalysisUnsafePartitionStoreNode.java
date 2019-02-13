/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.api.UnsafePartitionKind;

import jdk.vm.ci.meta.JavaKind;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
public class AnalysisUnsafePartitionStoreNode extends FixedWithNextNode {
    public static final NodeClass<AnalysisUnsafePartitionStoreNode> TYPE = NodeClass.create(AnalysisUnsafePartitionStoreNode.class);

    @Input ValueNode value;
    @Input ValueNode object;
    @Input ValueNode offset;
    protected final JavaKind accessKind;
    protected final LocationIdentity locationIdentity;
    protected final UnsafePartitionKind partitionKind;
    protected final ResolvedJavaType partitionType;

    public AnalysisUnsafePartitionStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity, UnsafePartitionKind partitionKind,
                    ResolvedJavaType partitionType) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
        this.object = object;
        this.offset = offset;
        this.accessKind = accessKind;
        this.locationIdentity = locationIdentity;
        this.partitionKind = partitionKind;
        this.partitionType = partitionType;
        assert accessKind != JavaKind.Void && accessKind != JavaKind.Illegal;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public JavaKind accessKind() {
        return accessKind;
    }

    public ValueNode value() {
        return value;
    }

    public UnsafePartitionKind partitionKind() {
        return partitionKind;
    }

    public ResolvedJavaType partitionType() {
        return partitionType;
    }
}
