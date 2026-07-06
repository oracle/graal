/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.Test;

import com.oracle.svm.core.util.TimeUtils;

import jdk.graal.compiler.core.test.CheckGraalInvariants;
import jdk.graal.compiler.core.test.CheckGraalInvariants.InvariantsTool;
import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.core.test.VerifyPhase.VerificationError;
import jdk.graal.compiler.core.test.VerifyUsageWithEquals;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaModuleLayer;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.internal.module.Modules;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Checks that all SVM classes comply with global invariants. This check inherits most check from
 * {@link CheckGraalInvariants}, for example to verify the use of {@link Object#equals(Object)} to
 * compare certain types instead of identity comparisons, but also adds SVM specific tests in
 * {@link SVMInvariantsTool#updateVerifiers(List)}.
 */
public class CheckSVMInvariants {

    protected VerifyReflectionUsage createVerifyReflectionUsage() {
        return VerifyReflectionUsage.create();
    }

    protected VerifyJavaTypeGetNameUsage createVerifyJavaTypeGetNameUsage() {
        return VerifyJavaTypeGetNameUsage.create();
    }

    /**
     * A {@link InvariantsTool} that only filters for svm classes and jars.
     */
    private final class SVMInvariantsTool extends CheckGraalInvariants.InvariantsTool {

        public static final String SVMINVARIANTS_JARS_PROP = "svm.test.CheckSVMInvariants.jars";

        @Override
        protected boolean shouldProcess(String classpathEntry) {
            if (classpathEntry.endsWith(".jar")) {
                /* The property is set in SVMInvariantsUnittestConfig in mx_substratevm. */
                String property = Objects.requireNonNull(System.getProperty(SVMINVARIANTS_JARS_PROP), "Required system property not set: " + SVMINVARIANTS_JARS_PROP);
                Set<String> jarsUnderTest = Set.of(property.split(File.pathSeparator));
                if (jarsUnderTest.contains(classpathEntry)) {
                    String name = new File(classpathEntry).getName();
                    return !name.contains("test");
                }
            }
            return false;
        }

        @Override
        protected List<String> getClassPath() {
            String additionalClassPath = System.getProperty("java.class.path");
            if (additionalClassPath != null) {
                return List.of(additionalClassPath.split(File.pathSeparator));
            }
            return super.getClassPath();
        }

        @Override
        public boolean shouldVerifyLibGraalInvariants() {
            return false;
        }

        @Override
        public boolean shouldVerifyFoldableMethods() {
            return false;
        }

        @Override
        public void verifyCurrentTimeMillis(MetaAccessProvider meta, MethodCallTargetNode t, ResolvedJavaType declaringClass) {
            ResolvedJavaType timeUtils = meta.lookupJavaType(TimeUtils.class);
            if (!declaringClass.equals(timeUtils)) {
                throw new VerificationError(t, "Should use System.nanoTime() for measuring elapsed time or TimeUtils.currentTimeMillis() for the time since the epoch");
            }
        }

        @Override
        protected void updateVerifiers(List<VerifyPhase<CoreProviders>> verifiers) {
            verifiers.add(new VerifyUserErrorUsage());
            verifiers.add(new VerifyRuntimeVersionFeature());
            verifiers.add(createVerifyReflectionUsage());
            verifiers.add(createVerifyJavaTypeGetNameUsage());
            verifiers.add(new VerifyRistrettoInvariants());
            verifiers.add(new VerifyRuntimeCompilationInvariants());
            verifiers.add(new VerifyUsageWithEquals(ResolvedJavaModule.class));
            verifiers.add(new VerifyUsageWithEquals(ResolvedJavaModuleLayer.class));
            verifiers.add(new VerifyUsageWithEquals(ResolvedJavaPackage.class));
            verifiers.add(new VerifySafeCCalls());
        }

        @Override
        public boolean shouldCheckUsage(OptionDescriptor option) {
            // TODO Figure out how to check only SVM options as this tool
            // ensures only SVM classes are scanned for option usages.
            return false;
        }

        @Override
        public boolean checkAssertions() {
            return false;
        }

    }

    @Test
    public void checkSVMInvariants() {
        /*
         * We don't want to bother with listing packages that the native image generator needs
         * access to, so we just open up all packages to everyone.
         */
        for (String moduleName : List.of("java.base", "jdk.jfr", "java.management", "jdk.management", "jdk.management.agent")) {
            Module module = ModuleLayer.boot().findModule(moduleName).get();
            for (String p : module.getPackages()) {
                Modules.addExports(module, p);
            }
        }

        CheckGraalInvariants.runTest(new SVMInvariantsTool());
    }

}
