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
package com.oracle.max.graal.compiler.lir;

import static com.sun.cri.ci.CiCallingConvention.Type.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.cri.*;
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
 *          +--------------------------------+ Caller frame
 *          |                                |
 *          : custom area*                   :
 *          |                                |
 *   -------+--------------------------------+---------------------
 *          | return address                 |
 *          +--------------------------------+                  ---
 *          |                                |                   ^
 *          : callee save area               :                   |
 *          |                                |                   |
 *          +--------------------------------+ Current frame     |
 *          | alignment padding              |                   |
 *          +--------------------------------+                   |
 *          | ALLOCA block n                 |                   |
 *          :     ...                        :                   |
 *          | ALLOCA block 0                 |                   |
 *          +--------------------------------+    ---            |
 *          | spill slot n                   |     ^           frame
 *          :     ...                        :     |           size
 *          | spill slot 0                   |  shared           |
 *          +- - - - - - - - - - - - - - - - +   slot            |
 *          | outgoing overflow argument n   |  indexes          |
 *          |     ...                        |     |             |
 *          | outgoing overflow argument 0   |     v             |
 *          +--------------------------------+    ---            |
 *          |                                |                   |
 *          : custom area                    :                   |
 *    %sp   |                                |                   v
 *   -------+--------------------------------+----------------  ---
 *
 * </pre>
 * Note that the size of stack allocated memory block (ALLOCA block) in
 * the frame may be greater than the size of a {@linkplain CiTarget#spillSlotSize spill slot}.
 * Note also that the layout of the caller frame shown only applies if the caller
 * was also compiled with Graal. In particular, native frames won't have
 * a custom area if the native ABI specifies that stack arguments are at
 * the bottom of the frame (e.g. System V ABI on AMD64).
 */
public final class FrameMap {

    public final GraalRuntime runtime;
    public final CiTarget target;
    private final RiRegisterConfig registerConfig;
    private final CiCallingConvention incomingArguments;

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
     * The amount of memory allocated within the frame for uses of stack allocated memory blocks.
     */
    private int stackBlocksSize;

    /**
     * The list of stack blocks allocated in this frame.
     */
    private StackBlock stackBlocks;

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
     */
    public FrameMap(GraalCompilation compilation, RiResolvedMethod method) {
        this.runtime = compilation.compiler.runtime;
        this.target = compilation.compiler.target;
        this.registerConfig = compilation.registerConfig;
        this.frameSize = -1;
        this.spillSlotCount = -2;

        if (method == null) {
            incomingArguments = new CiCallingConvention(new CiValue[0], 0);
        } else {
            incomingArguments = registerConfig.getCallingConvention(JavaCallee, CiUtil.signatureToKinds(method), target, false);
        }
    }

    /**
     * Adjusts the stack-size for stack-based outgoing arguments if required.
     *
     * @param cc the calling convention
     * @param type the type of calling convention
     */
    public void adjustOutgoingStackSize(CiCallingConvention cc, Type type) {
        assert type.out;
        if (frameSize != -1 && cc.stackSize != 0) {
            // TODO(tw): This is a special work around for Windows runtime calls that can happen and must be ignored.
            assert type == RuntimeCall;
        } else {
            if (type != RuntimeCall) {
                assert frameSize == -1 : "frame size must not yet be fixed!";
                reserveOutgoing(cc.stackSize);
            }
        }
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
        CiCalleeSaveLayout csl = registerConfig.getCalleeSaveLayout();
        if (csl != null) {
            frameSize += csl.size;
        }
        this.frameSize = target.alignFrameSize(frameSize);
    }

    /**
     * Informs the frame map that the compiled code uses a particular compiler stub, which
     * may need stack space for outgoing arguments.
     *
     * @param stub the compiler stub
     */
    public void usesStub(CompilerStub stub) {
        int argsSize = stub.inArgs.length * target.spillSlotSize;
        int resultSize = stub.resultKind.isVoid() ? 0 : target.spillSlotSize;
        reserveOutgoing(Math.max(argsSize, resultSize));
    }

    /**
     * Computes the offset of a stack slot relative to the frame register.
     * This is also the bit index of stack slots in the reference map.
     *
     * @param slot a stack slot
     * @return the offset of the stack slot
     */
    public int offsetForStackSlot(CiStackSlot slot) {
        assert frameSize >= 0 : "fame size not computed yet";
        if (slot.inCallerFrame()) {
            int callerFrame = frameSize() + target.arch.returnAddressSize;
            int callerFrameOffset = slot.index() * target.spillSlotSize;
            return callerFrame + callerFrameOffset;
        } else {
            int offset = slot.index() * target.spillSlotSize;
            assert offset <= frameSize() - target.spillSlotSize : "slot outside of frame";
            return offset;
        }
    }

    /**
     * Converts a stack slot into a stack address.
     *
     * @param slot a stack slot
     * @return a stack address
     */
    public CiAddress toStackAddress(CiStackSlot slot) {
        return new CiAddress(slot.kind, registerConfig.getFrameRegister().asValue(), offsetForStackSlot(slot));
    }

    /**
     * Gets the stack address within this frame for a given reserved stack block.
     *
     * @param stackBlock the value returned from {@link #reserveStackBlock(int, boolean)} identifying the stack block
     * @return a representation of the stack location
     */
    public CiAddress toStackAddress(StackBlock stackBlock) {
        return new CiAddress(target.wordKind, registerConfig.getFrameRegister().asValue(target.wordKind), offsetForStackBlock(stackBlock));
    }

    public CiStackSlot toStackSlot(StackBlock stackBlock) {
        return CiStackSlot.get(stackBlock.kind, offsetForStackBlock(stackBlock) / target.spillSlotSize);
    }

    /**
     * Reserves space for stack-based outgoing arguments.
     *
     * @param argsSize the amount of space to reserve for stack-based outgoing arguments
     */
    public void reserveOutgoing(int argsSize) {
        assert spillSlotCount == -2 : "cannot reserve outgoing stack slot space once register allocation has started";
        if (argsSize > outgoingSize) {
            outgoingSize = Util.roundUp(argsSize, target.spillSlotSize);
        }
    }

    /**
     * Encapsulates the details of a stack block reserved by a call to {@link FrameMap#reserveStackBlock(int, boolean)}.
     */
    public static final class StackBlock extends CiValue {
        /**
         * The size of this stack block.
         */
        private final int size;

        /**
         * The offset of this stack block within the frame space reserved for stack blocks.
         */
        private final int offset;

        private final StackBlock next;

        public StackBlock(StackBlock next, int size, int offset, CiKind kind) {
            super(kind);
            this.next = next;
            this.size = size;
            this.offset = offset;
        }

        @Override
        public String name() {
            return "StackBlock " + offset;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }
        @Override
        public boolean equalsIgnoringKind(CiValue other) {
            return this == other;
        }
        @Override
        public int hashCode() {
            return offset;
        }
    }

    /**
     * Reserves a block of memory in the frame of the method being compiled.
     *
     * @param size the number of bytes to reserve
     * @param refs specifies if the block is all references
     * @return a descriptor of the reserved block that can be used with {@link #toStackAddress(StackBlock)} once register
     *         allocation is complete and the size of the frame has been {@linkplain #finalizeFrame(int) finalized}.
     */
    public StackBlock reserveStackBlock(int size, boolean refs) {
        assert size % target.wordSize == 0;
        StackBlock block = new StackBlock(stackBlocks, size, stackBlocksSize, refs ? CiKind.Object : target.wordKind);
        stackBlocksSize += size;
        stackBlocks = block;
        return block;
    }

    private int offsetForStackBlock(StackBlock stackBlock) {
        assert stackBlock.offset >= 0 && stackBlock.offset + stackBlock.size <= stackBlocksSize : "invalid stack block";
        int offset = offsetToStackBlocks() + stackBlock.offset;
        assert offset <= (frameSize() - stackBlock.size) : "stack block outside of frame";
        return offset;
    }

    private int offsetToSpillArea() {
        return outgoingSize + customAreaSize();
    }

    private int offsetToSpillEnd() {
        return offsetToSpillArea() + spillSlotCount * target.spillSlotSize;
    }

    public int customAreaSize() {
        return runtime.getCustomStackAreaSize();
    }

    public int offsetToCustomArea() {
        return 0;
    }

    private int offsetToStackBlocks() {
        return offsetToSpillEnd();
    }

    private int offsetToStackBlocksEnd() {
        return offsetToStackBlocks() + stackBlocksSize;
    }

    public int offsetToCalleeSaveAreaStart() {
        CiCalleeSaveLayout csl = registerConfig.getCalleeSaveLayout();
        if (csl != null) {
            return offsetToCalleeSaveAreaEnd() - csl.size;
        } else {
            return offsetToCalleeSaveAreaEnd();
        }
    }

    public int offsetToCalleeSaveAreaEnd() {
        return frameSize;
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
        return (outgoingSize + customAreaSize()) / target.spillSlotSize;
    }

    /**
     * Initializes a ref map that covers all the slots in the frame.
     */
    public CiBitMap initFrameRefMap() {
        int frameSize = frameSize();
        int frameWords = frameSize / target.spillSlotSize;
        CiBitMap frameRefMap = new CiBitMap(frameWords);
        for (StackBlock sb = stackBlocks; sb != null; sb = sb.next) {
            if (sb.kind == CiKind.Object) {
                int firstSlot = offsetForStackBlock(sb) / target.wordSize;
                int words = sb.size / target.wordSize;
                for (int i = 0; i < words; i++) {
                    frameRefMap.set(firstSlot + i);
                }
            }
        }
        return frameRefMap;
    }
}
