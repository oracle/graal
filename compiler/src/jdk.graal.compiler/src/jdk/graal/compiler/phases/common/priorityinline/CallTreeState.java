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
package jdk.graal.compiler.phases.common.priorityinline;

public class CallTreeState {
    private boolean inlinedSinceLastExpansion;
    private boolean expandedSinceLastRound;
    private int numMethodsInlined;
    private int priorityInliningPhaseRound;

    public CallTreeState() {
        this.inlinedSinceLastExpansion = true;
        this.expandedSinceLastRound = true;
        this.numMethodsInlined = 0;
        this.priorityInliningPhaseRound = 0;
    }

    public boolean hasInlinedSinceLastExpansion() {
        return inlinedSinceLastExpansion;
    }

    public void setInlinedSinceLastExpansion(boolean inlinedSinceLastExpansion) {
        this.inlinedSinceLastExpansion = inlinedSinceLastExpansion;
    }

    public boolean hasExpandedSinceLastRound() {
        return expandedSinceLastRound;
    }

    public void setHasExpandedSinceLastRound(boolean expandedSinceLastRound) {
        this.expandedSinceLastRound = expandedSinceLastRound;
    }

    public int numMethodsInlined() {
        return numMethodsInlined;
    }

    public void incNumMethodsInlined() {
        numMethodsInlined++;
    }

    public boolean callTreeModifiedInLastRound() {
        return inlinedSinceLastExpansion || expandedSinceLastRound;
    }

    public void incRound() {
        priorityInliningPhaseRound++;
    }

    public int round() {
        return priorityInliningPhaseRound;
    }
}
