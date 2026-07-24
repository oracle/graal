/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.MacSpi;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.ModuleSupport;
import com.oracle.svm.shared.util.ReflectionUtil;

import sun.security.jca.GetInstance;

/**
 * Tests the {@code SecurityServicesFeature}.
 */
public class SecurityServiceTest {
    private static final String OMITTED_PROVIDER_ALGORITHM = "SHA256withECDSA";
    private static final String OMITTED_PROVIDER_SERVICE = "Signature";
    private static final String MISSING_KEY_GENERATOR_ALGORITHM = "GR69858DefinitelyMissing";
    private static final String REFLECTION_METADATA_PROVIDER_CLASS_NAME = "com.oracle.svm.test.services.SecurityServiceTest$ReflectionMetadataProvider";
    private static final String REFLECTION_METADATA_PROVIDER_NAME = "reflection-metadata-provider";
    private static final String REFLECTION_METADATA_PROVIDER_ALGORITHM = "reflection-metadata-algo";
    private static final String REFLECTION_METADATA_PROVIDER_MAC_ALGORITHM = "reflection-metadata-mac";
    private static final String SERVICE_LOADED_PROVIDER_CLASS_NAME = "com.oracle.svm.test.services.SecurityServiceTest$ServiceLoadedProvider";
    private static final String SERVICE_LOADED_PROVIDER_ALGORITHM = "service-loaded-provider-algo";
    private static final String TYPE_METADATA_PROVIDER_NAME = "type-metadata-provider";
    private static final String TYPE_METADATA_PROVIDER_ALGORITHM = "type-metadata-algo";
    private static final String REACHABLE_PROVIDER_WITHOUT_METADATA_NAME = "reachable-provider-without-metadata";
    private static final String REACHABLE_PROVIDER_WITHOUT_METADATA_ALGORITHM = "reachable-without-metadata-algo";

    public static class TestFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            // register the providers
            Security.addProvider(new NoOpProvider());
            Security.addProvider(new NoOpProviderTwo());
            // open sun.security.jca.GetInstance
            ModuleSupport.accessModuleByClass(ModuleSupport.Access.EXPORT, JCACompliantNoOpService.class,
                            ReflectionUtil.lookupClass(false, "sun.security.jca.GetInstance"));
        }

        @Override
        public void duringSetup(final DuringSetupAccess access) {
            if (!FutureDefaultsOptions.securityProvidersInitializedAtRunTime()) {
                // we use these (application) classes during Native image build
                RuntimeClassInitialization.initializeAtBuildTime(NoOpService.class);
                RuntimeClassInitialization.initializeAtBuildTime(NoOpProvider.class);
                RuntimeClassInitialization.initializeAtBuildTime(NoOpProviderTwo.class);
            }
            // register the service implementation for reflection explicitly,
            // non-standard services are not processed automatically
            RuntimeReflection.register(NoOpImpl.class);
            RuntimeReflection.register(NoOpImpl.class.getDeclaredConstructors());
        }
    }

    /**
     * This test ensures that the list of security providers is populated at run time, and not at
     * build time.
     */
    @Test
    public void testSecurityProviderRuntimeRegistration() {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Provider notRegistered = Security.getProvider("no-op-provider");
        Assert.assertNull("Provider is registered.", notRegistered);

        Security.addProvider(new NoOpProvider());

        Provider registered = Security.getProvider("no-op-provider");
        Assert.assertNotNull("Provider is not registered.", registered);
    }

    /**
     * Tests that native-image generation doesn't run into an issue (like NPE) if the application
     * uses a java.security.Provider.Service which isn't part of the services shipped in the JDK.
     *
     * @throws Exception
     * @see <a href="https://github.com/oracle/graal/issues/1883">issue-1883</a>
     */
    @Test
    public void testUnknownSecurityServices() throws Exception {
        if (FutureDefaultsOptions.securityProvidersInitializedAtRunTime()) {
            /* Register the provider at run time. */
            Security.addProvider(new NoOpProvider());
        }
        final Provider registered = Security.getProvider("no-op-provider");
        Assert.assertNotNull("Provider is not registered", registered);
        final Object impl = registered.getService("NoOp", "no-op-algo").newInstance(null);
        Assert.assertNotNull("No service instance was created", impl);
        MatcherAssert.assertThat("Unexpected service implementation class", impl, CoreMatchers.instanceOf(NoOpImpl.class));
    }

    @Test
    public void testAutomaticSecurityServiceRegistration() {
        try {
            if (FutureDefaultsOptions.securityProvidersInitializedAtRunTime()) {
                /* Register the provider at run time. */
                Security.addProvider(new NoOpProviderTwo());
            }
            JCACompliantNoOpService service = JCACompliantNoOpService.getInstance("no-op-algo-two");
            Assert.assertNotNull("No service instance was created", service);
            MatcherAssert.assertThat("Unexpected service implementation class", service, CoreMatchers.instanceOf(JcaCompliantNoOpServiceImpl.class));
        } catch (NoSuchAlgorithmException e) {
            Assert.fail("Failed to fetch noop service with exception: " + e);
        }
    }

    /** Tests service-driven GSS provider inclusion from §FS-security-providers.7.3. */
    @Test
    public void testGSSProviderServiceRegistration() throws Exception {
        Oid kerberosV5 = new Oid("1.2.840.113554.1.2.2");
        GSSManager manager = GSSManager.getInstance();
        Assert.assertTrue("The reachable GSS facade must preserve the Kerberos mechanism.", Set.of(manager.getMechs()).contains(kerberosV5));
        Assert.assertEquals("user@REALM", manager.createName("user@REALM", GSSName.NT_USER_NAME, kerberosV5).toString());
    }

    // Tests provider registration, complete services, and provider-object factory calls.
    // §FS-security-providers.2.1, §FS-security-providers.2.3, and §FS-security-providers.5.1
    @Test
    public void testReflectionMetadataProviderRegistration() throws Exception {
        Provider provider = (Provider) Class.forName(REFLECTION_METADATA_PROVIDER_CLASS_NAME).getDeclaredConstructor().newInstance();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue("Provider should be registered.", position > 0);
            JCACompliantNoOpService service = JCACompliantNoOpService.getInstance(REFLECTION_METADATA_PROVIDER_ALGORITHM);
            Assert.assertNotNull("No service instance was created", service);
            Assert.assertEquals("Unexpected service implementation class", ReflectionMetadataNoOpServiceImpl.class.getName(), service.getClass().getName());
            Assert.assertNotNull("No JCE service instance was created", Mac.getInstance(REFLECTION_METADATA_PROVIDER_MAC_ALGORITHM, provider));
        } finally {
            Security.removeProvider(REFLECTION_METADATA_PROVIDER_NAME);
        }
    }

    // Tests type-only registration, complete services, and provider-object factory calls.
    // §FS-security-providers.2.1, §FS-security-providers.2.3, and §FS-security-providers.5.1
    @Test
    public void testTypeMetadataProviderRegistration() throws Exception {
        Provider provider = new TypeMetadataProvider();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue("Provider should be registered.", position > 0);
            JCACompliantNoOpService service = JCACompliantNoOpService.getInstance(TYPE_METADATA_PROVIDER_ALGORITHM);
            Assert.assertNotNull("No service instance was created", service);
            Assert.assertEquals("Unexpected service implementation class", TypeMetadataNoOpServiceImpl.class.getName(), service.getClass().getName());
        } finally {
            Security.removeProvider(TYPE_METADATA_PROVIDER_NAME);
        }
    }

    /** Tests §FS-security-providers.4.1. */
    @Test
    public void testReachableProviderWithoutMetadataDoesNotRegisterServices() {
        Provider provider = new ReachableProviderWithoutMetadata();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue("Provider should be registered.", position > 0);
            Assert.assertThrows(NoSuchAlgorithmException.class, () -> JCACompliantNoOpService.getInstance(REACHABLE_PROVIDER_WITHOUT_METADATA_ALGORITHM));
        } finally {
            Security.removeProvider(REACHABLE_PROVIDER_WITHOUT_METADATA_NAME);
        }
    }

    /** Tests §FS-security-providers.7.2. */
    @Test
    public void testServiceLoaderProviderWithoutMetadataUsesReflectionLookupFailure() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());

        Assert.assertThrows(ClassNotFoundException.class, () -> Class.forName(SERVICE_LOADED_PROVIDER_CLASS_NAME));
        ServiceConfigurationError serviceLoaderError = Assert.assertThrows(ServiceConfigurationError.class, () -> ServiceLoader.load(Provider.class).stream()
                        .anyMatch(provider -> provider.type().getName().equals(SERVICE_LOADED_PROVIDER_CLASS_NAME)));
        Assert.assertTrue("ServiceLoader should report the missing provider class.", serviceLoaderError.getMessage().contains(SERVICE_LOADED_PROVIDER_CLASS_NAME));
        Assert.assertTrue("ServiceLoader should use the standard reflection lookup failure.", serviceLoaderError.getCause() instanceof ClassNotFoundException);

        Assert.assertThrows(NoSuchAlgorithmException.class, () -> JCACompliantNoOpService.getInstance(SERVICE_LOADED_PROVIDER_ALGORITHM));
    }

    /** Tests §FS-security-providers.7.2. */
    @Test
    public void testServiceLoaderProviderWithMetadataIsPreserved() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());

        try {
            Provider provider = ServiceLoader.load(Provider.class).stream()
                            .filter(candidate -> candidate.type().getName().equals(REFLECTION_METADATA_PROVIDER_CLASS_NAME))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Metadata-registered security provider should be visible through ServiceLoader."))
                            .get();
            Assert.assertEquals("Unexpected provider name", REFLECTION_METADATA_PROVIDER_NAME, provider.getName());
        } catch (ServiceConfigurationError e) {
            Assert.fail("Metadata-registered security provider should be loadable through ServiceLoader: " + e);
        }
    }

    @Delete
    @TargetClass(className = "sun.security.pkcs11.SunPKCS11")
    static final class Target_sun_security_pkcs11_SunPKCS11 {
    }

    /**
     * Tests whether a provider annotated with @Delete is not present at run time.
     */
    @Test
    public void testDeletedProvider() {
        final Provider registered = Security.getProvider("SunPKCS11");
        Assert.assertNull("Provider should not be present.", registered);
    }

    /** Tests the compatibility behavior in §FS-security-providers.7.3. */
    @Test
    public void testReachableBuiltInProviderIsIncluded() {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Assert.assertNotNull("Service-driven registration should include SunEC.", Security.getProvider("SunEC"));
    }

    /** Tests the compatibility behavior in §FS-security-providers.7.3. */
    @Test
    public void testReachableBuiltInProviderGetService() throws NoSuchAlgorithmException {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Provider.Service service = GetInstance.getService(OMITTED_PROVIDER_SERVICE, OMITTED_PROVIDER_ALGORITHM);
        Assert.assertEquals("SunEC", service.getProvider().getName());
    }

    /** Tests the compatibility behavior in §FS-security-providers.7.3. */
    @Test
    public void testReachableBuiltInProviderGetInstance() throws NoSuchAlgorithmException {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Assert.assertNotNull(GetInstance.getInstance(OMITTED_PROVIDER_SERVICE, null, OMITTED_PROVIDER_ALGORITHM));
    }

    /** Tests the compatibility behavior in §FS-security-providers.7.3. */
    @Test
    public void testReachableBuiltInProviderGetServices() {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Iterator<Provider.Service> services = GetInstance.getServices(OMITTED_PROVIDER_SERVICE, OMITTED_PROVIDER_ALGORITHM);
        Assert.assertTrue("Generic service iteration should include the reachable built-in provider.", services.hasNext());
    }

    /** Tests the standard unavailable result from §FS-security-providers.4.2. */
    @Test
    public void testGenericMissingAlgorithmExhaustsProviderList() {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Assert.assertThrows(NoSuchAlgorithmException.class, () -> KeyGenerator.getInstance(MISSING_KEY_GENERATOR_ALGORITHM));
    }

    /** Tests the compatibility behavior in §FS-security-providers.7.3. */
    @Test
    public void testSecurityGetAlgorithmsIncludesReachableBuiltInProviderAlgorithm() {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Set<String> algorithms = Security.getAlgorithms(OMITTED_PROVIDER_SERVICE);
        Assert.assertTrue("Generic algorithm discovery should expose the reachable built-in provider algorithm.",
                        algorithms.contains(OMITTED_PROVIDER_ALGORITHM.toUpperCase()));
    }

    /** Tests the compatibility behavior in §FS-security-providers.7.3. */
    @Test
    public void testSecurityGetProvidersFilterIncludesReachableBuiltInProvider() {
        Assume.assumeTrue("needs runtime initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());
        Assert.assertNotNull("Provider filtering should include algorithms from the reachable built-in provider.",
                        Security.getProviders(OMITTED_PROVIDER_SERVICE + "." + OMITTED_PROVIDER_ALGORITHM));
    }

    private static final class NoOpProvider extends Provider {

        static final long serialVersionUID = 1234L;

        /*
         * The java.security.Provider(String name, double version, String info) constructor was
         * deprecated in Java > 8
         */
        @SuppressWarnings("deprecation")
        protected NoOpProvider() {
            super("no-op-provider", 1.0, "No-op provider used in " + SecurityServiceTest.class.getName());
            putService(new NoOpService(this));
        }
    }

    private static final class NoOpService extends Provider.Service {
        NoOpService(final Provider provider) {
            super(provider, "NoOp", "no-op-algo", NoOpImpl.class.getName(), null, null);
        }
    }

    public static final class NoOpImpl {
        public NoOpImpl() {

        }
    }

    private static final class NoOpProviderTwo extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        protected NoOpProviderTwo() {
            super("no-op-provider-two", 1.0, "No-op provider two used in " + SecurityServiceTest.class.getName());
            putService(new Service(this, "JCACompliantNoOpService", "no-op-algo-two", JcaCompliantNoOpServiceImpl.class.getName(), null, null));
        }
    }

    /*
     * Service class' simple name must match its type. The service must also have a getInstance
     * method used to obtain its' instance.
     */
    private abstract static class JCACompliantNoOpService {
        public static JCACompliantNoOpService getInstance(String algorithm) throws NoSuchAlgorithmException {
            return (JCACompliantNoOpService) GetInstance.getInstance("JCACompliantNoOpService", null, algorithm).impl;
        }
    }

    public static class JcaCompliantNoOpServiceImpl extends JCACompliantNoOpService {
    }

    public static final class ReflectionMetadataNoOpServiceImpl extends JCACompliantNoOpService {
    }

    public static final class TypeMetadataNoOpServiceImpl extends JCACompliantNoOpService {
    }

    public static final class ReachableNoOpServiceImpl extends JCACompliantNoOpService {
    }

    public static final class ReflectionMetadataProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public ReflectionMetadataProvider() {
            super(REFLECTION_METADATA_PROVIDER_NAME, 1.0, "Provider registered through reflection metadata");
            putService(new Service(this, "JCACompliantNoOpService", REFLECTION_METADATA_PROVIDER_ALGORITHM,
                            ReflectionMetadataNoOpServiceImpl.class.getName(), null, null));
            putService(new Service(this, "Mac", REFLECTION_METADATA_PROVIDER_MAC_ALGORITHM, ReflectionMetadataMacSpi.class.getName(), null, null));
        }
    }

    public static final class TypeMetadataProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public TypeMetadataProvider() {
            super(TYPE_METADATA_PROVIDER_NAME, 1.0, "Provider registered through type-level reflection metadata");
            putService(new Service(this, "JCACompliantNoOpService", TYPE_METADATA_PROVIDER_ALGORITHM,
                            TypeMetadataNoOpServiceImpl.class.getName(), null, null));
        }
    }

    public static final class ReachableProviderWithoutMetadata extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public ReachableProviderWithoutMetadata() {
            super(REACHABLE_PROVIDER_WITHOUT_METADATA_NAME, 1.0, "Reachable provider without reflection metadata");
            putService(new Service(this, "JCACompliantNoOpService", REACHABLE_PROVIDER_WITHOUT_METADATA_ALGORITHM,
                            ReachableNoOpServiceImpl.class.getName(), null, null));
        }
    }

    public static final class ReflectionMetadataMacSpi extends MacSpi {
        @Override
        protected int engineGetMacLength() {
            return 0;
        }

        @Override
        protected void engineInit(Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException {
        }

        @Override
        protected void engineUpdate(byte input) {
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {
        }

        @Override
        protected byte[] engineDoFinal() {
            return new byte[0];
        }

        @Override
        protected void engineReset() {
        }
    }

    public static final class ServiceLoadedProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public ServiceLoadedProvider() {
            super("service-loaded-provider", 1.0, "Provider registered only through META-INF/services");
            putService(new Service(this, "JCACompliantNoOpService", SERVICE_LOADED_PROVIDER_ALGORITHM,
                            ReflectionMetadataNoOpServiceImpl.class.getName(), null, null));
        }
    }
}
