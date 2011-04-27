/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.lir;

import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.cri.ci.CiKind.*;
import static java.lang.reflect.Modifier.*;

import com.sun.c1x.*;
import com.sun.c1x.globalstub.*;
import com.sun.c1x.util.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiCallingConvention.Type;
import com.sun.cri.ri.*;

/**
 * This class is used to build the stack frame layout for a compiled method.
 *
 * This is the format of a stack frame on an x86 (i.e. IA32 or X64) platform:
 * <pre>
 *   Base       Contents
 *
 *          :                                :
 *          | incoming overflow argument n   |
 *          |     ...                        |
 *          | incoming overflow argument 0   |
 *          | return address                 | Caller frame
 *   -------+--------------------------------+----------------  ---
 *          |                                |                   ^
 *          : callee save area               :                   |
 *          |                                |                   |
 *          +--------------------------------+                   |
 *          | alignment padding              |                   |
 *          +--------------------------------+                   |
 *          | ALLOCA block n                 |                   |
 *          :     ...                        :                   |
 *          | ALLOCA block 0                 | Current frame     |
 *          +--------------------------------+                   |
 *          | monitor n                      |                   |
 *          :     ...                        :                   |
 *          | monitor 0                      |                   |
 *          +--------------------------------+    ---            |
 *          | spill slot n                   |     ^           frame
 *          :     ...                        :     |           size
 *          | spill slot 0                   |  shared           |
 *          +- - - - - - - - - - - - - - - - +   slot            |
 *          | outgoing overflow argument n   |  indexes          |
 *          |     ...                        |     |             |
 *    %sp   | outgoing overflow argument 0   |     v             v
 *   -------+--------------------------------+----------------  ---
 *
 * </pre>
 * Note that the size {@link Bytecodes#ALLOCA ALLOCA} blocks and {@code monitor}s in the frame may be greater
 * than the size of a {@linkplain CiTarget#spillSlotSize spill slot}.
 *
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public final class FrameMap {

    private final C1XCompilation compilation;
    private final CiCallingConvention incomingArguments;

    /**
     * Number of monitors used in this frame.
     */
    private final int monitorCount;

    /**
     * The final frame size.
     * Value is only set after register allocation is complete.
     */
    private int frameSize;

    /**
     * The number of spill slots allocated by the register allocator.
     * The value {@code -2} means that the size of outgoing argument stack slots
     * is not yet fixed. The value {@code -1} means that the register
     * allocator has started allocating spill slots and so the size of
     * outgoing stack slots cannot change as outgoing stack slots and
     * spill slots share the same slot index address space.
     */
    private int spillSlotCount;

    /**
     * The amount of memory allocated within the frame for uses of {@link Bytecodes#ALLOCA}.
     */
    private int stackBlocksSize;

    /**
     * Area occupied by outgoing overflow arguments.
     * This value is adjusted as calling conventions for outgoing calls are retrieved.
     */
    private int outgoingSize;

    /**
     * Creates a new frame map for the specified method.
     *
     * @param compilation the compilation context
     * @param method the outermost method being compiled
     * @param monitors the number of monitors allocated on the stack for this method
     */
    public FrameMap(C1XCompilation compilation, RiMethod method, int monitors) {
        this.compilation = compilation;
        this.frameSize = -1;
        this.spillSlotCount = -2;

        assert monitors >= 0 : "not set";
        monitorCount = monitors;
        if (method == null) {
            incomingArguments = new CiCallingConvention(new CiValue[0], 0);
        } else {
            CiKind receiver = !isStatic(method.accessFlags()) ? method.holder().kind() : null;
            incomingArguments = getCallingConvention(Util.signatureToKinds(method.signature(), receiver), JavaCallee);
        }
    }

    /**
     * Gets the calling convention for a call with the specified signature.
     *
     * @param type the type of calling convention being requested
     * @param signature the signature of the arguments
     * @return a {@link CiCallingConvention} instance describing the location of parameters and the return value
     */
    public CiCallingConvention getCallingConvention(CiKind[] signature, Type type) {
        CiCallingConvention cc = compilation.registerConfig.getCallingConvention(type, signature, compilation.target);
        if (type == RuntimeCall) {
            assert cc.stackSize == 0 : "runtime call should not have stack arguments";
        } else if (type.out) {
            assert frameSize == -1 : "frame size must not yet be fixed!";
            reserveOutgoing(cc.stackSize);
        }
        return cc;
    }

    /**
     * Gets the calling convention for the incoming arguments to the compiled method.
     * @return the calling convention for incoming arguments
     */
    public CiCallingConvention incomingArguments() {
        return incomingArguments;
    }

    /**
     * Gets the frame size of the compiled frame.
     * @return the size in bytes of the frame
     */
    public int frameSize() {
        assert this.frameSize != -1 : "frame size not computed yet";
        return frameSize;
    }

    /**
     * Sets the frame size for this frame.
     * @param frameSize the frame size in bytes
     */
    public void setFrameSize(int frameSize) {
        assert this.frameSize == -1 : "should only be calculated once";
        this.frameSize = frameSize;
    }

    /**
     * Computes the frame size for this frame, given the number of spill slots.
     * @param spillSlotCount the number of spill slots
     */
    public void finalizeFrame(int spillSlotCount) {
        assert this.spillSlotCount == -1 : "can only be set once";
        assert this.frameSize == -1 : "should only be calculated once";
        assert spillSlotCount >= 0 : "must be positive";

        this.spillSlotCount = spillSlotCount;
        int frameSize = offsetToStackBlocksEnd();
        frameSize += compilation.registerConfig.getCalleeSaveArea().size;
        this.frameSize = compilation.target.alignFrameSize(frameSize);
    }

    /**
     * Informs the frame map that the compiled code uses a particular global stub, which
     * may need stack space for outgoing arguments.
     *
     * @param stub the global stub
     */
    public void usesGlobalStub(GlobalStub stub) {
        reserveOutgoing(stub.argsSize);
    }

    /**
     * Converts a stack slot into a stack address.
     *
     * @param slot a stack slot
     * @return a stack address
     */
    public CiAddress toStackAddress(CiStackSlot slot) {
        int size = compilation.target.sizeInBytes(slot.kind);
        if (slot.inCallerFrame()) {
            int offset = slot.index() * compilation.target.spillSlotSize;
            return new CiAddress(slot.kind, CiRegister.CallerFrame.asValue(), offset);
        } else {
            int offset = offsetForOutgoingOrSpillSlot(slot.index(), size);
            return new CiAddress(slot.kind, CiRegister.Frame.asValue(), offset);
        }
    }

    /**
     * Gets the stack address within this frame for a given reserved stack block.
     *
     * @param stackBlock the value returned from {@link #reserveStackBlock(int)} identifying the stack block
     * @return a representation of the stack location
     */
    public CiAddress toStackAddress(StackBlock stackBlock) {
        return new CiAddress(CiKind.Word, compilation.registerConfig.getFrameRegister().asValue(Word), offsetForStackBlock(stackBlock));
    }

    /**
     * Converts the monitor index into the stack address of the object reference in the on-stack monitor.
     *
     * @param monitorIndex the monitor index
     * @return a representation of the stack address
     */
    public CiStackSlot toMonitorObjectStackAddress(int monitorIndex) {
        int byteIndex = offsetForMonitorObject(monitorIndex);
        assert byteIndex % compilation.target.wordSize == 0;
        return CiStackSlot.get(CiKind.Object, byteIndex / compilation.target.wordSize);
    }

    /**
     * Converts the monitor index into the stack address of the on-stak monitor.
     *
     * @param monitorIndex the monitor index
     * @return a representation of the stack address
     */
    public CiStackSlot toMonitorBaseStackAddress(int monitorIndex) {
        int byteIndex = offsetForMonitorBase(monitorIndex);
        assert byteIndex % compilation.target.wordSize == 0;
        return CiStackSlot.get(CiKind.Object, byteIndex / compilation.target.wordSize);
    }

    /**
     * Reserves space for stack-based outgoing arguments.
     *
     * @param argsSize the amount of space to reserve for stack-based outgoing arguments
     */
    public void reserveOutgoing(int argsSize) {
        assert spillSlotCount == -2 : "cannot reserve outgoing stack slot space once register allocation has started";
        if (argsSize > outgoingSize) {
            outgoingSize = Util.roundUp(argsSize, compilation.target.spillSlotSize);
        }
    }

    /**
     * Encapsulates the details of a stack block reserved by a call to {@link FrameMap#reserveStackBlock(int)}.
     */
    public static final class StackBlock {
        /**
         * The size of this stack block.
         */
        public final int size;

        /**
         * The offset of this stack block within the frame space reserved for stack blocks.
         */
        public final int offset;

        public StackBlock(int size, int offset) {
            this.size = size;
            this.offset = offset;
        }
    }

    /**
     * Reserves a block of memory in the frame of the method being compiled.
     *
     * @param size the number of bytes to reserve
     * @return a descriptor of the reserved block that can be used with {@link #toStackAddress(StackBlock)} once register
     *         allocation is complete and the size of the frame has been {@linkplain #finalizeFrame(int) finalized}.
     */
    public StackBlock reserveStackBlock(int size) {
        int wordSize = compilation.target.sizeInBytes(CiKind.Word);
        assert (size % wordSize) == 0;
        StackBlock block = new StackBlock(size, stackBlocksSize);
        stackBlocksSize += size;
        return block;
    }

    private int offsetForStackBlock(StackBlock stackBlock) {
        assert stackBlock.offset >= 0 && stackBlock.offset + stackBlock.size <= stackBlocksSize : "invalid stack block";
        int offset = offsetToStackBlocks() + stackBlock.offset;
        assert offset <= (frameSize() - stackBlock.size) : "spill outside of frame";
        return offset;
    }

    /**
     * Gets the stack pointer offset for a outgoing stack argument or compiler spill slot.
     *
     * @param slotIndex the index of the stack slot within the slot index space reserved for
     * @param size
     * @return
     */
    private int offsetForOutgoingOrSpillSlot(int slotIndex, int size) {
        assert slotIndex >= 0 && slotIndex < (initialSpillSlot() + spillSlotCount) : "invalid spill slot";
        int offset = slotIndex * compilation.target.spillSlotSize;
        assert offset <= (frameSize() - size) : "slot outside of frame";
        return offset;
    }

    private int offsetForMonitorBase(int index) {
        assert index >= 0 && index < monitorCount : "invalid monitor index";
        int size = compilation.runtime.sizeOfBasicObjectLock();
        assert size != 0 : "monitors are not on the stack in this VM";
        int offset = offsetToMonitors() + index * size;
        assert offset <= (frameSize() - size) : "monitor outside of frame";
        return offset;
    }

    private int offsetToSpillArea() {
        return outgoingSize;
    }

    private int offsetToSpillEnd() {
        return offsetToSpillArea() + spillSlotCount * compilation.target.spillSlotSize;
    }

    private int offsetToMonitors() {
        return offsetToCustomArea() + customAreaSize();
    }

    public int customAreaSize() {
        return compilation.runtime.getCustomStackAreaSize();
    }

    public int offsetToCustomArea() {
        return offsetToSpillEnd();
    }

    private int offsetToMonitorsEnd() {
        return offsetToMonitors() + (monitorCount * compilation.runtime.sizeOfBasicObjectLock());
    }

    private int offsetToStackBlocks() {
        return offsetToMonitorsEnd();
    }

    private int offsetToStackBlocksEnd() {
        return offsetToStackBlocks() + stackBlocksSize;
    }

    public int offsetToCalleeSaveAreaStart() {
        return offsetToCalleeSaveAreaEnd() - compilation.registerConfig.getCalleeSaveArea().size;
    }

    public int offsetToCalleeSaveAreaEnd() {
        return frameSize;
    }

    private int offsetForMonitorObject(int index)  {
        return offsetForMonitorBase(index) + compilation.runtime.basicObjectLockOffsetInBytes();
    }

    /**
     * Gets the index of the first available spill slot relative to the base of the frame.
     * After this call, no further outgoing stack slots can be {@linkplain #reserveOutgoing(int) reserved}.
     *
     * @return the index of the first available spill slot
     */
    public int initialSpillSlot() {
        if (spillSlotCount == -2) {
            spillSlotCount = -1;
        }
        return outgoingSize / compilation.target.spillSlotSize;
    }

}
