/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect.proxy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;

/**
 * @see ProxyRenamingSubstitutionProcessor
 */
@AutomaticallyRegisteredFeature
final class StableProxyNameFeature implements InternalFeature {
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        assert checkProxyNames(((AfterAnalysisAccessImpl) access).getUniverse().getTypes());
    }

    private static boolean checkProxyNames(List<AnalysisType> types) {
        if (!SubstrateUtil.assertionsEnabled()) {
            throw new AssertionError("Expensive check: should only run with assertions enabled.");
        }
        /* There should be no random proxy type names visible to the analysis. */
        if (types.stream().anyMatch(type -> ProxyRenamingSubstitutionProcessor.isProxyType(type) && type.getWrapped().getClass() != ProxySubstitutionType.class)) {
            throw new AssertionError("All proxies should be substituted.");
        }

        /* Proxy names should be unique. */
        Set<String> proxyNames = new HashSet<>();
        types.stream()
                        .filter(ProxyRenamingSubstitutionProcessor::isProxyType)
                        .map(AnalysisType::getName)
                        .forEach(name -> {
                            if (proxyNames.contains(name)) {
                                throw new AssertionError("Duplicate proxy name: " + name);
                            }
                            proxyNames.add(name);
                        });
        return true;
    }
}
