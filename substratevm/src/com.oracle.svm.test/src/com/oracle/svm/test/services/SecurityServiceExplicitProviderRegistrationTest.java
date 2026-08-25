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
package com.oracle.svm.test.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;

import javax.crypto.Mac;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.MissingReflectionRegistrationError;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;
import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--future-defaults=explicit-security-provider-registration",
                "--exact-reachability-metadata=com.oracle.svm.test.services"
})
public class SecurityServiceExplicitProviderRegistrationTest {
    private static final String REGISTERED_PROVIDER_NAME = "reflection-metadata-provider";

    // §FS-002-security-providers.7.1
    /** Tests explicit registration with run-time initialization. */
    @Test
    public void testExplicitRegistrationEnablesRuntimeProviderInitialization() {
        Assert.assertTrue(FutureDefaultsOptions.explicitSecurityProviderRegistration());
        Assert.assertTrue(FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
    }

    // §FS-002-security-providers.2.3, §FS-002-security-providers.2.4
    /** Tests default SecureRandom registration. */
    @Test
    public void testDefaultSecureRandomIncludesCompleteSunProvider() throws NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();
        Provider provider = random.getProvider();

        Assert.assertEquals("SUN", provider.getName());
        Assert.assertSame(provider, Security.getProvider("SUN"));
        Assert.assertNotNull("Default SecureRandom must retain its SHA dependency.", MessageDigest.getInstance("SHA", provider));
        Provider.Service jksService = provider.getService("KeyStore", "JKS");
        Assert.assertNotNull("Provider registration must retain unrelated advertised services.", jksService);
        Assert.assertNotNull("An unrelated advertised service must remain usable.", jksService.newInstance(null));
    }

    // §FS-002-security-providers.4.3, §FS-002-security-providers.7.3
    /** Tests omitted providers. */
    @Test
    public void testReachableFactoryDoesNotIncludeUnregisteredProvider() {
        Assert.assertThrows("An omitted HotSpot provider must report its missing registration.",
                        MissingReflectionRegistrationError.class, () -> Security.getProvider("SunEC"));
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            Assert.assertNotEquals("A different platform provider may supply the same algorithm.", "SunEC", signature.getProvider().getName());
        } catch (NoSuchAlgorithmException expected) {
            /* The algorithm is unavailable when no other platform provider supplies it. */
        }
    }

    // §FS-002-security-providers.2.1, §FS-002-security-providers.2.3, and
    // §FS-002-security-providers.5.1
    // Tests registration, complete services, and provider-object calls.
    @Test
    public void testReflectionMetadataProviderRegistration() throws Exception {
        Provider provider = new SecurityServiceTest.ReflectionMetadataProvider();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue(position > 0);
            SecurityServiceTest.JCACompliantNoOpService service = SecurityServiceTest.JCACompliantNoOpService.getInstance("reflection-metadata-algo");
            Assert.assertEquals(SecurityServiceTest.ReflectionMetadataNoOpServiceImpl.class, service.getClass());
            Assert.assertNotNull(Mac.getInstance("reflection-metadata-mac", provider));
        } finally {
            Security.removeProvider(REGISTERED_PROVIDER_NAME);
        }
    }

    // §FS-002-security-providers.2.1, §FS-002-security-providers.2.3, and
    // §FS-002-security-providers.5.1
    // Tests type-only registration, services, and provider-object calls.
    @Test
    public void testTypeMetadataProviderRegistration() throws Exception {
        Provider provider = new SecurityServiceTest.TypeMetadataProvider();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue(position > 0);
            SecurityServiceTest.JCACompliantNoOpService service = SecurityServiceTest.JCACompliantNoOpService.getInstance("type-metadata-algo");
            Assert.assertEquals(SecurityServiceTest.TypeMetadataNoOpServiceImpl.class, service.getClass());
        } finally {
            Security.removeProvider("type-metadata-provider");
        }
    }

    /** Tests §AR-002-security-providers.3 and §FS-002-security-providers.5.3. */
    @Test
    public void testInstantiatedProviderVerificationIsKeyedByClass() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        SecurityProviderRuntimeState.ProviderInfo registered = SecurityProviderRuntimeState.getProviderInfo(new SecurityServiceTest.ReflectionMetadataProvider());
        Assert.assertNotNull(registered);
        Assert.assertNull(registered.verificationFailure());
        Assert.assertEquals(SecurityProviderRuntimeState.AcquisitionKind.JDK_CONSTRUCTIBLE, registered.acquisitionKind());

        SecurityProviderRuntimeState.ProviderInfo supplied = SecurityProviderRuntimeState.getProviderInfo(new SameNameUnregisteredProvider());
        Assert.assertNotNull(supplied);
        Assert.assertNull(supplied.verificationFailure());
        Assert.assertEquals(SecurityProviderRuntimeState.AcquisitionKind.APPLICATION_SUPPLIED_ONLY, supplied.acquisitionKind());
    }

    /** Tests §FS-002-security-providers.5.1 and §FS-002-security-providers.5.3. */
    @Test
    public void testInstantiatedJceProviderUsesRetainedServiceInExplicitMode() throws Exception {
        Provider provider = new SecurityServiceTest.UnregisteredMacProvider();
        Assert.assertSame(provider, Mac.getInstance("unregistered-mac", provider).getProvider());
    }

    public static final class SameNameUnregisteredProvider extends Provider {
        private static final long serialVersionUID = 1L;

        public SameNameUnregisteredProvider() {
            super(REGISTERED_PROVIDER_NAME, "1.0", "Unregistered provider with a registered provider's name");
        }
    }
}
