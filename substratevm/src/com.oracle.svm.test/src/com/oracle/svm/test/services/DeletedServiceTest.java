/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ServiceLoader;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

// Checkstyle: allow Class.getSimpleName

/**
 * Test if the service marked with {@link Delete} is not present at run time.
 */
public class DeletedServiceTest {

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeClassInitialization.initializeAtBuildTime(DeletedService.class);
            RuntimeClassInitialization.initializeAtBuildTime(ServiceInterface.class);
        }
    }

    interface ServiceInterface {
    }

    /* Registered and deleted service. */
    public static class DeletedService implements ServiceInterface {
    }

    @Delete
    @TargetClass(DeletedService.class)
    static final class Target_com_oracle_svm_test_ServiceLoaderTest_DeletedService {
    }

    @Test
    public void testDeletedService() {
        ServiceLoader<ServiceInterface> loader = ServiceLoader.load(ServiceInterface.class);

        int numFound = 0;
        boolean foundDeleted = false;

        for (ServiceInterface instance : loader) {
            numFound++;
            String name = instance.getClass().getSimpleName();
            foundDeleted |= name.equals("DeletedService");
        }

        Assert.assertFalse("Should not find a service that is deleted.", foundDeleted);
        Assert.assertEquals(0, numFound);
    }
}
