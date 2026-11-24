/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalScope;

import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.test.AddModules;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIIntrinsics;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests based on special entry points in libgraal to test libgraal implementation details.
 */
@AddModules("jdk.incubator.vector")
public class LibGraalCompilerTest extends HotSpotGraalCompilerTest {

    static {
        if (LibGraal.isAvailable()) {
            LibGraal.registerNativeMethods(LibGraalCompilerTest.class);
        }
    }

    /**
     * Constant object fields that will create a remote oop reference when their value is read
     * within libgraal.
     */
    private static final String OOP_FIELD = "an oop";
    private static final Thread OOP_FIELD2 = new Thread("another oop");

    private static final String CLASS_NAME = System.getProperty("className");
    private static final boolean VERBOSE = Boolean.getBoolean("verbose");
    private static final int ITERATIONS = Integer.getInteger("iterations", 10);
    private static final int OOPS_PER_ITERATION = Integer.getInteger("oopsPerIteration", 1000);

    /**
     * Computes a hash based on the static final Object fields in {@code typeHandle}.
     *
     * Implemented by
     * {@code com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints#hashConstantOopFields}.
     *
     * @param isolateThreadAddress
     * @param typeHandle the type whose constant object fields are to be hashed
     * @param useScope should a {@linkplain HotSpotGraalServices#openLocalCompilationContext(Object)
     *            scope} be used for each iteration. If true, then
     *            {@code jdk.vm.ci.hotspot.HotSpotObjectConstantScope.close()} is being tested.
     *            Otherwise, {@code jdk.vm.ci.hotspot.Cleaner.clean()} is being tested.
     * @param iterations number of times to run the hashing loop
     * @param oopsPerIteration number of oops to process per hashing loop
     */
    public static native long hashConstantOopFields(long isolateThreadAddress,
                    long typeHandle,
                    boolean useScope,
                    int iterations,
                    int oopsPerIteration,
                    boolean verbose);

    @Test
    public void testHashConstantFields() throws ClassNotFoundException {
        Assume.assumeTrue(LibGraal.isAvailable());
        Class<?> cls = CLASS_NAME != null ? Class.forName(CLASS_NAME) : getClass();
        ResolvedJavaType type = getMetaAccess().lookupJavaType(cls);
        try (LibGraalScope _ = new LibGraalScope()) {
            long isolateThread = LibGraalScope.getIsolateThread();
            for (boolean useScope : new boolean[]{true, false}) {
                long hash = hashConstantOopFields(isolateThread,
                                LibGraal.translate(type),
                                useScope,
                                ITERATIONS,
                                OOPS_PER_ITERATION,
                                VERBOSE);
                if (VERBOSE) {
                    System.out.println("hash=" + hash);
                }
            }
        }
    }

    /**
     * Compiles a method inside libgraal and checks the number of unary and binary Vector API
     * library foreign calls in its graph. Java fallbacks must not satisfy this check.
     */
    private static native void checkVectorAPILibraryCalls(long isolateThreadAddress, long methodHandle, int unaryCalls, int binaryCalls);

    /**
     * Verifies expansion of unary and binary Vector API library calls by compiling the snippet
     * inside libgraal, where library symbol names refer to strings in the application JVM.
     */
    @Test
    public void testVectorAPILibraryCalls() {
        Assume.assumeTrue(LibGraal.isAvailable());
        Assume.assumeTrue(VectorAPIIntrinsics.intrinsificationSupported(getInitialOptions()));
        if (getArchitecture() instanceof AMD64 amd64) {
            Assume.assumeTrue(amd64.getFeatures().contains(AMD64.CPUFeature.AVX));
            /*
             * Match the size checks in VectorAPITest.testVectorAPILibraryForeignCalls. The JDK's
             * CPUFeatures assertions require these sizes when the library is initialized.
             */
            Assume.assumeTrue("JDK CPUFeatures requires at least 256 bits with AVX", FloatVector.SPECIES_PREFERRED.vectorBitSize() >= 256);
            if (amd64.getFeatures().contains(AMD64.CPUFeature.AVX512F)) {
                Assume.assumeTrue("JDK CPUFeatures requires at least 512 bits with AVX512F", FloatVector.SPECIES_PREFERRED.vectorBitSize() >= 512);
            }
        }
        Assume.assumeTrue(FloatVector.SPECIES_PREFERRED.vectorBitSize() >= 128);
        float[] src = new float[FloatVector.SPECIES_PREFERRED.length()];
        float[] dst = new float[src.length];
        /* Initialize the library entries and collect profiles before compiling through libgraal. */
        for (int i = 0; i < 5000; i++) {
            vectorAPILibraryCallsSnippet(src, dst);
        }
        try (LibGraalScope _ = new LibGraalScope()) {
            checkVectorAPILibraryCalls(LibGraalScope.getIsolateThread(), LibGraal.translate(getResolvedJavaMethod("vectorAPILibraryCallsSnippet")), 1, 1);
        }
    }

    /**
     * Exercises both library macros with a symbol name stored in the application JVM's heap.
     */
    private static void vectorAPILibraryCallsSnippet(float[] src, float[] dst) {
        FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, src, 0).lanewise(VectorOperators.EXP).lanewise(VectorOperators.ATAN2, 0.5f).intoArray(dst, 0);
    }
}
