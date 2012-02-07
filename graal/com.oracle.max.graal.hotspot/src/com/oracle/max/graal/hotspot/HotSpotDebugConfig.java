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
package com.oracle.max.graal.hotspot;

import java.util.*;
import java.util.regex.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.printer.*;


public class HotSpotDebugConfig implements DebugConfig {

    private final String logFilter;
    private final String meterFilter;
    private final String timerFilter;
    private final String dumpFilter;
    private final String methodFilter;
    private final List<DebugDumpHandler> dumpHandlers = new ArrayList<>();

    public HotSpotDebugConfig(String logFilter, String meterFilter, String timerFilter, String dumpFilter, String methodFilter) {
        this.logFilter = logFilter;
        this.meterFilter = meterFilter;
        this.timerFilter = timerFilter;
        this.dumpFilter = dumpFilter;
        this.methodFilter = methodFilter;
        dumpHandlers.add(new IdealGraphPrinterDumpHandler(GraalOptions.PrintIdealGraphAddress, GraalOptions.PrintIdealGraphPort));
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

    private boolean isEnabled(String filter) {
        return filter != null && checkContains(Debug.currentScope(), filter) && checkMethodFilter();
    }

    private static boolean checkContains(String currentScope, String filter) {
        if (filter.contains("*")) {
            /*filter = filter.replace("*", ".*");
            filter = filter.replace("[", "\\[");
            filter = filter.replace("]", "\\]");
            filter = filter.replace(":", "\\:");*/
            return Pattern.matches(filter, currentScope);
        }
        return currentScope.contains(filter);
    }

    private boolean checkMethodFilter() {
        if (methodFilter == null) {
            return true;
        } else {
            for (Object o : Debug.context()) {
                if (o instanceof RiMethod) {
                    RiMethod riMethod = (RiMethod) o;
                    if (CiUtil.format("%H.%n", riMethod).contains(methodFilter)) {
                        return true;
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

    private static void add(StringBuilder sb, String name, String filter) {
        if (filter != null) {
            sb.append(' ');
            sb.append(name);
            sb.append('=');
            sb.append(filter);
        }
    }

    @Override
    public RuntimeException interceptException(Throwable e) {
        if (e instanceof CiBailout) {
            return null;
        }
        Debug.setConfig(Debug.fixedConfig(true, true, false, false, dumpHandlers));
        // sync "Exception occured in scope: " with mx/sanitycheck.py::Test.__init__
        Debug.log(String.format("Exception occured in scope: %s", Debug.currentScope()));
        for (Object o : Debug.context()) {
            Debug.log("Context obj %s", o);
            if (o instanceof Graph) {
                Graph graph = (Graph) o;
                Debug.log("Found graph in context: ", graph);
                Debug.dump(o, "Exception graph");
            }
        }
        return null;
    }

    @Override
    public Collection<? extends DebugDumpHandler> dumpHandlers() {
        return dumpHandlers;
    }
}
