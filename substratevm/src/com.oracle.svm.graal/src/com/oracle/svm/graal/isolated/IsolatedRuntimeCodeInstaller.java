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

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.graal.meta.SubstrateMethod;

public final class IsolatedRuntimeCodeInstaller extends RuntimeCodeInstaller {

    /**
     * Called in a {@linkplain IsolatedCompileContext compilation isolate} to install the given
     * compilation result in the {@linkplain IsolatedCompileClient compilation client's} isolate.
     */
    public static ClientHandle<SubstrateInstalledCode> installInClientIsolate(ImageHeapRef<SubstrateMethod> methodRef, CompilationResult compilationResult,
                    ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {
        SubstrateMethod method = ImageHeapObjects.deref(methodRef);
        ClientIsolateThread clientIsolate = IsolatedCompileContext.get().getClient();
        CodeInstallInfo installInfo = new IsolatedRuntimeCodeInstaller(clientIsolate, method, compilationResult).doPrepareInstall();
        return installInClientIsolate0(clientIsolate, methodRef, installInfo, installedCodeFactoryHandle);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<SubstrateInstalledCode> installInClientIsolate0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext ClientIsolateThread isolate,
                    ImageHeapRef<SubstrateMethod> methodRef, CodeInstallInfo installInfo, ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {

        SubstrateMethod method = ImageHeapObjects.deref(methodRef);
        return installMethodCodeInClientIsolate(installInfo, installedCodeFactoryHandle, method);
    }

    private static ClientHandle<SubstrateInstalledCode> installMethodCodeInClientIsolate(CodeInstallInfo installInfo,
                    ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle, SharedRuntimeMethod method) {

        SubstrateInstalledCode.Factory installedCodeFactory = IsolatedCompileClient.get().unhand(installedCodeFactoryHandle);
        SubstrateInstalledCode installedCode;
        if (installedCodeFactory != null) {
            installedCode = installedCodeFactory.createSubstrateInstalledCode();
        } else {
            installedCode = new SubstrateInstalledCodeImpl(method);
        }
        installedCode.setCompilationId(IsolatedCompileClient.get().unhand(installInfo.getCompilationId()));
        installPrepared(method, installInfo, installedCode);
        return IsolatedCompileClient.get().hand(installedCode);
    }

    public static ClientHandle<SubstrateInstalledCode> installInClientIsolate(SharedRuntimeMethod compilerMethod,
                    ClientHandle<? extends SharedRuntimeMethod> clientMethodHandle, CompilationResult compilationResult,
                    ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {
        ClientIsolateThread clientIsolate = IsolatedCompileContext.get().getClient();
        CodeInstallInfo installInfo = new IsolatedRuntimeCodeInstaller(clientIsolate, compilerMethod, compilationResult).doPrepareInstall();
        return installInClientIsolate1(clientIsolate, clientMethodHandle, installInfo, installedCodeFactoryHandle);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<SubstrateInstalledCode> installInClientIsolate1(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext ClientIsolateThread isolate,
                    ClientHandle<? extends SharedRuntimeMethod> methodHandle, CodeInstallInfo installInfo, ClientHandle<? extends SubstrateInstalledCode.Factory> installedCodeFactoryHandle) {

        SharedRuntimeMethod method = IsolatedCompileClient.get().unhand(methodHandle);
        return installMethodCodeInClientIsolate(installInfo, installedCodeFactoryHandle, method);
    }

    @SuppressWarnings("try")
    private CodeInstallInfo doPrepareInstall() {
        IsolatedReferenceAdjuster adjuster = new IsolatedReferenceAdjuster();

        // A freshly allocated CodeInfo object is protected from the GC until the tether is set.
        CodeInfo codeInfo = RuntimeCodeInfoAccess.allocateMethodInfo();
        doPrepareInstall(adjuster, codeInfo);
        RuntimeCodeInfoAccess.guaranteeAllObjectsInImageHeap(codeInfo);

        ClientHandle<CompilationIdentifier> id = IsolatedHandles.nullHandle();
        if (compilationId instanceof IsolatedObjectProxy<?>) {
            @SuppressWarnings("unchecked")
            IsolatedObjectProxy<CompilationIdentifier> proxy = (IsolatedObjectProxy<CompilationIdentifier>) compilationId;
            id = proxy.getHandle();
        }

        CodeInstallInfo installInfo = UnmanagedMemory.malloc(SizeOf.get(CodeInstallInfo.class));
        installInfo.setCodeInfo(codeInfo);
        installInfo.setAdjusterData(adjuster.exportData());
        installInfo.setCompilationId(id);

        IsolatedRuntimeMethodInfoAccess.untrackInCurrentIsolate(installInfo.getCodeInfo());
        return installInfo;
    }

    private static void installPrepared(SharedMethod method, CodeInstallInfo installInfo, SubstrateInstalledCode installedCode) {
        IsolatedRuntimeMethodInfoAccess.startTrackingInCurrentIsolate(installInfo.getCodeInfo());

        IsolatedReferenceAdjuster.adjustAndDispose(installInfo.getAdjusterData(), IsolatedCompileClient.get().getHandleSet());
        installInfo.setAdjusterData(WordFactory.nullPointer());

        doInstallPrepared(method, installInfo.getCodeInfo(), installedCode);
        UnmanagedMemory.free(installInfo);
    }

    private final IsolateThread targetIsolate;
    private final CompilationIdentifier compilationId;

    private IsolatedRuntimeCodeInstaller(IsolateThread targetIsolate, SharedRuntimeMethod method, CompilationResult compilation) {
        super(method, compilation);
        this.targetIsolate = targetIsolate;
        this.compilationId = compilation.getCompilationId();
    }

    @Override
    protected Pointer allocateCodeMemory(long size) {
        PointerBase memory = allocateCodeMemory0(targetIsolate, WordFactory.unsigned(size));
        if (memory.isNull()) {
            throw new OutOfMemoryError("Could not allocate memory for runtime-compiled code.");
        }
        return (Pointer) memory;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static CodePointer allocateCodeMemory0(@SuppressWarnings("unused") IsolateThread targetIsolate, UnsignedWord size) {
        return RuntimeCodeInfoAccess.allocateCodeMemory(size);
    }

    @Override
    protected void makeCodeMemoryExecutableReadOnly(Pointer start, UnsignedWord size) {
        makeCodeMemoryExecutableReadOnly0(targetIsolate, start, size);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void makeCodeMemoryExecutableReadOnly0(@SuppressWarnings("unused") IsolateThread targetIsolate, Pointer start, UnsignedWord size) {
        RuntimeCodeInfoAccess.makeCodeMemoryExecutableReadOnly((CodePointer) start, size);
    }

    @Override
    protected void makeCodeMemoryExecutableWritable(Pointer start, UnsignedWord size) {
        makeCodeMemoryExecutableWritable0(targetIsolate, start, size);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static void makeCodeMemoryExecutableWritable0(@SuppressWarnings("unused") IsolateThread targetIsolate, Pointer start, UnsignedWord size) {
        RuntimeCodeInfoAccess.makeCodeMemoryExecutableWritable((CodePointer) start, size);
    }
}
