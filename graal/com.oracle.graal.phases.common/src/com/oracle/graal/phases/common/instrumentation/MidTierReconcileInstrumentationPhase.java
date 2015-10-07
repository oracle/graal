/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.instrumentation;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.FixedValueAnchorNode;
import com.oracle.graal.nodes.java.RawMonitorEnterNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationNode;
import com.oracle.graal.phases.common.instrumentation.nodes.MonitorProxyNode;

public class MidTierReconcileInstrumentationPhase extends Phase {

    private static RawMonitorEnterNode unproxify(MonitorProxyNode proxy) {
        for (RawMonitorEnterNode monitorEnter : proxy.getMonitorId().usages().filter(RawMonitorEnterNode.class)) {
            if (monitorEnter.object() == proxy.target()) {
                return monitorEnter;
            }
        }
        return null;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            instrumentationNode.onMidTierReconcileInstrumentation();
            ValueNode target = instrumentationNode.target();
            if (target instanceof MonitorProxyNode) {
                instrumentationNode.replaceFirstInput(target, unproxify((MonitorProxyNode) target));
            } else if (target instanceof FixedValueAnchorNode) {
                instrumentationNode.replaceFirstInput(target, GraphUtil.unproxify(target));
            }
        }
    }
}
