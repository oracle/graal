/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.hotspot.CompilationContext;
import org.graalvm.compiler.hotspot.CompilationTask;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl.CodeInfo;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Builtin;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawFieldAddress;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.util.OptionsEncoder;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.heap.Heap;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Entry points in libgraal corresponding to native methods in {@link LibGraalScope} and
 * {@code CompileTheWorld}.
 */
public final class LibGraalEntryPoints {

    /**
     * @see org.graalvm.compiler.hotspot.HotSpotTTYStreamProvider#execute
     */
    static final CGlobalData<Pointer> LOG_FILE_BARRIER = CGlobalDataFactory.createWord((Pointer) WordFactory.zero());

    static final CGlobalData<Pointer> GLOBAL_TIMESTAMP = CGlobalDataFactory.createBytes(() -> 8);

    /**
     * Map from a foreign call signature to a C global word that is the address of a pointer to a
     * {@link RuntimeStubInfo} struct.
     *
     * This map is used to mitigate against libgraal isolates compiling and installing duplicate
     * {@code RuntimeStub}s for a foreign call. Completely preventing duplicates requires
     * inter-isolate synchronization for which there is currently no support. A small number of
     * duplicates is acceptable given how few foreign call runtime stubs there are in practice.
     * Duplicates will only result when libgraal isolates race to install code for the same stub.
     * Testing shows that this is extremely rare.
     */
    static final Map<ForeignCallSignature, CGlobalData<Pointer>> STUBS = new HashMap<>();

    /**
     * A struct that encapsulates the address of the first instruction of and the registers killed
     * by a HotSpot {@code RuntimeStub}. One such struct is allocated for each entry in
     * {@link LibGraalEntryPoints#STUBS}.
     *
     * <pre>
     * struct RuntimeStubInfo {
     *     long start;
     *     int killedRegistersLength;
     *     int[killedRegistersLength] killedRegisters;
     * }
     * </pre>
     */
    @RawStructure
    public interface RuntimeStubInfo extends PointerBase {
        // Checkstyle: stop
        // @formatter:off
        /**
         * Address of first instruction in the stub.
         */
        @RawField long getStart();
        @RawField void setStart(long value);

        /**
         * Length of the killedRegisters array.
         */
        @RawField int  getKilledRegistersLength();
        @RawField void setKilledRegistersLength(int value);

        /**
         * First element of the killedRegisters array.
         */
        @RawField int firstKilledRegister();
        @RawFieldAddress CIntPointer addressOf_firstKilledRegister();
        // @formatter:on
        // Checkstyle: resume

        class Util {

            static RuntimeStubInfo allocateRuntimeStubInfo(int killedRegistersLength) {
                // The first element of the killedRegister array is part of
                // the RuntimeStubInfo size computation
                int registersTailSize = Integer.BYTES * (killedRegistersLength - 1);
                return UnmanagedMemory.malloc(SizeOf.get(RuntimeStubInfo.class) + registersTailSize);
            }

            /**
             * Allocates and initializes a {@code RuntimeStubInfo} from {@code stub}.
             */
            static RuntimeStubInfo newRuntimeStubInfo(Stub stub, Backend backend) {
                long start = stub.getCode(backend).getStart();
                EconomicSet<Register> registers = stub.getDestroyedCallerRegisters();

                RuntimeStubInfo rsi = allocateRuntimeStubInfo(registers.size());
                rsi.setStart(start);
                rsi.setKilledRegistersLength(registers.size());
                copyIntoCIntArray(registers, rsi.addressOf_firstKilledRegister());
                return rsi;
            }

            /**
             * Copies the {@linkplain Register#number register numbers} for the entries in
             * {@code register} into a newly allocated C int array and returns it.
             */
            static void copyIntoCIntArray(EconomicSet<Register> registers, CIntPointer dst) {
                int i = 0;
                for (Register r : registers) {
                    dst.write(i, r.number);
                    i++;
                }
            }

            /**
             * Selects entries from {@code allRegisters} corresponding to
             * {@linkplain Register#number register numbers} in the C int array {@code regNums} of
             * length {@code len} and returns them in a set.
             */
            static EconomicSet<Register> toRegisterSet(RegisterArray allRegisters, int len, CIntPointer regNums) {
                EconomicSet<Register> res = EconomicSet.create(len);
                for (int i = 0; i < len; i++) {
                    res.add(allRegisters.get(regNums.read(i)));
                }
                return res;
            }

            /**
             * Creates and returns a {@link CodeInfo} from the data in {@code rsi}.
             */
            static CodeInfo newCodeInfo(RuntimeStubInfo rsi, Backend backend) {
                RegisterArray allRegisters = backend.getCodeCache().getTarget().arch.getRegisters();
                long start = rsi.getStart();
                return new CodeInfo(start, toRegisterSet(allRegisters,
                                rsi.getKilledRegistersLength(),
                                rsi.addressOf_firstKilledRegister()));
            }
        }
    }

    @CEntryPoint(builtin = Builtin.GET_CURRENT_THREAD, name = "Java_org_graalvm_libgraal_LibGraalScope_getIsolateThreadIn")
    private static native IsolateThread getIsolateThreadIn(PointerBase env, PointerBase hsClazz, @IsolateContext Isolate isolate);

    @CEntryPoint(name = "Java_org_graalvm_libgraal_LibGraalScope_attachThreadTo", builtin = CEntryPoint.Builtin.ATTACH_THREAD)
    static native long attachThreadTo(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateContext long isolate);

    @CEntryPoint(name = "Java_org_graalvm_libgraal_LibGraalScope_detachThreadFrom", builtin = CEntryPoint.Builtin.DETACH_THREAD)
    static native void detachThreadFrom(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateThreadContext long isolateThread);

    static class CachedOptions {
        final OptionValues options;
        final long hash;

        CachedOptions(OptionValues options, long hash) {
            this.options = options;
            this.hash = hash;
        }
    }

    private static final ThreadLocal<CachedOptions> cachedOptions = new ThreadLocal<>();

    public static boolean hasLibGraalIsolatePeer() {
        return hasLibGraalIsolatePeer;
    }

    /**
     * Indicates whether this runtime has an associated peer runtime that it must be unregistered
     * from during shutdown.
     */
    private static boolean hasLibGraalIsolatePeer;

    private static OptionValues decodeOptions(long address, int size, int hash) {
        CachedOptions options = cachedOptions.get();
        if (options == null || options.hash != hash) {
            byte[] buffer = new byte[size];
            Unsafe.getUnsafe().copyMemory(null, address, buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET, size);
            int actualHash = Arrays.hashCode(buffer);
            if (actualHash != hash) {
                throw new IllegalArgumentException(actualHash + " != " + hash);
            }
            Map<String, Object> srcMap = OptionsEncoder.decode(buffer);
            final EconomicMap<OptionKey<?>, Object> dstMap = OptionValues.newOptionMap();
            final Iterable<OptionDescriptors> loader = OptionsParser.getOptionsLoader();
            for (Map.Entry<String, Object> e : srcMap.entrySet()) {
                final String optionName = e.getKey();
                final Object optionValue = e.getValue();
                OptionsParser.parseOption(optionName, optionValue, dstMap, loader);
            }

            options = new CachedOptions(new OptionValues(dstMap), hash);
            cachedOptions.set(options);
        }
        return options.options;
    }

    @SuppressWarnings({"unused"})
    @CEntryPoint(name = "Java_org_graalvm_libgraal_LibGraalObject_releaseHandle")
    public static boolean releaseHandle(PointerBase jniEnv,
                    PointerBase jclass,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId,
                    long handle) {
        try {
            ObjectHandles.getGlobal().destroy(WordFactory.pointer(handle));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings({"unused"})
    @CEntryPoint(name = "Java_org_graalvm_libgraal_LibGraalScope_getIsolateId")
    public static long getIsolateId(PointerBase jniEnv,
                    PointerBase jclass,
                    @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        try {
            hasLibGraalIsolatePeer = true;
            return IsolateUtil.getIsolateID();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * The implementation of
     * {@code org.graalvm.compiler.hotspot.test.CompileTheWorld.compileMethodInLibgraal()}.
     *
     * @param methodHandle the method to be compiled. This is a handle to a
     *            {@link HotSpotResolvedJavaMethod} in HotSpot's heap. A value of 0L can be passed
     *            to use this method for the side effect of initializing a
     *            {@link HotSpotGraalCompiler} instance without doing any compilation.
     * @param useProfilingInfo specifies if profiling info should be used during the compilation
     * @param installAsDefault specifies if the compiled code should be installed for the
     *            {@code Method*} associated with {@code methodHandle}
     * @param printMetrics specifies if global metrics should be printed and reset
     * @param optionsAddress native byte buffer storing a serialized {@link OptionValues} object
     * @param optionsSize the number of bytes in the buffer
     * @param optionsHash hash code of bytes in the buffer (computed with
     *            {@link Arrays#hashCode(byte[])})
     * @param stackTraceAddress a native buffer in which a serialized stack trace can be returned.
     *            The caller will only read from this buffer if this method returns 0. A returned
     *            serialized stack trace is returned in this buffer with the following format:
     *
     *            <pre>
     *            struct {
     *                int   length;
     *                byte  data[length]; // Bytes from a stack trace printed to a ByteArrayOutputStream.
     *            }
     *            </pre>
     *
     *            where {@code length} truncated to {@code stackTraceCapacity - 4} if necessary
     *
     * @param stackTraceCapacity the size of the stack trace buffer
     * @return a handle to a {@link InstalledCode} in HotSpot's heap or 0 if compilation failed
     */
    @SuppressWarnings({"unused", "try"})
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_test_CompileTheWorld_compileMethodInLibgraal", include = LibGraalFeature.IsEnabled.class)
    private static long compileMethod(JNIEnv jniEnv,
                    PointerBase jclass,
                    @CEntryPoint.IsolateThreadContext long isolateThread,
                    long methodHandle,
                    boolean useProfilingInfo,
                    boolean installAsDefault,
                    boolean printMetrics,
                    long optionsAddress,
                    int optionsSize,
                    int optionsHash,
                    long stackTraceAddress,
                    int stackTraceCapacity) {
        try (JNIMethodScope jniScope = new JNIMethodScope("compileMethod", jniEnv)) {
            HotSpotJVMCIRuntime runtime = runtime();
            HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime.getCompiler();
            if (methodHandle == 0L) {
                return 0L;
            }
            HotSpotResolvedJavaMethod method = LibGraal.unhand(HotSpotResolvedJavaMethod.class, methodHandle);

            int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
            HotSpotCompilationRequest request = new HotSpotCompilationRequest(method, entryBCI, 0L);
            try (CompilationContext scope = HotSpotGraalServices.openLocalCompilationContext(request)) {

                OptionValues options = decodeOptions(optionsAddress, optionsSize, optionsHash);
                CompilationTask task = new CompilationTask(runtime, compiler, request, useProfilingInfo, installAsDefault);
                task.runCompilation(options);
                HotSpotInstalledCode installedCode = task.getInstalledCode();

                if (printMetrics) {
                    GlobalMetrics metricValues = ((HotSpotGraalRuntime) compiler.getGraalRuntime()).getMetricValues();
                    metricValues.print(options);
                    metricValues.clear();
                }
                return LibGraal.translate(installedCode);
            }
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
            Heap.getHeap().doReferenceHandling();
        }
    }
}
