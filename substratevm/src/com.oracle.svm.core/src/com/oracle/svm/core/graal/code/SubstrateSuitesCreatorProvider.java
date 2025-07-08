/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2019, Arm Limited. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.core.phases.CommunityCompilerConfiguration;
import jdk.graal.compiler.core.phases.EconomyCompilerConfiguration;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.phases.tiers.SuitesCreator;

public class SubstrateSuitesCreatorProvider {
    private final SuitesCreator suitesCreator;

    private final SuitesCreator firstTierSuitesCreator;

    private final SuitesCreator fallbackSuitesCreator;

    protected static CompilerConfiguration getHostedCompilerConfiguration() {
        if (SubstrateOptions.useEconomyCompilerConfig()) {
            return new EconomyCompilerConfiguration();
        } else {
            return new CommunityCompilerConfiguration();
        }
    }

    protected static CompilerConfiguration getFallbackCompilerConfiguration() {
        return new EconomyCompilerConfiguration();
    }

    protected SubstrateSuitesCreatorProvider(SuitesCreator suitesCreator, SuitesCreator firstTierSuitesCreator, SuitesCreator fallbackSuitesCreator) {
        this.suitesCreator = suitesCreator;
        this.firstTierSuitesCreator = firstTierSuitesCreator;
        this.fallbackSuitesCreator = fallbackSuitesCreator;
    }

    protected SubstrateSuitesCreatorProvider(SuitesCreator suitesCreator, SuitesCreator firstTierSuitesCreator) {
        this(suitesCreator, firstTierSuitesCreator, new SubstrateSuitesCreator(getFallbackCompilerConfiguration()));
    }

    public SubstrateSuitesCreatorProvider() {
        this(new SubstrateSuitesCreator(getHostedCompilerConfiguration()), new SubstrateSuitesCreator(new EconomyCompilerConfiguration()),
                        new SubstrateSuitesCreator(getFallbackCompilerConfiguration()));
    }

    public final SuitesCreator getSuitesCreator() {
        return suitesCreator;
    }

    public final SuitesCreator getFirstTierSuitesCreator() {
        return firstTierSuitesCreator;
    }

    public final SuitesCreator getFallbackSuitesCreator() {
        return fallbackSuitesCreator;
    }
}
