/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import org.graalvm.compiler.core.phases.fuzzing.PhasePlanSerializer;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesProvider;

import jdk.vm.ci.code.Architecture;

/**
 * {@link SuitesProvider} that provides as default {@link Suites} the suites that are loaded from a
 * given file.
 */
public class HotSpotLoadedSuitesProvider extends HotSpotSuitesProvider {
    private final HotSpotSuitesProvider provider;
    private final String suitesFile;

    public HotSpotLoadedSuitesProvider(HotSpotSuitesProvider provider, String suitesFile) {
        super(provider.defaultSuitesCreator, provider.config, provider.runtime);

        this.provider = provider;
        this.suitesFile = suitesFile;
    }

    @Override
    public Suites getDefaultSuites(OptionValues options, Architecture arch) {
        return PhasePlanSerializer.loadPhasePlan(suitesFile, provider.getDefaultSuites(options, arch));
    }
}
