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
package com.oracle.graal.hotspot;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.printer.*;

public class HotSpotDebugConfig implements DebugConfig {

    private final DebugFilter logFilter;
    private final DebugFilter meterFilter;
    private final DebugFilter timerFilter;
    private final DebugFilter dumpFilter;
    private final MethodFilter[] methodFilter;
    private final List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
    private final PrintStream output;
    private final Set<Object> extraFilters = new HashSet<>();

    public HotSpotDebugConfig(String logFilter, String meterFilter, String timerFilter, String dumpFilter, String methodFilter, PrintStream output) {
        this.logFilter = DebugFilter.parse(logFilter);
        this.meterFilter = DebugFilter.parse(meterFilter);
        this.timerFilter = DebugFilter.parse(timerFilter);
        this.dumpFilter = DebugFilter.parse(dumpFilter);
        if (methodFilter == null || methodFilter.isEmpty()) {
            this.methodFilter = null;
        } else {
            String[] filters = methodFilter.split(",");
            this.methodFilter = new MethodFilter[filters.length];
            for (int i = 0; i < filters.length; i++) {
                this.methodFilter[i] = new MethodFilter(filters[i]);
            }
        }

        // Report the filters that have been configured so the user can verify it's what they expect
        if (logFilter != null || meterFilter != null || timerFilter != null || dumpFilter != null || methodFilter != null) {
            TTY.println(Thread.currentThread().getName() + ": " + toString());
        }
        dumpHandlers.add(new GraphPrinterDumpHandler());
        if (GraalOptions.PrintCFG) {
            if (GraalOptions.PrintBinaryGraphs) {
                TTY.println("CFG dumping slows down PrintBinaryGraphs: use -G:-PrintCFG to disable it");
            }
            dumpHandlers.add(new CFGPrinterObserver());
        }
        this.output = output;
    }

    public boolean isLogEnabled() {
        return isEnabled(logFilter);
    }

    public boolean isMeterEnabled() {
        return isEnabled(meterFilter);
    }

    public boolean isDumpEnabled() {
        return isEnabled(dumpFilter);
    }

    public boolean isTimeEnabled() {
        return isEnabled(timerFilter);
    }

    public PrintStream output() {
        return output;
    }

    private boolean isEnabled(DebugFilter filter) {
        return checkDebugFilter(Debug.currentScope(), filter) && checkMethodFilter();
    }

    private static boolean checkDebugFilter(String currentScope, DebugFilter filter) {
        return filter != null && filter.matches(currentScope);
    }

    private boolean checkMethodFilter() {
        if (methodFilter == null && extraFilters.isEmpty()) {
            return true;
        } else {
            for (Object o : Debug.context()) {
                if (extraFilters.contains(o)) {
                    return true;
                } else if (methodFilter != null) {
                    if (o instanceof JavaMethod) {
                        for (MethodFilter filter : methodFilter) {
                            if (filter.matches((JavaMethod) o)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug config:");
        add(sb, "Log", logFilter);
        add(sb, "Meter", meterFilter);
        add(sb, "Time", timerFilter);
        add(sb, "Dump", dumpFilter);
        add(sb, "MethodFilter", methodFilter);
        return sb.toString();
    }

    private static void add(StringBuilder sb, String name, Object filter) {
        if (filter != null) {
            sb.append(' ');
            sb.append(name);
            sb.append('=');
            if (filter instanceof Object[]) {
                sb.append(Arrays.toString((Object[]) filter));
            } else {
                sb.append(String.valueOf(filter));
            }
        }
    }

    @Override
    public RuntimeException interceptException(Throwable e) {
        if (e instanceof BailoutException) {
            return null;
        }
        Debug.setConfig(Debug.fixedConfig(true, true, false, false, dumpHandlers, output));
        Debug.log(String.format("Exception occurred in scope: %s", Debug.currentScope()));
        for (Object o : Debug.context()) {
            if (o instanceof Graph) {
                Debug.log("Context obj %s", o);
                if (GraalOptions.DumpOnError) {
                    Debug.dump(o, "Exception graph");
                } else {
                    Debug.log("Use -G:+DumpOnError to enable dumping of graphs on this error");
                }
            } else if (o instanceof Node) {
                String location = GraphUtil.approxSourceLocation((Node) o);
                if (location != null) {
                    Debug.log("Context obj %s (approx. location: %s)", o, location);
                } else {
                    Debug.log("Context obj %s", o);
                }
            } else {
                Debug.log("Context obj %s", o);
            }
        }
        return null;
    }

    @Override
    public Collection<? extends DebugDumpHandler> dumpHandlers() {
        return dumpHandlers;
    }

    @Override
    public void addToContext(Object o) {
        extraFilters.add(o);
    }

    @Override
    public void removeFromContext(Object o) {
        extraFilters.remove(o);
    }
}
