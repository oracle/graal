/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilation;
import org.graalvm.compiler.truffle.compiler.phases.TruffleTier;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.Heap;
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
import com.oracle.svm.truffle.api.SubstrateTruffleCompilerImpl;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener;

public class IsolateAwareTruffleCompiler implements SubstrateTruffleCompiler {
    private static final Word ISOLATE_INITIALIZING = WordFactory.signed(-1);

    private final UninterruptibleUtils.AtomicWord<Isolate> sharedIsolate = new UninterruptibleUtils.AtomicWord<>();

    protected final SubstrateTruffleCompilerImpl delegate;
    private final AtomicBoolean firstCompilation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public IsolateAwareTruffleCompiler(SubstrateTruffleCompilerImpl delegate) {
        this.delegate = delegate;
        this.firstCompilation = new AtomicBoolean(true);
    }

    @Override
    public void initialize(TruffleCompilable compilable, boolean firstInitialization) {
        if (SubstrateOptions.shouldCompileInIsolates()) {
            // Nothing; we initialize the compiler in our isolate
        } else {
            delegate.initialize(compilable, firstInitialization);
        }
    }

    @Override
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "False positive.")
    public void doCompile(TruffleCompilationTask task, TruffleCompilable compilable, TruffleCompilerListener listener) {

        if (!SubstrateOptions.shouldCompileInIsolates()) {
            delegate.doCompile(task, compilable, listener);
            return;
        }

        CompilerIsolateThread context = beforeCompilation();
        try {
            IsolatedCompileClient client = new IsolatedCompileClient(context);
            IsolatedCompileClient.set(client);
            try {
                IsolatedEventContext eventContext = null;
                if (listener != null) {
                    eventContext = new IsolatedEventContext(listener, compilable, task);
                }
                ClientHandle<CompilationIdentifier> compilationIdentifier = client.hand(delegate.createCompilationIdentifier(task, compilable));
                ClientHandle<String> thrownException = doCompile0(context,
                                (ClientIsolateThread) CurrentIsolate.getCurrentThread(),
                                ImageHeapObjects.ref(delegate),
                                client.hand(task),
                                client.hand((SubstrateCompilableTruffleAST) compilable),
                                compilationIdentifier,
                                client.hand(eventContext),
                                firstCompilation.getAndSet(false));

                String exception = client.unhand(thrownException);
                if (exception != null) {
                    throw new RuntimeException("Method doCompile threw: " + exception);
                }
            } finally {
                IsolatedCompileClient.set(null);
            }
        } finally {
            afterCompilation(context);
        }
    }

    protected CompilerIsolateThread beforeCompilation() {
        Isolate isolate = getSharedIsolate();
        if (isolate.isNull()) {
            if (sharedIsolate.compareAndSet(WordFactory.nullPointer(), (Isolate) ISOLATE_INITIALIZING)) {
                CompilerIsolateThread thread = IsolatedGraalUtils.createCompilationIsolate();
                Runtime.getRuntime().addShutdownHook(new Thread(this::sharedIsolateShutdown));
                sharedIsolate.set(Isolates.getIsolate(thread));
                return thread; // (already attached)
            }
            isolate = getSharedIsolate();
            assert isolate.isNonNull();
        }
        return (CompilerIsolateThread) Isolates.attachCurrentThread(isolate);
    }

    private Isolate getSharedIsolate() {
        Isolate isolate = sharedIsolate.get();
        while (isolate.equal(ISOLATE_INITIALIZING)) {
            PauseNode.pause();
            isolate = sharedIsolate.get();
        }
        return isolate;
    }

    private void sharedIsolateShutdown() {
        Isolate isolate = getSharedIsolate();
        CompilerIsolateThread context = (CompilerIsolateThread) Isolates.attachCurrentThread(isolate);
        compilerIsolateThreadShutdown(context);
        Isolates.detachThread(context);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    protected static void compilerIsolateThreadShutdown(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread context) {
        VMRuntime.shutdown();
    }

    protected void afterCompilation(CompilerIsolateThread context) {
        // Always detach to not obstruct tear-down of the compilation isolate on exit
        Isolates.detachThread(context);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<String> doCompile0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread context,
                    ClientIsolateThread client,
                    ImageHeapRef<SubstrateTruffleCompilerImpl> delegateRef,
                    ClientHandle<TruffleCompilationTask> taskHandle,
                    ClientHandle<SubstrateCompilableTruffleAST> compilableHandle,
                    ClientHandle<CompilationIdentifier> compilationIdentifier,
                    ClientHandle<IsolatedEventContext> eventContextHandle,
                    boolean firstCompilation) {
        IsolatedCompileContext.set(new IsolatedCompileContext(client));
        try {
            SubstrateTruffleCompilerImpl delegate = ImageHeapObjects.deref(delegateRef);
            IsolatedCompilableTruffleAST compilable = new IsolatedCompilableTruffleAST(compilableHandle);
            delegate.initialize(compilable, firstCompilation);
            IsolatedTruffleCompilationTask task = null;
            if (taskHandle.notEqual(IsolatedHandles.nullHandle())) {
                task = new IsolatedTruffleCompilationTask(taskHandle);
            }
            TruffleCompilerListener listener = null;
            if (eventContextHandle.notEqual(IsolatedHandles.nullHandle())) {
                listener = new IsolatedTruffleCompilerEventForwarder(eventContextHandle);
            }
            try (TruffleCompilation compilation = delegate.openCompilation(task, compilable)) {
                /*
                 * With isolated compilation we allocate the compilation id on the client side as it
                 * survives the compiler isolate.
                 */
                compilation.setCompilationId(new IsolatedTruffleCompilationIdentifier(compilationIdentifier, task, compilable));
                delegate.doCompile(compilation, listener);
            }
            return IsolatedHandles.nullHandle(); // no exception
        } catch (Throwable t) {
            StringWriter writer = new StringWriter();
            t.printStackTrace(new PrintWriter(writer));
            return IsolatedCompileContext.get().createStringInClient(writer.toString());
        } finally {
            /*
             * Compilation isolate do not use a dedicated reference handler thread, so we trigger
             * the reference handling manually when a compilation finishes.
             */
            Heap.getHeap().doReferenceHandling();
            IsolatedCompileContext.set(null);
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void copyEncodedOptions(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext ClientIsolateThread client, ClientHandle<byte[]> encodedOptionsHandle, PointerBase buffer) {
        byte[] encodedOptions = IsolatedCompileClient.get().unhand(encodedOptionsHandle);
        CTypeConversion.asByteBuffer(buffer, encodedOptions.length).put(encodedOptions);
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
        Isolate shared = getSharedIsolate();
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

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public TruffleTier getTruffleTier() {
        return delegate.getTruffleTier();
    }

}
