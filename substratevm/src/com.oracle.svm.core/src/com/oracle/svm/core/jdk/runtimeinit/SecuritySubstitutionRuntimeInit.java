/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URL;
import java.security.Provider;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JceProviderVerificationSupport;
import com.oracle.svm.core.jdk.SecurityProvidersInitializedAtRunTime;
import com.oracle.svm.shared.util.BasedOnJDKFile;

/**
 * The {@code javax.crypto.JceSecurity#verificationResults} cache is initialized by the
 * SecurityServicesFeature at build time, for all registered providers. The cache is used by
 * {@code javax.crypto.JceSecurity#canUseProvider} at run time to check whether a provider is
 * properly signed and can be used by JCE. It does that via jar verification which we cannot
 * support.
 */
@TargetClass(className = "javax.crypto.JceSecurity", onlyWith = SecurityProvidersInitializedAtRunTime.class)
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    /*
     * Map<Provider, ?> of providers that have already been verified. A value of PROVIDER_VERIFIED
     * indicates successful verification. Otherwise, the value is the Exception that caused the
     * verification to fail.
     */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Object, Object> verificationResults;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Provider, Object> verifyingProviders;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static Map<Class<?>, URL> codeBaseCacheRef = new WeakHashMap<>();

    @Substitute
    static Exception getVerificationResult(Provider p) {
        return JceProviderVerificationSupport.getVerificationResult(p);
    }
}

@SuppressWarnings("unused")
public class SecuritySubstitutionRuntimeInit {
}
