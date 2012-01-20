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

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.debug.*;


public class HotSpotDebugConfig implements DebugConfig {

    public final String logFilter;
    public final String meterFilter;
    public final String timerFilter;
    public final String dumpFilter;
    public final String methodFilter;

    public HotSpotDebugConfig(String logFilter, String meterFilter, String timerFilter, String dumpFilter, String methodFilter) {
        this.logFilter = logFilter;
        this.meterFilter = meterFilter;
        this.timerFilter = timerFilter;
        this.dumpFilter = dumpFilter;
        this.methodFilter = methodFilter;
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

    public boolean isTimerEnabled() {
        return isEnabled(timerFilter);
    }

    private boolean isEnabled(String filter) {
        return filter != null && Debug.currentScope().contains(filter) && checkMethodFilter();
    }

    private boolean checkMethodFilter() {
        if (methodFilter == null) {
            return true;
        } else {
            for (Object o : Debug.context()) {
                if (o instanceof RiMethod) {
                    RiMethod riMethod = (RiMethod) o;
                    if (riMethod.toString().contains(methodFilter)) {
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
}
