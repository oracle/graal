/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.printer;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;

/**
 * Verifies that graphs have no dead code.
 */
public class NoDeadCodeVerifyHandler implements DebugVerifyHandler {

    private static final Collection<Class<? extends Phase>> excludedPhases = Arrays.asList(FloatingReadPhase.class);

    private static boolean isExcluded(Phase phase) {
        for (Class<? extends Phase> c : excludedPhases) {
            if (c.isAssignableFrom(phase.getClass())) {
                return true;
            }
        }
        return false;
    }

    public void verify(Object object, Object... context) {
        StructuredGraph graph = extract(StructuredGraph.class, object);
        Phase phase = extract(Phase.class, context);
        String message = extract(String.class, context);
        if (graph != null && (phase == null || !isExcluded(phase))) {
            List<Node> removed = new ArrayList<>();
            new DeadCodeEliminationPhase(removed).run(graph);
            if (!removed.isEmpty()) {
                String prefix = message == null ? "" : message + ": ";
                throw new GraalInternalError("%sfound dead nodes in %s: %s", prefix, graph, removed);
            }
        }
    }
}
