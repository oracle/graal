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

import static com.oracle.graal.api.meta.Kind.*;

import com.oracle.graal.api.meta.*;

/**
 * Represents a compiler spill slot or an outgoing stack-based argument in a method's frame
 * or an incoming stack-based argument in a method's {@linkplain #inCallerFrame() caller's frame}.
 */
public final class CiStackSlot extends Value {
    private static final long serialVersionUID = -7725071921307318433L;

    private final int offset;
    private final boolean addFrameSize;

    /**
     * Gets a {@link CiStackSlot} instance representing a stack slot at a given index
     * holding a value of a given kind.
     *
     * @param kind The kind of the value stored in the stack slot.
     * @param offset The offset of the stack slot (in bytes)
     * @param inCallerFrame Specifies if the offset is relative to the stack pointer,
     *        or the beginning of the frame (stack pointer + total frame size).
     */
    public static CiStackSlot get(Kind kind, int offset, boolean addFrameSize) {
        assert kind.stackKind() == kind;
        assert addFrameSize || offset >= 0;

        if (offset % CACHE_GRANULARITY == 0) {
            CiStackSlot[][] cache;
            int index = offset / CACHE_GRANULARITY;
            if (!addFrameSize) {
                cache = OUT_CACHE;
            } else if (offset >= 0) {
                cache = IN_CACHE;
            } else {
                cache = SPILL_CACHE;
                index = -index;
            }
            CiStackSlot[] slots = cache[kind.ordinal()];
            if (index < slots.length) {
                CiStackSlot slot = slots[index];
                assert slot.kind == kind && slot.offset == offset && slot.addFrameSize == addFrameSize;
                return slot;
            }
        }
        return new CiStackSlot(kind, offset, addFrameSize);
    }

    /**
     * Private constructor to enforce use of {@link #get()} so that a cache can be used.
     */
    private CiStackSlot(Kind kind, int offset, boolean addFrameSize) {
        super(kind);
        this.offset = offset;
        this.addFrameSize = addFrameSize;
    }

    /**
     * Gets the offset of this stack slot, relative to the stack pointer.
     * @return The offset of this slot (in bytes).
     */
    public int offset(int totalFrameSize) {
        assert totalFrameSize > 0 || !addFrameSize;
        int result = offset + (addFrameSize ? totalFrameSize : 0);
        assert result >= 0;
        return result;
    }

    public boolean inCallerFrame() {
        return addFrameSize && offset >= 0;
    }

    public int rawOffset() {
        return offset;
    }

    public boolean rawAddFrameSize() {
        return addFrameSize;
    }

    @Override
    public int hashCode() {
        return kind.ordinal() ^ (offset << 4) ^ (addFrameSize ? 15 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof CiStackSlot) {
            CiStackSlot l = (CiStackSlot) o;
            return l.kind == kind && l.offset == offset && l.addFrameSize == addFrameSize;
        }
        return false;
    }

    @Override
    public String toString() {
        if (!addFrameSize) {
            return "out:" + offset + kindSuffix();
        } else if (offset >= 0) {
            return "in:" + offset + kindSuffix();
        } else {
            return "stack:" + (-offset) + kindSuffix();
        }
    }

    /**
     * Gets this stack slot used to pass an argument from the perspective of a caller.
     */
    public CiStackSlot asOutArg() {
        assert offset >= 0;
        if (addFrameSize) {
            return get(kind, offset, false);
        }
        return this;
    }

    /**
     * Gets this stack slot used to pass an argument from the perspective of a callee.
     */
    public CiStackSlot asInArg() {
        assert offset >= 0;
        if (!addFrameSize) {
            return get(kind, offset, true);
        }
        return this;
    }


    private static final int CACHE_GRANULARITY = 8;
    private static final int SPILL_CACHE_PER_KIND_SIZE = 100;
    private static final int PARAM_CACHE_PER_KIND_SIZE = 10;

    private static final CiStackSlot[][] SPILL_CACHE = makeCache(SPILL_CACHE_PER_KIND_SIZE, -1, true);
    private static final CiStackSlot[][] IN_CACHE = makeCache(PARAM_CACHE_PER_KIND_SIZE, 1, true);
    private static final CiStackSlot[][] OUT_CACHE = makeCache(PARAM_CACHE_PER_KIND_SIZE, 1, false);

    private static CiStackSlot[][] makeCache(int cachePerKindSize, int sign, boolean addFrameSize) {
        CiStackSlot[][] cache = new CiStackSlot[Kind.VALUES.length][];
        for (Kind kind : new Kind[] {Illegal, Int, Long, Float, Double, Object, Jsr}) {
            CiStackSlot[] slots = new CiStackSlot[cachePerKindSize];
            for (int i = 0; i < cachePerKindSize; i++) {
                slots[i] = new CiStackSlot(kind, sign * i * CACHE_GRANULARITY, addFrameSize);
            }
            cache[kind.ordinal()] = slots;
        }
        return cache;
    }
}
