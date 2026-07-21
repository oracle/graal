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

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;

public class DomainSpecificTuningPolicy extends TuningPolicy {

    public static final String METHOD_FORMAT = "%H.%n";

    private abstract static class Rule extends TuningPolicy {
    }

    private static class BoostRule extends Rule {
        private final double cutoffAmplifier;
        private final double parentAmplifier;
        private final boolean mustInline;

        BoostRule(double cutoffAmplifier, double parentAmplifier, boolean mustInline) {
            this.cutoffAmplifier = cutoffAmplifier;
            this.parentAmplifier = parentAmplifier;
            this.mustInline = mustInline;
        }

        @Override
        public double cutoffLocalBenefitAmplifier(CutoffNode node) {
            return cutoffAmplifier;
        }

        @Override
        public double parentLocalBenefitAmplifier(ParentNode node) {
            return parentAmplifier;
        }

        @Override
        public boolean mustInline(ParentNode node) {
            return mustInline;
        }
    }

    private static final EconomicMap<String, Rule> namedRules;

    static {
        namedRules = EconomicMap.create();
        namedRules.put("scala.Tuple2.equals", new BoostRule(50.0, 1.0, true));
        namedRules.put("scala.Tuple3.equals", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.Tuple3.hashCode", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.equals", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.equals2", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.equalsNumObject", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.equalsNumNum", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.equalsCharObject", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.equalsNumChar", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.runtime.BoxesRunTime.typeCode", new BoostRule(50.0, 1.0, false));
        namedRules.put("java.lang.Number.intValue", new BoostRule(50.0, 1.0, true));
        namedRules.put("scala.reflect.ClassTag.newArray", new BoostRule(50.0, 1.0, false));
        namedRules.put("scala.reflect.ClassTag$class.newArray", new BoostRule(50.0, 1.0, false));
    }

    @Override
    public double cutoffLocalBenefitAmplifier(CutoffNode node) {
        final String name = node.targetMethod().format(METHOD_FORMAT);
        final Rule rule = namedRules.get(name);
        if (rule != null) {
            return rule.cutoffLocalBenefitAmplifier(node);
        }
        return 1.0;
    }

    @Override
    public double parentLocalBenefitAmplifier(ParentNode node) {
        if (node.targetMethod() == null) {
            return 1.0;
        }
        final String name = node.targetMethod().format(METHOD_FORMAT);
        final Rule rule = namedRules.get(name);
        if (rule != null) {
            return rule.parentLocalBenefitAmplifier(node);
        }
        return 1.0;
    }

    @Override
    public boolean mustInline(ParentNode node) {
        return false;
    }
}
