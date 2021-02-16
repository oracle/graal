/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.annotate.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.nodes.WriteHeapBaseNode;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

@AutomaticFeature
class SubstrateSegfaultHandlerFeature implements Feature {
    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(SubstrateSegfaultHandler.class)) {
            return; /* No segfault handler. */
        }
        VMError.guarantee(ImageSingletons.contains(RegisterDumper.class));
        RuntimeSupport.getRuntimeSupport().addStartupHook(SubstrateSegfaultHandler::startupHook);
    }
}

public abstract class SubstrateSegfaultHandler {

    public static class Options {
        @Option(help = "Install segfault handler that prints register contents and full Java stacktrace. Default: enabled for an executable, disabled for a shared library.")//
        static final RuntimeOptionKey<Boolean> InstallSegfaultHandler = new RuntimeOptionKey<>(null);
    }

    static void startupHook() {
        Boolean optionValue = Options.InstallSegfaultHandler.getValue();
        if (optionValue == Boolean.TRUE || (optionValue == null && ImageInfo.isExecutable())) {
            ImageSingletons.lookup(SubstrateSegfaultHandler.class).install();
        }
    }

    /** Installs the platform dependent segfault handler. */
    protected abstract void install();

    /** Called from the platform dependent segfault handler to enter the isolate. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.", overridesCallers = true)
    protected static boolean tryEnterIsolate(RegisterDumper.Context context) {
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            PointerBase heapBase = RegisterDumper.singleton().getHeapBase(context);
            WriteHeapBaseNode.writeCurrentVMHeapBase(heapBase);
        }
        if (SubstrateOptions.MultiThreaded.getValue()) {
            PointerBase threadPointer = RegisterDumper.singleton().getThreadPointer(context);
            WriteCurrentVMThreadNode.writeCurrentVMThread((IsolateThread) threadPointer);
        }

        /*
         * The following probing is subject to implicit recursion as it may trigger a new segfault.
         * However, this is fine, as it will eventually result in native stack overflow.
         */
        Isolate isolate = VMThreads.IsolateTL.get();
        return Isolates.checkSanity(isolate) == CEntryPointErrors.NO_ERROR &&
                        (!SubstrateOptions.SpawnIsolates.getValue() || isolate.equal(KnownIntrinsics.heapBase()));
    }

    /** Called from the platform dependent segfault handler to print diagnostics. */
    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.", overridesCallers = true)
    protected static void dump(RegisterDumper.Context context) {
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        dumpInterruptibly(context);
    }

    private static void dumpInterruptibly(RegisterDumper.Context context) {
        Log log = Log.log();
        log.autoflush(true);

        log.newline();
        log.string("[ [ SubstrateSegfaultHandler caught a segfault. ] ]").newline();

        PointerBase sp = RegisterDumper.singleton().getSP(context);
        PointerBase ip = RegisterDumper.singleton().getIP(context);
        SubstrateUtil.printDiagnostics(log, (Pointer) sp, (CodePointer) ip, context);

        log.string("Segfault detected, aborting process. Use runtime option -R:-InstallSegfaultHandler if you don't want to use SubstrateSegfaultHandler.").newline();
        log.newline();

        ImageSingletons.lookup(LogHandler.class).fatalError();
    }
}
