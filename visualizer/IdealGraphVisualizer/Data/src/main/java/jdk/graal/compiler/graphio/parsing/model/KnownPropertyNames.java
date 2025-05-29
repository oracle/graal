/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing.model;

/**
 * @author odouda
 */
public final class KnownPropertyNames {
    // Prop names from ModelBuilder
    public static final String PROPNAME_HAS_PREDECESSOR = "hasPredecessor"; // NOI18N
    public static final String PROPNAME_IDX = "idx"; // NOI18N
    public static final String PROPNAME_SHORT_NAME = "shortName"; // NOI18N
    public static final String PROPNAME_NAME = "name"; // NOI18N
    public static final String PROPNAME_CLASS = "class"; // NOI18N
    public static final String PROPNAME_BLOCK = "block"; // NOI18N

    // Prop names from Difference
    public static final String PROPNAME_STATE = "state"; // NOI18N

    // Prop name from FileStackProcessor
    public static final String PROPNAME_NODE_SOURCE_POSITION = "nodeSourcePosition"; // NOI18N

    public static final String PROPNAME_DUPLICATE = "_isDuplicate"; // NOI18N
    public static final String PROPNAME_TYPE = "type"; // NOI18N
    public static final String PROPNAME_ID = "id"; // NOI18N
    public static final String PROPNAME_FREQUENCY = "relativeFrequency"; // NOI18N
    public static final String PROPNAME_EXCEPTION_PROBABILITY = "exceptionProbability"; // NOI18N
    public static final String PROPNAME_TRUE_PROBABILITY = "trueSuccessorProbability"; // NOI18N
    public static final String PROPNAME_DUMP_SPEC = "dump_spec"; // NOI18N
    public static final String PROPNAME_FIGURE = "figure"; // NOI18N
    public static final String PROPNAME_CONNECTION_COUNT = "connectionCount"; // NOI18N
    public static final String PROPNAME_PREDECESSOR_COUNT = "predecessorCount"; // NOI18N

    public static final String PROPNAME_VM_UUID = "vm.uuid"; // NOI18N
    public static final String PROPNAME_CMDLINE = "sun.java.command"; // NOI18N
    public static final String PROPNAME_JVM_ARGS = "jvmArguments"; // NOI18N

    public static final String PROPNAME_USER_LABEL = "igv.userLabel";

    private KnownPropertyNames() {
    }
}
