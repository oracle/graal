/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Substitutions for {@link java.lang.System} methods.
 */
@ClassSubstitution(java.lang.System.class)
public class SystemSubstitutions {

    public static final ForeignCallDescriptor JAVA_TIME_MILLIS = new ForeignCallDescriptor("javaTimeMillis", long.class);
    public static final ForeignCallDescriptor JAVA_TIME_NANOS = new ForeignCallDescriptor("javaTimeNanos", long.class);

    @MacroSubstitution(macro = ArrayCopyNode.class)
    public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length);

    @MethodSubstitution
    public static long currentTimeMillis() {
        return callLong(JAVA_TIME_MILLIS);
    }

    @MethodSubstitution
    public static long nanoTime() {
        return callLong(JAVA_TIME_NANOS);
    }

    @MacroSubstitution(macro = SystemIdentityHashCodeNode.class)
    @MethodSubstitution
    public static int identityHashCode(Object x) {
        if (probability(NOT_FREQUENT_PROBABILITY, x == null)) {
            return 0;
        }

        return computeHashCode(x);
    }

    @NodeIntrinsic(value = ForeignCallNode.class, setStampFromReturnType = true)
    public static long callLong(@ConstantNodeParameter ForeignCallDescriptor descriptor) {
        if (descriptor == JAVA_TIME_MILLIS) {
            return System.currentTimeMillis();
        }
        assert descriptor == JAVA_TIME_NANOS;
        return System.nanoTime();
    }
}
