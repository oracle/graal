/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes.dispatch;

import java.util.ArrayList;

import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Object that holds the type and method used for the dispatch. The <code>needsMethodDispatch</code>
 * field denotes that a method-based dispatch is required, in which case
 * <code>dispatchedMethod</code> must be non-null.
 */
public class DispatchInfo implements Comparable<DispatchInfo> {
    public static DispatchInfo match(ArrayList<DispatchInfo> dispatches, ResolvedJavaMethod targetMethod, InliningProvider inliningProvider) {
        for (DispatchInfo dispatchInfo : dispatches) {
            if (inliningProvider.isSameMethodForDevirtualizationCheck(dispatchInfo.dispatchedMethod, targetMethod)) {
                return dispatchInfo;
            }
        }
        return null;
    }

    public double probability;
    public ResolvedJavaType dispatchedType;
    public ResolvedJavaMethod dispatchedMethod;
    public boolean needsMethodDispatch;

    @Override
    public int compareTo(DispatchInfo that) {
        if (this.probability > that.probability) {
            return -1;
        } else if (this.probability < that.probability) {
            return 1;
        } else {
            return 0;
        }
    }
}
