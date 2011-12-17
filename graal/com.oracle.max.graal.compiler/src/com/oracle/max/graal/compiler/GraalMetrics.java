/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.criutils.*;


/**
 * This class contains a number of fields that collect metrics about compilation, particularly
 * the number of times certain optimizations are performed.
 */
public final class GraalMetrics {
    // Checkstyle: stop
    public int CompiledMethods;
    public int TargetMethods;
    public int LocalValueNumberHits;
    public int ValueMapResizes;
    public int InlinedFinalizerChecks;
    public int InlineForcedMethods;
    public int InlineForbiddenMethods;
    public int InlineConsidered;
    public int InlinePerformed;
    public int InlineUncompiledConsidered;
    public int InlineUncompiledPerformed;
    public int BlocksDeleted;
    public int BytecodesCompiled;
    public int CodeBytesEmitted;
    public int SafepointsEmitted;
    public int ExceptionHandlersEmitted;
    public int DataPatches;
    public int DirectCallSitesEmitted;
    public int IndirectCallSitesEmitted;
    public int LiveHIRInstructions;
    public int LIRInstructions;
    public int LIRVariables;
    public int LIRXIRInstructions;
    public int LIRMoveInstructions;
    public int LSRAIntervalsCreated;
    public int LSRASpills;
    public int LoadConstantIterations;
    public int CodeBufferCopies;
    public int UniqueValueIdsAssigned;
    public int FrameStatesCreated;
    public int FrameStateValuesCreated;
    public int LoopsPeeled;
    public int LoopsInverted;
    public int PartialUsageProbability;
    public int FullUsageProbability;
    public int Rematerializations;
    public int GlobalValueNumberingHits;
    public int ExplicitExceptions;
    public int GuardsHoisted;
    // Checkstyle: resume

    public void print() {
        for (Entry<String, MetricsEntry> m : map.entrySet()) {
            printField(m.getKey(), m.getValue().value);
        }
        printFields(GraalMetrics.class);
    }

    public static class MetricsEntry {
        public int value;

        public void increment() {
            increment(1);
        }

        public void increment(int val) {
            value += val;
        }
    }

    private LinkedHashMap<String, MetricsEntry> map = new LinkedHashMap<String, MetricsEntry>();

    public MetricsEntry get(String name) {
        if (!map.containsKey(name)) {
            map.put(name, new MetricsEntry());
        }
        return map.get(name);
    }

    public void printFields(Class<?> javaClass) {
        final String className = javaClass.getSimpleName();
        TTY.println(className + " {");
        for (final Field field : javaClass.getFields()) {
            printField(field, false);
        }
        TTY.println("}");
    }

    public void printField(final Field field, boolean tabbed) {
        final String fieldName = String.format("%35s", field.getName());
        try {
            String prefix = tabbed ? "" : "    " + fieldName + " = ";
            String postfix = tabbed ? "\t" : "\n";
            if (field.getType() == int.class) {
                TTY.print(prefix + field.getInt(this) + postfix);
            } else if (field.getType() == boolean.class) {
                TTY.print(prefix + field.getBoolean(this) + postfix);
            } else if (field.getType() == float.class) {
                TTY.print(prefix + field.getFloat(this) + postfix);
            } else if (field.getType() == String.class) {
                TTY.print(prefix + field.get(this) + postfix);
            } else if (field.getType() == Map.class) {
                Map<?, ?> m = (Map<?, ?>) field.get(this);
                TTY.print(prefix + printMap(m) + postfix);
            } else {
                TTY.print(prefix + field.get(this) + postfix);
            }
        } catch (IllegalAccessException e) {
            // do nothing.
        }
    }

    private static String printMap(Map<?, ?> m) {
        StringBuilder sb = new StringBuilder();

        List<String> keys = new ArrayList<String>();
        for (Object key : m.keySet()) {
            keys.add((String) key);
        }
        Collections.sort(keys);

        for (String key : keys) {
            sb.append(key);
            sb.append("\t");
            sb.append(m.get(key));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void printField(String fieldName, long value) {
        TTY.print("    " + fieldName + " = " + value + "\n");
    }

    private static void printField(String fieldName, double value) {
        TTY.print("    " + fieldName + " = " + value + "\n");
    }
}

