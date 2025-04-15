/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.gc;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.word.Word.nullPointer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemoryGrowNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemorySizeNode;
import com.oracle.svm.webimage.wasm.types.WasmUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.word.Word;

/**
 * Manages the runtime memory layout.
 * <p>
 * Currently, WASM has only a single linear memory, so it is impossible to create more than one
 * memory region that can grow (almost) arbitrarily (unlike in 64-bit virtual address spaces).
 * <p>
 * The WASM memory layout looks like this:
 *
 * <pre>
 *     Address ranges   Contents
 *
 *                  0 +--------------------------------------------------+
 *                    | Not used, acts as the NULL region so that        |
 *                    | pointers to address 0 aren't  valid              |
 *          HEAP_BASE +--------------------------------------------------+
 *                    | All objects in the image heap                    |
 *           HEAP_TOP +--------------------------------------------------+
 *                    | Unused.                                          |
 *                    | To make sure the stack is WASM-page aligned      |
 *       STACK_BOTTOM +--------------------------------------------------+
 *                    | Reserved for shadow stack                        |
 *         STACK_BASE +--------------------------------------------------+
 * (= ALLOCATOR_BASE) | Allocator region                                 |
 *                    | Contains all memory allocated at runtime         |
 *       allocatorTop +--------------------------------------------------+
 *                    | Available memory for the allocator region to     |
 *                    | grow into before it needs to request more memory |
 *                    | from the runtime                                 |
 *             size() +--------------------------------------------------+
 *                    | Remaining available memory, but not yet          |
 *                    | requested from the runtime                       |
 *                    |                                                  |
 *                    :                        ...                       :
 * </pre>
 */
public class MemoryLayout {
    /**
     * The base address of the image heap.
     */
    public static final Pointer HEAP_BASE = Word.pointer(1 << 12);

    /**
     * The size of the image heap in bytes.
     */
    public static final CGlobalData<Word> IMAGE_HEAP_SIZE = CGlobalDataFactory.forSymbol("__webimage_heap_size");

    /**
     * The size of the shadow stack in Wasm pages.
     */
    public static final CGlobalData<Word> STACK_SIZE = CGlobalDataFactory.forSymbol("__webimage_stack_size");

    /**
     * The address of the first byte after the last byte in the image heap.
     * <p>
     * The image heap has size {@code HEAP_TOP - HEAP_BASE}.
     */
    public static final CGlobalData<Word> HEAP_TOP = CGlobalDataFactory.forSymbol("__webimage_heap_top");

    /**
     * The address of the bottom of the shadow stack. If the stack ever extends below this point, it
     * causes a stack-overflow.
     */
    public static final CGlobalData<Word> STACK_BOTTOM = CGlobalDataFactory.forSymbol("__webimage_stack_bottom");

    /**
     * The address of the base of the shadow stack. The stack grows from here on down.
     */
    public static final CGlobalData<Word> STACK_BASE = CGlobalDataFactory.forSymbol("__webimage_stack_base");

    /**
     * The first byte that the allocator can use.
     */
    public static final CGlobalData<Word> ALLOCATOR_BASE = CGlobalDataFactory.forSymbol("__webimage_allocator_base");

    /**
     * The current end of the allocator region.
     * <p>
     * The is dynamically bumped when allocations happen.
     */
    private static Pointer allocatorTop = nullPointer();

    /**
     * Lays out the initial memory once the heap and stack sizes are known.
     * <p>
     * Fills the given map with values for the {@link CGlobalData} for this class.
     *
     * @param globalData A map that is to be filled with {@link CGlobalData}.
     * @param heapSize The size of the image heap in bytes
     * @param stackSize The size of the shadow stack in pages
     * @return Minimally required memory size in bytes.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static int constructLayout(EconomicMap<CGlobalData<?>, UnsignedWord> globalData, int heapSize, int stackSize) {
        Pointer heapTop = HEAP_BASE.add(heapSize);
        // The bottom of the stack is the next WASM page after the image heap.
        Pointer stackBottom = (Pointer) alignToPage(heapTop);
        Pointer stackBase = stackBottom.add(pageSize().multiply(stackSize));

        globalData.put(MemoryLayout.IMAGE_HEAP_SIZE, Word.unsigned(heapSize));
        globalData.put(MemoryLayout.STACK_SIZE, Word.unsigned(stackSize));

        globalData.put(MemoryLayout.HEAP_TOP, heapTop);
        globalData.put(MemoryLayout.STACK_BOTTOM, stackBottom);
        globalData.put(MemoryLayout.STACK_BASE, stackBase);
        globalData.put(MemoryLayout.ALLOCATOR_BASE, stackBase);

        // Also define image heap related symbols that are used in Native Image code
        globalData.put(Isolates.IMAGE_HEAP_BEGIN, HEAP_BASE);
        globalData.put(Isolates.IMAGE_HEAP_END, heapTop);

        return (int) stackBase.rawValue();
    }

    /**
     * Total memory size in pages. Corresponds to the {@code memory.size} instruction.
     */
    @NodeIntrinsic(WasmMemorySizeNode.class)
    public static native int pages();

    /**
     * The {@code memory.grow} instruction.
     *
     * @return Old memory size in number of pages.
     */
    @NodeIntrinsic(WasmMemoryGrowNode.class)
    public static native UnsignedWord grow(UnsignedWord numPages);

    @AlwaysInline("Is lowered to a constant")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getStackBottom() {
        return STACK_BOTTOM.get();
    }

    @AlwaysInline("Is lowered to a constant")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getStackBase() {
        return STACK_BASE.get();
    }

    /**
     * The base of the allocator region.
     */
    @AlwaysInline("Is lowered to a constant")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getAllocatorBase() {
        return ALLOCATOR_BASE.get();
    }

    /**
     * The top of the allocator region (exclusive).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getAllocatorTop() {
        return allocatorTop;
    }

    /**
     * Bumps the top of the allocator region.
     *
     * @param newTop The new top of the region, must be page-aligned.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called during initialization")
    private static void setAllocatorTop(Pointer newTop) {
        assert newTop.belowOrEqual(size()) : "Allocator top is above end of memory";
        assert newTop.aboveOrEqual(allocatorTop) : "Allocator top is below old top";
        assert alignToPage(newTop).equal(newTop) : "Allocator top is not page-aligned";
        allocatorTop = newTop;
    }

    /**
     * Total memory size in bytes.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called during initialization")
    public static UnsignedWord size() {
        return pageSize().multiply(pages());
    }

    @Fold
    public static UnsignedWord pageSize() {
        return Word.unsigned(WasmUtil.PAGE_SIZE);
    }

    /**
     * Initializes the remainder of the memory layout.
     */
    @Uninterruptible(reason = "Runs while module is constructed", callerMustBe = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Allocator not yet initialized")
    public static void initialize() {
        // Initially, the allocator region is completely empty.
        setAllocatorTop(getAllocatorBase());
        assert checkAlignments();
        WasmAllocation.initialize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called during initialization")
    public static boolean checkAlignments() {
        // The stack and allocator base must be page aligned.
        VMError.guarantee(UnsignedUtils.isAMultiple(getStackBottom(), pageSize()));
        VMError.guarantee(UnsignedUtils.isAMultiple(getAllocatorBase(), pageSize()));
        VMError.guarantee(UnsignedUtils.isAMultiple(allocatorTop, pageSize()));

        // Always return true so that this method can be used in an assertion.
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called during initialization")
    public static UnsignedWord alignToPage(UnsignedWord address) {
        return UnsignedUtils.roundUp(address, pageSize());
    }

    @Uninterruptible(reason = "Modifies memory size.")
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called during initialization")
    public static boolean ensureMemorySize(UnsignedWord numBytes) {
        int currentSize = pages();

        UnsignedWord numPages = alignToPage(numBytes).unsignedDivide(pageSize());

        if (numPages.aboveThan(currentSize)) {
            return tryGrow(numPages.subtract(currentSize));
        } else {
            return true;
        }
    }

    @Uninterruptible(reason = "Modifies memory size.")
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Called during initialization")
    public static boolean tryGrow(UnsignedWord numPages) {
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, numPages.equal(0))) {
            return true;
        }

        UnsignedWord oldSize = grow(numPages);
        return !oldSize.equal(Word.unsigned(-1));
    }

    /**
     * Ensures that the allocator region contains {@code [ALLOCATOR_BASE, newTop)}.
     *
     * @return The new top (may differ from given top address). Or null if growing memory fails
     */
    @Uninterruptible(reason = "Modifies memory size.")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    public static Pointer ensureAllocatorRegion(Pointer newTop) {
        Pointer alignedTop = (Pointer) alignToPage(newTop);
        if (ensureMemorySize(alignedTop)) {
            setAllocatorTop(alignedTop);
            return getAllocatorTop();
        }

        return nullPointer();
    }

}
