/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.security.ssl.SSLLogger;

/**
 * Root certificates in native image are fixed/embedded into the image, at image build time, based
 * on the certificate configuration used for the image generator. This avoids the need to ship a
 * `cacerts` file alongside the image executable.
 *
 * <p>
 * Users are also allowed to override the embedded root certificate at run time by setting the
 * `javax.net.ssl.trustStore*` system properties. For more details about both buildtime and runtime
 * certificate management, please refer to <a href=
 * "https://www.graalvm.org/dev/reference-manual/native-image/dynamic-features/CertificateManagement/">CertificateManagement.md</a>.
 *
 * <p>
 * For embedding the build time root certificates, the implementation "freezes" the return values of
 * TrustStoreManager managers by invoking them at image build time (using reflection because the
 * class is non-public) and returning the frozen values using a substitution.
 */
@AutomaticallyRegisteredFeature
final class TrustStoreManagerFeature implements InternalFeature {

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
         * lib/security/blacklisted.certs, so this class must be initialized at image build time.
         * This is the default anyway for code JDK classes, but since this class is relevant for
         * security we spell it out explicitly.
         *
         * Note when a runtime certificate file is specified, we still honor/use the build time
         * lib/security/blacklisted.certs file
         */
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        rci.initializeAtBuildTime("sun.security.util.UntrustedCertificates", "Required for TrustStoreManager");
    }
}

final class TrustStoreManagerSupport {

    final Set<X509Certificate> buildtimeTrustedCerts;
    final KeyStore buildtimeTrustedKeyStore;

    TrustStoreManagerSupport(Set<X509Certificate> buildtimeTrustedCerts, KeyStore buildtimeTrustedKeyStore) {
        this.buildtimeTrustedCerts = buildtimeTrustedCerts;
        this.buildtimeTrustedKeyStore = buildtimeTrustedKeyStore;
    }

    /**
     * This method creates a TrustStoreDescriptor if any of the "javax.net.ssl.trustStore*"
     * properties are set, or otherwise returns null.
     */
    static Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor getRuntimeTrustStoreDescriptor() {
        /* First read current system properties. */
        String storePropName = System.getProperty("javax.net.ssl.trustStore");
        String storePropType = System.getProperty("javax.net.ssl.trustStoreType");
        String storePropProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
        String storePropPassword = System.getProperty("javax.net.ssl.trustStorePassword");

        /*
         * Check if any of the properties are set. If not, then should not attempt to create a trust
         * store descriptor.
         */
        if (storePropName == null && storePropType == null && storePropProvider == null && storePropPassword == null) {
            return null;
        }

        if (storePropName == null) {
            throw VMError.unsupportedFeature(
                            "System property javax.net.ssl.trustStore must be also set if any of javax.net.ssl.trustStore(Type|Provider|Password) are set." +
                                            "See https://www.graalvm.org/dev/reference-manual/native-image/dynamic-features/CertificateManagement/#runtime-options for more details about runtime certificate management.");
        }

        /* Setting remaining properties to defaults if unset. */
        if (storePropType == null) {
            storePropType = KeyStore.getDefaultType();
        }
        if (storePropProvider == null) {
            storePropProvider = "";
        }
        if (storePropPassword == null) {
            storePropPassword = "";
        }

        /* Creating TrustStoreDescriptor. */
        Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor descriptor = createTrustStoreDescriptor(storePropName, storePropType, storePropProvider,
                        storePropPassword);

        /*
         * Checking if TrustStoreDescriptor was able to find a valid trust store.
         */
        if (descriptor == null) {
            throw VMError.unsupportedFeature("Inaccessible trust store: " + storePropName +
                            "See https://www.graalvm.org/dev/reference-manual/native-image/dynamic-features/CertificateManagement/#runtime-options for more details about runtime certificate management.");
        }

        return descriptor;
    }

    /**
     * Creates a new TrustStoreDescriptor object.
     *
     * @return A new TrustStoreDescriptor or {@code null} if an appropriate descriptor for the
     *         provided parameters could not be found.
     */
    private static Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor createTrustStoreDescriptor(String storePropName, String storePropType, String storePropProvider,
                    String storePropPassword) {
        /* This code is largely taken from the JDK. */
        String temporaryName = "";
        File temporaryFile = null;
        long temporaryTime = 0L;
        if (!"NONE".equals(storePropName)) {
            File f = new File(storePropName);
            if (f.isFile() && f.canRead()) {
                temporaryName = storePropName;
                temporaryFile = f;
                temporaryTime = f.lastModified();
            } else {
                // The file is inaccessible.
                if (SSLLogger.isOn && SSLLogger.isOn("trustmanager")) {
                    SSLLogger.fine("Inaccessible trust store: " + storePropName);
                }

                return null;
            }
        } else {
            temporaryName = storePropName;
        }

        return new Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor(
                        temporaryName, storePropType, storePropProvider,
                        storePropPassword, temporaryFile, temporaryTime);
    }

}

@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME)
final class Target_sun_security_ssl_TrustStoreManager {
    /*
     * This singleton object caches the last retrieved trusted KeyStore and set of trusted
     * certificates.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME +
                    "$TrustAnchorManager") private static Target_sun_security_ssl_TrustStoreManager_TrustAnchorManager tam;

    @Substitute
    private static Set<X509Certificate> getTrustedCerts() throws Exception {
        Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor runtimeDescriptor = TrustStoreManagerSupport.getRuntimeTrustStoreDescriptor();
        if (runtimeDescriptor == null) {
            return ImageSingletons.lookup(TrustStoreManagerSupport.class).buildtimeTrustedCerts;
        }
        return tam.getTrustedCerts(runtimeDescriptor);
    }

    @Substitute
    private static KeyStore getTrustedKeyStore() throws Exception {
        Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor runtimeDescriptor = TrustStoreManagerSupport.getRuntimeTrustStoreDescriptor();
        if (runtimeDescriptor == null) {
            return ImageSingletons.lookup(TrustStoreManagerSupport.class).buildtimeTrustedKeyStore;
        }
        return tam.getKeyStore(runtimeDescriptor);
    }
}

@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME, innerClass = "TrustStoreDescriptor")
final class Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor {
    @Delete private static String defaultStorePath;
    @Delete private static String defaultStore;
    @Delete private static String jsseDefaultStore;

    @Delete
    static native Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor createInstance();

    @Alias
    @SuppressWarnings("unused")
    Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor(String storeName, String storeType, String storeProvider, String storePassword, File storeFile, long lastModified) {
        throw VMError.shouldNotReachHere("This is an alias to the original constructor in the target class, so this code is unreachable");
    }
}

@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME, innerClass = "TrustAnchorManager")
final class Target_sun_security_ssl_TrustStoreManager_TrustAnchorManager {

    @Alias
    native Set<X509Certificate> getTrustedCerts(Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor descriptor) throws Exception;

    @Alias
    native KeyStore getKeyStore(Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor descriptor) throws Exception;
}
