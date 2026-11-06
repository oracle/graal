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

package com.oracle.svm.core.jdk.runtimeinit;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Properties;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.LegacySecurityProvidersInitializedAtRunTime;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeAccess;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;

/** Provider-list behavior retained only for the run-time-initialized compatibility mode. */

@TargetClass(value = java.security.Security.class, onlyWith = LegacySecurityProvidersInitializedAtRunTime.class)
final class Target_java_security_Security_LegacyRuntimeInit {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    static Properties props;

    @Substitute
    public static Provider getProvider(String name) {
        return SecurityProviderRuntimeAccess.traceJdkProviderLookup(sun.security.jca.Providers.getProviderList().getProvider(name));
    }
}

@TargetClass(value = java.security.Security.class, innerClass = "SecPropLoader", onlyWith = LegacySecurityProvidersInitializedAtRunTime.class)
final class Target_java_security_Security_SecPropLoader_LegacyRuntimeInit {
    @Substitute
    private static void loadMaster() {
        Target_java_security_Security_LegacyRuntimeInit.props = SecurityProviderRuntimeState.getSavedInitialSecurityProperties();
    }
}

@TargetClass(className = "sun.security.jca.ProviderList", onlyWith = LegacySecurityProvidersInitializedAtRunTime.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderList_LegacyRuntimeInit {
    @Alias //
    private Target_sun_security_jca_ProviderConfig[] configs;

    @Alias
    private native Provider getProvider(int index);

    @Alias
    private native int getIndex(String name);

    @Substitute
    public Provider getProvider(String name) {
        int index = getIndex(name);
        return index >= 0 ? SecurityProviderRuntimeAccess.traceJdkProviderLookup(getProvider(index)) : null;
    }

    /** Return only active providers without removing inactive configurations from this list. */
    @Substitute
    public Provider[] toArray() {
        ArrayList<Provider> activeProviders = new ArrayList<>(configs.length);
        for (Target_sun_security_jca_ProviderConfig config : configs) {
            Provider provider = config.getProvider();
            if (provider != null) {
                activeProviders.add(provider);
            }
        }
        return activeProviders.toArray(new Provider[0]);
    }
}

public final class SecurityProviderLegacyRuntimeInitSubstitutions {
}
