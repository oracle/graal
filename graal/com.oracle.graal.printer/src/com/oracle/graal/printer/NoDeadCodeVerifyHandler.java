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

import static com.oracle.graal.printer.NoDeadCodeVerifyHandler.Options.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.common.*;

/**
 * Verifies that graphs have no dead code.
 */
public class NoDeadCodeVerifyHandler implements DebugVerifyHandler {

    // The options below will be removed once all phases clean up their own dead code.

    private static final int OFF = 0;
    private static final int INFO = 1;
    private static final int VERBOSE = 2;
    private static final int FATAL = 3;

    static class Options {
        // @formatter:off
        @Option(help = "Run level for NoDeadCodeVerifyHandler (0 = off, 1 = info, 2 = verbose, 3 = fatal)")
        public static final OptionValue<Integer> NDCV = new OptionValue<>(0);
        // @formatter:on
    }

    /**
     * Only the first instance of failure at any point is shown. This will also be removed once all
     * phases clean up their own dead code.
     */
    private static final Map<String, Boolean> discovered = new ConcurrentHashMap<>();

    public void verify(Object object, String message) {
        if (NDCV.getValue() != OFF && object instanceof StructuredGraph) {
            StructuredGraph graph = (StructuredGraph) object;
            List<Node> before = graph.getNodes().snapshot();
            new DeadCodeEliminationPhase().run(graph);
            List<Node> after = graph.getNodes().snapshot();
            assert after.size() <= before.size();
            if (before.size() != after.size()) {
                if (discovered.put(message, Boolean.TRUE) == null) {
                    before.removeAll(after);
                    String prefix = message == null ? "" : message + ": ";
                    GraalInternalError error = new GraalInternalError("%sfound dead nodes in %s: %s", prefix, graph, before);
                    if (NDCV.getValue() == INFO) {
                        System.out.println(error.getMessage());
                    } else if (NDCV.getValue() == VERBOSE) {
                        error.printStackTrace(System.out);
                    } else {
                        assert NDCV.getValue() == FATAL;
                        throw error;
                    }
                }
            }
        }
    }
}
