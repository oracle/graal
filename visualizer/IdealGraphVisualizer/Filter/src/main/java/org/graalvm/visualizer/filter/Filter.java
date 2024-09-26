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

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedEventProvider;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import org.graalvm.visualizer.graph.Diagram;
import org.openide.cookies.OpenCookie;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Defines a Filter that processes Diagrams. The actual processing must be implemented
 * in either {@link #apply(org.graalvm.visualizer.filter.FilterEnvironment)} or
 * {@link #apply(org.graalvm.visualizer.graph.Diagram)}. One of the methods <b>MUST</b>
 * be implemented ! The two methods have a default stub implementation for backwards
 * compatibility with existing user scripts, which call {@link #apply(org.graalvm.visualizer.graph.Diagram)}.
 *
 * @author sdedic
 */
public interface Filter extends Properties.Provider, ChangedEventProvider<Filter> {

    public String getName();

    /**
     * Applies the filter on the diagram. Backwads-compatible way how to
     * execute a single filter.
     * The default implementation will create its own execution environment,
     * except if it is:<ul>
     * <li>executed from within {@link FilterExecution#process}, and
     * <li>it is applied on the same diagram
     * </ul>
     * In that case it is run within the same environment as the calling
     * {@link FilterExecution#process}. This allows to call nested Filters e.g. from
     * user scripts without complicating the code.
     *
     * @param d the diagram on which the filter applies.
     * @throws java.util.concurrent.CancellationException may throw, when the processing cancelled
     */
    default public void apply(Diagram d) {
        FilterExecution e = FilterExecution.getOrCreate(d, this);
        e.processSingle(this);
    }

    /**
     * Applies the filter. The "env" parameter gives access to the processed diagram
     * The default implementation delegates to the {@link #apply(org.graalvm.visualizer.graph.Diagram)}.
     */
    default void applyWith(FilterEnvironment env) {
        apply(env.getDiagram());
    }

    /**
     * Cancels the pending operation on diagram d. If the diagram is not being
     * processed at the moment, cancel is not supported or fails, should return true.
     *
     * @param d diagram to cancel
     */
    default public boolean cancel(FilterEnvironment d) {
        return false;
    }

    /**
     * Provides additional optional services for this filter.
     *
     * @return Lookup for this filter
     */
    public Lookup getLookup();

    /**
     * @return the service that opens the editor
     * @deprecated find additional services in {@link #getLookup}
     */
    @Deprecated
    OpenCookie getEditor();

    @Override
    ChangedEvent<Filter> getChangedEvent();

    /**
     * Shared instance of an empty filter. The filter does nothing and fires no
     * events.
     */
    @NbBundle.Messages("FILTER_None=Empty filter")
    public static Filter NONE = new Filter() {
        private final Properties props = Properties.immutableEmpty();
        private final ChangedEvent<Filter> event = new ChangedEvent<Filter>(this) {
            @Override
            public void removeListener(ChangedListener<Filter> l) {
            }

            @Override
            public void addListener(ChangedListener<Filter> l) {
            }
        };

        @Override
        public String getName() {
            return Bundle.FILTER_None();
        }

        @Override
        public void apply(Diagram d) {
        }

        @Override
        public void applyWith(FilterEnvironment env) {
        }

        @Override
        public boolean cancel(FilterEnvironment d) {
            return true;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        @Override
        public OpenCookie getEditor() {
            return null;
        }

        @Override
        public ChangedEvent<Filter> getChangedEvent() {
            return event;
        }

        @Override
        public Properties getProperties() {
            return props;
        }
    };
}
