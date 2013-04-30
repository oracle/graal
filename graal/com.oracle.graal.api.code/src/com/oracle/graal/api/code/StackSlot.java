/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Represents a compiler spill slot or an outgoing stack-based argument in a method's frame or an
 * incoming stack-based argument in a method's {@linkplain #isInCallerFrame() caller's frame}.
 */
public final class StackSlot extends AllocatableValue {

    private static final long serialVersionUID = -7725071921307318433L;

    private final int offset;
    private final boolean addFrameSize;

    /**
     * Gets a {@link StackSlot} instance representing a stack slot at a given index holding a value
     * of a given kind.
     * 
     * @param kind The kind of the value stored in the stack slot.
     * @param offset The offset of the stack slot (in bytes)
     * @param addFrameSize Specifies if the offset is relative to the stack pointer, or the
     *            beginning of the frame (stack pointer + total frame size).
     */
    public static StackSlot get(PlatformKind kind, int offset, boolean addFrameSize) {
        assert addFrameSize || offset >= 0;

        if (offset % CACHE_GRANULARITY == 0) {
            StackSlot slot;
            if (!addFrameSize) {
                slot = OUT_CACHE.lookup(kind, offset);
            } else if (offset >= 0) {
                slot = IN_CACHE.lookup(kind, offset);
            } else {
                slot = SPILL_CACHE.lookup(kind, offset);
            }
            if (slot != null) {
                assert slot.getPlatformKind().equals(kind) && slot.offset == offset && slot.addFrameSize == addFrameSize;
                return slot;
            }
        }
        return new StackSlot(kind, offset, addFrameSize);
    }

    /**
     * Private constructor to enforce use of {@link #get(PlatformKind, int, boolean)} so that a
     * cache can be used.
     */
    private StackSlot(PlatformKind kind, int offset, boolean addFrameSize) {
        super(kind);
        this.offset = offset;
        this.addFrameSize = addFrameSize;
    }

    /**
     * Gets the offset of this stack slot, relative to the stack pointer.
     * 
     * @return The offset of this slot (in bytes).
     */
    public int getOffset(int totalFrameSize) {
        assert totalFrameSize > 0 || !addFrameSize;
        int result = offset + (addFrameSize ? totalFrameSize : 0);
        assert result >= 0;
        return result;
    }

    public boolean isInCallerFrame() {
        return addFrameSize && offset >= 0;
    }

    public int getRawOffset() {
        return offset;
    }

    public boolean getRawAddFrameSize() {
        return addFrameSize;
    }

    @Override
    public int hashCode() {
        return getPlatformKind().hashCode() ^ (offset << 4) ^ (addFrameSize ? 15 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof StackSlot) {
            StackSlot l = (StackSlot) o;
            return l.getPlatformKind().equals(getPlatformKind()) && l.offset == offset && l.addFrameSize == addFrameSize;
        }
        return false;
    }

    @Override
    public String toString() {
        if (!addFrameSize) {
            return "out:" + offset + getKindSuffix();
        } else if (offset >= 0) {
            return "in:" + offset + getKindSuffix();
        } else {
            return "stack:" + (-offset) + getKindSuffix();
        }
    }

    /**
     * Gets this stack slot used to pass an argument from the perspective of a caller.
     */
    public StackSlot asOutArg() {
        assert offset >= 0;
        if (addFrameSize) {
            return get(getPlatformKind(), offset, false);
        }
        return this;
    }

    /**
     * Gets this stack slot used to pass an argument from the perspective of a callee.
     */
    public StackSlot asInArg() {
        assert offset >= 0;
        if (!addFrameSize) {
            return get(getPlatformKind(), offset, true);
        }
        return this;
    }

    private static final int SPILL_CACHE_PER_KIND_SIZE = 100;
    private static final int PARAM_CACHE_PER_KIND_SIZE = 10;
    private static final int CACHE_GRANULARITY = 8;

    private static class Cache extends HashMap<PlatformKind, StackSlot[]> {

        private static final long serialVersionUID = 4424132866289682843L;

        private final int cachePerKindSize;
        private final int sign;
        private final boolean addFrameSize;

        Cache(int cachePerKindSize, int sign, boolean addFrameSize) {
            this.cachePerKindSize = cachePerKindSize;
            this.sign = sign;
            this.addFrameSize = addFrameSize;
        }

        StackSlot lookup(PlatformKind kind, int offset) {
            int index = sign * offset / CACHE_GRANULARITY;
            StackSlot[] slots = this.get(kind);
            if (slots == null) {
                slots = new StackSlot[cachePerKindSize];
                for (int i = 0; i < cachePerKindSize; i++) {
                    slots[i] = new StackSlot(kind, sign * i * CACHE_GRANULARITY, addFrameSize);
                }
                this.put(kind, slots);
            }
            if (index < slots.length) {
                return slots[index];
            } else {
                return null;
            }
        }
    }

    private static final Cache SPILL_CACHE = new Cache(SPILL_CACHE_PER_KIND_SIZE, -1, true);
    private static final Cache IN_CACHE = new Cache(PARAM_CACHE_PER_KIND_SIZE, 1, true);
    private static final Cache OUT_CACHE = new Cache(PARAM_CACHE_PER_KIND_SIZE, 1, false);
}
