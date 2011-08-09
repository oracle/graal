/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.java;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

/**
 * Instruction that is used to refer to the address of an on-stack monitor.
 */
public final class MonitorAddressNode extends ValueNode {

    private int monitorIndex;

    public MonitorAddressNode(int monitorIndex, Graph graph) {
        super(CiKind.Word, graph);
        this.monitorIndex = monitorIndex;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMonitorAddress(this);
    }

    public int monitorIndex() {
        return monitorIndex;
    }

    public void setMonitorIndex(int monitorIndex) {
        this.monitorIndex = monitorIndex;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("monitorIndex", monitorIndex);
        return properties;
    }
}
