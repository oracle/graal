/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.spi.UncheckedInterfaceProvider;

/**
 * The {@code Parameter} instruction is a placeholder for an incoming argument to a function call.
 */
@NodeInfo(nameTemplate = "P({p#index})")
public final class ParameterNode extends AbstractLocalNode implements IterableNodeType, UncheckedInterfaceProvider {

    public static final NodeClass<ParameterNode> TYPE = NodeClass.create(ParameterNode.class);

    private Stamp uncheckedStamp;

    public ParameterNode(int index, StampPair stamp) {
        super(TYPE, index, stamp.getTrustedStamp());
        this.uncheckedStamp = stamp.getUncheckedStamp();
    }

    public Stamp uncheckedStamp() {
        return uncheckedStamp;
    }
}
