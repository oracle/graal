/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Native image uses the principle of "immutable security" for the root certificates: They are fixed
 * at image build time based on the the certificate configuration used for the image generator. This
 * avoids shipping a `cacerts` file or requiring to set a system property to set up root
 * certificates that are provided by the OS where the image runs.
 *
 * As a consequence, system properties such as `javax.net.ssl.trustStore` do not have an effect at
 * run time. They need to be provided at image build time.
 *
 * The implementation "freezes" the return values of TrustStoreManager managers by invoking them at
 * image build time (using reflection because the class is non-public) and returning the frozen
 * values using a substitution.
 */
@AutomaticFeature
final class TrustStoreManagerFeature implements Feature {

    static final String TRUST_STORE_MANAGER_CLASS_NAME = "sun.security.ssl.TrustStoreManager";

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        try {
            Class<?> trustStoreManagerClass = access.findClassByName(TRUST_STORE_MANAGER_CLASS_NAME);
            @SuppressWarnings("unchecked")
            Set<X509Certificate> trustedCerts = (Set<X509Certificate>) ReflectionUtil.lookupMethod(trustStoreManagerClass, "getTrustedCerts").invoke(null);
            KeyStore trustedKeyStore = (KeyStore) ReflectionUtil.lookupMethod(trustStoreManagerClass, "getTrustedKeyStore").invoke(null);

            ImageSingletons.add(TrustStoreManagerSupport.class, new TrustStoreManagerSupport(trustedCerts, trustedKeyStore));
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        /*
         * The class initializer of UntrustedCertificates loads the file
         * lib/security/blacklisted.certs, so this class must be initialized at image built time.
         * This is the default anyway for code JDK classes, but since this this class is relevant
         * for security we spell it out explicitly.
         */
        RuntimeClassInitialization.initializeAtBuildTime(sun.security.util.UntrustedCertificates.class);
        RuntimeClassInitialization.initializeAtBuildTime(org.jcp.xml.dsig.internal.dom.XMLDSigRI.class);
    }
}

final class TrustStoreManagerSupport {
    final Set<X509Certificate> trustedCerts;
    final KeyStore trustedKeyStore;

    TrustStoreManagerSupport(Set<X509Certificate> trustedCerts, KeyStore trustedKeyStore) {
        this.trustedCerts = trustedCerts;
        this.trustedKeyStore = trustedKeyStore;
    }
}

@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME)
final class Target_sun_security_ssl_TrustStoreManager {

    @Substitute
    private static Set<X509Certificate> getTrustedCerts() throws Exception {
        return ImageSingletons.lookup(TrustStoreManagerSupport.class).trustedCerts;
    }

    @Substitute
    private static KeyStore getTrustedKeyStore() throws Exception {
        return ImageSingletons.lookup(TrustStoreManagerSupport.class).trustedKeyStore;
    }
}

/*
 * The internal classes to describe and load root certificates must not be reachable at run time.
 */

@Delete
@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME, innerClass = "TrustStoreDescriptor")
final class Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor {
}

@Delete
@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME, innerClass = "TrustAnchorManager")
final class Target_sun_security_ssl_TrustStoreManager_TrustAnchorManager {
}
