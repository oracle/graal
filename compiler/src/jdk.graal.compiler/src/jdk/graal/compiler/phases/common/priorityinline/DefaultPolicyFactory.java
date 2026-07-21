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

import java.util.ArrayList;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.tuning.BytecodeInterpreterTuningPolicy;
import jdk.graal.compiler.phases.common.priorityinline.tuning.CompositeTuningPolicy;
import jdk.graal.compiler.phases.common.priorityinline.tuning.DomainSpecificTuningPolicy;
import jdk.graal.compiler.phases.common.priorityinline.tuning.NoTuningPolicy;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(PolicyFactory.class)
public class DefaultPolicyFactory implements PolicyFactory {

    @Override
    public Expander.Policy createExpanderPolicy(OptionValues options, HighTierContext context) {
        return new Expander.DefaultPolicy();
    }

    @Override
    public Inliner.Policy createInlinerPolicy(OptionValues options) {
        return new Inliner.DefaultPolicy();
    }

    @Override
    public TuningPolicy createTuningPolicy(OptionValues options) {
        final ArrayList<TuningPolicy> tuningPolicies = new ArrayList<>();
        final String policyNames = PriorityInliningPhase.Options.PriorityInliningTuningPolicy.getValue(options);
        for (String policyName : policyNames.split(",")) {
            switch (policyName) {
                case "DomainSpecific":
                    tuningPolicies.add(new DomainSpecificTuningPolicy());
                    break;
                case "BytecodeInterpreter":
                    tuningPolicies.add(new BytecodeInterpreterTuningPolicy());
                    break;
                case "None":
                case "":
                    tuningPolicies.add(new NoTuningPolicy());
                    break;
                default:
                    throw GraalError.shouldNotReachHere("Unknown policy specified: " + policyName); // ExcludeFromJacocoGeneratedReport
            }
        }
        return new CompositeTuningPolicy(tuningPolicies);
    }
}
