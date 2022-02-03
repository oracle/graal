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

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.util.UnsafePartitionKind;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public class UnsafePartitionStoreNode extends RawStoreNode {
    public static final NodeClass<UnsafePartitionStoreNode> TYPE = NodeClass.create(UnsafePartitionStoreNode.class);

    protected final UnsafePartitionKind partitionKind;
    protected final ResolvedJavaType partitionType;

    public UnsafePartitionStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity, UnsafePartitionKind partitionKind,
                    ResolvedJavaType partitionType) {
        super(TYPE, object, offset, value, accessKind, locationIdentity, true, MemoryOrderMode.PLAIN, null, false);
        this.partitionKind = partitionKind;
        this.partitionType = partitionType;
    }

    public UnsafePartitionKind partitionKind() {
        return partitionKind;
    }

    public ResolvedJavaType partitionType() {
        return partitionType;
    }
}
