/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.TVMCI;

/**
 * Descriptor of the slots of frame objects. Multiple frame instances are associated with one such
 * descriptor.
 *
 * @since 0.8 or earlier
 */
public final class FrameDescriptor implements Cloneable {

    private final Object defaultValue;
    private final ArrayList<FrameSlot> slots;
    private final HashMap<Object, FrameSlot> identifierToSlotMap;
    private Assumption version;
    private HashMap<Object, Assumption> identifierToNotInFrameAssumptionMap;

    /**
     * Flag that can be used by the runtime to track that {@link Frame#materialize()} was called on
     * a frame that has this descriptor. Since the flag is not public API, access is encapsulated
     * via {@link TVMCI}.
     *
     * @since 0.14
     */
    boolean materializeCalled;

    private static final String NEVER_PART_OF_COMPILATION_MESSAGE = "interpreter-only. includes hashmap operations.";

    /**
     * Constructs empty descriptor. The {@link #getDefaultValue()} is <code>null</code>.
     *
     * @since 0.8 or earlier
     */
    public FrameDescriptor() {
        this(null);
    }

    /**
     * Constructs new descriptor with specified {@link #getDefaultValue()}.
     *
     * @param defaultValue to be returned from {@link #getDefaultValue()}
     * @since 0.8 or earlier
     */
    public FrameDescriptor(Object defaultValue) {
        CompilerAsserts.neverPartOfCompilation("do not create a FrameDescriptor from compiled code");
        this.defaultValue = defaultValue;
        slots = new ArrayList<>();
        identifierToSlotMap = new HashMap<>();
        version = createVersion();
    }

    /**
     * Use {@link #FrameDescriptor()}.
     *
     * @return new instance of the descriptor
     * @deprecated
     * @since 0.8 or earlier
     */
    @Deprecated
    public static FrameDescriptor create() {
        return new FrameDescriptor();
    }

    /**
     * Use {@link #FrameDescriptor(java.lang.Object) }.
     *
     * @return new instance of the descriptor
     * @deprecated
     * @since 0.8 or earlier
     */
    @Deprecated
    public static FrameDescriptor create(Object defaultValue) {
        return new FrameDescriptor(defaultValue);
    }

    /**
     * Adds frame slot. Delegates to
     * {@link #addFrameSlot(java.lang.Object, java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)
     * addFrameSlot}(identifier, <code>null</code>, {@link FrameSlotKind#Illegal}). This is a slow
     * operation that switches to interpreter mode.
     *
     * @param identifier key for the slot
     * @return the newly created slot
     * @since 0.8 or earlier
     */
    public FrameSlot addFrameSlot(Object identifier) {
        return addFrameSlot(identifier, null, FrameSlotKind.Illegal);
    }

    /**
     * Adds frame slot. Delegates to
     * {@link #addFrameSlot(java.lang.Object, java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)
     * addFrameSlot}(identifier, <code>null</code>, <code>kind</code>). This is a slow operation
     * that switches to interpreter mode.
     *
     * @param identifier key for the slot
     * @param kind the kind of the new slot
     * @return the newly created slot
     * @since 0.8 or earlier
     */
    public FrameSlot addFrameSlot(Object identifier, FrameSlotKind kind) {
        return addFrameSlot(identifier, null, kind);
    }

    /**
     * Adds new frame slot to {@link #getSlots()} list. This is a slow operation that switches to
     * interpreter mode.
     *
     * @param identifier key for the slot - it needs proper {@link #equals(java.lang.Object)} and
     *            {@link Object#hashCode()} implementations
     * @param info additional {@link FrameSlot#getInfo() information for the slot}
     * @param kind the kind of the new slot
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @since 0.8 or earlier
     */
    public FrameSlot addFrameSlot(Object identifier, Object info, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        if (identifierToSlotMap.containsKey(identifier)) {
            throw new IllegalArgumentException("duplicate frame slot: " + identifier);
        }
        FrameSlot slot = new FrameSlot(this, identifier, info, kind, slots.size());
        slots.add(slot);
        identifierToSlotMap.put(identifier, slot);
        updateVersion();
        invalidateNotInFrameAssumption(identifier);
        return slot;
    }

    /**
     * Finds an existing slot. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @return the slot or <code>null</code>
     * @since 0.8 or earlier
     */
    public FrameSlot findFrameSlot(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        return identifierToSlotMap.get(identifier);
    }

    /**
     * Finds an existing slot or creates new one. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @return the slot
     * @since 0.8 or earlier
     */
    public FrameSlot findOrAddFrameSlot(Object identifier) {
        FrameSlot result = findFrameSlot(identifier);
        if (result != null) {
            return result;
        }
        return addFrameSlot(identifier);
    }

    /**
     * Finds an existing slot or creates new one. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @param kind the kind for the newly created slot
     * @return the found or newly created slot
     * @since 0.8 or earlier
     */
    public FrameSlot findOrAddFrameSlot(Object identifier, FrameSlotKind kind) {
        FrameSlot result = findFrameSlot(identifier);
        if (result != null) {
            return result;
        }
        return addFrameSlot(identifier, kind);
    }

    /**
     * Finds an existing slot or creates new one. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @param info info for the newly created slot
     * @param kind the kind for the newly created slot
     * @return the found or newly created slot
     * @since 0.8 or earlier
     */
    public FrameSlot findOrAddFrameSlot(Object identifier, Object info, FrameSlotKind kind) {
        FrameSlot result = findFrameSlot(identifier);
        if (result != null) {
            return result;
        }
        return addFrameSlot(identifier, info, kind);
    }

    /**
     * Removes a slot. If the identifier is found, its slot is removed from this descriptor. This is
     * a slow operation.
     *
     * @param identifier identifies the slot to remove
     * @throws IllegalArgumentException if no such frame slot exists
     * @since 0.8 or earlier
     */
    public void removeFrameSlot(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        if (!identifierToSlotMap.containsKey(identifier)) {
            throw new IllegalArgumentException("no such frame slot: " + identifier);
        }
        slots.remove(identifierToSlotMap.get(identifier));
        identifierToSlotMap.remove(identifier);
        updateVersion();
        getNotInFrameAssumption(identifier);
    }

    /**
     * Returns number of slots in the descriptor.
     *
     * @return the same value as {@link #getSlots()}.{@link List#size()} would return
     * @since 0.8 or earlier
     */
    public int getSize() {
        return slots.size();
    }

    /**
     * Current set of slots in the descriptor.
     *
     * @return unmodifiable list of {@link FrameSlot}
     * @since 0.8 or earlier
     */
    public List<? extends FrameSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * Retrieve the list of all the identifiers associated with this frame descriptor.
     *
     * @return the list of all the identifiers in this frame descriptor
     * @since 0.8 or earlier
     */
    public Set<Object> getIdentifiers() {
        return Collections.unmodifiableSet(identifierToSlotMap.keySet());
    }

    /**
     * Deeper copy of the descriptor. Copies all slots in the descriptor, but only their
     * {@linkplain FrameSlot#getIdentifier() identifier} and {@linkplain FrameSlot#getInfo() info}
     * but not their {@linkplain FrameSlot#getKind() kind}!
     *
     * @return new instance of a descriptor with copies of values from this one
     * @since 0.8 or earlier
     */
    public FrameDescriptor copy() {
        FrameDescriptor clonedFrameDescriptor = new FrameDescriptor(this.defaultValue);
        for (int i = 0; i < slots.size(); i++) {
            FrameSlot slot = slots.get(i);
            clonedFrameDescriptor.addFrameSlot(slot.getIdentifier(), slot.getInfo(), FrameSlotKind.Illegal);
        }
        return clonedFrameDescriptor;
    }

    /**
     * Shallow copy of the descriptor. Re-uses the existing slots in new descriptor. As a result, if
     * you {@link FrameSlot#setKind(com.oracle.truffle.api.frame.FrameSlotKind) change kind} of one
     * of the slots it is changed in the original as well as in the shallow copy.
     *
     * @return new instance of a descriptor with copies of values from this one
     * @since 0.8 or earlier
     */
    public FrameDescriptor shallowCopy() {
        FrameDescriptor clonedFrameDescriptor = new FrameDescriptor(this.defaultValue);
        clonedFrameDescriptor.slots.addAll(slots);
        clonedFrameDescriptor.identifierToSlotMap.putAll(identifierToSlotMap);
        return clonedFrameDescriptor;
    }

    /**
     * Invalidates the current, and create a new version assumption.
     */
    void updateVersion() {
        version.invalidate();
        version = createVersion();
    }

    /**
     * Returns an assumption reflecting the frame's current version, which is updated every time a
     * slot is added or removed, or an existing slot's kind is changed. This assumption is
     * associated with compiled code that depends on the internal frame layout.
     *
     * @return an assumption invalidated when a slot is added or removed, or a slot kind changed.
     * @since 0.8 or earlier
     */
    public Assumption getVersion() {
        return version;
    }

    private static Assumption createVersion() {
        return Truffle.getRuntime().createAssumption("frame version");
    }

    /**
     * Default value for the created slots.
     *
     * @return value provided to {@link #FrameDescriptor(java.lang.Object)}
     * @since 0.8 or earlier
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Make an assumption that no slot with the specified identifier is present in this frame
     * descriptor. Invalidated when a frame slot with the identifier is added.
     *
     * @param identifier frame slot identifier
     * @return an assumption that this frame descriptor does not contain a slot with the identifier
     * @throws IllegalArgumentException if the frame descriptor contains a slot with the identifier
     * @since 0.8 or earlier
     */
    public Assumption getNotInFrameAssumption(Object identifier) {
        if (identifierToSlotMap.containsKey(identifier)) {
            throw new IllegalArgumentException("Cannot get not-in-frame assumption for existing frame slot!");
        }

        if (identifierToNotInFrameAssumptionMap == null) {
            identifierToNotInFrameAssumptionMap = new HashMap<>();
        } else {
            Assumption assumption = identifierToNotInFrameAssumptionMap.get(identifier);
            if (assumption != null) {
                return assumption;
            }
        }
        Assumption assumption = Truffle.getRuntime().createAssumption("identifier not in frame");
        identifierToNotInFrameAssumptionMap.put(identifier, assumption);
        return assumption;
    }

    private void invalidateNotInFrameAssumption(Object identifier) {
        if (identifierToNotInFrameAssumptionMap != null) {
            Assumption assumption = identifierToNotInFrameAssumptionMap.get(identifier);
            if (assumption != null) {
                assumption.invalidate();
                identifierToNotInFrameAssumptionMap.remove(identifier);
            }
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FrameDescriptor@").append(Integer.toHexString(hashCode()));
        sb.append("{");
        boolean comma = false;
        for (FrameSlot slot : slots) {
            if (comma) {
                sb.append(", ");
            } else {
                comma = true;
            }
            sb.append(slot.getIndex()).append(":").append(slot.getIdentifier());
        }
        sb.append("}");
        return sb.toString();
    }

    /** @since 0.14 */
    static final class AccessorFrames extends Accessor {
        @Override
        protected Frames framesSupport() {
            return new FramesImpl();
        }

        static final class FramesImpl extends Frames {
            @Override
            protected void markMaterializeCalled(FrameDescriptor descriptor) {
                descriptor.materializeCalled = true;
            }

            @Override
            protected boolean getMaterializeCalled(FrameDescriptor descriptor) {
                return descriptor.materializeCalled;
            }
        }
    }

    static {
        // registers into Accessor.FRAMES
        @SuppressWarnings("unused")
        AccessorFrames unused = new AccessorFrames();
    }
}
