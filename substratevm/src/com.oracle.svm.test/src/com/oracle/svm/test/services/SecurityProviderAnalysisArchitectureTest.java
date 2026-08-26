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

import java.security.Provider;

import javax.crypto.Mac;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;

public class SecurityProviderAnalysisArchitectureTest {
    /** Tests §AR-001-security-providers.3 and §AR-001-security-providers.4. */
    @Test
    public void typeMetadataCreatesApplicationSuppliedVerificationState() throws Exception {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        Provider provider = new SecurityServiceTest.TypeMetadataProvider();
        SecurityProviderRuntimeState.ProviderInfo info = SecurityProviderRuntimeState.getProviderInfo(provider);
        Assert.assertNotNull("Type-only provider metadata must establish a JCE verification result.", info);
        Assert.assertEquals(SecurityProviderRuntimeState.AcquisitionKind.APPLICATION_SUPPLIED_ONLY, info.acquisitionKind());
        Assert.assertNull("The application-supplied provider should pass class-based verification.", info.verificationFailure());
        Assert.assertNotNull(Mac.getInstance("type-metadata-mac", provider));
    }

    /** Tests §AR-001-security-providers.4 manifest merge semantics. */
    @Test
    public void failedBuildTimeVerificationIsNotOverwritten() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        Provider provider = new SecurityServiceTest.FailedVerificationProvider();
        SecurityProviderRuntimeState.ProviderInfo info = SecurityProviderRuntimeState.getProviderInfo(provider);
        Assert.assertNotNull("The failed verification outcome must be retained.", info);
        Assert.assertNotNull("A successful later catalog pass must not erase the failure.", info.verificationFailure());
        Assert.assertTrue(info.verificationFailure().getMessage().contains("simulated build-time provider verification failure"));
        Assert.assertThrows(SecurityException.class, () -> Mac.getInstance("failed-verification-mac", provider));
    }

    /** Tests §AR-001-security-providers.3 without granting JDK construction eligibility. */
    @Test
    public void instantiatedProviderWithoutMetadataIsValidationOnly() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        assertApplicationSuppliedProviderWasValidated(new SecurityServiceTest.ReachableProviderWithoutMetadata());
    }

    /** §AR-001-security-providers.3: An image-heap provider instance is validated. */
    @Test
    public void imageHeapProviderWithoutMetadataIsValidated() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());
        assertApplicationSuppliedProviderWasValidated(SecurityServiceTest.ImageHeapProviderHolder.PROVIDER);
    }

    private static void assertApplicationSuppliedProviderWasValidated(Provider provider) {
        SecurityProviderRuntimeState.ProviderInfo info = SecurityProviderRuntimeState.getProviderInfo(provider);
        Assert.assertNotNull("An instantiated provider must have a build-time validation outcome.", info);
        Assert.assertEquals(SecurityProviderRuntimeState.AcquisitionKind.APPLICATION_SUPPLIED_ONLY, info.acquisitionKind());
        Assert.assertNull("The test provider should pass build-time validation.", info.verificationFailure());
    }
}
