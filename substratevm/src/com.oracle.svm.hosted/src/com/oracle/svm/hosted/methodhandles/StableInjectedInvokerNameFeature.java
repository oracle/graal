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
package com.oracle.svm.hosted.methodhandles;

import static com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor.INJECTED_INVOKER_CLASS_NAME_SUBSTRING;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

/**
 * @see InjectedInvokerRenamingSubstitutionProcessor
 */
@AutomaticallyRegisteredFeature
final class StableInjectedInvokerNameFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        InjectedInvokerRenamingSubstitutionProcessor injectedInvokerSubst = new InjectedInvokerRenamingSubstitutionProcessor();
        access.registerSubstitutionProcessor(injectedInvokerSubst);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        assert checkInjectedInvokerNames(((AfterAnalysisAccessImpl) access).getUniverse().getTypes());
    }

    private static boolean checkInjectedInvokerNames(List<AnalysisType> types) {
        if (!SubstrateUtil.assertionsEnabled()) {
            throw new AssertionError("Expensive check: should only run with assertions enabled.");
        }
        /* There should be no random injected invoker names visible to the analysis. */
        if (types.stream().anyMatch(type -> InjectedInvokerRenamingSubstitutionProcessor.isInjectedInvokerType(type) && type.getWrapped().getClass() != InjectedInvokerSubstitutionType.class)) {
            throw new AssertionError("All injected invoker should be substituted.");
        }

        /* Injected invoker names should be unique. */
        Set<String> injectedInvokerNames = new HashSet<>();
        types.stream()
                        .map(AnalysisType::getName)
                        .filter(x -> x.contains(INJECTED_INVOKER_CLASS_NAME_SUBSTRING))
                        .forEach(name -> {
                            if (injectedInvokerNames.contains(name)) {
                                throw new AssertionError("Duplicate injected invoker name: " + name);
                            }
                            injectedInvokerNames.add(name);
                        });
        return true;
    }
}
