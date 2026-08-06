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

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.BasedOnJDKFile;

@TargetClass(value = java.security.Security.class, onlyWith = SecurityProvidersInitializedAtBuildTime.class)
final class Target_java_security_Security_ProviderLookup {

    /** §FS-security-providers.6: Successful name-based lookup traces provider type access. */
    @Substitute
    public static Provider getProvider(String name) {
        return SecurityProviderRuntimeAccess.traceLookup(sun.security.jca.Providers.getProviderList().getProvider(name));
    }

}

/** Keeps provider mutation tracing active in both provider-list initialization modes. */
@TargetClass(java.security.Security.class)
final class Target_java_security_Security_ProviderMutation {
    /** §FS-security-providers.6.1: Mutation traces only the supplied provider. */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/java/security/Security.java#L469-L478")
    public static synchronized int insertProviderAt(Provider provider, int position) {
        SecurityProviderRuntimeAccess.traceLookup(provider);

        sun.security.jca.ProviderList providers = sun.security.jca.Providers.getFullProviderList();
        sun.security.jca.ProviderList updatedProviders = sun.security.jca.ProviderList.insertAt(providers, provider, position - 1);
        if (providers == updatedProviders) {
            return -1;
        }
        sun.security.jca.Providers.setProviderList(updatedProviders);
        return updatedProviders.getIndex(provider.getName()) + 1;
    }
}

/** Keeps provider enumeration tracing active in both provider-list initialization modes. */
@TargetClass(java.security.Security.class)
final class Target_java_security_Security_ProviderEnumeration {
    @Substitute
    public static Provider[] getProviders() {
        return SecurityProviderRuntimeAccess.traceLookups(sun.security.jca.Providers.getFullProviderList().toArray());
    }
}

public final class SecurityProviderTracingSubstitutions {
}
