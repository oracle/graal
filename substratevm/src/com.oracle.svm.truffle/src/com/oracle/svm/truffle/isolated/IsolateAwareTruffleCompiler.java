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
package com.oracle.svm.truffle.isolated;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.util.OptionsEncoder;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.graal.isolated.ImageHeapObjects;
import com.oracle.svm.graal.isolated.ImageHeapRef;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedGraalUtils;
import com.oracle.svm.graal.isolated.IsolatedHandles;
import com.oracle.svm.truffle.api.SubstrateCompilableTruffleAST;
import com.oracle.svm.truffle.api.SubstrateTruffleCompiler;

public class IsolateAwareTruffleCompiler implements SubstrateTruffleCompiler {
    private final UninterruptibleUtils.AtomicWord<Isolate> sharedIsolate = new UninterruptibleUtils.AtomicWord<>();

    protected final SubstrateTruffleCompiler delegate;
    private final AtomicBoolean firstCompilation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public IsolateAwareTruffleCompiler(SubstrateTruffleCompiler delegate) {
        this.delegate = delegate;
        this.firstCompilation = new AtomicBoolean(true);
    }

    @Override
    public void initialize(Map<String, Object> options, CompilableTruffleAST compilable, boolean firstInitialization) {
        if (SubstrateOptions.shouldCompileInIsolates()) {
            // Nothing; we initialize the compiler in our isolate
        } else {
            delegate.initialize(options, compilable, firstInitialization);
        }
    }

    @Override
    public TruffleCompilation openCompilation(CompilableTruffleAST compilable) {
        return delegate.openCompilation(compilable);
    }

    @Override
    public TruffleDebugContext openDebugContext(Map<String, Object> options, TruffleCompilation compilation) {
        return delegate.openDebugContext(options, compilation); // not used in isolated doCompile()
    }

    @Override
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "False positive.")
    public void doCompile(TruffleDebugContext debug, TruffleCompilation compilation, Map<String, Object> options,
                    TruffleInliningPlan inlining, TruffleCompilationTask task, TruffleCompilerListener listener) {

        if (!SubstrateOptions.shouldCompileInIsolates()) {
            delegate.doCompile(null, compilation, options, inlining, task, listener);
            return;
        }

        CompilerIsolateThread context = beforeCompilation();
        try {
            IsolatedCompileClient client = new IsolatedCompileClient(context);
            IsolatedCompileClient.set(client);
            try {
                byte[] encodedOptions = options.isEmpty() ? null : OptionsEncoder.encode(options);
                byte[] encodedRuntimeOptions = IsolatedGraalUtils.encodeRuntimeOptionValues();
                IsolatedEventContext eventContext = null;
                if (listener != null) {
                    eventContext = new IsolatedEventContext(listener, compilation.getCompilable(), inlining);
                }
                ClientHandle<String> thrownException = doCompile0(context,
                                (ClientIsolateThread) CurrentIsolate.getCurrentThread(),
                                ImageHeapObjects.ref(delegate),
                                client.hand(((TruffleCompilationIdentifier) compilation)),
                                client.hand((SubstrateCompilableTruffleAST) compilation.getCompilable()),
                                client.hand(encodedOptions),
                                IsolatedGraalUtils.getNullableArrayLength(encodedOptions),
                                client.hand(inlining),
                                client.hand(task),
                                client.hand(eventContext),
                                client.hand(encodedRuntimeOptions),
                                IsolatedGraalUtils.getNullableArrayLength(encodedRuntimeOptions),
                                firstCompilation.getAndSet(false));

                String exception = client.unhand(thrownException);
                if (exception != null) {
                    throw new RuntimeException("doCompile threw: " + exception);
                }
            } finally {
                IsolatedCompileClient.set(null);
            }
        } finally {
            afterCompilation(context);
        }
    }

    protected CompilerIsolateThread beforeCompilation() {
        Isolate isolate = sharedIsolate.get();
        if (isolate.isNull()) {
            CompilerIsolateThread thread = IsolatedGraalUtils.createCompilationIsolate();
            isolate = Isolates.getIsolate(thread);
            if (sharedIsolate.compareAndSet(WordFactory.nullPointer(), isolate)) {
                return thread; // (already attached)
            }
            Isolates.tearDownIsolate(thread); // lost the race
            isolate = sharedIsolate.get();
            assert isolate.isNonNull();
        }
        return (CompilerIsolateThread) Isolates.attachCurrentThread(isolate);
    }

    protected void afterCompilation(CompilerIsolateThread context) {
        // Always detach to not obstruct tear-down of the compilation isolate on exit
        Isolates.detachThread(context);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<String> doCompile0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread context,
                    ClientIsolateThread client,
                    ImageHeapRef<SubstrateTruffleCompiler> delegateRef,
                    ClientHandle<TruffleCompilationIdentifier> compilationIdentifierHandle,
                    ClientHandle<SubstrateCompilableTruffleAST> compilableHandle,
                    ClientHandle<byte[]> encodedOptionsHandle,
                    int encodedOptionsLength,
                    ClientHandle<TruffleInliningPlan> inliningHandle,
                    ClientHandle<TruffleCompilationTask> taskHandle,
                    ClientHandle<IsolatedEventContext> eventContextHandle,
                    ClientHandle<byte[]> encodedRuntimeOptionsHandle,
                    int encodedRuntimeOptionsLength,
                    boolean firstCompilation) {

        IsolatedCompileContext.set(new IsolatedCompileContext(client));
        try {
            IsolatedGraalUtils.applyClientRuntimeOptionValues(encodedRuntimeOptionsHandle, encodedRuntimeOptionsLength);
            SubstrateTruffleCompiler delegate = ImageHeapObjects.deref(delegateRef);
            Map<String, Object> options = decodeOptions(client, encodedOptionsHandle, encodedOptionsLength);
            IsolatedCompilableTruffleAST compilable = new IsolatedCompilableTruffleAST(compilableHandle);
            delegate.initialize(options, compilable, firstCompilation);
            TruffleCompilation compilation = new IsolatedCompilationIdentifier(compilationIdentifierHandle, compilable);
            IsolatedTruffleInliningPlan inlining = new IsolatedTruffleInliningPlan(inliningHandle);
            TruffleCompilationTask task = null;
            if (taskHandle.notEqual(IsolatedHandles.nullHandle())) {
                task = new IsolatedTruffleCompilationTask(taskHandle);
            }
            TruffleCompilerListener listener = null;
            if (eventContextHandle.notEqual(IsolatedHandles.nullHandle())) {
                listener = new IsolatedTruffleCompilerEventForwarder(eventContextHandle);
            }
            delegate.doCompile(null, compilation, options, inlining, task, listener);
            return IsolatedHandles.nullHandle(); // no exception
        } catch (Throwable t) {
            StringWriter writer = new StringWriter();
            t.printStackTrace(new PrintWriter(writer));
            return IsolatedCompileContext.get().createStringInClient(writer.toString());
        } finally {
            IsolatedCompileContext.set(null);
        }
    }

    private static Map<String, Object> decodeOptions(ClientIsolateThread client, ClientHandle<byte[]> encodedOptionsHandle, int encodedOptionsLength) {
        if (encodedOptionsLength <= 0) {
            return Collections.emptyMap();
        }
        byte[] encodedOptions = new byte[encodedOptionsLength];
        try (PinnedObject pinnedEncodedOptions = PinnedObject.create(encodedOptions)) {
            copyEncodedOptions(client, encodedOptionsHandle, pinnedEncodedOptions.addressOfArrayElement(0));
        }
        return OptionsEncoder.decode(encodedOptions);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void copyEncodedOptions(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext ClientIsolateThread client, ClientHandle<byte[]> encodedOptionsHandle, PointerBase buffer) {
        byte[] encodedOptions = IsolatedCompileClient.get().unhand(encodedOptionsHandle);
        CTypeConversion.asByteBuffer(buffer, encodedOptions.length).put(encodedOptions);
    }

    @Override
    public String getCompilerConfigurationName() {
        return delegate.getCompilerConfigurationName(); // constant
    }

    @Override
    public void teardown() {
        if (SubstrateOptions.shouldCompileInIsolates()) {
            tearDownIsolateOnShutdown();
        }
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    protected void tearDownIsolateOnShutdown() {
        Isolate shared = sharedIsolate.get();
        if (shared.isNonNull()) {
            IsolateThread current = Isolates.attachCurrentThread(shared);
            Isolates.tearDownIsolate(current);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public PartialEvaluator getPartialEvaluator() {
        return delegate.getPartialEvaluator();
    }

    @Override
    public SnippetReflectionProvider getSnippetReflection() {
        return delegate.getSnippetReflection();
    }
}
