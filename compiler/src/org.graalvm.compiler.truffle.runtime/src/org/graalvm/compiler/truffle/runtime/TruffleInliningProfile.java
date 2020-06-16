/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

public class TruffleInliningProfile {

    private final OptimizedDirectCallNode callNode;
    private final int nodeCount;
    private final int deepNodeCount;
    private final double frequency;
    private final int recursions;

    private TruffleInliningPolicy.FailedReason failedReason;
    private int queryIndex = -1;
    private double score;

    private TruffleInliningProfile cached;

    public TruffleInliningProfile(OptimizedDirectCallNode callNode, int nodeCount, int deepNodeCount, double frequency, int recursions) {
        this.callNode = callNode;
        this.nodeCount = nodeCount;
        this.deepNodeCount = deepNodeCount;
        this.frequency = frequency;
        this.recursions = recursions;
    }

    public boolean isCached() {
        return cached != null;
    }

    public TruffleInliningProfile getCached() {
        return cached;
    }

    public void setCached(TruffleInliningProfile cached) {
        this.cached = cached;
    }

    public int getRecursions() {
        return recursions;
    }

    public OptimizedDirectCallNode getCallNode() {
        return callNode;
    }

    public int getCallSites() {
        return callNode.getKnownCallSiteCount();
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getScore() {
        return score;
    }

    public TruffleInliningPolicy.FailedReason getFailedReason() {
        return failedReason;
    }

    public void setQueryIndex(int queryIndex) {
        this.queryIndex = queryIndex;
    }

    public int getQueryIndex() {
        return queryIndex;
    }

    public void setFailedReason(TruffleInliningPolicy.FailedReason reason) {
        this.failedReason = reason;
    }

    public boolean isForced() {
        return callNode.isInliningForced();
    }

    public double getFrequency() {
        return frequency;
    }

    public int getDeepNodeCount() {
        return deepNodeCount;
    }

    private String formatReason() {
        if (failedReason == null) {
            return null;
        } else {
            return failedReason.format(callNode.getCallTarget());
        }
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ASTSize", String.format("%5d/%5d", nodeCount, deepNodeCount));
        properties.put("frequency", String.format("%8.4f", getFrequency()));
        properties.put("score", String.format("%8.4f", getScore()));
        properties.put(String.format("index=%3d, force=%s, callSites=%2d", queryIndex, (isForced() ? "Y" : "N"), getCallSites()), "");
        properties.put("reason", cached == null ? formatReason() : formatReason() + " (cached)");
        return properties;
    }
}
