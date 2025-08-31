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
package jdk.graal.compiler.printer;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpHandler;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugDumpHandlersFactory.class)
public class GraalDebugHandlersFactory implements DebugDumpHandlersFactory {

    private final SnippetReflectionProvider snippetReflection;

    public GraalDebugHandlersFactory() {
        this.snippetReflection = null;
    }

    public GraalDebugHandlersFactory(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    @Override
    public List<DebugDumpHandler> createHandlers(OptionValues options) {
        List<DebugDumpHandler> handlers = new ArrayList<>();
        handlers.add(new GraphPrinterDumpHandler((debug, graph) -> new BinaryGraphPrinter(debug, snippetReflection)));
        if (DebugOptions.PrintCanonicalGraphStrings.getValue(options)) {
            handlers.add(new GraphPrinterDumpHandler((debug, graph) -> createStringPrinter(snippetReflection)));
        }
        handlers.add(new NodeDumper());
        handlers.add(new CFGPrinterObserver());
        if (DebugOptions.PrintBlockMapping.getValue(options)) {
            handlers.add(new BciBlockMappingDumpHandler());
        }
        return handlers;
    }

    private static final class NodeDumper implements DebugDumpHandler {
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
