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
import java.security.Provider;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.BasedOnJDKFile;

@TargetClass(value = java.security.Security.class, onlyWith = SecurityProvidersInitializedAtBuildTime.class)
final class Target_java_security_Security_ProviderLookup {

    /** §FS-002-security-providers.6: Successful name-based lookup traces provider construction. */
    @Substitute
    public static Provider getProvider(String name) {
        return SecurityProviderRuntimeAccess.traceJdkProviderLookup(sun.security.jca.Providers.getProviderList().getProvider(name));
    }

}

/** Keeps provider mutation behavior unchanged while native tracing is enabled. */
@TargetClass(java.security.Security.class)
final class Target_java_security_Security_ProviderMutation {
    /** §FS-002-security-providers.6.1: An existing provider object is not reflection metadata. */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/java/security/Security.java#L469-L478")
    public static synchronized int insertProviderAt(Provider provider, int position) {
        sun.security.jca.ProviderList providers = sun.security.jca.Providers.getFullProviderList();
        sun.security.jca.ProviderList updatedProviders = FutureDefaultsOptions.explicitSecurityProviderRegistration()
                        ? SecurityProviderListSupport.insertAtVisiblePosition(providers, provider, position)
                        : sun.security.jca.ProviderList.insertAt(providers, provider, position - 1);
        if (providers == updatedProviders) {
            return -1;
        }
        sun.security.jca.Providers.setProviderList(updatedProviders);
        return FutureDefaultsOptions.explicitSecurityProviderRegistration()
                        ? SecurityProviderListSupport.visibleIndex(updatedProviders, provider.getName())
                        : updatedProviders.getIndex(provider.getName()) + 1;
    }
}

/** Keeps provider enumeration tracing active in both provider-list initialization modes. */
@TargetClass(java.security.Security.class)
final class Target_java_security_Security_ProviderEnumeration {
    @Substitute
    public static Provider[] getProviders() {
        return SecurityProviderRuntimeAccess.traceJdkProviderLookups(sun.security.jca.Providers.getFullProviderList().toArray());
    }

    /** §FS-002-security-providers.6.1: Trace only providers returned by a filtered lookup. */
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
            Target_java_security_Security_Criteria criteria = new Target_java_security_Security_Criteria(entry.getKey(), entry.getValue());
            candidates.removeIf(provider -> !criteria.isCriterionSatisfied(provider));
            if (candidates.isEmpty()) {
                return null;
            }
        }
        return SecurityProviderRuntimeAccess.traceJdkProviderLookups(candidates.toArray(new Provider[0]));
    }
}

@TargetClass(value = java.security.Security.class, innerClass = "Criteria")
final class Target_java_security_Security_Criteria {
    @Alias
    Target_java_security_Security_Criteria(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") String value) {
    }

    @Alias
    native boolean isCriterionSatisfied(Provider provider);
}

@TargetClass(className = "sun.security.jca.GetInstance")
final class Target_sun_security_jca_GetInstance_Tracing {
    // §FS-002-security-providers.6.1
    /** Trace cached SPI and provider types after successful service construction. */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/sun/security/jca/GetInstance.java#L243-L253")
    public static void checkSuperClass(Provider.Service service, Class<?> subClass, Class<?> superClass) throws NoSuchAlgorithmException {
        // §FS-002-security-providers.4.1 and §FS-002-security-providers.5.1:
        // A provider-object factory call must fail before it exposes an unregistered provider.
        SecurityProviderRuntimeAccess.requireRegisteredProvider(service.getProvider());
        if (superClass != null && !superClass.isAssignableFrom(subClass)) {
            // Checkstyle: allow inconsistent exceptions and errors (JDK-compatible message)
            throw new NoSuchAlgorithmException("class configured for " + service.getType() + ": " +
                            service.getClassName() + " not a " + service.getType());
            // Checkstyle: disallow inconsistent exceptions and errors
        }
        SecurityProviderRuntimeAccess.traceServiceSelection(service.getProvider(), superClass);
    }
}

public final class SecurityProviderTracingSubstitutions {
}
