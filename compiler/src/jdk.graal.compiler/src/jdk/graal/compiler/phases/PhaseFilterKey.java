/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.GraphFilter;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import org.graalvm.collections.EconomicMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * An option that accepts a phase filter value. See {@link #HELP} for details.
 */
public final class PhaseFilterKey extends OptionKey<String> {

    /**
     * The token in a phase filter value for denoting a filter to be used in a phase free context
     * (i.e. match against graphs). For example, the token {@code "<compilation>"} can be used for a
     * filter that operates on whole compilations instead of individual phases. If null, then the
     * {@code phase} argument to {@link #matches} cannot be null.
     */
    final String phaselessGraphFilterToken;

    public PhaseFilterKey(String defaultValue, String phaselessGraphFilterToken) {
        super(defaultValue);
        this.phaselessGraphFilterToken = phaselessGraphFilterToken;
    }

    /**
     * Option help message describing the syntax of a phase filter specification.
     */
    public static final String HELP = """

                    A phase filter is a phase name, optionally followed by '=' and a method
                    filter. Multiple phase filters can be specified, separated by ':'.
                    A phase name is matched as a substring.

                    For example:

                       PartialEscape:Loop=B.foo,A.*

                    matches PartialEscapePhase for compilation of all methods and any phase
                    containing "Loop" in its name for compilation of B.foo as well as all
                    methods in class A.

                    A phase filter specification cannot be empty. Specify "*" to match
                    any phase.""";

    private final ConcurrentHashMap<String, BasePhase.PhaseFilter> parsedValues = new ConcurrentHashMap<>();

    @Override
    protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
        if (newValue != null && newValue.isEmpty()) {
            throw new IllegalArgumentException("Value for " + getName() + " cannot be empty");
        }
    }

    /**
     * Determines whether {@code phase} when applied to {@code graph} is matched by {@code filter}.
     */
    public boolean matches(OptionValues options, BasePhase<?> phase, StructuredGraph graph) {
        String filter = getValue(options);
        if (filter == null) {
            return false;
        } else {
            GraalError.guarantee(phase != null || phaselessGraphFilterToken != null, "Cannot pass null phase unless phaselessGraphFilterToken is non-null");
            return parsedValues.computeIfAbsent(filter, f -> PhaseFilterKey.parse(f, phaselessGraphFilterToken)).matches(phase, graph);
        }
    }

    /**
     * Creates a phase filter based on a specification string. The string is a colon-separated list
     * of phase names or {@code phase_name=filter} pairs. Phase names match any phase of which they
     * are a substring. Filters follow {@link MethodFilter} syntax.
     */
    private static BasePhase.PhaseFilter parse(String specification, String phaselessGraphFilterToken) {
        EconomicMap<Pattern, GraphFilter> filters = EconomicMap.create();
        String[] parts = specification.trim().split(":");
        GraphFilter phaselessGraphFilter = null;
        for (String part : parts) {
            String phaseName;
            GraphFilter graphFilter;
            if (part.contains("=")) {
                String[] pair = part.split("=");
                if (pair.length != 2) {
                    throw new IllegalArgumentException("expected phase_name=filter pair in: " + part);
                }
                phaseName = pair[0];
                graphFilter = new GraphFilter(pair[1]);
            } else {
                phaseName = part;
                graphFilter = new GraphFilter(null);
            }
            if (phaseName.equals(phaselessGraphFilterToken)) {
                phaselessGraphFilter = graphFilter;
            } else {
                Pattern phasePattern = Pattern.compile(".*" + MethodFilter.createGlobString(phaseName) + ".*");
                filters.put(phasePattern, graphFilter);
            }
        }
        return new BasePhase.PhaseFilter(filters, phaselessGraphFilter);
    }
}
