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

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.serviceprovider.LibGraalService;

/**
 * Policy factory is a service provider for inlining policies.
 */
@LibGraalService
public interface PolicyFactory {
    /**
     * Gets the priority of this policy. The priority will be used when selecting a policy via
     * service loading. Each factory discovered in a specific service loader call must have a unique
     * priority.
     *
     * @return a priority value where a higher value means a higher priority (like
     *         {@link Thread#getPriority()}
     */
    default int priority() {
        return 0;
    }

    default boolean isAllowed() {
        return true;
    }

    Expander.Policy createExpanderPolicy(OptionValues options, HighTierContext context);

    Inliner.Policy createInlinerPolicy(OptionValues options);

    TuningPolicy createTuningPolicy(OptionValues options);
}
