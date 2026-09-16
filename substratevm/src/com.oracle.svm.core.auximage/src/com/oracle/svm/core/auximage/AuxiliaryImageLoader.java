/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.concurrent.ThreadLocalRandom;

import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.genscavenge.ImageHeapInfo;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.os.AuxiliaryImageProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.Timer;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.guest.staging.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

/**
 * Loads an auxiliary image and provides access to it. Sufficient address space must have been
 * reserved with an {@linkplain SubstrateOptions#ReservedAuxiliaryImageBytes option} or
 * {@linkplain CreateIsolateParameters#getAuxiliaryImageReservedSpaceSize() isolate parameters}.
 * Auxiliary images can also be loaded {@linkplain CreateIsolateParameters#getAuxiliaryImagePath()
 * during isolate creation} and accessed through {@link #getLoaded()}.
 * <p>
 * Auxiliary images are tightly coupled with the native image from which they were captured. Loading
 * a persisted auxiliary image during execution of a different native image is not supported and
 * results in errors or undefined behavior.
 *
 * @see AuxiliaryImageBuilder
 */
public final class AuxiliaryImageLoader {
    private static final CGlobalData<WordPointer> primaryImageId = CGlobalDataFactory.createWord(generatePrimaryImageId());

    private static WordBase generatePrimaryImageId() {
        long value;
        do {
            value = ThreadLocalRandom.current().nextLong();
        } while (value == 0);
        return Word.signed(value);
    }

    /** The primary image's identifier, non-zero and reasonably unique for safety checks. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static ComparableWord getPrimaryImageId() {
        return primaryImageId.get().read();
    }

    /** The begin of a reserved space for auxiliary images, or {@code null}. */
    private static Pointer auxImageReservedBegin;
    private static UnsignedWord auxImageReservedBytes;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setAuxImageReservedSpace(Pointer begin, UnsignedWord reservedBytes) {
        assert auxImageReservedBegin.isNull();
        assert auxImageReservedBytes.equal(Word.zero());

        assert PointerUtils.isAMultiple(auxImageReservedBegin, Word.unsigned(Heap.getHeap().getImageHeapAlignment()));
        assert UnsignedUtils.isAMultiple(auxImageReservedBytes, VirtualMemoryProvider.get().getGranularity());

        auxImageReservedBegin = begin;
        auxImageReservedBytes = reservedBytes;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasAuxImageReservedSpace() {
        return auxImageReservedBegin.isNonNull() && auxImageReservedBytes.aboveThan(0);
    }

    /** The start of a currently loaded auxiliary image, or {@code null}. */
    private static Pointer auxImageBegin;
    private static Pointer auxImageEnd;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setAuxImageLocation(Pointer begin, Pointer end, UnsignedWord imageHeapToOriginObjectOffset) {
        VMError.guarantee(begin.isNull() == end.isNull() && auxImageBegin.isNull() == auxImageEnd.isNull(), "sanity");
        VMError.guarantee(auxImageBegin.isNull() || begin.isNull(), "Cannot replace a loaded image in-place");
        auxImageBegin = begin;
        auxImageEnd = end;
        setInstanceReference(imageHeapToOriginObjectOffset);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void setInstanceReference(UnsignedWord imageHeapToOriginObjectOffset) {
        AuxiliaryImageMetadata meta = null;
        ImageHeapInfo heapInfo = null;
        if (auxImageBegin.isNonNull()) {
            Pointer objectAddress = KnownIntrinsics.heapBase().add(imageHeapToOriginObjectOffset);
            VMError.guarantee(objectAddress.aboveOrEqual(auxImageBegin) && objectAddress.belowThan(auxImageEnd));
            meta = (AuxiliaryImageMetadata) objectAddress.toObject();
            heapInfo = meta.heapInfo;
        }

        AuxiliaryImageHeapImpl heapImpl = AuxiliaryImageHeapImpl.singleton();
        heapImpl.heapInfo = heapInfo;

        loadedInstance = meta;
    }

    private static volatile AuxiliaryImageMetadata loadedInstance;

    /**
     * Load an auxiliary image from the given file, provided that no other auxiliary image is
     * currently loaded. Loading an auxiliary image requires reserving sufficient memory in the
     * address space, either
     * {@linkplain CreateIsolateParameters#getAuxiliaryImageReservedSpaceSize() during isolate
     * creation} or {@linkplain SubstrateOptions#ReservedAuxiliaryImageBytes via an option}.
     */
    public static AuxiliaryImage load(String filePath) {
        LoadAuxiliaryImageOperation op = new LoadAuxiliaryImageOperation(filePath);
        op.enqueue();
        if (op.caughtException != null) {
            throw op.caughtException;
        }
        notifyObserversAfterCodeInstalled();
        return getLoaded();
    }

    /**
     * Returns the {@link AuxiliaryImage} object to access the currently loaded auxiliary image.
     * Throws {@link IllegalStateException} if no image is loaded.
     */
    public static AuxiliaryImage getLoaded() {
        AuxiliaryImageMetadata meta = loadedInstance;
        if (meta == null) {
            throw new IllegalStateException("No auxiliary image has been loaded.");
        }
        return meta.image;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static AuxiliaryImageMetadata getLoadedMetadata() {
        return loadedInstance;
    }

    /**
     * Returns <code>true</code> if an {@link AuxiliaryImage} is loaded, else <code>false</code>.
     */
    public static boolean hasLoaded() {
        return loadedInstance != null;
    }

    private static final class LoadAuxiliaryImageOperation extends JavaVMOperation {
        private final String filePath;
        private RuntimeException caughtException;

        LoadAuxiliaryImageOperation(String filePath) {
            super(VMOperationInfos.get(LoadAuxiliaryImageOperation.class, "Load auxiliary image", SystemEffect.SAFEPOINT));
            this.filePath = filePath;
        }

        @Override
        protected void operate() {
            try {
                operate0();
            } catch (RuntimeException e) {
                caughtException = e;
            }
        }

        protected void operate0() {
            if (loadedInstance != null) {
                throw new IllegalStateException("An auxiliary image is already loaded.");
            }
            if (!hasAuxImageReservedSpace()) {
                throw new IllegalStateException("No space was reserved for loading an auxiliary image during isolate creation");
            }

            try (Timer _ = AuxiliaryImageTracing.loadTimers.total.start()) {
                WordPointer loadedBegin = UnsafeStackValue.get(WordPointer.class);
                WordPointer loadedEnd = UnsafeStackValue.get(WordPointer.class);
                try (CTypeConversion.CCharPointerHolder cFilePath = CTypeConversion.toCString(filePath)) {
                    try (Timer _ = AuxiliaryImageTracing.loadTimers.load.start()) {
                        int code = AuxiliaryImageProvider.get().loadAuxiliaryImage(auxImageReservedBegin, auxImageReservedBytes, cFilePath.get(), loadedBegin, loadedEnd);
                        if (code != 0) {
                            throw new RuntimeException("Auxiliary image could not be loaded: " + CEntryPointErrors.getDescription(code));
                        }
                    }

                    try (Timer _ = AuxiliaryImageTracing.loadTimers.installCode.start()) {
                        doInstallLoadedImageCode();
                    }

                    AuxiliaryImageHeapImpl heapImpl = AuxiliaryImageHeapImpl.singleton();
                    VMError.guarantee(auxImageBegin.isNonNull() && auxImageEnd.isNonNull() && loadedInstance != null && heapImpl.heapInfo != null);
                }
            } finally {
                AuxiliaryImageTracing.traceAfterLoad();
            }
        }
    }

    /**
     * Installs the persisted runtime-compiled code that is contained in the currently loaded
     * auxiliary image into the code cache, or does nothing if no auxiliary image is loaded.
     */
    static void installLoadedImageCode() {
        doInstallLoadedImageCode();
        notifyObserversAfterCodeInstalled();
    }

    private static void doInstallLoadedImageCode() {
        AuxiliaryImageMetadata instance = AuxiliaryImageLoader.loadedInstance;
        if (instance != null) {
            if (PersistedRuntimeCode.isSupportedInCurrentImage()) {
                if (instance.code.length > 0) { // at once instead of triggering multiple safepoints
                    InstallAuxiliaryImagePersistedCodeOperation vmOp = new InstallAuxiliaryImagePersistedCodeOperation(instance);
                    vmOp.enqueue();
                }
            } else {
                assert instance.code.length == 0 : "No runtime compilation, where does the compiled code come from?";
            }
        }
    }

    private static void notifyObserversAfterCodeInstalled() {
        assert !VMOperation.isInProgressAtSafepoint();
        AuxiliaryImageMetadata instance = AuxiliaryImageLoader.loadedInstance;
        if (instance != null && PersistedRuntimeCode.isSupportedInCurrentImage() && instance.code.length > 0) {
            for (AuxiliaryImageCodeObserver observer : instance.codeObservers) {
                observer.afterAllAuxiliaryImageCodeInstalled();
            }
        }
    }

    private static class InstallAuxiliaryImagePersistedCodeOperation extends JavaVMOperation {
        private final AuxiliaryImageMetadata instance;

        InstallAuxiliaryImagePersistedCodeOperation(AuxiliaryImageMetadata instance) {
            super(VMOperationInfos.get(InstallAuxiliaryImagePersistedCodeOperation.class, "Install auxiliary image persisted code", SystemEffect.SAFEPOINT));
            this.instance = instance;
        }

        @Override
        protected void operate() {
            try {
                PersistedRuntimeCodeInstaller.installAll(instance);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Persisted code cache installation failed.", t);
            }
        }
    }

    private AuxiliaryImageLoader() {
    }
}
