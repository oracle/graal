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

import java.util.EventObject;
import java.util.concurrent.CancellationException;

/**
 * Events fired from {@link FilterSequence} during {@link Filter} execution.
 *
 * @author Svatopluk Dedic
 */
public final class FilterEvent extends EventObject {
    private final Filter filter;
    private final Throwable executionError;
    private final Diagram filteredDiagram;
    private final FilterExecution execution;

    public FilterEvent(FilterExecution exec, FilterSequence source, Filter filter, Diagram filteredDiagram) {
        this(exec, source, filter, filteredDiagram, null);
    }

    public FilterEvent(FilterExecution exec, FilterSequence source, Filter filter, Diagram filteredDiagram, Throwable error) {
        super(source);
        this.filter = filter;
        this.executionError = error;
        this.filteredDiagram = filteredDiagram;
        this.execution = exec;
    }

    public FilterExecution getExecution() {
        return execution;
    }

    @Override
    public FilterSequence getSource() {
        return (FilterSequence) super.getSource();
    }

    /**
     * @return filter about to run or just executed
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * @return exception thrown from filter execution, or {@code null}.
     */
    public Throwable getExecutionError() {
        return executionError;
    }

    /**
     * @return diagram being filtered
     */
    public Diagram getFilteredDiagram() {
        return filteredDiagram;
    }

    public boolean wasInterrupted() {
        return (executionError instanceof FilterCanceledException) ||
                (executionError instanceof CancellationException);
    }
}
