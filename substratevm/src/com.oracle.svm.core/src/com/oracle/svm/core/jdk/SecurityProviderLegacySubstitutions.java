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

import java.security.Provider;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.BasedOnJDKFile;

/** Lookup-tracing shims needed only while the two compatibility modes remain supported. */

@TargetClass(value = java.security.Security.class, onlyWith = SecurityProvidersInitializedAtBuildTime.class)
final class Target_java_security_Security_ProviderLookup_Legacy {
    @Substitute
    public static Provider getProvider(String name) {
        return SecurityProviderRuntimeAccess.traceJdkProviderLookup(sun.security.jca.Providers.getProviderList().getProvider(name));
    }
}

@TargetClass(value = java.security.Security.class, onlyWith = LegacySecurityProviderRegistration.class)
final class Target_java_security_Security_ProviderEnumeration_Legacy {
    @Substitute
    public static Provider[] getProviders() {
        return SecurityProviderRuntimeAccess.traceJdkProviderLookups(sun.security.jca.Providers.getFullProviderList().toArray());
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
            Target_java_security_Security_Criteria_Legacy criteria = new Target_java_security_Security_Criteria_Legacy(entry.getKey(), entry.getValue());
            candidates.removeIf(provider -> !criteria.isCriterionSatisfied(provider));
            if (candidates.isEmpty()) {
                return null;
            }
        }
        return SecurityProviderRuntimeAccess.traceJdkProviderLookups(candidates.toArray(new Provider[0]));
    }
}

@TargetClass(value = java.security.Security.class, innerClass = "Criteria", onlyWith = LegacySecurityProviderRegistration.class)
final class Target_java_security_Security_Criteria_Legacy {
    @Alias
    Target_java_security_Security_Criteria_Legacy(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") String value) {
    }

    @Alias
    native boolean isCriterionSatisfied(Provider provider);
}

public final class SecurityProviderLegacySubstitutions {
}
