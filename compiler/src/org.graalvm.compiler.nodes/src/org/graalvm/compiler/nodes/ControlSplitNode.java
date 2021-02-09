/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.ProfilingInfo;

/**
 * The {@code ControlSplitNode} is a base class for all instructions that split the control flow
 * (i.e., have more than one successor).
 */
@NodeInfo
public abstract class ControlSplitNode extends FixedNode {
    public static final NodeClass<ControlSplitNode> TYPE = NodeClass.create(ControlSplitNode.class);

    protected ProfileSource profileSource;

    protected ControlSplitNode(NodeClass<? extends ControlSplitNode> c, Stamp stamp, ProfileSource profileSource) {
        super(c, stamp);
        this.profileSource = profileSource;
    }

    /**
     * The source of this node's knowledge about its branch probabilities (also used for loop
     * frequencies), in decreasing order of trust. Information injected via annotations is most
     * trusted, followed by information from {@link ProfilingInfo#isMature()} profiling info. All
     * other sources of probabilities/frequencies are unknown.
     */
    public enum ProfileSource {
        /**
         * The profiling information was injected via annotations, or in some other way during
         * compilation based on domain knowledge (e.g., exception paths are very improbable).
         */
        INJECTED,
        /**
         * The profiling information comes from mature profiling information.
         */
        PROFILED,
        /**
         * The profiling information comes from immature profiling information or some unknown
         * source.
         */
        UNKNOWN;

        /**
         * Combine the sources of knowledge about profiles. This returns the most trusted source of
         * the two, e.g., it treats a combination of profiled and unknown information as profiled
         * overall.
         *
         * For example, when deriving a loop's frequency from a trusted exit probability, we want to
         * treat the derived frequency as trusted as well, even if the loop contains some other
         * control flow with unknown branch probabilities.
         */
        public ProfileSource combine(ProfileSource other) {
            if (this.ordinal() < other.ordinal()) {
                return this;
            } else {
                return other;
            }
        }

        public static boolean isTrusted(ProfileSource source) {
            return source == INJECTED || source == PROFILED;
        }
    }

    public abstract double probability(AbstractBeginNode successor);

    /**
     * Attempts to set the probability for the given successor to the passed value (which has to be
     * in the range of 0.0 and 1.0). Returns whether setting the probability was successful. When
     * successful, sets the source of the knowledge about probabilities to {@code profileSource}.
     */
    public abstract boolean setProbability(AbstractBeginNode successor, double value, ProfileSource profileSource);

    /**
     * Primary successor of the control split. Data dependencies on the node have to be scheduled in
     * the primary successor. Returns null if data dependencies are not expected.
     *
     * @return the primary successor
     */
    public abstract AbstractBeginNode getPrimarySuccessor();

    /**
     * Returns the number of successors.
     */
    public abstract int getSuccessorCount();

    /**
     * Returns the source of this node's knowledge about its branch probabilities.
     */
    public ProfileSource getProfileSource() {
        return profileSource;
    }

    /**
     * Sets the source of this node's knowledge about its branch probabilities.
     */
    public void setProfileSource(ProfileSource profileSource) {
        this.profileSource = profileSource;
    }
}
