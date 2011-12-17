/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes;

import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;

/**
 * Encapsulates the object that is locked and unlocked. This node is referenced by a {@link MonitorEnterNode},
 * all {@link MonitorExitNode} that correspond to this monitor enter, and in all {@link FrameState}s in between
 * the monitor enter and monitor exits.
 */
public class MonitorObject extends ValueNode implements LIRLowerable {
    @Input private ValueNode owner;

    public ValueNode owner() {
        return owner;
    }

    /**
     * Creates a new MonitorObjectNode.
     *
     * @param object The object that is processed by the monitor operation.
     */
    public MonitorObject(ValueNode object) {
        super(object.stamp());
        this.owner = object;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to do, monitor objects are processed as part of the monitor enter / monitor exit nodes.
    }
}
