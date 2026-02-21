/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.guest.staging.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.code.DynamicMethodAddressResolutionHeapSupport;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.os.AbstractCopyingImageHeapProvider;
import com.oracle.svm.core.os.LayeredImageHeapSupport;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;
import org.graalvm.word.impl.Word;

/**
 * An image heap provider for Windows that creates image heaps that are copy-on-write clones of the
 * loaded image heap. Supports both single-layer and layered image builds. For layered builds, each
 * layer's image heap is individually copied and patched using the platform-independent
 * {@link LayeredImageHeapSupport}.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class WindowsImageHeapProvider extends AbstractCopyingImageHeapProvider {

    @Override
    @Uninterruptible(reason = "Called during early isolate initialization.")
    public void resolveForwardSymbolReferences() {
        if (ImageLayerBuildingSupport.buildingImageLayer() && ImageLayerBuildingSupport.buildingSharedLayer()) {
            patchForwardReferenceCGlobals();
        }
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer heapBaseOut, WordPointer imageHeapEndOut) {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            /* Non-layered: delegate to the standard copying path. */
            return super.initialize(reservedAddressSpace, reservedSize, heapBaseOut, imageHeapEndOut);
        }

        /*
         * On PE/COFF (Windows), the NEXT_SECTION linked list cannot be populated via relocations
         * because the MSVC linker requires all symbols to be resolved at link time, and the next
         * layer's section symbol doesn't exist yet when the current layer is built. Patch the
         * forward references at runtime BEFORE any code iterates through the linked list
         * (getTotalRequiredAddressSpaceSize, patchLayeredImageHeap, initializeLayeredImage).
         */
        patchNextSectionPointers();

        /* Layered build: initialize each layer's image heap separately. */
        Pointer selfReservedMemory = Word.nullPointer();
        UnsignedWord requiredSize = getTotalRequiredAddressSpaceSize();
        if (reservedAddressSpace.isNull()) {
            UnsignedWord alignment = Word.unsigned(Heap.getHeap().getHeapBaseAlignment());
            selfReservedMemory = VirtualMemoryProvider.get().reserve(requiredSize, alignment, false);
            if (selfReservedMemory.isNull()) {
                return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
            }
        } else if (reservedSize.belowThan(requiredSize)) {
            return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
        }

        Pointer heapBase;
        Pointer selfReservedHeapBase;
        if (DynamicMethodAddressResolutionHeapSupport.isEnabled()) {
            UnsignedWord preHeapRequiredBytes = getPreHeapAlignedSizeForDynamicMethodAddressResolver();
            if (selfReservedMemory.isNonNull()) {
                selfReservedHeapBase = selfReservedMemory.add(preHeapRequiredBytes);
                heapBase = selfReservedHeapBase;
            } else {
                heapBase = reservedAddressSpace.add(preHeapRequiredBytes);
                selfReservedHeapBase = Word.nullPointer();
            }

            int error = DynamicMethodAddressResolutionHeapSupport.get().initialize();
            if (error != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return error;
            }

            error = DynamicMethodAddressResolutionHeapSupport.get().install(heapBase);
            if (error != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return error;
            }
        } else {
            heapBase = selfReservedMemory.isNonNull() ? selfReservedMemory : reservedAddressSpace;
            selfReservedHeapBase = selfReservedMemory;
        }

        /* Update heap base and image heap end. */
        assert PointerUtils.isAMultiple(heapBase, Word.unsigned(Heap.getHeap().getHeapBaseAlignment()));
        heapBaseOut.write(heapBase);

        Pointer imageHeapEnd = getImageHeapEnd(heapBase);
        assert PointerUtils.isAMultiple(imageHeapEnd, VirtualMemoryProvider.get().getGranularity());
        imageHeapEndOut.write(imageHeapEnd);

        Pointer imageHeapStart = getImageHeapBegin(heapBase);
        int result = initializeLayeredImage(imageHeapStart, imageHeapEnd, selfReservedHeapBase);
        if (result != CEntryPointErrors.NO_ERROR) {
            freeImageHeap(selfReservedHeapBase);
        }
        return result;
    }

    /**
     * Initialize a layered image by copying each layer's image heap and then applying cross-layer
     * patches.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private int initializeLayeredImage(Pointer imageHeapStart, Pointer imageHeapEnd, Pointer selfReservedHeapBase) {
        UnsignedWord imageHeapAlignment = Word.unsigned(Heap.getHeap().getImageHeapAlignment());
        assert PointerUtils.isAMultiple(imageHeapStart, imageHeapAlignment);

        /* Apply cross-layer patches (code pointer relocation, heap ref patches, field updates). */
        LayeredImageHeapSupport.patchLayeredImageHeap();

        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        Pointer currentHeapStart = imageHeapStart;

        while (currentSection.isNonNull()) {
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            Word heapWritableBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_BEGIN));
            Word heapWritableEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_END));

            /* Each layer's image heap starts at an aligned offset. */
            currentHeapStart = PointerUtils.roundUp(currentHeapStart, imageHeapAlignment);

            UnsignedWord imageHeapSize = getImageHeapSizeInFile(heapBegin, heapEnd);

            /* Commit memory and copy this layer's image heap. */
            int result = commitAndCopyMemory(heapBegin, imageHeapSize, currentHeapStart);
            if (result != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return result;
            }

            /* Protect read-only parts before the writable section. */
            UnsignedWord writableBeginPageOffset = UnsignedUtils.roundDown(heapWritableBegin.subtract(heapBegin), pageSize);
            if (writableBeginPageOffset.aboveThan(0)) {
                if (VirtualMemoryProvider.get().protect(currentHeapStart, writableBeginPageOffset, Access.READ) != 0) {
                    freeImageHeap(selfReservedHeapBase);
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }

            /* Protect read-only parts after the writable section. */
            UnsignedWord writableEndPageOffset = UnsignedUtils.roundUp(heapWritableEnd.subtract(heapBegin), pageSize);
            if (writableEndPageOffset.belowThan(imageHeapSize)) {
                Pointer afterWritableBoundary = currentHeapStart.add(writableEndPageOffset);
                UnsignedWord afterWritableSize = imageHeapSize.subtract(writableEndPageOffset);
                if (VirtualMemoryProvider.get().protect(afterWritableBoundary, afterWritableSize, Access.READ) != 0) {
                    freeImageHeap(selfReservedHeapBase);
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }

            currentHeapStart = currentHeapStart.add(imageHeapSize);

            /* Advance to the next layer. */
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }
        assert imageHeapEnd.equal(currentHeapStart);
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * Pointer to the CGlobal fixup table in the .svm_fix section. The table is present in all
     * PE/COFF builds (with count=0 for non-shared layers). Must be unconditionally non-null so
     * the field can be relinked across layers without a class initialization mismatch.
     */
    private static final CGlobalData<Pointer> CGLOBAL_FIXUP_TABLE = CGlobalDataFactory.forSymbol("__svm_cglobal_fixup_table");

    private static final CGlobalData<CCharPointer> MSG_GET_MODULE_FAILED = CGlobalDataFactory.createCString("GetModuleHandleA(NULL) failed");
    private static final CGlobalData<CCharPointer> MSG_GET_PROC_FAILED = CGlobalDataFactory.createCString("GetProcAddress failed for layer symbol");
    /**
     * Patch the NEXT_SECTION linked list that could not be populated at link time on PE/COFF. Uses
     * {@code GetProcAddress} to find the next layer's section symbol in the main executable module.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static void patchNextSectionPointers() {
        CGlobalData<CCharPointer> nextSymbolName = ImageLayerSection.getNextLayerSectionSymbolName();
        if (nextSymbolName == null) {
            return;
        }
        Pointer section = ImageLayerSection.getInitialLayerSection().get();
        Pointer nextSectionValue = section.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        if (section.isNonNull() && nextSectionValue.isNull()) {
            /*
             * The next layer's section symbol is exported from the main executable (or a loaded
             * DLL). Use GetModuleHandleA(NULL) to get the executable's module handle, then
             * GetProcAddress to find the symbol.
             */
            HMODULE exeModule = LibLoaderAPI.GetModuleHandleA(Word.nullPointer());
            if (exeModule.isNull()) {
                CEntryPointActions.failFatally(0xBAD1, MSG_GET_MODULE_FAILED.get());
            }
            PointerBase nextSection = LibLoaderAPI.GetProcAddress(exeModule, nextSymbolName.get());
            if (nextSection.isNull()) {
                CEntryPointActions.failFatally(0xBAD2, MSG_GET_PROC_FAILED.get());
            }
            section.writeWord(ImageLayerSection.getEntryOffset(NEXT_SECTION), nextSection);
        }
    }

    /**
     * Resolve forward-reference CGlobal slots using the runtime fixup table. The table is in the
     * .svm_fix section and maps CGlobal slot addresses to symbol names. For each entry, we look up
     * the symbol in the extension layer's module (which auto-exports all symbols) via
     * GetProcAddress and write the resolved address to the CGlobal slot.
     *
     * <pre>
     * Fixup table layout:
     *   int32: numEntries
     *   int32: stringDataOffset (from table start)
     *   int64[numEntries]: absolute addresses of CGlobal slots (resolved by PE loader)
     *   int32[numEntries]: string offsets (relative to stringDataOffset)
     *   char[]: null-terminated symbol name strings
     * </pre>
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static void patchForwardReferenceCGlobals() {
        Pointer fixupTable = CGLOBAL_FIXUP_TABLE.get();
        int numEntries = fixupTable.readInt(0);
        if (numEntries == 0) {
            return;
        }

        int stringDataOffset = fixupTable.readInt(4);
        Pointer slotPtrs = fixupTable.add(8);
        Pointer nameOffsets = slotPtrs.add(Word.unsigned(numEntries).multiply(8));
        Pointer stringData = fixupTable.add(stringDataOffset);

        HMODULE exeModule = LibLoaderAPI.GetModuleHandleA(Word.nullPointer());
        if (exeModule.isNull()) {
            CEntryPointActions.failFatally(0xBAD1, MSG_GET_MODULE_FAILED.get());
        }

        for (int i = 0; i < numEntries; i++) {
            Pointer slotAddr = slotPtrs.readWord(i * 8);
            int nameOff = nameOffsets.readInt(i * 4);
            CCharPointer symbolName = (CCharPointer) stringData.add(nameOff);

            PointerBase resolved = LibLoaderAPI.GetProcAddress(exeModule, symbolName);
            if (resolved.isNonNull()) {
                slotAddr.writeWord(0, resolved);
            }
            /*
             * If GetProcAddress returns null, the symbol is in a system DLL (CRT, kernel32, etc.)
             * and was already resolved by the linker via import libraries. Leave it as-is.
             */
        }
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int commitAndCopyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            /*
             * In layered builds, patchLayeredImageHeap() has already modified the loaded image
             * heaps in memory (e.g. cross-layer code pointer patches, heap reference patches,
             * field updates). We must copy from the loaded memory to preserve these patches. The
             * file-mapping optimization would map fresh from the on-disk image, losing the
             * patches applied to the writable heap sections.
             */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        HANDLE imageHeapFileMapping = getImageHeapFileMapping();
        if (imageHeapFileMapping.isNull()) {
            /* Fall back to copying from memory. */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        /* Map a copy-on-write view of the image heap. */
        if (VirtualMemoryProvider.get().mapFile(newImageHeap, imageHeapSize, imageHeapFileMapping, getImageHeapFileOffset(),
                        Access.READ | Access.WRITE).isNull()) {
            /* Fall back to copying from memory. */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        /* Copy relocatable pages. */
        return copyMemory(IMAGE_HEAP_RELOCATABLE_BEGIN.get(), IMAGE_HEAP_RELOCATABLE_END.get().subtract(IMAGE_HEAP_RELOCATABLE_BEGIN.get()),
                        newImageHeap.add(IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(IMAGE_HEAP_BEGIN.get())));
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        LibC.memcpy(newImageHeap, loadedImageHeap, imageHeapSize);
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * The pseudovariable __ImageBase provided by the MSVC linker represents the DOS header of the
     * module, which is what a Win32 module begins with. In other words, it is the base address of
     * the module and is the same as its HMODULE.
     */
    private static final CGlobalData<PointerBase> IMAGE_BASE = CGlobalDataFactory.forSymbol("__ImageBase");

    /** The cached handle of the image heap file mapping that closes when the process exits. */
    private static final CGlobalData<WordPointer> IMAGE_HEAP_FILE_MAPPING = CGlobalDataFactory.createWord(WindowsUtils.UNINITIALIZED_HANDLE);

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static HANDLE getImageHeapFileMapping() {
        HANDLE value = IMAGE_HEAP_FILE_MAPPING.get().read();
        if (value.equal(WindowsUtils.UNINITIALIZED_HANDLE)) {
            HANDLE fileMapping = createImageHeapFileMapping();
            HANDLE existingMapping = (HANDLE) ((Pointer) IMAGE_HEAP_FILE_MAPPING.get()).compareAndSwapWord(0,
                            WindowsUtils.UNINITIALIZED_HANDLE, fileMapping, LocationIdentity.ANY_LOCATION);

            if (existingMapping.equal(WindowsUtils.UNINITIALIZED_HANDLE)) {
                value = fileMapping;
            } else {
                /* Another thread has already created the mapping, so use that. */
                value = existingMapping;
                WinBase.CloseHandle(fileMapping);
            }
        }
        return value;
    }

    /** Returns a handle to the image heap file mapping or the null pointer in case of an error. */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static HANDLE createImageHeapFileMapping() {
        /* Get the path of the file that contains the image heap. */
        WCharPointer filePath = StackValue.get(WinBase.MAX_PATH, WCharPointer.class);
        int length = LibLoaderAPI.GetModuleFileNameW((HMODULE) IMAGE_BASE.get(), filePath, WinBase.MAX_PATH);
        if (length == 0 || length == WinBase.MAX_PATH) {
            return Word.nullPointer();
        }

        /* Open the file for mapping. */
        HANDLE fileHandle = FileAPI.CreateFileW(filePath, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_DELETE(),
                        Word.nullPointer(), FileAPI.OPEN_EXISTING(), 0, Word.nullPointer());
        if (fileHandle.equal(WinBase.INVALID_HANDLE_VALUE())) {
            return Word.nullPointer();
        }

        /* Create the mapping and close the file. */
        HANDLE fileMapping = MemoryAPI.CreateFileMappingW(fileHandle, Word.nullPointer(), MemoryAPI.PAGE_READONLY(),
                        0, 0, Word.nullPointer());
        WinBase.CloseHandle(fileHandle);
        return fileMapping;
    }

    private static final CGlobalData<WordPointer> IMAGE_HEAP_FILE_OFFSET = CGlobalDataFactory.createWord();

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static UnsignedWord getImageHeapFileOffset() {
        /*
         * The file offset of a relative virtual address (RVA) in a PE image can be determined by
         * inspecting the PE image headers.
         *
         * We do this using two helper functions from ntdll.dll: RtlImageNtHeader and
         * RtlAddressInSectionTable. Unfortunately, they are not publicly documented, but I think it
         * is unlikely that this will pose a problem (and it would definitely not go unnoticed).
         *
         * Alternatively, we could use equivalent functions from dbghelp.dll (ImageNtHeader and
         * ImageRvaToVa), but this would introduce an additional DLL dependency (which we would like
         * to avoid). Or we could even do it manually. The tricky part would be finding the header
         * of the section containing the RVA.
         */
        UnsignedWord value = IMAGE_HEAP_FILE_OFFSET.get().read();
        if (value.equal(0)) {
            /* Get the NT header of the module that contains the image heap. */
            PointerBase ntHeader = invokeRtlImageNtHeader(IMAGE_BASE.get());
            /* Compute the RVA of the image heap. */
            int rva = (int) (IMAGE_HEAP_BEGIN.get().rawValue() - IMAGE_BASE.get().rawValue());
            /* Get the file offset of the image heap. */
            value = invokeRtlAddressInSectionTable(ntHeader, rva);
            IMAGE_HEAP_FILE_OFFSET.get().write(value);
        }
        return value;
    }

    private static final CGlobalData<CCharPointer> NTDLL_DLL = CGlobalDataFactory.createCString("ntdll.dll");
    private static final CGlobalData<CCharPointer> RTL_IMAGE_NT_HEADER = CGlobalDataFactory.createCString("RtlImageNtHeader");
    private static final CGlobalData<CCharPointer> RTL_ADDRESS_IN_SECTION_TABLE = CGlobalDataFactory.createCString("RtlAddressInSectionTable");

    private static final int ERROR_BAD_EXE_FORMAT = 0xC1;

    /** Locates the IMAGE_NT_HEADERS structure in a PE image and returns a pointer to the data. */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static PointerBase invokeRtlImageNtHeader(PointerBase imageBase) {
        RtlImageNtHeader rtlImageNtHeader = WindowsUtils.getFunctionPointer(NTDLL_DLL.get(), RTL_IMAGE_NT_HEADER.get(), true);
        PointerBase ntHeader = rtlImageNtHeader.invoke(imageBase);
        if (ntHeader.isNull()) {
            CEntryPointActions.failFatally(ERROR_BAD_EXE_FORMAT, RTL_IMAGE_NT_HEADER.get());
        }
        return ntHeader;
    }

    private interface RtlImageNtHeader extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        PointerBase invoke(PointerBase imageBase);
    }

    /**
     * Locates a relative virtual address (RVA) within the image header of a file that is mapped as
     * a file and returns the offset of the corresponding byte in the file.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static UnsignedWord invokeRtlAddressInSectionTable(PointerBase ntHeader, int rva) {
        RtlAddressInSectionTable rtlAddressInSectionTable = WindowsUtils.getFunctionPointer(NTDLL_DLL.get(), RTL_ADDRESS_IN_SECTION_TABLE.get(), true);
        UnsignedWord offset = (UnsignedWord) rtlAddressInSectionTable.invoke(ntHeader, Word.nullPointer(), rva);
        if (offset.equal(0)) {
            CEntryPointActions.failFatally(ERROR_BAD_EXE_FORMAT, RTL_ADDRESS_IN_SECTION_TABLE.get());
        }
        return offset;
    }

    private interface RtlAddressInSectionTable extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        PointerBase invoke(PointerBase ntHeader, PointerBase base, int rva);
    }
}
