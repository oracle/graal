/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;

/**
 * This class is used to build the stack frame layout for a compiled method. A {@link StackSlot} is
 * used to index slots of the frame relative to the stack pointer. The frame size is only fixed
 * after register allocation when all spill slots have been allocated. Both the outgoing argument
 * area and the spill are can grow until then. Therefore, outgoing arguments are indexed from the
 * stack pointer, while spill slots are indexed from the beginning of the frame (and the total frame
 * size has to be added to get the actual offset from the stack pointer).
 */
public abstract class FrameMap {

    public final TargetDescription target;
    public final RegisterConfig registerConfig;

    /**
     * The final frame size, not including the size of the
     * {@link Architecture#getReturnAddressSize() return address slot}. The value is only set after
     * register allocation is complete, i.e., after all spill slots have been allocated.
     */
    private int frameSize;

    /**
     * Initial size of the area occupied by spill slots and other stack-allocated memory blocks.
     */
    protected int initialSpillSize;

    /**
     * Size of the area occupied by spill slots and other stack-allocated memory blocks.
     */
    protected int spillSize;

    /**
     * Size of the area occupied by outgoing overflow arguments. This value is adjusted as calling
     * conventions for outgoing calls are retrieved. On some platforms, there is a minimum outgoing
     * size even if no overflow arguments are on the stack.
     */
    protected int outgoingSize;

    /**
     * Determines if this frame has values on the stack for outgoing calls.
     */
    private boolean hasOutgoingStackArguments;

    /**
     * The list of stack slots allocated in this frame that are present in every reference map.
     */
    private final List<StackSlot> objectStackSlots;

    /**
     * Records whether an offset to an incoming stack argument was ever returned by
     * {@link #offsetForStackSlot(StackSlot)}.
     */
    private boolean accessesCallerFrame;

    /**
     * Creates a new frame map for the specified method. The given registerConfig is optional, in
     * case null is passed the default RegisterConfig from the CodeCacheProvider will be used.
     */
    public FrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        this.target = codeCache.getTarget();
        this.registerConfig = registerConfig == null ? codeCache.getRegisterConfig() : registerConfig;
        this.frameSize = -1;
        this.outgoingSize = codeCache.getMinimumOutgoingSize();
        this.objectStackSlots = new ArrayList<>();
    }

    protected int returnAddressSize() {
        return target.arch.getReturnAddressSize();
    }

    protected int calleeSaveAreaSize() {
        CalleeSaveLayout csl = registerConfig.getCalleeSaveLayout();
        return csl != null ? csl.size : 0;
    }

    /**
     * Determines if an offset to an incoming stack argument was ever returned by
     * {@link #offsetForStackSlot(StackSlot)}.
     */
    public boolean accessesCallerFrame() {
        return accessesCallerFrame;
    }

    /**
     * Gets the size of a stack slot.
     *
     * @return stack slot size
     */
    public int stackSlotSize() {
        return target.wordSize;
    }

    /**
     * Gets the frame size of the compiled frame, not including the size of the
     * {@link Architecture#getReturnAddressSize() return address slot}.
     *
     * @return The size of the frame (in bytes).
     */
    public int frameSize() {
        assert frameSize != -1 : "frame size not computed yet";
        return frameSize;
    }

    public int outgoingSize() {
        return outgoingSize;
    }

    /**
     * Determines if any space is used in the frame apart from the
     * {@link Architecture#getReturnAddressSize() return address slot}.
     */
    public boolean frameNeedsAllocating() {
        int unalignedFrameSize = spillSize - returnAddressSize();
        return hasOutgoingStackArguments || unalignedFrameSize != 0;
    }

    /**
     * Gets the total frame size of the compiled frame, including the size of the
     * {@link Architecture#getReturnAddressSize() return address slot}.
     *
     * @return The total size of the frame (in bytes).
     */
    public abstract int totalFrameSize();

    /**
     * Gets the current size of this frame. This is the size that would be returned by
     * {@link #frameSize()} if {@link #finish()} were called now.
     */
    public abstract int currentFrameSize();

    /**
     * Aligns the given frame size to the stack alignment size and return the aligned size.
     *
     * @param size the initial frame size to be aligned
     * @return the aligned frame size
     */
    protected abstract int alignFrameSize(int size);

    /**
     * Computes the final size of this frame. After this method has been called, methods that change
     * the frame size cannot be called anymore, e.g., no more spill slots or outgoing arguments can
     * be requested.
     */
    public void finish() {
        assert this.frameSize == -1 : "must only be set once";
        if (freedSlots != null) {
            // If the freed slots cover the complete spill area (except for the return
            // address slot), then the spill size is reset to its initial value.
            // Without this, frameNeedsAllocating() would never return true.
            int total = 0;
            for (StackSlot s : freedSlots) {
                total += target.getSizeInBytes(s.getKind());
            }
            if (total == spillSize - initialSpillSize) {
                // reset spill area size
                spillSize = initialSpillSize;
            }
            freedSlots = null;
        }
        frameSize = currentFrameSize();
        if (frameSize > registerConfig.getMaximumFrameSize()) {
            throw new BailoutException(String.format("Frame size (%d) exceeded maximum allowed frame size (%d).", frameSize, registerConfig.getMaximumFrameSize()));
        }
    }

    /**
     * Computes the offset of a stack slot relative to the frame register.
     *
     * @param slot a stack slot
     * @return the offset of the stack slot
     */
    public int offsetForStackSlot(StackSlot slot) {
        assert (!slot.getRawAddFrameSize() && slot.getRawOffset() < outgoingSize) || (slot.getRawAddFrameSize() && slot.getRawOffset() < 0 && -slot.getRawOffset() <= spillSize) ||
                        (slot.getRawAddFrameSize() && slot.getRawOffset() >= 0);
        if (slot.isInCallerFrame()) {
            accessesCallerFrame = true;
        }
        return slot.getOffset(totalFrameSize());
    }

    /**
     * Computes the index of a stack slot relative to slot 0. This is also the bit index of stack
     * slots in the reference map.
     *
     * @param slot a stack slot
     * @return the index of the stack slot
     */
    public int indexForStackSlot(StackSlot slot) {
        assert offsetForStackSlot(slot) % stackSlotSize() == 0;
        return offsetForStackSlot(slot) / stackSlotSize();
    }

    /**
     * Gets the offset from the stack pointer to the stack area where callee-saved registers are
     * stored.
     *
     * @return The offset to the callee save area (in bytes).
     */
    public abstract int offsetToCalleeSaveArea();

    /**
     * Informs the frame map that the compiled code calls a particular method, which may need stack
     * space for outgoing arguments.
     *
     * @param cc The calling convention for the called method.
     */
    public void callsMethod(CallingConvention cc) {
        reserveOutgoing(cc.getStackSize());
    }

    /**
     * Reserves space for stack-based outgoing arguments.
     *
     * @param argsSize The amount of space (in bytes) to reserve for stack-based outgoing arguments.
     */
    public void reserveOutgoing(int argsSize) {
        assert frameSize == -1 : "frame size must not yet be fixed";
        outgoingSize = Math.max(outgoingSize, argsSize);
        hasOutgoingStackArguments = hasOutgoingStackArguments || argsSize > 0;
    }

    /**
     * Reserves a new spill slot in the frame of the method being compiled. The returned slot is
     * aligned on its natural alignment, i.e., an 8-byte spill slot is aligned at an 8-byte
     * boundary.
     *
     * @param kind The kind of the spill slot to be reserved.
     * @param additionalOffset
     * @return A spill slot denoting the reserved memory area.
     */
    protected abstract StackSlot allocateNewSpillSlot(LIRKind kind, int additionalOffset);

    /**
     * Returns the spill slot size for the given {@link LIRKind}. The default value is the size in
     * bytes for the target architecture.
     *
     * @param kind the {@link LIRKind} to be stored in the spill slot.
     * @return the size in bytes
     */
    public int spillSlotSize(LIRKind kind) {
        return target.getSizeInBytes(kind.getPlatformKind());
    }

    /**
     * Reserves a spill slot in the frame of the method being compiled. The returned slot is aligned
     * on its natural alignment, i.e., an 8-byte spill slot is aligned at an 8-byte boundary, unless
     * overridden by a subclass.
     *
     * @param kind The kind of the spill slot to be reserved.
     * @return A spill slot denoting the reserved memory area.
     */
    public StackSlot allocateSpillSlot(LIRKind kind) {
        assert frameSize == -1 : "frame size must not yet be fixed";
        if (freedSlots != null) {
            for (Iterator<StackSlot> iter = freedSlots.iterator(); iter.hasNext();) {
                StackSlot s = iter.next();
                if (s.getLIRKind().equals(kind)) {
                    iter.remove();
                    if (freedSlots.isEmpty()) {
                        freedSlots = null;
                    }
                    return s;
                }
            }
        }
        int size = spillSlotSize(kind);
        spillSize = NumUtil.roundUp(spillSize + size, size);
        return allocateNewSpillSlot(kind, 0);
    }

    private Set<StackSlot> freedSlots;

    /**
     * Frees a spill slot that was obtained via {@link #allocateSpillSlot(LIRKind)} such that it can
     * be reused for the next allocation request for the same kind of slot.
     */
    public void freeSpillSlot(StackSlot slot) {
        if (freedSlots == null) {
            freedSlots = new HashSet<>();
        }
        freedSlots.add(slot);
    }

    /**
     * Reserves a number of contiguous slots in the frame of the method being compiled. If the
     * requested number of slots is 0, this method returns {@code null}.
     *
     * @param slots the number of slots to reserve
     * @param objects specifies the indexes of the object pointer slots. The caller is responsible
     *            for guaranteeing that each such object pointer slot is initialized before any
     *            instruction that uses a reference map. Without this guarantee, the garbage
     *            collector could see garbage object values.
     * @param outObjectStackSlots if non-null, the object pointer slots allocated are added to this
     *            list
     * @return the first reserved stack slot (i.e., at the lowest address)
     */
    public StackSlot allocateStackSlots(int slots, BitSet objects, List<StackSlot> outObjectStackSlots) {
        assert frameSize == -1 : "frame size must not yet be fixed";
        if (slots == 0) {
            return null;
        }
        spillSize += (slots * stackSlotSize());

        if (!objects.isEmpty()) {
            assert objects.length() <= slots;
            StackSlot result = null;
            for (int slotIndex = 0; slotIndex < slots; slotIndex++) {
                StackSlot objectSlot = null;
                if (objects.get(slotIndex)) {
                    objectSlot = allocateNewSpillSlot(LIRKind.reference(Kind.Object), slotIndex * stackSlotSize());
                    objectStackSlots.add(objectSlot);
                    if (outObjectStackSlots != null) {
                        outObjectStackSlots.add(objectSlot);
                    }
                }
                if (slotIndex == 0) {
                    if (objectSlot != null) {
                        result = objectSlot;
                    } else {
                        result = allocateNewSpillSlot(LIRKind.value(target.wordKind), 0);
                    }
                }
            }
            assert result != null;
            return result;

        } else {
            return allocateNewSpillSlot(LIRKind.value(target.wordKind), 0);
        }
    }

    public ReferenceMap initReferenceMap(boolean hasRegisters) {
        ReferenceMap refMap = target.createReferenceMap(hasRegisters, frameSize() / stackSlotSize());
        for (StackSlot slot : objectStackSlots) {
            setReference(slot, refMap);
        }
        return refMap;
    }

    /**
     * Marks the specified location as a reference in the reference map of the debug information.
     * The tracked location can be a {@link RegisterValue} or a {@link StackSlot}. Note that a
     * {@link Constant} is automatically tracked.
     *
     * @param location The location to be added to the reference map.
     * @param refMap A reference map, as created by {@link #initReferenceMap(boolean)}.
     */
    public void setReference(Value location, ReferenceMap refMap) {
        LIRKind kind = location.getLIRKind();
        if (isRegister(location)) {
            refMap.setRegister(asRegister(location).getReferenceMapIndex(), kind);
        } else if (isStackSlot(location)) {
            int offset = offsetForStackSlot(asStackSlot(location));
            refMap.setStackSlot(offset, kind);
        } else {
            assert isConstant(location);
        }
    }
}
