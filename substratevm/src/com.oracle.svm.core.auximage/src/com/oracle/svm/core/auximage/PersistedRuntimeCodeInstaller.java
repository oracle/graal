/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import static com.oracle.svm.core.c.NonmovableArrays.fromImageHeap;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.AssertionsSupport;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.guest.staging.option.RuntimeOptionValues;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;

final class PersistedRuntimeCodeInstaller extends AbstractRuntimeCodeInstaller {

    @SuppressWarnings("unlikely-arg-type")
    static void installAll(AuxiliaryImageMetadata image) {
        long totalCodeAndDataSize = 0;
        for (PersistedRuntimeCode p : image.code) {
            if (p.isValid()) {
                totalCodeAndDataSize = Math.addExact(totalCodeAndDataSize, ((ValidPersistedRuntimeCode) p).alignedCodeAndDataSize());
            }
            /*
             * Call clearAddress on all code for a chance to observe a clean state before
             * reinstalling.
             */
            p.installedCode.clearAddress();
        }

        EnumSet<?> currentCpuFeatures = AuxiliaryImagePersistence.getCPUFeatures();
        boolean cpuFeatureMismatch = image.cpuFeatures == null || currentCpuFeatures == null || !currentCpuFeatures.containsAll(image.cpuFeatures);

        /*
         * Allocate memory for code and data, if image contains valid persisted runtime code.
         */
        Pointer nextCodeAndData = Word.nullPointer();
        if (!cpuFeatureMismatch && totalCodeAndDataSize > 0) {
            nextCodeAndData = (Pointer) RuntimeCodeInfoAccess.allocateCodeMemory(Word.unsigned(totalCodeAndDataSize));
            if (nextCodeAndData.isNull()) {
                throw new OutOfMemoryError("Could not allocate memory for persisted runtime code.");
            }
        }

        for (PersistedRuntimeCode p : image.code) {
            if (cpuFeatureMismatch) {
                p.installedCode.invalidate();
            } else if (p.isValid()) {
                assert nextCodeAndData.isNonNull() : "Code memory should have been pre-allocated";
                ValidPersistedRuntimeCode persistedRuntimeCode = (ValidPersistedRuntimeCode) p;
                new PersistedRuntimeCodeInstaller(nextCodeAndData, persistedRuntimeCode).install();
                nextCodeAndData = nextCodeAndData.add(persistedRuntimeCode.alignedCodeAndDataSize());
            }
        }
        for (AuxiliaryImageCodeObserver observer : image.codeObservers) {
            observer.onAllAuxiliaryImageCodeInstalled();
        }
    }

    private final Pointer codeAndData;
    private final ValidPersistedRuntimeCode persisted;

    private PersistedRuntimeCodeInstaller(Pointer codeAndData, ValidPersistedRuntimeCode code) {
        this.codeAndData = codeAndData;
        this.persisted = code;
    }

    private void install() {
        try (DebugContext debug = new DebugContext.Builder(RuntimeOptionValues.singleton().get()).build()) {
            int codeSize = persisted.codeBytes.length;
            int dataOffset = persisted.dataOffset;
            int dataSize = persisted.dataBytes.length;
            int codeAndDataMemorySize = persisted.alignedCodeAndDataSize();

            ByteBuffer buffer = CTypeConversion.asByteBuffer(codeAndData, codeAndDataMemorySize);
            buffer.put(persisted.codeBytes);
            makeCodeMemoryExecutableReadOnly(codeAndData, Word.unsigned(codeSize));

            buffer.position(persisted.dataOffset);
            buffer.put(persisted.dataBytes);

            NonmovableArray<InstalledCodeObserver.InstalledCodeObserverHandle> observerHandles;
            try (Indent _ = debug.logAndIndent("Registering code observers while installing persisted method %s", persisted.name)) {
                InstalledCodeObserver[] observers = ImageSingletons.lookup(InstalledCodeObserverSupport.class).createObservers(debug, persisted.method, null, codeAndData, codeSize);
                observerHandles = InstalledCodeObserverSupport.installObservers(observers);
            }

            CodeInfo info = RuntimeCodeInfoAccess.allocateMethodInfo(fromImageHeap(persisted.preparedObjectData));
            Object tether = UntetheredCodeInfoAccess.getTetherUnsafe(info); // already tethered
            try {
                RuntimeCodeInfoAccess.initialize(info, codeAndData, persisted.entryPointOffset, codeSize, dataOffset, dataSize, codeAndDataMemorySize, persisted.tier, observerHandles, true);
                RuntimeCodeInfoAccess.setCodeObjectConstantsInfo(info, fromImageHeap(persisted.codeConstantsReferenceMapEncoding), persisted.codeConstantsReferenceMapIndex);
                CodeInfoAccess.setState(info, CodeInfo.STATE_CODE_CONSTANTS_LIVE);
                CodeInfoAccess.setEncodings(info, fromImageHeap(persisted.objectConstants), fromImageHeap(persisted.classes),
                                fromImageHeap(persisted.memberNames), fromImageHeap(persisted.otherStrings), fromImageHeap(persisted.methodTable), persisted.methodTableFirstId,
                                persisted.methodTableEntryCount);
                CodeInfoAccess.setFrameInfo(info, fromImageHeap(persisted.frameInfoEncodings));
                CodeInfoAccess.setCodeInfo(info, fromImageHeap(persisted.codeInfoIndex), fromImageHeap(persisted.codeInfoEncodings), persisted.codeInfoIndexEntriesPerBlock,
                                fromImageHeap(persisted.codeInfoDefaultFrameInfoIndexes),
                                fromImageHeap(persisted.stackReferenceMapEncoding));
                RuntimeCodeInfoAccess.setDeoptimizationMetadata(info, fromImageHeap(persisted.deoptimizationStartOffsets),
                                fromImageHeap(persisted.deoptimizationEncodings), fromImageHeap(persisted.deoptimizationObjectConstants));

                doInstallPreparedAndTethered(persisted.method, info, persisted.installedCode);

                if (haveAssertions()) {
                    RuntimeCodeInfoAccess.guaranteeAllObjectsInImageHeap(info);
                }
            } finally {
                CodeInfoAccess.releaseTether(info, tether);
            }
        }
    }

    @Fold
    static boolean haveAssertions() {
        return AssertionsSupport.singleton().desiredAssertionStatus(CodeInfoAccess.class);
    }
}
