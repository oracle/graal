/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.Isolates.ProtectionDomain;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.os.MemoryProtectionProvider;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.meta.SubstrateMethod;

import jdk.vm.ci.code.InstalledCode;

public final class IsolatedGraalUtils {

    public static CompilerIsolateThread createCompilationIsolate() {
        CreateIsolateParameters.Builder builder = new CreateIsolateParameters.Builder();
        long addressSpaceSize = SubstrateOptions.CompilationIsolateAddressSpaceSize.getValue();
        if (addressSpaceSize > 0) {
            builder.reservedAddressSpaceSize(WordFactory.signed(addressSpaceSize));
        }
        /*
         * if protection keys are used, the compilation isolate needs to use the same protection
         * domain as the client, otherwise it cannot access the client's code cache
         */
        if (MemoryProtectionProvider.isAvailable()) {
            ProtectionDomain domain = MemoryProtectionProvider.singleton().getProtectionDomain();
            builder.setProtectionDomain(domain);
        }
        // Compilation isolates do the reference handling manually to avoid the extra thread.
        builder.appendArgument(getOptionString(SubstrateOptions.ConcealedOptions.AutomaticReferenceHandling, false));
        CreateIsolateParameters params = builder.build();
        CompilerIsolateThread isolate = (CompilerIsolateThread) Isolates.createIsolate(params);
        initializeCompilationIsolate(isolate);
        return isolate;
    }

    private static void initializeCompilationIsolate(CompilerIsolateThread isolate) {
        byte[] encodedOptions = encodeRuntimeOptionValues();
        try (PinnedObject pin = PinnedObject.create(encodedOptions)) {
            initializeCompilationIsolate0(isolate, pin.addressOfArrayElement(0), encodedOptions.length);
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void initializeCompilationIsolate0(
                    @SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate, PointerBase runtimeOptions, int runtimeOptionsLength) {
        applyClientRuntimeOptionValues(runtimeOptions, runtimeOptionsLength);
        VMRuntime.initialize();
    }

    public static InstalledCode compileInNewIsolateAndInstall(SubstrateMethod method) {
        CompilerIsolateThread context = createCompilationIsolate();
        IsolatedCompileClient.set(new IsolatedCompileClient(context));
        ClientHandle<SubstrateInstalledCode> installedCodeHandle = compileInNewIsolateAndInstall0(context, (ClientIsolateThread) CurrentIsolate.getCurrentThread(), ImageHeapObjects.ref(method));
        Isolates.tearDownIsolate(context);
        InstalledCode installedCode = (InstalledCode) IsolatedCompileClient.get().unhand(installedCodeHandle);
        IsolatedCompileClient.set(null);
        return installedCode;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<SubstrateInstalledCode> compileInNewIsolateAndInstall0(
                    @SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate, ClientIsolateThread clientIsolate, ImageHeapRef<SubstrateMethod> methodRef) {

        IsolatedCompileContext.set(new IsolatedCompileContext(clientIsolate));

        SubstrateMethod method = ImageHeapObjects.deref(methodRef);
        DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(GraalSupport.getRuntimeConfig().getSnippetReflection())).build();
        CompilationResult compilationResult = SubstrateGraalUtils.doCompile(debug, GraalSupport.getRuntimeConfig(), GraalSupport.getLIRSuites(), method);
        ClientHandle<SubstrateInstalledCode> installedCodeHandle = IsolatedRuntimeCodeInstaller.installInClientIsolate(
                        methodRef, compilationResult, IsolatedHandles.nullHandle());
        Log.log().string("Code for " + method.format("%H.%n(%p)") + ": " + compilationResult.getTargetCodeSize() + " bytes").newline();

        IsolatedCompileContext.set(null);
        return installedCodeHandle;
    }

    public static void compileInNewIsolate(SubstrateMethod method) {
        if (SubstrateOptions.shouldCompileInIsolates()) {
            CompilerIsolateThread context = createCompilationIsolate();
            IsolatedCompileClient.set(new IsolatedCompileClient(context));
            compileInNewIsolate0(context, (ClientIsolateThread) CurrentIsolate.getCurrentThread(), ImageHeapObjects.ref(method));
            Isolates.tearDownIsolate(context);
            IsolatedCompileClient.set(null);
        } else {
            try (DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(GraalSupport.getRuntimeConfig().getSnippetReflection())).build()) {
                SubstrateGraalUtils.compile(debug, method);
            }
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void compileInNewIsolate0(
                    @SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate, ClientIsolateThread clientIsolate, ImageHeapRef<SubstrateMethod> methodRef) {

        IsolatedCompileContext.set(new IsolatedCompileContext(clientIsolate));
        try (DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(GraalSupport.getRuntimeConfig().getSnippetReflection())).build()) {
            SubstrateGraalUtils.doCompile(debug, GraalSupport.getRuntimeConfig(), GraalSupport.getLIRSuites(), ImageHeapObjects.deref(methodRef));
        }
        IsolatedCompileContext.set(null);
    }

    public static byte[] encodeRuntimeOptionValues() {
        /* Copy all options that are relevant for the compilation isolate. */
        EconomicMap<OptionKey<?>, Object> result = EconomicMap.create();
        UnmodifiableMapCursor<OptionKey<?>, Object> cur = RuntimeOptionValues.singleton().getMap().getEntries();
        while (cur.advance()) {
            OptionKey<?> optionKey = cur.getKey();
            if (shouldCopyToCompilationIsolate(optionKey)) {
                result.put(optionKey, cur.getValue());
            }
        }

        /*
         * All compilation isolates should use the same folder for debug dumps, to avoid confusion
         * of users. Always setting the DumpPath option in the compilation isolates is the easiest
         * way to achieve that.
         */
        result.put(DebugOptions.DumpPath, DebugOptions.getDumpDirectoryName(RuntimeOptionValues.singleton()));
        return OptionValuesEncoder.encode(result);
    }

    private static boolean shouldCopyToCompilationIsolate(OptionKey<?> optionKey) {
        if (optionKey instanceof RuntimeOptionKey) {
            return ((RuntimeOptionKey<?>) optionKey).shouldCopyToCompilationIsolate();
        }

        /*
         * For all other options (Truffle, Graal, ...) outside the control of Native Image, we
         * assume that they are relevant for the compilation isolate.
         */
        return true;
    }

    public static int getNullableArrayLength(Object array) {
        return (array != null) ? Array.getLength(array) : -1;
    }

    public static void applyClientRuntimeOptionValues(PointerBase encodedOptionsPtr, int encodedOptionsLength) {
        if (encodedOptionsPtr.isNull()) {
            return;
        }
        byte[] encodedOptions = new byte[encodedOptionsLength];
        ByteBuffer buffer = CTypeConversion.asByteBuffer(encodedOptionsPtr, encodedOptionsLength);
        buffer.get(encodedOptions);

        EconomicMap<OptionKey<?>, Object> options = OptionValuesEncoder.decode(encodedOptions);
        options.replaceAll((k, v) -> OptionsParser.parseOptionValue(k.getDescriptor(), v));
        RuntimeOptionValues.singleton().update(options);
    }

    private static String getOptionString(RuntimeOptionKey<Boolean> option, boolean value) {
        return "-XX:" + (value ? "+" : "-") + option.getName();
    }

    private IsolatedGraalUtils() {
    }
}
