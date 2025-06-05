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

import com.oracle.svm.core.SubstrateSegfaultHandler;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.Isolates.ProtectionDomain;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.IsolateSupportImpl;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.os.MemoryProtectionProvider;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.TruffleRuntimeCompilationSupport;
import com.oracle.svm.graal.meta.SubstrateMethod;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.InstalledCode;

public final class IsolatedGraalUtils {

    /**
     * Creates a compilation isolate. The compilation isolate will inherit the values of some
     * options from the main isolate, while using custom values for other options. Here is a list of
     * sources for option values, sorted from least to highest priority. If options are specified
     * multiple times, the highest priority takes precedence.
     *
     * <ol>
     * <li>The compilation isolate implicitly inherits the values of hosted and runtime options that
     * were specified during the image build.</li>
     * <li>For runtime options, where {@link RuntimeOptionKey#shouldCopyToCompilationIsolate}
     * returns true, the main isolate copies its option value to the compilation isolate
     * arguments.</li>
     * <li>Options that are specified in {@link SubstrateOptions#CompilationIsolateOptions} are
     * copied to the compilation isolate arguments as well.</li>
     * </ol>
     *
     * All Native Image-specific runtime options are passed to the compilation isolate during
     * isolate creation. This is important for Native Image code that is used during early startup.
     *
     * All options outside the control of Native Image (e.g., Truffle or Graal) are encoded as
     * binary data and applied to the compilation isolate after the isolate is already fully
     * started. Note that these option values have the highest priority and take precedence.
     *
     * Note that the compilation isolate always parses the runtime options that it receives from the
     * main isolate. This is even the case if {@link SubstrateOptions#ParseRuntimeOptions} is
     * disabled.
     */
    public static CompilerIsolateThread createCompilationIsolate() {
        CreateIsolateParameters.Builder builder = new CreateIsolateParameters.Builder();
        long addressSpaceSize = SubstrateOptions.CompilationIsolateAddressSpaceSize.getValue();
        if (addressSpaceSize > 0) {
            builder.reservedAddressSpaceSize(Word.signed(addressSpaceSize));
        }
        /*
         * if protection keys are used, the compilation isolate needs to use the same protection
         * domain as the client, otherwise it cannot access the client's code cache
         */
        if (MemoryProtectionProvider.isAvailable()) {
            ProtectionDomain domain = MemoryProtectionProvider.singleton().getProtectionDomain();
            builder.setProtectionDomain(domain);
        }

        appendOptionsRelevantForCompilationIsolates(builder);
        appendOptionsExplicitlySetForCompilationIsolates(builder);

        CreateIsolateParameters params = builder.build();
        CompilerIsolateThread isolate = (CompilerIsolateThread) IsolateSupportImpl.createIsolate(params, true);
        initializeCompilationIsolate(isolate);
        return isolate;
    }

    private static void appendOptionsRelevantForCompilationIsolates(CreateIsolateParameters.Builder builder) {
        /* Append all native image options that are relevant for the compilation isolate. */
        var cur = RuntimeOptionValues.singleton().getMap().getEntries();
        while (cur.advance()) {
            if (cur.getKey() instanceof RuntimeOptionKey<?> runtimeOptionKey && runtimeOptionKey.shouldCopyToCompilationIsolate()) {
                appendArgument(builder, runtimeOptionKey, cur.getValue());
            }
        }

        /* Compilation isolates do the reference handling manually to avoid the extra thread. */
        appendArgument(builder, SubstrateOptions.ConcealedOptions.AutomaticReferenceHandling, false);

        /* Disable signal handling for compilation isolates. */
        appendArgument(builder, SubstrateOptions.EnableSignalHandling, false);
        appendArgument(builder, SubstrateSegfaultHandler.Options.InstallSegfaultHandler, false);
    }

    private static void appendOptionsExplicitlySetForCompilationIsolates(CreateIsolateParameters.Builder builder) {
        String optionString = SubstrateOptions.CompilationIsolateOptions.getValue();
        if (optionString == null) {
            return;
        }

        int start = 0;
        char prev = ' ';
        boolean withinQuotes = false;
        for (int i = 0; i < optionString.length(); i++) {
            char c = optionString.charAt(i);
            if (!withinQuotes && prev == '\'' && c != ' ') {
                throw new Isolates.IsolateException("Failed while parsing " + SubstrateOptions.CompilationIsolateOptions.getName() + ": space expected after " + optionString.substring(0, i));
            }

            if (c == '\'') {
                withinQuotes = !withinQuotes;
                if (withinQuotes && prev != ' ') {
                    throw new Isolates.IsolateException("Failed while parsing " + SubstrateOptions.CompilationIsolateOptions.getName() + ": expected space after " + optionString.substring(0, i));
                }

                if (!withinQuotes && i > start) {
                    builder.appendArgument(optionString.substring(start, i));
                }
                start = i + 1;
            } else if (!withinQuotes && c == ' ') {
                if (i > start) {
                    builder.appendArgument(optionString.substring(start, i));
                }
                start = i + 1;
            }
            prev = c;
        }

        if (withinQuotes) {
            throw new Isolates.IsolateException("Failed while parsing " + SubstrateOptions.CompilationIsolateOptions.getName() + ": unmatched single quote.");
        }

        /* Add the remaining part. */
        if (start < optionString.length()) {
            builder.appendArgument(optionString.substring(start));
        }
    }

    private static void appendArgument(CreateIsolateParameters.Builder builder, OptionKey<?> optionKey, Object value) {
        String optionString = "-XX:";
        if (value instanceof Boolean b) {
            optionString += b ? "+" : "-";
            optionString += optionKey.getName();
        } else {
            optionString += optionKey.getName();
            optionString += "=";
            optionString += formatOptionValue(value);
        }
        builder.appendArgument(optionString);
    }

    private static String formatOptionValue(Object value) {
        if (value instanceof Number n) {
            return n.toString();
        } else if (value instanceof String str) {
            return str;
        } else {
            throw VMError.shouldNotReachHere("Unexpected option type: " + value);
        }
    }

    private static void initializeCompilationIsolate(CompilerIsolateThread isolate) {
        byte[] encodedOptions = encodeNonNativeImageRuntimeOptionValues();
        try (PrimitiveArrayView ref = PrimitiveArrayView.createForReading(encodedOptions)) {
            initializeCompilationIsolate0(isolate, ref.addressOfArrayElement(0), encodedOptions.length);
        }
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.VoidExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static void initializeCompilationIsolate0(
                    @SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate, PointerBase runtimeOptions, int runtimeOptionsLength) {
        applyClientRuntimeOptionValues(runtimeOptions, runtimeOptionsLength);
        VMRuntime.initialize();
    }

    public static InstalledCode compileInNewIsolateAndInstall(SubstrateMethod method, SubstrateInstalledCode.Factory installedCodeFactory) {
        InstalledCode installedCode;
        CompilerIsolateThread context = createCompilationIsolate();
        IsolatedCompileClient.set(new IsolatedCompileClient(context));
        try {
            ClientHandle<SubstrateInstalledCode> installedCodeHandle = compileInNewIsolateAndInstall0(context,
                            (ClientIsolateThread) CurrentIsolate.getCurrentThread(), ImageHeapObjects.ref(method), IsolatedCompileClient.get().hand(installedCodeFactory));
            Isolates.tearDownIsolate(context);
            installedCode = (InstalledCode) IsolatedCompileClient.get().unhand(installedCodeHandle);
        } finally {
            IsolatedCompileClient.set(null);
        }
        return installedCode;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.ResetContextWordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(epilogue = IsolatedCompileContext.ExitCompilationEpilogue.class, callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static ClientHandle<SubstrateInstalledCode> compileInNewIsolateAndInstall0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate,
                    ClientIsolateThread clientIsolate, ImageHeapRef<SubstrateMethod> methodRef, ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {

        IsolatedCompileContext.set(new IsolatedCompileContext(clientIsolate));
        // The context is cleared in the CEntryPointOptions.epilogue (also in case of an exception)

        SubstrateMethod method = ImageHeapObjects.deref(methodRef);
        RuntimeConfiguration runtimeConfiguration = TruffleRuntimeCompilationSupport.getRuntimeConfig();
        DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(runtimeConfiguration.getProviders().getSnippetReflection())).build();
        CompilationResult compilationResult = SubstrateGraalUtils.doCompile(debug, TruffleRuntimeCompilationSupport.getRuntimeConfig(), TruffleRuntimeCompilationSupport.getLIRSuites(), method);
        ClientHandle<SubstrateInstalledCode> installedCodeHandle = IsolatedRuntimeCodeInstaller.installInClientIsolate(methodRef, compilationResult, installedCodeFactoryHandle);
        Log.log().string("Code for " + method.format("%H.%n(%p)") + ": " + compilationResult.getTargetCodeSize() + " bytes").newline();

        return installedCodeHandle;
    }

    public static void compileInNewIsolate(SubstrateMethod method) {
        if (SubstrateOptions.shouldCompileInIsolates()) {
            CompilerIsolateThread context = createCompilationIsolate();
            IsolatedCompileClient.set(new IsolatedCompileClient(context));
            try {
                compileInNewIsolate0(context, (ClientIsolateThread) CurrentIsolate.getCurrentThread(), ImageHeapObjects.ref(method));
                Isolates.tearDownIsolate(context);
            } finally {
                IsolatedCompileClient.set(null);
            }
        } else {
            RuntimeConfiguration runtimeConfiguration = TruffleRuntimeCompilationSupport.getRuntimeConfig();
            try (DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(runtimeConfiguration.getProviders().getSnippetReflection())).build()) {
                SubstrateGraalUtils.compile(debug, method);
            }
        }
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.ResetContextWordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(epilogue = IsolatedCompileContext.ExitCompilationEpilogue.class, callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static WordBase compileInNewIsolate0(
                    @SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread isolate, ClientIsolateThread clientIsolate, ImageHeapRef<SubstrateMethod> methodRef) {

        IsolatedCompileContext.set(new IsolatedCompileContext(clientIsolate));
        // The context is cleared in the CEntryPointOptions.epilogue (also in case of an exception)

        RuntimeConfiguration runtimeConfiguration = TruffleRuntimeCompilationSupport.getRuntimeConfig();
        try (DebugContext debug = new Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(runtimeConfiguration.getProviders().getSnippetReflection())).build()) {
            SubstrateGraalUtils.doCompile(debug, TruffleRuntimeCompilationSupport.getRuntimeConfig(), TruffleRuntimeCompilationSupport.getLIRSuites(), ImageHeapObjects.deref(methodRef));
        }

        return Word.zero();
    }

    private static byte[] encodeNonNativeImageRuntimeOptionValues() {
        EconomicMap<OptionKey<?>, Object> result = EconomicMap.create();
        var cur = RuntimeOptionValues.singleton().getMap().getEntries();
        while (cur.advance()) {
            OptionKey<?> optionKey = cur.getKey();
            if (!(optionKey instanceof RuntimeOptionKey)) {
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

    private static void applyClientRuntimeOptionValues(PointerBase encodedOptionsPtr, int encodedOptionsLength) {
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

    public static int getNullableArrayLength(Object array) {
        return (array != null) ? Array.getLength(array) : -1;
    }

    private IsolatedGraalUtils() {
    }
}
