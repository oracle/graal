/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.util.*;

public final class TruffleInliningResult implements Iterable<TruffleInliningProfile> {

    private final OptimizedCallTarget callTarget;
    private final Map<OptimizedCallNode, TruffleInliningProfile> profiles;
    private final Set<TruffleInliningProfile> inlined;
    private final int nodeCount;

    public TruffleInliningResult(OptimizedCallTarget callTarget, List<TruffleInliningProfile> profiles, Set<TruffleInliningProfile> inlined, int nodeCount) {
        this.callTarget = callTarget;
        this.profiles = new HashMap<>();
        for (TruffleInliningProfile profile : profiles) {
            this.profiles.put(profile.getCallNode(), profile);
        }
        this.nodeCount = nodeCount;
        this.inlined = inlined;
    }

    public Map<OptimizedCallNode, TruffleInliningProfile> getProfiles() {
        return profiles;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public OptimizedCallTarget getCallTarget() {
        return callTarget;
    }

    public boolean isInlined(OptimizedCallNode path) {
        return inlined.contains(profiles.get(path));
    }

    public int size() {
        return inlined.size();
    }

    public Iterator<TruffleInliningProfile> iterator() {
        return Collections.unmodifiableSet(inlined).iterator();
    }

    @Override
    public String toString() {
        return inlined.toString();
    }
}
