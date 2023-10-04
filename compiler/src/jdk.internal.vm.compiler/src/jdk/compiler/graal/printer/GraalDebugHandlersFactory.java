/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.printer;

import java.util.ArrayList;
import java.util.List;

import jdk.compiler.graal.api.replacements.SnippetReflectionProvider;
import jdk.compiler.graal.debug.DebugCloseable;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.DebugDumpHandler;
import jdk.compiler.graal.debug.DebugHandler;
import jdk.compiler.graal.debug.DebugHandlersFactory;
import jdk.compiler.graal.debug.DebugOptions;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.nodeinfo.Verbosity;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.ScheduleResult;
import jdk.compiler.graal.nodes.util.GraphUtil;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.schedule.SchedulePhase;
import jdk.compiler.graal.serviceprovider.ServiceProvider;

@ServiceProvider(DebugHandlersFactory.class)
public class GraalDebugHandlersFactory implements DebugHandlersFactory {

    private final SnippetReflectionProvider snippetReflection;

    public GraalDebugHandlersFactory() {
        this.snippetReflection = null;
    }

    public GraalDebugHandlersFactory(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    @Override
    public List<DebugHandler> createHandlers(OptionValues options) {
        List<DebugHandler> handlers = new ArrayList<>();
        handlers.add(new GraphPrinterDumpHandler((debug, graph) -> new BinaryGraphPrinter(debug, snippetReflection)));
        if (DebugOptions.PrintCanonicalGraphStrings.getValue(options)) {
            handlers.add(new GraphPrinterDumpHandler((debug, graph) -> createStringPrinter(snippetReflection)));
        }
        handlers.add(new NodeDumper());
        handlers.add(new CFGPrinterObserver());
        handlers.add(new NoDeadCodeVerifyHandler());
        if (DebugOptions.PrintBlockMapping.getValue(options)) {
            handlers.add(new BciBlockMappingDumpHandler());
        }
        return handlers;
    }

    private static class NodeDumper implements DebugDumpHandler {
        @Override
        public void dump(Object object, DebugContext debug, boolean forced, String format, Object... arguments) {
            if (debug.isLogEnabled()) {
                if (object instanceof Node) {
                    Node node = (Node) object;
                    String location = GraphUtil.approxSourceLocation(node);
                    String nodeName = node.toString(Verbosity.Debugger);
                    if (location != null) {
                        debug.log("Context obj %s (approx. location: %s)", nodeName, location);
                    } else {
                        debug.log("Context obj %s", nodeName);
                    }
                }
            }
        }
    }

    private static CanonicalStringGraphPrinter createStringPrinter(SnippetReflectionProvider snippetReflection) {
        return new CanonicalStringGraphPrinter(snippetReflection);
    }

    @SuppressWarnings("try")
    static ScheduleResult tryGetSchedule(DebugContext debug, StructuredGraph graph) {
        ScheduleResult scheduleResult = graph.getLastSchedule();
        if (scheduleResult == null) {
            // Also provide a schedule when an error occurs
            if (DebugOptions.PrintGraphWithSchedule.getValue(graph.getOptions()) || debug.contextLookup(Throwable.class) != null) {
                try (DebugCloseable noIntercept = debug.disableIntercept()) {
                    SchedulePhase.runWithoutContextOptimizations(graph);
                    scheduleResult = graph.getLastSchedule();
                } catch (Throwable t) {
                }
            }
        }
        return scheduleResult;
    }
}
