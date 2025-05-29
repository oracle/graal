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
import org.openide.util.Lookup;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Service that allows to (partially) control filters applied to the diagram. The service
 * is to be exposed from the diagram UI components / models through {@link Lookup}.
 *
 * @author sdedic
 */
public interface DiagramFilters {
    /**
     * Returns a FilterSequence that allows to manipulate with filters
     * applied to the graph. The sequence always exists, but may be empty initially.
     *
     * @return filter sequence instance.
     */
    public FilterSequence getFilterSequence();

    /**
     * Returns scripting environment for the diagram.
     *
     * @return
     */
    public ScriptEnvironment getScriptEnvironment();

    /**
     * @return all applied filters.
     */
    public List<Filter> getFilters();

    /**
     * @return the script-based filters in effect.
     */
    public List<Filter> getScriptFilters();

    /**
     * Processes the selected diagram with a filter. The process is run
     * asynchronously and only when it finishes, the current diagram is replaced
     * in the cache. If the filter fails to process the graph, the original
     * diagram stays in effect.
     * <p/>
     * The Filter is applied on the "user" state of the diagram, if 'append"
     * parameter is {@code false} - the modification is applied to the start
     * state of the diagram, subsequent applications discard the previous
     * changes.
     * <p/>
     * If 'append' is {@code true}, the new modification will be applied on top
     * of the preceding one. In such case the filter is applied again even
     * though it was applied in the past.
     * <p/>
     * If 'env' is different to previous {@link ScriptEnvironment}, all previous
     * scripts are discarded.
     * <p/>
     * It is possible to set {@link ScriptEnvironment} only, for filters to use.
     *
     * @param filterToApply user filter to apply to the diagram.
     * @param env           if {@code null} previous or default {@link ScriptEnvironment}
     *                      is used.
     * @param append        if {@code true}, will apply the filter on top of the
     *                      existing data otherwise cleanse all before used scripts.
     * @param callback      callback code which will receive the {@link Diagram}
     *                      instance when it is ready (filtered).
     * @return future representing the process
     */
    public Future<Diagram> applyScriptFilter(Filter filterToApply, ScriptEnvironment env, boolean append, Consumer<Diagram> callback);
}
