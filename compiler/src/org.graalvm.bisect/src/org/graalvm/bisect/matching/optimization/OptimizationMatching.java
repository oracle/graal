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
package org.graalvm.bisect.matching.optimization;

import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.Optimization;

import java.util.List;

/**
 * Describes a matching of optimizations between two compilations of the same method in two experiments.
 */
public interface OptimizationMatching {
    /**
     * Gets a list of optimizations with experiment IDs that were not matched with any other optimization from the other
     * method.
     * @return a list of extra optimizations
     */
    List<ExtraOptimization> getExtraOptimizations();

    /**
     * Gets a list of optimization that were present in both compiled methods.
     * @return a list of optimizations present in both compiled methods
     */
    List<Optimization> getMatchedOptimizations();

    /**
     * Represents an optimization that was not matched with any other optimization in the other compiled method.
     */
    class ExtraOptimization {
        /**
         * Gets the ID of the experiment to which this optimization belongs.
         * @return the ID of the experiment of this optimization
         */
        public ExperimentId getExperimentId() {
            return experimentId;
        }

        public Optimization getOptimization() {
            return optimization;
        }

        private final ExperimentId experimentId;
        private final Optimization optimization;

        ExtraOptimization(ExperimentId experimentId, Optimization optimization) {
            this.experimentId = experimentId;
            this.optimization = optimization;
        }

        @Override
        public int hashCode() {
            return experimentId.hashCode() + optimization.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ExtraOptimization)) {
                return false;
            }
            ExtraOptimization other = (ExtraOptimization) object;
            return experimentId == other.experimentId && optimization.equals(other.optimization);
        }
    }
}
