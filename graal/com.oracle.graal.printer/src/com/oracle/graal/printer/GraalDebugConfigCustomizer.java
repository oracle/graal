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
package com.oracle.graal.printer;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.service.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.util.*;

@ServiceProvider(DebugConfigCustomizer.class)
public class GraalDebugConfigCustomizer implements DebugConfigCustomizer {
    public void customize(DebugConfig config) {
        config.dumpHandlers().add(new GraphPrinterDumpHandler());
        config.dumpHandlers().add(new NodeDumper());
        if (PrintCFG.getValue() || PrintBackendCFG.getValue()) {
            if (PrintBinaryGraphs.getValue() && PrintCFG.getValue()) {
                TTY.out.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -G:-PrintCFG to disable it");
            }
            config.dumpHandlers().add(new CFGPrinterObserver(PrintCFG.getValue()));
        }
        config.verifyHandlers().add(new NoDeadCodeVerifyHandler());
    }

    private static class NodeDumper implements DebugDumpHandler {

        public void dump(Object object, String message) {
            if (object instanceof Node) {
                String location = GraphUtil.approxSourceLocation((Node) object);
                String node = ((Node) object).toString(Verbosity.Debugger);
                if (location != null) {
                    Debug.log("Context obj %s (approx. location: %s)", node, location);
                } else {
                    Debug.log("Context obj %s", node);
                }
            }
        }

        public void close() {
        }
    }
}
