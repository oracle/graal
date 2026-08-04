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
package jdk.graal.compiler.duplication.phases.simulation;

import jdk.graal.compiler.nodes.FixedNode;

public class DuplicationDecision {
    private final FixedNode target;
    private final int codeSize;
    private final double benefit;
    private final boolean requiresReadElimination;

    public DuplicationDecision(FixedNode target, int codeSize, double benefit, boolean requiresReadElimination) {
        this.target = target;
        this.codeSize = codeSize;
        this.benefit = benefit;
        this.requiresReadElimination = requiresReadElimination;
    }

    public boolean requiresReadElimination() {
        return requiresReadElimination;
    }

    public FixedNode getTarget() {
        return target;
    }

    public int getCodeSize() {
        return codeSize;
    }

    public double getBenefit() {
        return benefit;
    }

    @Override
    public String toString() {
        return "Target:" + target + " cost:" + codeSize + " benefit:" + benefit;
    }

}
