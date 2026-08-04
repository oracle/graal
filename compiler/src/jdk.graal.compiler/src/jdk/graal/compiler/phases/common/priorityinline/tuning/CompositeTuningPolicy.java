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
package jdk.graal.compiler.phases.common.priorityinline.tuning;

import java.util.List;

import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;

public class CompositeTuningPolicy extends TuningPolicy {
    private final List<TuningPolicy> policies;

    public CompositeTuningPolicy(List<TuningPolicy> policies) {
        this.policies = policies;
    }

    @Override
    public double cutoffLocalBenefitAmplifier(CutoffNode node) {
        double amplifier = 1.0;
        for (TuningPolicy policy : policies) {
            amplifier *= policy.cutoffLocalBenefitAmplifier(node);
        }
        return amplifier;
    }

    @Override
    public double parentLocalBenefitAmplifier(ParentNode node) {
        double amplifier = 1.0;
        for (TuningPolicy policy : policies) {
            amplifier *= policy.parentLocalBenefitAmplifier(node);
        }
        return amplifier;
    }

    @Override
    public boolean mustInline(ParentNode node) {
        for (TuningPolicy policy : policies) {
            if (policy.mustInline(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double relativeBenefitThresholdMultiplier(CutoffNode node) {
        double multiplier = 1.0;
        for (TuningPolicy policy : policies) {
            multiplier *= policy.relativeBenefitThresholdMultiplier(node);
        }
        return multiplier;
    }

    @Override
    public double callGraphSizePenaltyMultiplier(CutoffNode node) {
        double multiplier = 1.0;
        for (TuningPolicy policy : policies) {
            multiplier *= policy.callGraphSizePenaltyMultiplier(node);
        }
        return multiplier;
    }

    @Override
    public double smallRootIrPenaltyMultiplier(CutoffNode node) {
        double multiplier = 1.0;
        for (TuningPolicy policy : policies) {
            multiplier *= policy.smallRootIrPenaltyMultiplier(node);
        }
        return multiplier;
    }

    @Override
    public double largeChildrenCountPenaltyMultiplier(CutoffNode node) {
        double multiplier = 1.0;
        for (TuningPolicy policy : policies) {
            multiplier *= policy.largeChildrenCountPenaltyMultiplier(node);
        }
        return multiplier;
    }

    @Override
    public double defaultMinimumFrequencyForExpansionMultiplier(CutoffNode node) {
        double multiplier = 1.0;
        for (TuningPolicy policy : policies) {
            multiplier *= policy.defaultMinimumFrequencyForExpansionMultiplier(node);
        }
        return multiplier;
    }

    @Override
    public double rootSizePenaltyMultiplier(CutoffNode node) {
        double multiplier = 1.0;
        for (TuningPolicy policy : policies) {
            multiplier *= policy.rootSizePenaltyMultiplier(node);
        }
        return multiplier;
    }
}
