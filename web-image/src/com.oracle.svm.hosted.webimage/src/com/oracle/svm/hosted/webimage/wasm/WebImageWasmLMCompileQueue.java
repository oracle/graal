/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm;

import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.code.WebImageCompileQueue;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.phases.common.AddressLoweringPhase;
import jdk.graal.compiler.phases.tiers.Suites;

public class WebImageWasmLMCompileQueue extends WebImageCompileQueue {
    public WebImageWasmLMCompileQueue(FeatureHandler featureHandler, HostedUniverse hUniverse, RuntimeConfiguration runtimeConfiguration, DebugContext debug) {
        super(featureHandler, hUniverse, runtimeConfiguration, debug);
    }

    @Override
    protected Suites createRegularSuites() {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();
        Suites suites = super.createRegularSuites();

        suites.getLowTier().replacePlaceholder(AddressLoweringPhase.class, backend.newAddressLoweringPhase(backend.getCodeCache()));

        featureHandler.forEachGraalFeature(feature -> feature.registerGraalPhases(backend.getProviders(), suites, true));
        return suites;
    }
}
