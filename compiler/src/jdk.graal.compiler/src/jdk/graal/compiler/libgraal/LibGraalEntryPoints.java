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
package jdk.graal.compiler.libgraal;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import jdk.graal.compiler.hotspot.libgraal.RunTime;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.Word;
import jdk.graal.nativeimage.LibGraalRuntime;
import org.graalvm.collections.EconomicSet;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;

import jdk.internal.misc.Unsafe;

/**
 * Encapsulates {@link CEntryPoint} implementations.
 */
final class LibGraalEntryPoints {

    @Platforms(Platform.HOSTED_ONLY.class)
    LibGraalEntryPoints() {
    }

    /**
     * The implementation of
     * {@code jdk.graal.compiler.hotspot.test.LibGraalCompilationDriver#compileMethodInLibgraal}.
     * Calls {@link RunTime#compileMethod}.
     *
     * @param methodHandle the method to be compiled. This is a handle to a
     *            {@code HotSpotResolvedJavaMethod} in HotSpot's heap. A value of 0L can be passed
     *            to use this method for the side effect of initializing a
     *            {@code HotSpotGraalCompiler} instance without doing any compilation.
     * @param useProfilingInfo specifies if profiling info should be used during the compilation
     * @param installAsDefault specifies if the compiled code should be installed for the
     *            {@code Method*} associated with {@code methodHandle}
     * @param printMetrics specifies if global metrics should be printed and reset
     * @param optionsAddress native byte buffer storing a serialized {@code OptionValues} object
     * @param optionsSize the number of bytes in the buffer
     * @param optionsHash hash code of bytes in the buffer (computed with
     *            {@link Arrays#hashCode(byte[])})
     * @param stackTraceAddress a native buffer in which a serialized stack trace can be returned.
     *            The caller will only read from this buffer if this method returns 0. A returned
     *            serialized stack trace is returned in this buffer with the following format:
     * 
     *            <pre>
     *               struct {
     *                   int   length;
     *                   byte  data[length]; // Bytes from a stack trace printed to a ByteArrayOutputStream.
     *               }
     *            </pre>
     *
     *            where {@code length} is truncated to {@code stackTraceCapacity - 4} if necessary
     *
     * @param stackTraceCapacity the size of the stack trace buffer
     * @param timeAndMemBufferAddress 16-byte native buffer to store result of time and memory
     *            measurements of the compilation
     * @param profilePathBufferAddress native buffer containing a 0-terminated C string representing
     *            {@code Options#LoadProfiles} path.
     * @return a handle to a {@code InstalledCode} in HotSpot's heap or 0 if compilation failed
     */
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_jdk_graal_compiler_hotspot_test_LibGraalCompilationDriver_compileMethodInLibgraal", include = LibGraalFeature.IsEnabled.class)
    private static long compileMethod(JNIEnv jniEnv,
                    PointerBase jclass,
                    @IsolateThreadContext long isolateThread,
                    long methodHandle,
                    boolean useProfilingInfo,
                    boolean installAsDefault,
                    boolean printMetrics,
                    boolean eagerResolving,
                    long optionsAddress,
                    int optionsSize,
                    int optionsHash,
                    long stackTraceAddress,
                    int stackTraceCapacity,
                    long timeAndMemBufferAddress,
                    long profilePathBufferAddress) {
        try (JNIMethodScope jniScope = new JNIMethodScope("compileMethod", jniEnv)) {
            String profileLoadPath;
            if (profilePathBufferAddress > 0) {
                profileLoadPath = CTypeConversion.toJavaString(Word.pointer(profilePathBufferAddress));
            } else {
                profileLoadPath = null;
            }
            BiConsumer<Long, Long> timeAndMemConsumer;
            if (timeAndMemBufferAddress != 0) {
                timeAndMemConsumer = (timeSpent, bytesAllocated) -> {
                    Unsafe.getUnsafe().putLong(timeAndMemBufferAddress, bytesAllocated);
                    Unsafe.getUnsafe().putLong(timeAndMemBufferAddress + 8, timeSpent);
                };
            } else {
                timeAndMemConsumer = null;
            }

            return RunTime.compileMethod(methodHandle, useProfilingInfo,
                            installAsDefault, printMetrics, eagerResolving,
                            optionsAddress, optionsSize, optionsHash,
                            profileLoadPath, timeAndMemConsumer);
        } catch (Throwable t) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.printStackTrace(new PrintStream(baos));
            byte[] stackTrace = baos.toByteArray();
            int length = Math.min(stackTraceCapacity - Integer.BYTES, stackTrace.length);
            Unsafe.getUnsafe().putInt(stackTraceAddress, length);
            Unsafe.getUnsafe().copyMemory(stackTrace, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, stackTraceAddress + Integer.BYTES, length);
            return 0L;
        } finally {
            /*
             * libgraal doesn't use a dedicated reference handler thread, so we trigger the
             * reference handling manually when a compilation finishes.
             */
            doReferenceHandling();
        }
    }

    @CEntryPoint(name = "Java_jdk_graal_compiler_hotspot_test_LibGraalCompilerTest_hashConstantOopFields", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings({"unused", "try"})
    private static long hashConstantOopFields(JNIEnv jniEnv,
                    PointerBase jclass,
                    @IsolateThreadContext long isolateThread,
                    long typeHandle,
                    boolean useScope,
                    int iterations,
                    int oopsPerIteration,
                    boolean verbose) {
        try (JNIMethodScope scope = new JNIMethodScope("hashConstantOopFields", jniEnv)) {
            Runnable doReferenceHandling = LibGraalEntryPoints::doReferenceHandling;
            return RunTime.hashConstantOopFields(typeHandle, useScope, iterations, oopsPerIteration, verbose, doReferenceHandling);
        } catch (Throwable t) {
            JNIExceptionWrapper.throwInHotSpot(jniEnv, t);
            return 0;
        }
    }

    /**
     * Since reference handling is synchronous in libgraal, explicitly perform it here and then run
     * any code which is expecting to process a reference queue to let it clean up.
     */
    static void doReferenceHandling() {
        LibGraalRuntime.processReferences();
        synchronized (LibGraalJVMCISubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.class) {
            LibGraalJVMCISubstitutions.Target_jdk_vm_ci_hotspot_Cleaner.clean();
        }
    }

    static EconomicSet<String> explicitOptions = EconomicSet.create();

    static void initializeOptions(Map<String, String> settings) {
        for (var e : settings.entrySet()) {
            String name = e.getKey();
            String stringValue = e.getValue();
            Object value;
            if (name.startsWith("X") && stringValue.isEmpty()) {
                name = name.substring(1);
                value = stringValue;
            } else {
                RuntimeOptions.Descriptor desc = RuntimeOptions.getDescriptor(name);
                if (desc == null) {
                    throw new IllegalArgumentException("Could not find option " + name);
                }
                value = desc.convertValue(stringValue);
                explicitOptions.add(name);
            }
            try {
                RuntimeOptions.set(name, value);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    static void printOptions(PrintStream out, String prefix) {
        Comparator<RuntimeOptions.Descriptor> comparator = Comparator.comparing(RuntimeOptions.Descriptor::name);
        RuntimeOptions.listDescriptors().stream().sorted(comparator).forEach(d -> {
            String assign = explicitOptions.contains(d.name()) ? ":=" : "=";
            OptionValues.printHelp(out, prefix,
                            d.name(),
                            RuntimeOptions.get(d.name()),
                            d.valueType(),
                            assign,
                            "[community edition]",
                            d.help(),
                            List.of());
        });
    }
}
