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

import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;

import com.oracle.svm.core.util.UserError;

/**
 * Retirement boundary for deprecated security-provider options and service-driven inclusion.
 */
final class LegacySecurityProviderCompatibility {
    private LegacySecurityProviderCompatibility() {
    }

    static void registerAdditionalProviders(BeforeAnalysisAccess access, Consumer<Class<?>> registerProvider) {
        for (String value : SecurityServicesFeature.Options.AdditionalSecurityProviders.getValue().values()) {
            for (String className : value.split(",")) {
                Class<?> providerClass = access.findClassByName(className);
                UserError.guarantee(providerClass != null,
                                "Manually marked security provider class doesn't exist: %s. Make sure that the class name is correct and that the class is on the image builder classpath.", className);
                registerProvider.accept(providerClass);
            }
        }
    }

    static Iterable<Class<?>> additionalServiceTypes(BeforeAnalysisAccess access, Iterable<Class<?>> knownServices) {
        EconomicSet<Class<?>> services = EconomicSet.create(knownServices);
        for (String value : SecurityServicesFeature.Options.AdditionalSecurityServiceTypes.getValue().values()) {
            for (String className : value.split(",")) {
                Class<?> serviceClass = access.findClassByName(className);
                UserError.guarantee(serviceClass != null, "Unable to find additional security service class %s", className);
                services.add(serviceClass);
            }
        }
        return services;
    }
}
