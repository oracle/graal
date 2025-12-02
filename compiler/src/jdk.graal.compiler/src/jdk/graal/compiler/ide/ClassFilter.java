/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.ide;

import java.util.ArrayList;

/**
 * The ClassFilter interface provides a way to filter classes based on their names. Implementations
 * of this interface can be used to determine whether a class should be reported or not.
 *
 * @see IDEReport
 */
public sealed interface ClassFilter permits PrefixFilter, CompositeFilter, AcceptAllFilter {

    /**
     * Checks whether a class with the given name should be included in the reports.
     *
     * @param className the name of the class to check
     * @return true if the class should be included in reports, false otherwise
     */
    boolean shouldBeReported(String className);

    /**
     * Creates a ClassFilter instance based on a filter description. The filter description is a
     * comma-separated list of prefixes.
     *
     * @param filterDescr the filter description
     * @return a ClassFilter instance that matches the given description
     */
    static ClassFilter parseFilterDescr(String filterDescr) {
        var individualFilterDescr = filterDescr.split(",");
        var filters = new ArrayList<ClassFilter>(individualFilterDescr.length);
        for (var descr : individualFilterDescr) {
            filters.add(new PrefixFilter(descr));
        }
        return switch (filters.size()) {
            case 0 -> AcceptAllFilter.INSTANCE;
            case 1 -> filters.getFirst();
            default -> new CompositeFilter(filters);
        };
    }

}
