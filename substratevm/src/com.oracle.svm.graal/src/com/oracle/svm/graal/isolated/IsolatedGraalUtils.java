/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionValues;
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
        CreateIsolateParameters params = builder.build();
        return (CompilerIsolateThread) Isolates.createIsolate(params);
    }

    public static InstalledCode compileInNewIsolateAndInstall(SubstrateMethod method) {
        CompilerIsolateThread context = createCompilationIsolate();
        IsolatedCompileClient.set(new IsolatedCompileClient(context));
        byte[] encodedOptions = encodeRuntimeOptionValues();
        ClientHandle<SubstrateInstalledCode> installedCodeHandle = compileInNewIsolateAndInstall0(context, (ClientIsolateThread) CurrentIsolate.getCurrentThread(),
                        ImageHeapObjects.ref(method), IsolatedCompileClient.get().hand(encodedOptions), getNullableArrayLength(encodedOptions));
        Isolates.tearDownIsolate(context);
        InstalledCode installedCode = (InstalledCode) IsolatedCompileClient.get().unhand(installedCodeHandle);
        IsolatedCompileClient.set(null);
        return installedCode;
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<SubstrateInstalledCode> compileInNewIsolateAndInstall0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate,
                    ClientIsolateThread clientIsolate, ImageHeapRef<SubstrateMethod> methodRef, ClientHandle<byte[]> encodedOptions, int encodedOptionsLength) {

        IsolatedCompileContext.set(new IsolatedCompileContext(clientIsolate));

        applyClientRuntimeOptionValues(encodedOptions, encodedOptionsLength);
        assert SubstrateOptions.shouldCompileInIsolates();

        SubstrateMethod method = ImageHeapObjects.deref(methodRef);
        DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(GraalSupport.getRuntimeConfig().getSnippetReflection())).build();
        CompilationResult compilationResult = SubstrateGraalUtils.doCompile(debug, GraalSupport.getRuntimeConfig(), GraalSupport.getSuites(), GraalSupport.getLIRSuites(), method);
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
            byte[] encodedOptions = encodeRuntimeOptionValues();
            compileInNewIsolate0(context, (ClientIsolateThread) CurrentIsolate.getCurrentThread(), ImageHeapObjects.ref(method),
                            IsolatedCompileClient.get().hand(encodedOptions), getNullableArrayLength(encodedOptions));
            Isolates.tearDownIsolate(context);
            IsolatedCompileClient.set(null);
        } else {
            try (DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(GraalSupport.getRuntimeConfig().getSnippetReflection())).build()) {
                SubstrateGraalUtils.compile(debug, method);
            }
        }
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void compileInNewIsolate0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate, ClientIsolateThread clientIsolate,
                    ImageHeapRef<SubstrateMethod> methodRef, ClientHandle<byte[]> encodedOptions, int encodedOptionsLength) {

        IsolatedCompileContext.set(new IsolatedCompileContext(clientIsolate));

        applyClientRuntimeOptionValues(encodedOptions, encodedOptionsLength);
        assert SubstrateOptions.shouldCompileInIsolates();

        try (DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(GraalSupport.getRuntimeConfig().getSnippetReflection())).build()) {
            SubstrateGraalUtils.doCompile(debug, GraalSupport.getRuntimeConfig(), GraalSupport.getSuites(), GraalSupport.getLIRSuites(), ImageHeapObjects.deref(methodRef));
        }
        IsolatedCompileContext.set(null);
    }

    public static byte[] encodeRuntimeOptionValues() {
        UnmodifiableEconomicMap<OptionKey<?>, Object> map = RuntimeOptionValues.singleton().getMap();
        return map.isEmpty() ? null : OptionValuesEncoder.encode(map);
    }

    public static int getNullableArrayLength(Object array) {
        return (array != null) ? Array.getLength(array) : -1;
    }

    public static void applyClientRuntimeOptionValues(ClientHandle<byte[]> encodedOptionsHandle, int encodedOptionsLength) {
        if (!encodedOptionsHandle.equal(IsolatedHandles.nullHandle())) {
            byte[] encodedOptions = new byte[encodedOptionsLength];
            try (PinnedObject pin = PinnedObject.create(encodedOptions)) {
                copyOptions(IsolatedCompileContext.get().getClient(), encodedOptionsHandle, pin.addressOfArrayElement(0));
            }
            EconomicMap<OptionKey<?>, Object> options = OptionValuesEncoder.decode(encodedOptions);
            options.replaceAll((k, v) -> OptionsParser.parseOptionValue(k.getDescriptor(), v));
            RuntimeOptionValues.singleton().update(options);
        }
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void copyOptions(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext ClientIsolateThread isolate, ClientHandle<byte[]> encodedOptionsHandle, PointerBase buffer) {
        byte[] encodedOptions = IsolatedCompileClient.get().unhand(encodedOptionsHandle);
        CTypeConversion.asByteBuffer(buffer, encodedOptions.length).put(encodedOptions);
    }

    private IsolatedGraalUtils() {
    }
}
