/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.graph.MemoryKillMarker;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNodeInterface;
import org.graalvm.word.LocationIdentity;

/**
 * This interface marks nodes that kill a set of memory locations represented by
 * {@linkplain LocationIdentity} (i.e. change a value at one or more locations that belong to these
 * location identities). This does not only include real memory kills like subclasses of
 * {@linkplain FixedNode} that, e.g., write a memory location, but also conceptual memory kills,
 * i.e., nodes in the memory graph that mark the last accesses to such a location, like a
 * {@linkplain MemoryPhiNode} node.
 */
public interface MemoryKill extends ValueNodeInterface, MemoryKillMarker {

    /**
     * Determine if the given node represents a {@link MemoryKill} in Graal IR. A node is a memory
     * kill if it implements the memory kill API and actually kills a location identity other than
     * {@link NoLocation}.
     */
    static boolean isMemoryKill(Node n) {
        // Single memory kills always have to return a killed location identity. Multi-memory kills
        // however can return a zero length array and thus not kill any location. This is handy to
        // implement cases where nodes are only memory kills based on a dynamic property.
        if (isSingleMemoryKill(n)) {
            LocationIdentity killedLocation = asSingleMemoryKill(n).getKilledLocationIdentity();
            return !killedLocation.equals(NO_LOCATION);
        } else if (isMultiMemoryKill(n)) {
            LocationIdentity[] killedLocations = asMultiMemoryKill(n).getKilledLocationIdentities();
            if (killedLocations.length == 0) {
                // no memory kill
                return false;
            } else if (killedLocations.length == 1) {
                // no memory kill
                return !killedLocations[0].equals(NO_LOCATION);
            } else {
                // definitely a memory kill killing multiple locations
                return true;
            }
        }

        return false;
    }

    static boolean isSingleMemoryKill(Node n) {
        return n instanceof SingleMemoryKill;
    }

    static boolean isMultiMemoryKill(Node n) {
        return n instanceof MultiMemoryKill;
    }

    static SingleMemoryKill asSingleMemoryKill(Node n) {
        assert isSingleMemoryKill(n);
        return (SingleMemoryKill) n;
    }

    static MultiMemoryKill asMultiMemoryKill(Node n) {
        assert isMultiMemoryKill(n);
        return (MultiMemoryKill) n;
    }

    /**
     * Special {@link LocationIdentity} used to express that this location is never killing
     * anything, thus it is {@link LocationIdentity#isImmutable()} {@code true} and should only be
     * used very carefully.
     */
    LocationIdentity NO_LOCATION = new NoLocation();

    class NoLocation extends LocationIdentity {
        private NoLocation() {
            // only a single instance of this should ever live
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public String toString() {
            return "NO_LOCATION";
        }
    }

    /**
     * Special {@link LocationIdentity} used to express that a {@link MultiMemoryKill} actually does
     * not kill any location. Using this location can be handy to express that a memory kill only
     * kills under certain, parameterized, conditions. Should be used with caution.
     */
    LocationIdentity[] MULTI_KILL_NO_LOCATION = new LocationIdentity[]{};

}
