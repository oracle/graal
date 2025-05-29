/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.core.meta.SharedMethod;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;

/**
 * When using the open type world, each interface a type implements has a unique dispatch table. For
 * a given type, all the tables are stored together with the DynamicHubs' vtable slots. The logic
 * for determining the starting offset of the dispatch table is contained in
 * {@link OpenTypeWorldDispatchTableSnippets}.
 */
@NodeInfo(size = NodeSize.SIZE_UNKNOWN, cycles = NodeCycles.CYCLES_UNKNOWN)
public class LoadOpenTypeWorldDispatchTableStartingOffset extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<LoadOpenTypeWorldDispatchTableStartingOffset> TYPE = NodeClass.create(LoadOpenTypeWorldDispatchTableStartingOffset.class);

    @Input protected ValueNode hub;
    @OptionalInput protected ValueNode interfaceTypeID;

    protected final SharedMethod target;

    public LoadOpenTypeWorldDispatchTableStartingOffset(ValueNode hub, SharedMethod target) {
        super(TYPE, StampFactory.forInteger(64));
        this.hub = hub;
        this.target = target;
        this.interfaceTypeID = null;
    }

    public LoadOpenTypeWorldDispatchTableStartingOffset(ValueNode hub, ValueNode interfaceTypeID) {
        super(TYPE, StampFactory.forInteger(64));
        this.hub = hub;
        this.target = null;
        this.interfaceTypeID = interfaceTypeID;
    }

    public ValueNode getHub() {
        return hub;
    }

    public ValueNode getInterfaceTypeID() {
        return interfaceTypeID;
    }

    public SharedMethod getTarget() {
        return target;
    }
}
