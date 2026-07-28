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
package com.oracle.svm.hosted;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tracks provider-registration intent separately from reflection metadata emitted to realize it.
 */
final class SecurityProviderRegistrationPlanner {
    enum Source {
        APPLICATION_METADATA,
        APPLICATION_VERIFICATION_METADATA,
        PRESERVE,
        SECURE_RANDOM_PLATFORM,
        LEGACY_ADDITIONAL_PROVIDER,
        LEGACY_SERVICE_REACHABILITY
    }

    private final Set<Class<?>> candidates = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> completed = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> verificationCompleted = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> completePlans = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> legacyGeneratedReflection = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Class<?>, Set<Source>> sources = new ConcurrentHashMap<>();
    private final AtomicBoolean changed = new AtomicBoolean();

    void addCandidate(Class<?> providerClass) {
        if (candidates.add(providerClass)) {
            changed.set(true);
        }
    }

    private void recordSource(Class<?> providerClass, Source source) {
        sources.computeIfAbsent(providerClass, _ -> ConcurrentHashMap.newKeySet()).add(source);
    }

    void requestCompleteProvider(Class<?> providerClass, Source source) {
        addCandidate(providerClass);
        recordSource(providerClass, source);
        if (completePlans.add(providerClass)) {
            changed.set(true);
        }
    }

    void beforeLegacyReflectionRegistration(Class<?> providerClass) {
        recordSource(providerClass, Source.LEGACY_SERVICE_REACHABILITY);
        legacyGeneratedReflection.add(providerClass);
    }

    boolean processNewProviders(Function<Class<?>, Source> signalSource, Consumer<Class<?>> includeProvider, Consumer<Class<?>> registerVerification) {
        boolean discoveredCandidate = changed.getAndSet(false);
        boolean processed = false;
        for (Class<?> providerClass : candidates) {
            Source signal = !legacyGeneratedReflection.contains(providerClass) ? signalSource.apply(providerClass) : null;
            if (signal != null) {
                recordSource(providerClass, signal);
                if (signal == Source.APPLICATION_VERIFICATION_METADATA) {
                    if (verificationCompleted.add(providerClass)) {
                        registerVerification.accept(providerClass);
                        processed = true;
                    }
                } else {
                    completePlans.add(providerClass);
                }
            }
            if (completePlans.contains(providerClass) && completed.add(providerClass)) {
                includeProvider.accept(providerClass);
                processed = true;
            }
        }
        return processed || discoveredCandidate;
    }
}
