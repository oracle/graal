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

import org.graalvm.visualizer.filter.spi.GraphFilterLocator;
import org.graalvm.visualizer.graph.Diagram;
import org.openide.util.Lookup;

/**
 * Various utilities related to filters.
 *
 * @author sdedic
 */
public final class Filters {
    /**
     * Convenience method, to apply filters in a chain on a diagram.
     * Filters (from {@link #getFilters}) will be applied in the order given by the "sequence" parameter.
     * Filters not present in "sequence" will be executed last, in the order of appearance in {@link #getFilters}.
     * <p/>
     * Unlike {@link #applyWithCancel}, running filters cannot be cancelled externally. If the filter is aborted
     * for some reason, the exception will be logged and silently ignored.
     *
     * @param chain filters to apply
     * @param d     diagram to process
     */
    public static void apply(FilterChain chain, Diagram d) {
        FilterExecution.getExecutionService().createExecution(chain, null, d).process();
    }

    public static FilterExecution applyWithCancel(FilterChain chain, Diagram d, FilterChain sequence) {
        return FilterExecution.getExecutionService().createExecution(chain, null, d);
    }

    /**
     * Locates a FilterChainSource in the Lookup. Uses {@link GraphFilterLocator}
     * SPI to search in the Lookup.
     *
     * @param lkp the lookup, e.g. from FileObject
     * @return FilterChainSource, is it exists.
     */
    public static FilterProvider locateChainSource(Lookup lkp) {
        return Lookup.getDefault().lookupAll(GraphFilterLocator.class)
                .stream().sequential()
                .map((gfl) -> gfl.findChain(lkp))
                .filter((i) -> i != null).findFirst().orElse(null);
    }

    public static <T> T lookupFilter(Filter f, Class<T> clazz) {
        if (clazz.isInstance(f)) {
            return clazz.cast(f);
        } else {
            return f.getLookup().lookup(clazz);
        }
    }

    private Filters() {
    }
}
