/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jca;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;

/**
 * Tracks provider-registration intent separately from the reflection metadata that realizes it,
 * as required by §FS-002-security-providers.8.9.1.
 *
 * <p>
 * A complete plan makes a provider JDK-constructible and retains its service catalog; an
 * instantiated provider records only the JCE verification needed for application-supplied use.
 * Iterative processing ensures signals discovered during analysis are handled before analysis
 * completes.
 */
final class SecurityProviderRegistrationPlanner {
    record RegistrationPlan(RuntimeDynamicAccessMetadata registrationMetadata, RuntimeDynamicAccessMetadata completeMetadata) {
        RegistrationPlan {
            if (registrationMetadata == null && completeMetadata != null) {
                registrationMetadata = completeMetadata;
            }
        }
    }

    private final Set<Class<?>> candidates = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> instantiatedCandidates = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> forcedCompletePlans = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, RuntimeDynamicAccessMetadata> completedMetadata = new ConcurrentHashMap<>();
    private final Map<Class<?>, RuntimeDynamicAccessMetadata> verificationMetadata = new ConcurrentHashMap<>();
    private final AtomicBoolean changed = new AtomicBoolean();

    void addCandidate(Class<?> providerClass) {
        if (candidates.add(providerClass)) {
            changed.set(true);
        }
    }

    void addInstantiatedCandidate(Class<?> providerClass) {
        addCandidate(providerClass);
        if (instantiatedCandidates.add(providerClass)) {
            changed.set(true);
        }
    }

    void requestCompleteProvider(Class<?> providerClass) {
        addCandidate(providerClass);
        if (forcedCompletePlans.add(providerClass)) {
            changed.set(true);
        }
    }

    boolean processNewProviders(Function<Class<?>, RegistrationPlan> planProvider,
                    BiConsumer<Class<?>, RuntimeDynamicAccessMetadata> includeProvider,
                    BiConsumer<Class<?>, RuntimeDynamicAccessMetadata> registerVerification) {
        boolean discoveredCandidate = changed.getAndSet(false);
        boolean processed = false;
        for (Class<?> providerClass : candidates) {
            RegistrationPlan plan = planProvider.apply(providerClass);
            RuntimeDynamicAccessMetadata complete = forcedCompletePlans.contains(providerClass)
                            ? RuntimeDynamicAccessMetadata.alwaysAvailable(false)
                            : plan != null ? plan.completeMetadata() : null;
            RuntimeDynamicAccessMetadata registration = plan != null ? plan.registrationMetadata() : null;
            RuntimeDynamicAccessMetadata verification = instantiatedCandidates.contains(providerClass)
                            ? RuntimeDynamicAccessMetadata.merge(registration, RuntimeDynamicAccessMetadata.alwaysAvailable(false))
                            : registration;
            if (complete != null && completedMetadata.put(providerClass, complete) != complete) {
                includeProvider.accept(providerClass, complete);
                processed = true;
            }
            if (verification != null && verificationMetadata.put(providerClass, verification) != verification) {
                registerVerification.accept(providerClass, verification);
                processed = true;
            }
        }
        return processed || discoveredCandidate;
    }
}
