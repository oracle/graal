/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.filter;

import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.script.ScriptEnvironment;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a shared environment to execute filters. Filter implementations
 * may use the context object to track/share data. The environment instance will
 * be kept unless the set of filters change.
 * Filters always run in a single thread, but the thread is not dedicated (can be taken from a thread
 * pool).
 *
 * @author sdedic
 */
public abstract class FilterEnvironment implements AutoCloseable {
    private final ScriptEnvironment scriptEnv;
    private final Diagram d;
    private final Map<String, Object> globals = Collections.synchronizedMap(new HashMap<>());

    // prevents subclassing
    protected FilterEnvironment(Diagram d, ScriptEnvironment scriptEnv) {
        this.d = d;
        this.scriptEnv = scriptEnv;
    }

    public ScriptEnvironment getScriptEnvironment() {
        return scriptEnv;
    }

    /**
     * The diagram to process / filter
     *
     * @return diagram instance
     */
    public Diagram getDiagram() {
        return d;
    }

    public static FilterEnvironment simple(Diagram d) {
        return FilterChain.createStub(d);
    }

    public Map<String, Object> globals() {
        return globals;
    }

    public abstract void close() throws IOException;
}
