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
package com.oracle.svm.core.jdk;

import java.lang.reflect.Constructor;
import java.security.Provider;
import java.util.Properties;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.api.replacements.Fold;

/// AR-security-providers: Security Provider Architecture
///
/// The security-provider implementation separates build-time policy from run-time enforcement.
/// Reflection metadata, platform rules, and compatibility inputs are build-time registration
/// signals; the reflection registry is not itself the provider-policy model. This architecture
/// implements §FS-security-providers.
///
/// ## 1. Independent Transition Axes
///
/// `SecurityProviderMode` represents provider inclusion and provider-list initialization as
/// independent axes. Hosted components query this mode instead of reading future-default options
/// independently. Substitutions whose implementation differs by mode use build-time predicates, so
/// an application cannot change image-build policy through a run-time system property. This
/// realizes §FS-security-providers.7.
///
/// ## 2. Registration Signals and Plans
///
/// The hosted registration planner records provider candidates together with the provenance of the
/// signal that requests them: application reflection metadata, the platform-owned `SecureRandom`
/// rule, a deprecated provider option, or legacy service-type reachability. It produces an explicit
/// provider plan. Metadata emitted while realizing that plan is an output and is not reinterpreted
/// as a new application signal. This realizes §FS-security-providers.2 and
/// §FS-security-providers.7.3.
///
/// ## 3. Hosted Registration Components
///
/// `SecurityServicesFeature` coordinates the feature lifecycle. The registration planner owns
/// provider intent and iteration-safe candidate processing. The catalog registrar constructs
/// eligible providers and registers their service catalogs. `LegacySecurityProviderCompatibility`
/// owns deprecated options and service-driven inclusion. Provider code accesses reflection
/// registrations through a narrow query rather than the concrete metadata builder.
///
/// ## 4. Run-Time Manifest
///
/// Hosted registration writes one typed manifest entry per provider class. The entry combines
/// whether the JDK may construct the provider with the preserved JCE verification outcome. An
/// application-supplied provider can carry verification information without being marked as
/// JDK-constructible. The manifest is keyed by provider class name, as required by
/// §FS-security-providers.5.3.
///
/// ## 5. Run-Time Access Services
///
/// This class owns the manifest. `BuiltInSecurityProviderLoader` owns JDK aliases and construction,
/// `SecurityProviderRuntimeAccess` owns tracing and missing-registration diagnostics, and
/// `JceProviderVerificationSupport` translates manifest outcomes to the JDK contract. The two
/// provider-list initialization modes share these services.
///
/// ## 6. Service Descriptors
///
/// Explicit provider registration preserves `java.security.Provider` descriptors without treating
/// them as provider-registration signals, independently of provider-list initialization. Legacy
/// suppression remains part of the compatibility policy. This realizes
/// §FS-security-providers.7.2.
///
/// ## 7. Concurrent Analysis
///
/// Provider subtype callbacks add candidates to concurrent collections. A serialized feature pass
/// consumes signals, realizes plans, and requests additional analysis iterations. Callbacks do not
/// schedule iterations directly.
///
/// ## 8. Retirement Boundary
///
/// Deprecated provider options and service-reachability inclusion are confined to
/// `LegacySecurityProviderCompatibility`. Removing compatibility behavior does not change the
/// planner, catalog registrar, run-time manifest, or planned-default substitutions.
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public final class SecurityProviderRuntimeState {
    public enum AcquisitionKind {
        APPLICATION_SUPPLIED_ONLY,
        JDK_CONSTRUCTIBLE
    }

    public record ProviderInfo(AcquisitionKind acquisitionKind, Exception verificationFailure) {
    }

    private final EconomicMap<String, ProviderInfo> providerInfos = ImageHeapMap.create("securityProviderInfos");

    private Properties savedInitialSecurityProperties;
    private Constructor<?> sunECConstructor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SecurityProviderRuntimeState() {
    }

    @Fold
    public static SecurityProviderRuntimeState singleton() {
        return ImageSingletons.lookup(SecurityProviderRuntimeState.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerJdkConstructibleProvider(String providerClassName, Object verificationResult) {
        registerProvider(providerClassName, AcquisitionKind.JDK_CONSTRUCTIBLE, verificationResult);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerApplicationSuppliedProvider(String providerClassName, Object verificationResult) {
        registerProvider(providerClassName, AcquisitionKind.APPLICATION_SUPPLIED_ONLY, verificationResult);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void registerProvider(String providerClassName, AcquisitionKind acquisitionKind, Object verificationResult) {
        Exception verificationFailure = verificationResult instanceof Exception exception ? exception : null;
        AcquisitionKind effectiveAcquisitionKind = acquisitionKind;
        ProviderInfo previous = providerInfos.get(providerClassName);
        if (previous != null && previous.acquisitionKind() == AcquisitionKind.JDK_CONSTRUCTIBLE) {
            effectiveAcquisitionKind = AcquisitionKind.JDK_CONSTRUCTIBLE;
        }
        if (previous != null && previous.verificationFailure() != null) {
            verificationFailure = previous.verificationFailure();
        }
        providerInfos.put(providerClassName, new ProviderInfo(effectiveAcquisitionKind, verificationFailure));
    }

    public ProviderInfo getProviderInfo(Provider provider) {
        return providerInfos.get(provider.getClass().getName());
    }

    public boolean isJdkConstructible(String providerClassName) {
        ProviderInfo info = providerInfos.get(providerClassName);
        return info != null && info.acquisitionKind() == AcquisitionKind.JDK_CONSTRUCTIBLE;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSunECConstructor(Constructor<?> constructor) {
        sunECConstructor = constructor;
    }

    Constructor<?> getSunECConstructor() {
        return sunECConstructor;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSavedInitialSecurityProperties(Properties properties) {
        savedInitialSecurityProperties = properties;
    }

    public Properties getSavedInitialSecurityProperties() {
        return savedInitialSecurityProperties;
    }
}
