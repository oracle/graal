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
import java.util.Set;

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
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;
import com.oracle.svm.shared.util.ModuleSupport;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.test.NativeImageBuildArgs;

import sun.security.jca.GetInstance;

/**
 * Tests the {@code SecurityServicesFeature}.
 */
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:AdditionalSecurityServiceTypes=com.oracle.svm.test.services.SecurityServiceTest$JCACompliantNoOpService",
                "-H:-UnlockExperimentalVMOptions"
})
public class SecurityServiceTest {
    private static final String REFLECTION_METADATA_PROVIDER_NAME = "reflection-metadata-provider";
    private static final String REFLECTION_METADATA_PROVIDER_ALGORITHM = "reflection-metadata-algo";
    private static final String REFLECTION_METADATA_PROVIDER_MAC_ALGORITHM = "reflection-metadata-mac";
    private static final String SERVICE_LOADED_PROVIDER_ALGORITHM = "service-loaded-provider-algo";
    private static final String TYPE_METADATA_PROVIDER_NAME = "type-metadata-provider";
    private static final String TYPE_METADATA_PROVIDER_ALGORITHM = "type-metadata-algo";
    private static final String TYPE_METADATA_PROVIDER_MAC_ALGORITHM = "type-metadata-mac";
    private static final String FAILED_VERIFICATION_PROVIDER_MAC_ALGORITHM = "failed-verification-mac";
    private static final String REACHABLE_PROVIDER_WITHOUT_METADATA_NAME = "reachable-provider-without-metadata";
    private static final String REACHABLE_PROVIDER_WITHOUT_METADATA_ALGORITHM = "reachable-without-metadata-algo";

    public static class TestFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            // register the providers
            Security.addProvider(new NoOpProvider());
            Security.addProvider(new NoOpProviderTwo());
            Security.addProvider(new LegacyConstructorProvider());
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
                RuntimeClassInitialization.initializeAtBuildTime(LegacyConstructorProvider.class);
            }
            // register the service implementation for reflection explicitly,
            // non-standard services are not processed automatically
            RuntimeReflection.register(NoOpImpl.class);
            RuntimeReflection.register(NoOpImpl.class.getDeclaredConstructors());
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            /*
             * Deterministically model the negative outcome produced by build-time JCE
             * authentication. Registering the later successful catalog result must not erase it.
             */
            SecurityProviderRuntimeState.currentLayer().registerApplicationSuppliedProvider(
                            FailedVerificationProvider.class.getName(),
                            new SecurityException("simulated build-time provider verification failure"));
        }
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

    /** §FS-security-providers.7.3: Tests the public-constructor compatibility surface. */
    @Test
    public void testLegacyServiceInclusionRegistersEveryPublicProviderConstructor() throws Exception {
        Assert.assertFalse(FutureDefaultsOptions.explicitSecurityProviderRegistration());
        Provider provider = LegacyConstructorProvider.class.getConstructor(String.class).newInstance("reflected");
        Assert.assertEquals("legacy-constructor-provider-reflected", provider.getName());
    }

    /** §FS-security-providers.7.3: Tests service-driven GSS provider inclusion. */
    @Test
    public void testGSSProviderServiceRegistration() throws Exception {
        Oid kerberosV5 = new Oid("1.2.840.113554.1.2.2");
        GSSManager manager = GSSManager.getInstance();
        Assert.assertTrue("The reachable GSS facade must preserve the Kerberos mechanism.", Set.of(manager.getMechs()).contains(kerberosV5));
        Assert.assertEquals("user@REALM", manager.createName("user@REALM", GSSName.NT_USER_NAME, kerberosV5).toString());
    }

    /** §FS-security-providers.5.3 and §FS-security-providers.7.3: Tests compatibility-mode verification. */
    @Test
    public void testTypeMetadataApplicationProviderVerification() throws Exception {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        Assume.assumeFalse("tests compatibility-mode verification", FutureDefaultsOptions.explicitSecurityProviderRegistration());

        Provider provider = new TypeMetadataProvider();
        SecurityProviderRuntimeState.ProviderInfo info = SecurityProviderRuntimeState.getProviderInfo(provider);
        Assert.assertNotNull("Type-only provider metadata must establish a JCE verification result.", info);
        Assert.assertEquals(SecurityProviderRuntimeState.AcquisitionKind.APPLICATION_SUPPLIED_ONLY, info.acquisitionKind());
        Assert.assertNull("The application-supplied provider should pass class-based verification.", info.verificationFailure());
        Assert.assertNotNull(Mac.getInstance(TYPE_METADATA_PROVIDER_MAC_ALGORITHM, provider));
    }

    /** §FS-security-providers.5.3: Tests preservation of the failed-verification outcome. */
    @Test
    public void testFailedBuildTimeProviderVerificationStaysUnusable() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        Provider provider = new FailedVerificationProvider();
        SecurityProviderRuntimeState.ProviderInfo info = SecurityProviderRuntimeState.getProviderInfo(provider);
        Assert.assertNotNull("The failed verification outcome must be retained.", info);
        Assert.assertNotNull("A successful later catalog pass must not erase the failure.", info.verificationFailure());
        Assert.assertTrue(info.verificationFailure().getMessage().contains("simulated build-time provider verification failure"));
        Assert.assertThrows(SecurityException.class,
                        () -> Mac.getInstance(FAILED_VERIFICATION_PROVIDER_MAC_ALGORITHM, provider));
    }

    /** §FS-security-providers.4.1: Tests omission without registration metadata. */
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

    /** §FS-security-providers.4.3: Tests the non-exact diagnostic. */
    @Test
    public void testUnregisteredJceProviderReportsActionableDiagnostic() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        Assume.assumeFalse("tests the compatibility-mode fallback", FutureDefaultsOptions.explicitSecurityProviderRegistration());

        Provider provider = new UnregisteredMacProvider();
        SecurityException error = Assert.assertThrows(SecurityException.class,
                        () -> Mac.getInstance("unregistered-mac", provider));
        Assert.assertTrue("The diagnostic must identify the provider type",
                        error.getMessage().contains(UnregisteredMacProvider.class.getName()));
        Assert.assertTrue("The compatibility-mode diagnostic must explain that metadata is inert for services.",
                        error.getMessage().contains("does not enable provider construction or services in compatibility mode"));
        Assert.assertTrue("The diagnostic must name the explicit registration migration.",
                        error.getMessage().contains("--future-defaults=explicit-security-provider-registration"));
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

    static final class NoOpProvider extends Provider {

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

    public static final class LegacyConstructorProvider extends Provider {
        static final long serialVersionUID = 1234L;

        public LegacyConstructorProvider() {
            this("default");
        }

        @SuppressWarnings("deprecation")
        public LegacyConstructorProvider(String configuration) {
            super("legacy-constructor-provider-" + configuration, 1.0, "Provider with legacy public constructors");
            putService(new Service(this, "JCACompliantNoOpService", "legacy-constructor-algo",
                            JcaCompliantNoOpServiceImpl.class.getName(), null, null));
        }
    }

    /*
     * Service class' simple name must match its type. The service must also have a getInstance
     * method used to obtain its' instance.
     */
    abstract static class JCACompliantNoOpService {
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
            if (ImageInfo.inImageBuildtimeCode() && !FutureDefaultsOptions.explicitSecurityProviderRegistration()) {
                throw new AssertionError("Compatibility mode must not instantiate a provider solely because it has reflection metadata.");
            }
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
            putService(new Service(this, "Mac", TYPE_METADATA_PROVIDER_MAC_ALGORITHM, ReflectionMetadataMacSpi.class.getName(), null, null));
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

    public static final class UnregisteredMacProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public UnregisteredMacProvider() {
            super("unregistered-mac-provider", 1.0, "Provider used to test missing-registration diagnostics");
            putService(new Service(this, "Mac", "unregistered-mac", ReflectionMetadataMacSpi.class.getName(), null, null));
        }
    }

    public static final class FailedVerificationProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public FailedVerificationProvider() {
            super("failed-verification-provider", 1.0, "Provider with a preserved build-time verification failure");
            putService(new Service(this, "Mac", FAILED_VERIFICATION_PROVIDER_MAC_ALGORITHM,
                            ReflectionMetadataMacSpi.class.getName(), null, null));
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
