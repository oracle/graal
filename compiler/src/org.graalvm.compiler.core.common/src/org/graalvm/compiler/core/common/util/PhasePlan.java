/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.util;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An ordered list of compiler phases.
 *
 * @param <T> the type of a phase in the plan
 */
public interface PhasePlan<T> {

    /**
     * Gets an unmodifiable view on the phases in this plan.
     */
    List<T> getPhases();

    /**
     * Gets a name for a phase in the plan.
     *
     * This should be in the format of a qualified Java class name.
     */
    default String getPhaseName(T phase) {
        return phase.getClass().getName();
    }

    /**
     * Retrieves the changes to the state of the graph caused by the phase at {@code position} in
     * the phase plan.
     *
     * @param position the position of the phase in the plan
     */
    default String getGraphStateDiff(int position) {
        return null;
    }

    /**
     * Gets the index of the phase that caused a failure during the execution of this phase plan
     * (assertion error, compilation bailout, or any other Throwable).
     *
     * @return the index of the phase that caused the failure, {@code -1} means no failure occurred.
     */
    default int getFailureIndex() {
        return -1;
    }

    /**
     * Utility for formatting a plan as a string.
     */
    final class Printer {
        private static final String CONNECTING_INDENT = "\u2502   "; // "| "
        private static final String EMPTY_INDENT = "    ";
        private static final String CHILD = "\u251c\u2500\u2500 "; // "|-- "
        private static final String LAST_CHILD = "\u2514\u2500\u2500 "; // "`-- "

        final Map<String, String> abbreviations = new HashMap<>();

        /**
         * Prints {@code plan} to {@code buf}.
         *
         * @return {@code buf}
         */
        public <T> Formatter printTo(PhasePlan<T> plan, Formatter buf) {
            return printPlan("", plan, buf);
        }

        /**
         * Prints {@code plan} to a string and returns it.
         */
        public <T> String toString(PhasePlan<T> plan) {
            return printPlan("", plan, new Formatter()).toString();
        }

        @SuppressWarnings("unchecked")
        private <T> Formatter printPlan(String indent, PhasePlan<T> plan, Formatter buf) {
            int index = 0;
            for (Iterator<T> iter = plan.getPhases().iterator(); iter.hasNext();) {
                T phase = iter.next();
                String className = plan.getPhaseName(phase);
                boolean hasNext = iter.hasNext();
                buf.format("%s%s%s", indent, hasNext ? Printer.CHILD : Printer.LAST_CHILD, abbreviate(className));

                String graphState = plan.getGraphStateDiff(index);
                if (graphState != null) {
                    buf.format(" %s", graphState);
                }

                if (plan.getFailureIndex() == index) {
                    buf.format("  /* THE FAILURE OCCURRED DURING THIS PHASE */");
                }

                buf.format("%n");

                if (phase instanceof PhasePlan) {
                    PhasePlan<T> subPlan = (PhasePlan<T>) phase;
                    String nextLevel = hasNext ? CONNECTING_INDENT : EMPTY_INDENT;
                    printPlan(indent + nextLevel, subPlan, buf);
                }

                index++;
            }
            return buf;
        }

        private static int firstCapitalAfterPeriod(String s) {
            for (int i = 1; i < s.length(); i++) {
                if (s.charAt(i - 1) == '.' && Character.isUpperCase(s.charAt(i))) {
                    return i;
                }
            }
            return 0;
        }

        private String abbreviate(String className) {
            String abbreviation = abbreviations.get(className);
            if (abbreviation == null) {
                int simpleClassNameStart = firstCapitalAfterPeriod(className);
                String simpleClassName = className.substring(simpleClassNameStart);
                String packageName = simpleClassNameStart != 0 ? className.substring(0, simpleClassNameStart - 1) : "";
                if (abbreviations.values().contains(simpleClassName)) {
                    abbreviation = simpleClassName + " [" + packageName + "]";
                } else {
                    abbreviation = simpleClassName;
                }
                abbreviations.put(className, abbreviation);
            }
            return abbreviation;
        }
    }
}
