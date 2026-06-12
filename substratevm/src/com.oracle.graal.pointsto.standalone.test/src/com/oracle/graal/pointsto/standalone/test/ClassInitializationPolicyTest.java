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

package com.oracle.graal.pointsto.standalone.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.standalone.StandaloneHost.ClassInitializationOutcome;
import com.oracle.graal.pointsto.standalone.StandaloneOptions;
import com.oracle.graal.pointsto.standalone.test.classes.ClassInitializationPolicyCase.BuildTimePrecisionCase;
import com.oracle.graal.pointsto.standalone.test.classes.ClassInitializationPolicyCase.FailingInitializationCase;
import com.oracle.graal.pointsto.standalone.test.classes.ClassInitializationPolicyCase.FallbackWorker;
import com.oracle.graal.pointsto.standalone.test.classes.ClassInitializationPolicyCase.PreciseResult;
import com.oracle.graal.pointsto.standalone.test.classes.ClassInitializationPolicyCase.PreciseWorker;
import com.oracle.graal.pointsto.standalone.test.classes.ClassInitializationPolicyCase.RuntimeOnlyPrecisionCase;
import com.oracle.graal.pointsto.standalone.test.classes.classinitpolicy.PackageRuntimeOnlyCase;

/**
 * Verifies standalone build-time class-initialization policy outcomes and their effect on static
 * field precision.
 */
public class ClassInitializationPolicyTest extends StandaloneAnalysisTest {
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final String DISABLE_CLASS_INITIALIZATION_FAILURE_PRINTING = "-H:-" + StandaloneOptions.StandalonePrintClassInitializationFailures.getName();

    @Test
    public void testBuildTimeInitializationUsesFinalStaticFieldValue() {
        runAnalysisMethod(BuildTimePrecisionCase.class, "entry");

        assertClassInitializationOutcome(BuildTimePrecisionCase.class, ClassInitializationOutcome.INITIALIZED);
        assertNotReachable(findClassInitializer(BuildTimePrecisionCase.class));

        AnalysisMethod entry = findMethod(BuildTimePrecisionCase.class, "entry");
        assertResultTypes(entry, PreciseResult.class);
        assertInvokeCallees(entry, findOnlyInvokeBci(entry), findMethod(PreciseWorker.class, "work"));
    }

    @Test
    public void testExactRuntimeOnlyPolicyRootsClassInitializerAndKeepsStaticFieldFlow() {
        runAnalysisMethod(RuntimeOnlyPrecisionCase.class, "entry", NO_PARAMETERS, runtimeOnlyClassOption(RuntimeOnlyPrecisionCase.class));

        assertClassInitializationOutcome(RuntimeOnlyPrecisionCase.class, ClassInitializationOutcome.RUNTIME_ONLY);
        assertReachable(findClassInitializer(RuntimeOnlyPrecisionCase.class));
        assertFieldTypes(findField(RuntimeOnlyPrecisionCase.class, "worker"), FallbackWorker.class, PreciseWorker.class);
    }

    @Test
    public void testPackageRuntimeOnlyPolicyRootsClassInitializer() {
        runAnalysisMethod(PackageRuntimeOnlyCase.class, "entry", NO_PARAMETERS, runtimeOnlyPackageOption(PackageRuntimeOnlyCase.class));

        assertClassInitializationOutcome(PackageRuntimeOnlyCase.class, ClassInitializationOutcome.RUNTIME_ONLY);
        assertReachable(findClassInitializer(PackageRuntimeOnlyCase.class));
    }

    @Test
    public void testFailedBuildTimeInitializationFallsBackToRuntimeHandling() {
        runAnalysisMethod(FailingInitializationCase.class, "entry", NO_PARAMETERS, DISABLE_CLASS_INITIALIZATION_FAILURE_PRINTING);

        assertClassInitializationOutcome(FailingInitializationCase.class, ClassInitializationOutcome.FAILED);
        assertReachable(findClassInitializer(FailingInitializationCase.class));
        assertEquals("Expected one failed class-initialization attempt.", 1, standaloneHost().getClassInitializationFailureCount());
        assertEquals("Expected one class with a recorded class-initialization failure.", 1, standaloneHost().getClassInitializationFailureTypeCount());
    }

    private AnalysisType assertClassInitializationOutcome(Class<?> clazz, ClassInitializationOutcome expectedOutcome) {
        AnalysisType type = findClass(clazz);
        assertNotNull("Expected the fixture class to be present in the analysis universe.", type);
        assertEquals("Unexpected class-initialization outcome for " + clazz.getTypeName(), expectedOutcome, standaloneHost().getClassInitializationOutcome(type));
        return type;
    }

    private static String runtimeOnlyClassOption(Class<?> clazz) {
        return "-H:" + StandaloneOptions.StandaloneExtraRuntimeInitializedClasses.getName() + "=" + clazz.getName();
    }

    private static String runtimeOnlyPackageOption(Class<?> clazz) {
        return "-H:" + StandaloneOptions.StandaloneExtraRuntimeInitializedPackages.getName() + "=" + clazz.getPackageName();
    }
}
