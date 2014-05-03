/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.cfs;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.PhaseContext;

public class FlowSensitiveReductionPhase extends BasePhase<PhaseContext> {

    private final MetaAccessProvider metaAccess;

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public FlowSensitiveReductionPhase(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
    }

    @Override
    protected final void run(StructuredGraph graph, PhaseContext context) {
        try (Debug.Scope s = Debug.scope("FlowSensitiveReduction")) {
            if (graph.isOSR()) {
                Debug.log("Skipping OSR method %s", graph.method() == null ? "" : MetaUtil.format("%H.%n", graph.method()));
                return;
            }
            Debug.dump(graph, "FlowSensitiveReduction initial");
            new FlowSensitiveReduction(graph.start(), new State(), context).apply();
            Debug.dump(graph, "FlowSensitiveReduction done");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

}