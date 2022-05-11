/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.matching.tree;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.matching.optimization.OptimizationMatching;
import org.graalvm.bisect.matching.optimization.SetBasedOptimizationMatcher;

/**
 * Creates a matching of optimization trees by flattening them to lists of optimization.
 */
public class FlatTreeMatcher implements TreeMatcher {
    final SetBasedOptimizationMatcher optimizationMatcher = new SetBasedOptimizationMatcher();

    /**
     * Creates a matching of optimization trees by flattening them to lists of optimization.
     * @param method1 the method from the first experiment
     * @param method2 the method from the second experiment
     * @return a description of matched and extra optimizations
     */
    @Override
    public FlatTreeMatching match(ExecutedMethod method1, ExecutedMethod method2) {
        OptimizationMatching optimizationMatching = optimizationMatcher.match(
                method1.getOptimizationsRecursive(),
                method2.getOptimizationsRecursive()
        );
        return new FlatTreeMatching(optimizationMatching);
    }
}
