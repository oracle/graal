/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
// Checkstyle: stop
import sun.security.action.OpenFileInputStreamAction;
import sun.security.ssl.SSLLogger;
// Checkstyle: resume

/**
 * Root certificates in native image are fixed/embedded into the image, at image build time, based
 * on the the certificate configuration used for the image generator. This avoids shipping a
 * `cacerts` file or requiring to set a system property to set up root certificates that are
 * provided by the OS where the image runs.
 * <p>
 * However, users are allowed to override these root certificates at run time by setting the
 * `javax.net.ssl.trustStore` system property to point to a file path containing the certificates.
 * <p>
 * For embedding the build time root certificates, the implementation "freezes" the return values of
 * TrustStoreManager managers by invoking them at image build time (using reflection because the
 * class is non-public) and returning the frozen values using a substitution, if at run time the
 * `javax.net.ssl.trustStore` system property isn't set
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
         * lib/security/blacklisted.certs, so this class must be initialized at image build time.
         * This is the default anyway for code JDK classes, but since this this class is relevant
         * for security we spell it out explicitly. When the native application uses a custom
         * truststore at run time (by setting the -Djavax.net.ssl.trustStore system property) we
         * still honour/use the build time lib/security/blacklisted.certs.
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

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME +
                    "$TrustAnchorManager") private static Target_sun_security_ssl_TrustStoreManager_TrustAnchorManager tam;

    @Substitute
    private static Set<X509Certificate> getTrustedCerts() throws Exception {
        Set<X509Certificate> set = new HashSet<>(ImageSingletons.lookup(TrustStoreManagerSupport.class).trustedCerts);
        set.addAll(tam.getTrustedCerts(Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor.createInstance()));
        return Collections.unmodifiableSet(set);
    }
}

@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME, innerClass = "TrustStoreDescriptor")
final class Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor {

    @Alias String storeName;
    @Alias File storeFile;
    @Alias String storeProvider;
    @Alias String storeType;
    @Alias String storePassword;

    @Alias
    static native Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor createInstance();
}

@TargetClass(className = TrustStoreManagerFeature.TRUST_STORE_MANAGER_CLASS_NAME, innerClass = "TrustAnchorManager")
final class Target_sun_security_ssl_TrustStoreManager_TrustAnchorManager {

    @SuppressWarnings({"unused", "static-method"}) //
    @Alias
    native Set<X509Certificate> getTrustedCerts(Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor descriptor);

    @Alias
    private static KeyStore loadKeyStore(
                    Target_sun_security_ssl_TrustStoreManager_TrustStoreDescriptor descriptor) throws Exception {
        KeyStore embeddedKeystore = ImageSingletons.lookup(TrustStoreManagerSupport.class).trustedKeyStore;
        if (!"NONE".equals(descriptor.storeName) &&
                        descriptor.storeFile == null) {

            // No file available, no KeyStore available.
            if (SSLLogger.isOn && SSLLogger.isOn("trustmanager")) {
                SSLLogger.fine("No keystore found at runtime, returning embedded one");
            }

            return embeddedKeystore;
        }

        KeyStore ks;
        if (descriptor.storeProvider.isEmpty()) {
            ks = KeyStore.getInstance(descriptor.storeType);
        } else {
            ks = KeyStore.getInstance(
                            descriptor.storeType, descriptor.storeProvider);
        }

        char[] password = null;
        if (!descriptor.storePassword.isEmpty()) {
            password = descriptor.storePassword.toCharArray();
        }

        if (!"NONE".equals(descriptor.storeName)) {
            try (FileInputStream fis = AccessController.doPrivileged(
                            new OpenFileInputStreamAction(descriptor.storeFile))) {
                ks.load(fis, password);
            } catch (FileNotFoundException fnfe) {
                // No file available, no KeyStore available.
                if (SSLLogger.isOn && SSLLogger.isOn("trustmanager")) {
                    SSLLogger.fine(
                                    "Not available key store: " + descriptor.storeName);
                }

                return embeddedKeystore;
            }
        } else {
            ks.load(null, password);
        }

        KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection(password);
        for (Enumeration<String> aliases = embeddedKeystore.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            if (ks.containsAlias(alias)) {
                continue;
            }
            ks.setEntry(alias, embeddedKeystore.getEntry(alias, passwordProtection), passwordProtection);
        }
        return ks;
    }
}
