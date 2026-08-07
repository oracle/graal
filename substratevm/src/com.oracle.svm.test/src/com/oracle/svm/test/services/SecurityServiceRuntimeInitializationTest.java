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

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import javax.crypto.KeyGenerator;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.test.NativeImageBuildArgs;

import sun.security.jca.GetInstance;

@NativeImageBuildArgs({
                "--future-defaults=run-time-initialize-security-providers",
                "--exact-reachability-metadata=com.oracle.svm.test.services",
                "-H:+UnlockExperimentalVMOptions",
                "-H:AdditionalSecurityServiceTypes=com.oracle.svm.test.services.SecurityServiceTest$JCACompliantNoOpService",
                "-H:-UnlockExperimentalVMOptions"
})
public class SecurityServiceRuntimeInitializationTest {
    private static final String OMITTED_PROVIDER_ALGORITHM = "SHA256withECDSA";
    private static final String OMITTED_PROVIDER_SERVICE = "Signature";
    private static final String MISSING_KEY_GENERATOR_ALGORITHM = "GR69858DefinitelyMissing";
    private static final String REFLECTION_METADATA_PROVIDER_CLASS_NAME = "com.oracle.svm.test.services.SecurityServiceTest$ReflectionMetadataProvider";
    private static final String REFLECTION_METADATA_PROVIDER_NAME = "reflection-metadata-provider";
    private static final String SERVICE_LOADED_PROVIDER_CLASS_NAME = "com.oracle.svm.test.services.SecurityServiceTest$ServiceLoadedProvider";
    private static final String SERVICE_LOADED_PROVIDER_ALGORITHM = "service-loaded-provider-algo";

    @Test
    public void testRuntimeInitializationModeIsActive() {
        Assert.assertTrue(FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Assert.assertFalse(FutureDefaultsOptions.explicitSecurityProviderRegistration());
    }

    @Test
    public void testSecurityProviderRuntimeRegistration() {
        Assert.assertNull("Provider is registered.", Security.getProvider("no-op-provider"));
        Security.addProvider(new SecurityServiceTest.NoOpProvider());
        Assert.assertNotNull("Provider is not registered.", Security.getProvider("no-op-provider"));
    }

    /**
     * Tests the regression from <a href="https://github.com/oracle/graal/issues/1883">issue
     * 1883</a>.
     */
    @Test
    public void testUnknownSecurityServices() throws Exception {
        Security.addProvider(new SecurityServiceTest.NoOpProvider());
        Provider registered = Security.getProvider("no-op-provider");
        Assert.assertNotNull("Provider is not registered", registered);
        Object implementation = registered.getService("NoOp", "no-op-algo").newInstance(null);
        Assert.assertNotNull("No service instance was created", implementation);
        Assert.assertEquals(SecurityServiceTest.NoOpImpl.class, implementation.getClass());
    }

    /** §FS-security-providers.7.2: Tests absent service-provider metadata. */
    @Test
    public void testServiceLoaderProviderWithoutMetadataUsesReflectionLookupFailure() {
        Assert.assertTrue(ImageInfo.inImageRuntimeCode());
        ServiceConfigurationError error = Assert.assertThrows(ServiceConfigurationError.class,
                        () -> findServiceLoaderProvider(SERVICE_LOADED_PROVIDER_CLASS_NAME));
        Assert.assertTrue(error.getMessage().contains(SERVICE_LOADED_PROVIDER_CLASS_NAME));
        Assert.assertThrows(NoSuchAlgorithmException.class,
                        () -> SecurityServiceTest.JCACompliantNoOpService.getInstance(SERVICE_LOADED_PROVIDER_ALGORITHM));
    }

    /** §FS-security-providers.7.2: Tests preserved metadata-registered service providers. */
    @Test
    public void testServiceLoaderProviderWithMetadataIsPreserved() {
        Provider provider = findServiceLoaderProvider(REFLECTION_METADATA_PROVIDER_CLASS_NAME).get();
        Assert.assertEquals(REFLECTION_METADATA_PROVIDER_NAME, provider.getName());
    }

    private static ServiceLoader.Provider<Provider> findServiceLoaderProvider(String providerClassName) {
        Iterator<ServiceLoader.Provider<Provider>> providers = ServiceLoader.load(Provider.class).stream().iterator();
        while (true) {
            try {
                if (!providers.hasNext()) {
                    throw new AssertionError("Security provider should be visible through ServiceLoader: " + providerClassName);
                }
                ServiceLoader.Provider<Provider> provider = providers.next();
                if (provider.type().getName().equals(providerClassName)) {
                    return provider;
                }
            } catch (ServiceConfigurationError error) {
                if (error.getMessage().contains(providerClassName)) {
                    throw error;
                }
                /*
                 * Some JDK modules contribute providers whose implementation module is not in
                 * the image. They are unrelated to the provider selected by this test.
                 */
            }
        }
    }

    /** §FS-security-providers.7.3: Tests compatibility-mode provider inclusion. */
    @Test
    public void testReachableBuiltInProviderIsIncluded() {
        Assert.assertNotNull("Service-driven registration should include SunEC.", Security.getProvider("SunEC"));
    }

    /** §FS-security-providers.7.3: Tests compatibility-mode service lookup. */
    @Test
    public void testReachableBuiltInProviderGetService() throws NoSuchAlgorithmException {
        Provider.Service service = GetInstance.getService(OMITTED_PROVIDER_SERVICE, OMITTED_PROVIDER_ALGORITHM);
        Assert.assertEquals("SunEC", service.getProvider().getName());
    }

    /** §FS-security-providers.7.3: Tests compatibility-mode service instantiation. */
    @Test
    public void testReachableBuiltInProviderGetInstance() throws NoSuchAlgorithmException {
        Assert.assertNotNull(GetInstance.getInstance(OMITTED_PROVIDER_SERVICE, null, OMITTED_PROVIDER_ALGORITHM));
    }

    /** §FS-security-providers.7.3: Tests compatibility-mode service enumeration. */
    @Test
    public void testReachableBuiltInProviderGetServices() {
        Iterator<Provider.Service> services = GetInstance.getServices(OMITTED_PROVIDER_SERVICE, OMITTED_PROVIDER_ALGORITHM);
        Assert.assertTrue(services.hasNext());
    }

    /** §FS-security-providers.4.2: Tests the standard unavailable result. */
    @Test
    public void testGenericMissingAlgorithmExhaustsProviderList() {
        Assert.assertThrows(NoSuchAlgorithmException.class, () -> KeyGenerator.getInstance(MISSING_KEY_GENERATOR_ALGORITHM));
    }

    /** §FS-security-providers.7.3: Tests compatibility-mode algorithm enumeration. */
    @Test
    public void testSecurityGetAlgorithmsIncludesReachableBuiltInProviderAlgorithm() {
        Set<String> algorithms = Security.getAlgorithms(OMITTED_PROVIDER_SERVICE);
        Assert.assertTrue(algorithms.contains(OMITTED_PROVIDER_ALGORITHM.toUpperCase()));
    }

    /** §FS-security-providers.7.3: Tests compatibility-mode provider filtering. */
    @Test
    public void testSecurityGetProvidersFilterIncludesReachableBuiltInProvider() {
        Assert.assertNotNull(Security.getProviders(OMITTED_PROVIDER_SERVICE + "." + OMITTED_PROVIDER_ALGORITHM));
    }
}
