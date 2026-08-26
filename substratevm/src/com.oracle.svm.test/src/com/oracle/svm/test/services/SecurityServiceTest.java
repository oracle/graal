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

import javax.crypto.MacSpi;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
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
    private static final String TYPE_METADATA_PROVIDER_NAME = "type-metadata-provider";
    private static final String TYPE_METADATA_PROVIDER_ALGORITHM = "type-metadata-algo";
    private static final String FAILED_VERIFICATION_PROVIDER_MAC_ALGORITHM = "failed-verification-mac";

    public static class TestFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            // register the providers
            Security.addProvider(new NoOpProviderTwo());
            // open sun.security.jca.GetInstance
            ModuleSupport.accessModuleByClass(ModuleSupport.Access.EXPORT, JCACompliantNoOpService.class,
                            ReflectionUtil.lookupClass(false, "sun.security.jca.GetInstance"));
        }

        @Override
        public void duringSetup(final DuringSetupAccess access) {
            if (!FutureDefaultsOptions.securityProvidersInitializedAtRunTime()) {
                // we use these (application) classes during Native image build
                RuntimeClassInitialization.initializeAtBuildTime(NoOpProviderTwo.class);
            }
            RuntimeClassInitialization.initializeAtBuildTime(ImageHeapProvider.class);
            RuntimeClassInitialization.initializeAtBuildTime(ImageHeapProviderHolder.class);
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            /*
             * Deterministically model the negative outcome produced by build-time JCE
             * authentication. Registering the later successful catalog result must not erase it.
             */
            SecurityProviderRuntimeState.currentLayer().registerProvider(
                            FailedVerificationProvider.class.getName(),
                            SecurityProviderRuntimeState.AcquisitionKind.APPLICATION_SUPPLIED_ONLY,
                            new SecurityException("simulated build-time provider verification failure"),
                            RuntimeDynamicAccessMetadata.alwaysAvailable(false));
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

    public static final class ReflectionMetadataProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public ReflectionMetadataProvider() {
            super(REFLECTION_METADATA_PROVIDER_NAME, 1.0, "Provider registered through reflection metadata");
            if (ImageInfo.inImageBuildtimeCode() && !FutureDefaultsOptions.metadataSecurityProviderRegistration()) {
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
            putService(new Service(this, "Mac", "type-metadata-mac", ReflectionMetadataMacSpi.class.getName(), null, null));
        }
    }

    public static final class ReachableProviderWithoutMetadata extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public ReachableProviderWithoutMetadata() {
            super("reachable-provider-without-metadata", 1.0, "Reachable provider without reflection metadata");
        }
    }

    static final class ImageHeapProviderHolder {
        static final Provider PROVIDER = new ImageHeapProvider();
    }

    public static final class ImageHeapProvider extends Provider {
        static final long serialVersionUID = 1234L;

        @SuppressWarnings("deprecation")
        public ImageHeapProvider() {
            super("image-heap-provider-without-metadata", 1.0, "Image-heap provider without reflection metadata");
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

}
