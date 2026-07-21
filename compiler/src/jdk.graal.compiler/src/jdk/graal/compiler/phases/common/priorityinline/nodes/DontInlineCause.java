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
package jdk.graal.compiler.phases.common.priorityinline.nodes;

public enum DontInlineCause {
    Unspecified("unspecified", "the reason for not inlining is unspecified."),
    NotUsedForInlining("not-used-for-inlining", "inlining this method is not allowed."),
    DirectedDontInline("directed-dont-inline", "the callsite is excluded by a directed dont-inline rule."),
    Indirect("indirect", "call is indirect."),
    NotWithinBudget("not-within-budget", "budget was too small to inline this callsite."),
    CostBenefit("cost-benefit", "not worth inlining according to the cost-benefit analysis."),
    CantSpeculate("speculation-not-allowed", "We can't speculate at this position due to previous deopts");

    private String shortDescription;
    private String longDescription;

    DontInlineCause(String shortDescription, String longDescription) {
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
    }

    public String shortDescription() {
        return shortDescription;
    }

    public String longDescription() {
        return longDescription;
    }
}
