/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.module.ServicesCatalog;

@AutomaticallyRegisteredImageSingleton
@Platforms(Platform.HOSTED_ONLY.class)
public class ServiceCatalogSupport {
    final ConcurrentHashMap<String, Set<String>> omittedServiceProviders = new ConcurrentHashMap<>();
    boolean sealed;

    public static ServiceCatalogSupport singleton() {
        return ImageSingletons.lookup(ServiceCatalogSupport.class);
    }

    public void seal() {
        sealed = true;
    }

    public void removeServicesFromServicesCatalog(String serviceProvider, Set<String> services) {
        VMError.guarantee(!sealed,
                        "Removing services from a catalog is allowed only before analysis. ServiceCatalogSupport.removeServicesFromServicesCatalog called during or after analysis. ");
        omittedServiceProviders.put(serviceProvider, services);
    }

    @SuppressWarnings("unchecked")
    public void enableServiceCatalogMapTransformer(Feature.BeforeAnalysisAccess access) {
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(ServicesCatalog.class, "map"), (receiver, original) -> {
            VMError.guarantee(sealed);
            ConcurrentHashMap<String, List<ServicesCatalog.ServiceProvider>> map = (ConcurrentHashMap<String, List<ServicesCatalog.ServiceProvider>>) original;
            final ConcurrentHashMap<String, List<ServicesCatalog.ServiceProvider>> res = new ConcurrentHashMap<>();
            map.forEach((key, value) -> {
                if (omittedServiceProviders.containsKey(key)) {
                    var omittedServices = omittedServiceProviders.get(key);
                    List<ServicesCatalog.ServiceProvider> filtered = value.stream()
                                    .filter(v -> !omittedServices.contains(v.providerName()))
                                    .collect(Collectors.toList());
                    res.put(key, filtered);
                } else {
                    res.put(key, value);
                }
            });
            return res;
        });
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(ModuleDescriptor.Provides.class, "providers"), (receiver, original) -> {
            VMError.guarantee(sealed, "Service provider detector must be registered before the analysis starts");
            List<String> providers = (List<String>) original;
            String service = ((ModuleDescriptor.Provides) receiver).service();
            if (omittedServiceProviders.containsKey(service)) {
                var omittedProviders = omittedServiceProviders.get(service);
                providers = providers.stream()
                                .filter(p -> !omittedProviders.contains(p))
                                .collect(Collectors.toList());
            }
            return providers;
        });
    }
}
