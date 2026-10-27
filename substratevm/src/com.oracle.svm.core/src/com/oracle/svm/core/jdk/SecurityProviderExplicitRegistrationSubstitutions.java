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

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.SubstrateUtil;

/**
 * Planned-default substitutions for reflection-metadata-driven provider registration. Every target
 * in this file is guarded by {@link ExplicitSecurityProviderRegistration}; compatibility modes do
 * not execute these method bodies.
 */

@TargetClass(value = java.security.Security.class, onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_java_security_Security_ExplicitRegistration {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    static Properties props;

    /** §FS-002-security-providers.3.3: Keep omission diagnostics outside ProviderList mutation. */
    @Substitute
    public static Provider getProvider(String name) {
        Provider provider = sun.security.jca.Providers.getProviderList().getProvider(name);
        if (provider == null) {
            SecurityProviderRuntimeAccess.reportMissingConfiguredProvider(name);
        }
        return SecurityProviderRuntimeAccess.traceJdkProviderLookup(provider);
    }

    @Substitute
    public static Provider[] getProviders() {
        Provider[] providers = sun.security.jca.Providers.getFullProviderList().toArray();
        return SecurityProviderRuntimeAccess.traceJdkProviderLookups(providers);
    }

    /** §FS-002-security-providers.5.1: Trace only providers returned by a filtered lookup. */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/java/security/Security.java#L691-L722")
    public static Provider[] getProviders(Map<String, String> filter) {
        Provider[] allProviders = sun.security.jca.Providers.getFullProviderList().toArray();
        Set<Map.Entry<String, String>> entries = filter.entrySet();

        if (allProviders.length == 0) {
            return null;
        } else if (entries == null) {
            return SecurityProviderRuntimeAccess.traceJdkProviderLookups(allProviders);
        } else if (entries.isEmpty()) {
            return null;
        }

        LinkedList<Provider> candidates = new LinkedList<>(Arrays.asList(allProviders));
        for (Map.Entry<String, String> entry : entries) {
            Target_java_security_Security_Criteria_ExplicitRegistration criteria = new Target_java_security_Security_Criteria_ExplicitRegistration(entry.getKey(), entry.getValue());
            candidates.removeIf(provider -> !criteria.isCriterionSatisfied(provider));
            if (candidates.isEmpty()) {
                return null;
            }
        }
        return SecurityProviderRuntimeAccess.traceJdkProviderLookups(candidates.toArray(new Provider[0]));
    }

    /** §FS-002-security-providers.4.2: Insert relative to providers visible to the application. */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/java/security/Security.java#L469-L478")
    public static synchronized int insertProviderAt(Provider provider, int position) {
        sun.security.jca.ProviderList providers = sun.security.jca.Providers.getFullProviderList();
        sun.security.jca.ProviderList updatedProviders = SecurityProviderListSupport.insertAtVisiblePosition(providers, provider, position);
        if (providers == updatedProviders) {
            return -1;
        }
        sun.security.jca.Providers.setProviderList(updatedProviders);
        return SecurityProviderListSupport.visibleIndex(updatedProviders, provider.getName());
    }
}

@TargetClass(value = java.security.Security.class, innerClass = "Criteria", onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_java_security_Security_Criteria_ExplicitRegistration {
    @Alias
    Target_java_security_Security_Criteria_ExplicitRegistration(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") String value) {
    }

    @Alias
    native boolean isCriterionSatisfied(Provider provider);
}

@TargetClass(value = java.security.Security.class, innerClass = "SecPropLoader", onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_java_security_Security_SecPropLoader_ExplicitRegistration {
    /** Use the security properties captured from the image-builder JVM. */
    @Substitute
    private static void loadMaster() {
        Target_java_security_Security_ExplicitRegistration.props = SecurityProviderRuntimeState.getSavedInitialSecurityProperties();
    }
}

// §FS-002-security-providers.2.1 and §FS-002-security-providers.3.1:
// Reject an unregistered SUN provider before the JDK SecureRandom fallback can expose it.
@TargetClass(className = "sun.security.jca.Providers", onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_sun_security_jca_Providers_ExplicitRegistration {
    @Alias
    public static native sun.security.jca.ProviderList getProviderList();

    @Substitute
    public static Provider getSunProvider() {
        if (!SecurityProviderRuntimeAccess.isJdkAcquirable("sun.security.provider.Sun")) {
            SecurityProviderRuntimeAccess.reportMissingRegistration(sun.security.provider.Sun.class);
        }
        return new sun.security.provider.Sun();
    }

    /** Preserve inactive conditional configurations in their original positions. */
    @Substitute
    public static sun.security.jca.ProviderList getFullProviderList() {
        return getProviderList();
    }
}

@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = ExplicitSecurityProviderRegistration.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderConfig_ExplicitRegistration {
    @Alias //
    String provName;

    @Alias //
    Provider provider;

    @Alias
    Target_sun_security_jca_ProviderConfig_ExplicitRegistration(@SuppressWarnings("unused") Provider provider) {
    }

    @Alias
    native Provider getProvider();
}

@TargetClass(className = "sun.security.jca.ProviderList", onlyWith = ExplicitSecurityProviderRegistration.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderList_ExplicitRegistration {
    @Alias //
    Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] configs;

    @Alias
    Target_sun_security_jca_ProviderList_ExplicitRegistration(
                    @SuppressWarnings("unused") Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] configs,
                    @SuppressWarnings("unused") boolean allLoaded) {
    }

    @Alias
    private native Provider getProvider(int index);

    @Alias
    private native int getIndex(String name);

    /** Preserve inactive conditional configurations when an active provider is removed. */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/sun/security/jca/ProviderList.java#L112-L129")
    public static sun.security.jca.ProviderList remove(sun.security.jca.ProviderList providerList, String name) {
        Target_sun_security_jca_ProviderList_ExplicitRegistration targetProviderList = SubstrateUtil.cast(providerList,
                        Target_sun_security_jca_ProviderList_ExplicitRegistration.class);
        providerList.toArray();
        int removeIndex = -1;
        for (int index = 0; index < targetProviderList.configs.length; index++) {
            Target_sun_security_jca_ProviderConfig_ExplicitRegistration config = targetProviderList.configs[index];
            Provider provider = config.provider;
            if (provider != null && provider.getName().equals(name)) {
                removeIndex = index;
                break;
            }
        }
        if (removeIndex < 0) {
            return providerList;
        }

        Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] updatedConfigs = new Target_sun_security_jca_ProviderConfig_ExplicitRegistration[targetProviderList.configs.length - 1];
        System.arraycopy(targetProviderList.configs, 0, updatedConfigs, 0, removeIndex);
        System.arraycopy(targetProviderList.configs, removeIndex + 1, updatedConfigs, removeIndex,
                        targetProviderList.configs.length - removeIndex - 1);
        return SubstrateUtil.cast(new Target_sun_security_jca_ProviderList_ExplicitRegistration(updatedConfigs, true),
                        sun.security.jca.ProviderList.class);
    }

    @Substitute
    public Provider getProvider(String name) {
        int index = getIndex(name);
        return index >= 0 ? getProvider(index) : null;
    }

    /** Return active providers without removing inactive configurations from the list. */
    @Substitute
    public Provider[] toArray() {
        ArrayList<Provider> activeProviders = new ArrayList<>(configs.length);
        for (Target_sun_security_jca_ProviderConfig_ExplicitRegistration config : configs) {
            Provider activeProvider = config.getProvider();
            if (activeProvider != null) {
                activeProviders.add(activeProvider);
            }
        }
        return activeProviders.toArray(new Provider[0]);
    }
}

final class SecurityProviderListSupport {
    /** §FS-002-security-providers.4.2: Translate an API position through visible providers. */
    static sun.security.jca.ProviderList insertAtVisiblePosition(sun.security.jca.ProviderList providerList, Provider provider, int position) {
        Target_sun_security_jca_ProviderList_ExplicitRegistration targetProviderList = SubstrateUtil.cast(providerList,
                        Target_sun_security_jca_ProviderList_ExplicitRegistration.class);
        Provider[] activeProviders = providerList.toArray();
        for (Provider activeProvider : activeProviders) {
            if (activeProvider.getName().equals(provider.getName())) {
                return providerList;
            }
        }

        int visibleIndex = position <= 0 || position > activeProviders.length ? activeProviders.length : position - 1;
        int rawInsertionIndex = targetProviderList.configs.length;
        int currentVisibleIndex = 0;
        int lastActiveRawIndex = -1;
        for (int rawIndex = 0; rawIndex < targetProviderList.configs.length && currentVisibleIndex < activeProviders.length; rawIndex++) {
            if (configurationMatchesProvider(targetProviderList.configs[rawIndex], activeProviders[currentVisibleIndex])) {
                if (currentVisibleIndex == visibleIndex) {
                    rawInsertionIndex = rawIndex;
                    break;
                }
                lastActiveRawIndex = rawIndex;
                currentVisibleIndex++;
            }
        }
        if (visibleIndex == activeProviders.length && lastActiveRawIndex >= 0) {
            rawInsertionIndex = lastActiveRawIndex + 1;
        }

        Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] updatedConfigs = new Target_sun_security_jca_ProviderConfig_ExplicitRegistration[targetProviderList.configs.length + 1];
        System.arraycopy(targetProviderList.configs, 0, updatedConfigs, 0, rawInsertionIndex);
        updatedConfigs[rawInsertionIndex] = new Target_sun_security_jca_ProviderConfig_ExplicitRegistration(provider);
        System.arraycopy(targetProviderList.configs, rawInsertionIndex, updatedConfigs, rawInsertionIndex + 1,
                        targetProviderList.configs.length - rawInsertionIndex);
        return SubstrateUtil.cast(new Target_sun_security_jca_ProviderList_ExplicitRegistration(updatedConfigs, true),
                        sun.security.jca.ProviderList.class);
    }

    static int visibleIndex(sun.security.jca.ProviderList providerList, String name) {
        int visibleIndex = 0;
        for (Provider provider : providerList.toArray()) {
            visibleIndex++;
            if (provider.getName().equals(name)) {
                return visibleIndex;
            }
        }
        return -1;
    }

    private static boolean configurationMatchesProvider(Target_sun_security_jca_ProviderConfig_ExplicitRegistration config, Provider provider) {
        if (config.provider == provider || config.provName.equals(provider.getName()) || config.provName.equals(provider.getClass().getName())) {
            return true;
        }
        SecurityProviderRuntimeState.ConfiguredProviderInfo configuredProvider = SecurityProviderRuntimeState.getConfiguredProviderForDiagnostics(config.provName);
        if (configuredProvider != null && configuredProvider.providerClassName().equals(provider.getClass().getName())) {
            return true;
        }
        String builtInProviderClassName = BuiltInSecurityProviderLoader.getProviderClassName(config.provName);
        return provider.getClass().getName().equals(builtInProviderClassName) ||
                        SecurityProviderRuntimeAccess.isConfiguredProviderAcquirable(config.provName);
    }

    private SecurityProviderListSupport() {
    }
}

@TargetClass(className = "sun.security.jca.GetInstance", onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_sun_security_jca_GetInstance_ExplicitRegistration {
    /** §FS-002-security-providers.3.3: Named factories diagnose at their acquisition boundary. */
    @Substitute
    public static Provider.Service getService(String type, String algorithm, String providerName)
                    throws NoSuchAlgorithmException, NoSuchProviderException {
        // Checkstyle: allow inconsistent exceptions and errors (JDK-compatible messages)
        if (providerName == null || providerName.isEmpty()) {
            throw new IllegalArgumentException("missing provider");
        }
        Provider provider = sun.security.jca.Providers.getProviderList().getProvider(providerName);
        if (provider == null) {
            SecurityProviderRuntimeAccess.reportMissingConfiguredProvider(providerName);
            throw new NoSuchProviderException("no such provider: " + providerName);
        }
        Provider.Service service = provider.getService(type, algorithm);
        if (service == null) {
            throw new NoSuchAlgorithmException("no such algorithm: " + algorithm + " for provider " + providerName);
        }
        // Checkstyle: disallow inconsistent exceptions and errors
        return service;
    }

}

public final class SecurityProviderExplicitRegistrationSubstitutions {
}
