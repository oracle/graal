/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.TVMCI;
import org.graalvm.collections.EconomicMap;

/**
 * Descriptor of the slots of frame objects. Multiple frame instances are associated with one such
 * descriptor. The FrameDescriptor is not thread-safe until it's given to a first RootNode's
 * constructor. After that it has thread-safe properties.
 *
 * @since 0.8 or earlier
 */
public final class FrameDescriptor implements Cloneable {

    private final Object defaultValue;
    private final ArrayList<FrameSlot> slots;
    private final EconomicMap<Object, FrameSlot> identifierToSlotMap;
    @CompilationFinal private volatile Assumption version;
    private EconomicMap<Object, Assumption> identifierToNotInFrameAssumptionMap;
    @CompilationFinal private volatile int size;
    private final Object lock;

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
        this(defaultValue, null);
    }

    private FrameDescriptor(Object defaultValue, Object lock) {
        CompilerAsserts.neverPartOfCompilation("do not create a FrameDescriptor from compiled code");
        this.defaultValue = defaultValue;
        this.slots = new ArrayList<>();
        this.identifierToSlotMap = EconomicMap.create();
        this.version = createVersion();
        this.lock = lock == null ? this : lock;
    }

    /**
     * Adds frame slot. Delegates to
     * {@link #addFrameSlot(java.lang.Object, java.lang.Object, FrameSlotKind) addFrameSlot}
     * (identifier, <code>null</code>, {@link FrameSlotKind#Illegal}). This is a slow operation that
     * switches to interpreter mode. Note that even if it is checked that the FrameDescriptor does
     * not have the slot for a given identifier before adding the slot for the given identifier it
     * can still fail with an {@link IllegalArgumentException} since the FrameDescriptor can be
     * modified concurrently. In such case consider using {@link #findOrAddFrameSlot(Object)}
     * instead.
     *
     * @param identifier key for the slot - must not be {@code null} and needs proper
     *            {@link #equals(java.lang.Object)} and {@link Object#hashCode()} implementations
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @throws NullPointerException if {@code identifier} is {@code null}
     * @since 0.8 or earlier
     */
    public FrameSlot addFrameSlot(Object identifier) {
        return addFrameSlot(identifier, null, FrameSlotKind.Illegal);
    }

    /**
     * Adds frame slot. Delegates to
     * {@link #addFrameSlot(java.lang.Object, java.lang.Object, FrameSlotKind) addFrameSlot}
     * (identifier, <code>null</code>, <code>kind</code>). This is a slow operation that switches to
     * interpreter mode. Note that even if it is checked that the FrameDescriptor does not have the
     * slot for a given identifier before adding the slot for the given identifier it can still fail
     * with an {@link IllegalArgumentException} since the FrameDescriptor can be modified
     * concurrently. In such case consider using {@link #findOrAddFrameSlot(Object, FrameSlotKind)}
     * instead.
     *
     * @param identifier key for the slot - must not be {@code null} and needs proper
     *            {@link #equals(java.lang.Object)} and {@link Object#hashCode()} implementations
     * @param kind the kind of the new slot
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @throws NullPointerException if {@code identifier} or {@code kind} is {@code null}
     * @since 0.8 or earlier
     */
    public FrameSlot addFrameSlot(Object identifier, FrameSlotKind kind) {
        return addFrameSlot(identifier, null, kind);
    }

    /**
     * Adds new frame slot to {@link #getSlots()} list. This is a slow operation that switches to
     * interpreter mode. Note that even if it is checked that the FrameDescriptor does not have the
     * slot for a given identifier before adding the slot for the given identifier it can still fail
     * with an {@link IllegalArgumentException} since the FrameDescriptor can be modified
     * concurrently. In such case consider using
     * {@link #findOrAddFrameSlot(Object, Object, FrameSlotKind)} instead.
     *
     * @param identifier key for the slot - must not be {@code null} and needs proper
     *            {@link #equals(java.lang.Object)} and {@link Object#hashCode()} implementations
     * @param info additional {@linkplain FrameSlot#getInfo() information for the slot}, may be null
     * @param kind the kind of the new slot
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @throws NullPointerException if {@code identifier} or {@code kind} is {@code null}
     * @since 0.8 or earlier
     */
    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "All increments and decrements are synchronized.")
    public FrameSlot addFrameSlot(Object identifier, Object info, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(kind, "kind");
        synchronized (lock) {
            if (identifierToSlotMap.containsKey(identifier)) {
                throw new IllegalArgumentException("duplicate frame slot: " + identifier);
            }
            FrameSlot slot = new FrameSlot(this, identifier, info, kind, size);
            size++;
            slots.add(slot);
            identifierToSlotMap.put(identifier, slot);
            updateVersion();
            invalidateNotInFrameAssumption(identifier);
            return slot;
        }
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
        synchronized (lock) {
            return identifierToSlotMap.get(identifier);
        }
    }

    /**
     * Finds an existing slot or creates new one. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @return the found or newly created slot
     * @throws NullPointerException if {@code identifier} is {@code null}
     * @since 0.8 or earlier
     */
    public FrameSlot findOrAddFrameSlot(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            FrameSlot result = findFrameSlot(identifier);
            if (result != null) {
                return result;
            }
            return addFrameSlot(identifier);
        }
    }

    /**
     * Finds an existing slot or creates new one. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @param kind the kind for the newly created slot
     * @return the found or newly created slot
     * @throws NullPointerException if {@code identifier} or {@code kind} is {@code null}
     * @since 0.8 or earlier
     */
    public FrameSlot findOrAddFrameSlot(Object identifier, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            FrameSlot result = findFrameSlot(identifier);
            if (result != null) {
                return result;
            }
            return addFrameSlot(identifier, kind);
        }
    }

    /**
     * Finds an existing slot or creates new one. This is a slow operation.
     *
     * @param identifier the key of the slot to search for
     * @param info info for the newly created slot
     * @param kind the kind for the newly created slot
     * @return the found or newly created slot
     * @throws NullPointerException if {@code identifier} or {@code kind} is {@code null}
     * @since 0.8 or earlier
     */
    public FrameSlot findOrAddFrameSlot(Object identifier, Object info, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            FrameSlot result = findFrameSlot(identifier);
            if (result != null) {
                return result;
            }
            return addFrameSlot(identifier, info, kind);
        }
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
        synchronized (lock) {
            FrameSlot slot = identifierToSlotMap.get(identifier);
            if (slot == null) {
                throw new IllegalArgumentException("no such frame slot: " + identifier);
            }
            slots.remove(slot);
            identifierToSlotMap.removeKey(identifier);
            updateVersion();
            getNotInFrameAssumption(identifier);
        }
    }

    /**
     * Kind of the provided slot. Specified either at
     * {@link #addFrameSlot(java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind) creation
     * time} or updated via {@link #setFrameSlotKind(FrameSlot, FrameSlotKind)} method.
     *
     * @param frameSlot the slot
     * @return current kind of this slot
     * @since 1.0
     */
    public FrameSlotKind getFrameSlotKind(final FrameSlot frameSlot) {
        assert checkFrameSlotOwnership(frameSlot);
        /*
         * not checking that the frame slot is not removed from the FrameDescriptor kind is volatile
         * we can read it without locking the FrameDescriptor
         */
        return frameSlot.kind;
    }

    /**
     * Changes the kind of the provided slot. Change of the slot kind is done on <em>slow path</em>
     * and invalidates assumptions about version of {@link FrameDescriptor this descriptor}.
     *
     * @param frameSlot the slot
     * @param kind new kind of the slot
     * @since 1.0
     */
    public void setFrameSlotKind(final FrameSlot frameSlot, final FrameSlotKind kind) {
        if (frameSlot.kind != kind) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setFrameSlotKindSlow(frameSlot, kind);
        }
    }

    private void setFrameSlotKindSlow(FrameSlot frameSlot, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            assert checkFrameSlotOwnershipUnsafe(frameSlot);
            /*
             * Not checking that the frame slot is not removed from the FrameDescriptor letting it
             * continue will only result in extra version update.
             */
            if (frameSlot.kind != kind) { // recheck under lock
                /*
                 * First, only invalidate before updating kind so it's impossible to read a new kind
                 * and old still valid assumption.
                 */
                frameSlot.descriptor.version.invalidate();
                if (frameSlot.sharedWith != null) {
                    for (FrameDescriptor frameDescriptor : frameSlot.sharedWith.keySet()) {
                        assert frameDescriptor.lock == lock;
                        frameDescriptor.version.invalidate();
                    }
                }
                frameSlot.kind = kind;
                frameSlot.descriptor.version = createVersion();
                if (frameSlot.sharedWith != null) {
                    for (FrameDescriptor frameDescriptor : frameSlot.sharedWith.keySet()) {
                        assert frameDescriptor.lock == lock;
                        frameDescriptor.version = createVersion();
                    }
                }
            }
        }
    }

    private boolean checkFrameSlotOwnershipUnsafe(FrameSlot frameSlot) {
        return frameSlot.descriptor == this || (frameSlot.sharedWith != null && frameSlot.sharedWith.containsKey(this));
    }

    private boolean checkFrameSlotOwnership(FrameSlot frameSlot) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            return checkFrameSlotOwnershipUnsafe(frameSlot);
        }
    }

    /**
     * Returns the size of an array which is needed for storing all the slots in it using their
     * {@link FrameSlot#getIndex()} as a position in the array. (The number may be bigger than the
     * number of slots, if some slots are removed.)
     *
     * @return the size of the frame
     * @since 0.8 or earlier
     */
    public int getSize() {
        if (CompilerDirectives.inCompiledCode()) {
            if (!this.version.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
        }
        return this.size;
    }

    /**
     * Retrieve the current list of slots in the descriptor. Further changes are not reflected in
     * the returned collection.
     *
     * @return the unmodifiable snapshot list of {@link FrameSlot}
     * @since 0.8 or earlier
     */
    public List<? extends FrameSlot> getSlots() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }

    /**
     * Retrieve the current set of all the identifiers associated with this frame descriptor.
     * Further changes are not reflected in the returned collection.
     *
     * @return the unmodifiable snapshot set of all the identifiers in this frame descriptor
     * @since 0.8 or earlier
     */
    public Set<Object> getIdentifiers() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            return unmodifiableSetFromEconomicMap(EconomicMap.create(identifierToSlotMap));
        }
    }

    private static <K> Set<K> unmodifiableSetFromEconomicMap(EconomicMap<K, ?> map) {
        return new AbstractSet<K>() {
            @Override
            public Iterator<K> iterator() {
                return new Iterator<K>() {
                    private final Iterator<K> it = map.getKeys().iterator();

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public K next() {
                        return it.next();
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }

            @SuppressWarnings("unchecked")
            @Override
            public boolean contains(Object o) {
                return map.containsKey((K) o);
            }

            @Override
            public boolean add(K e) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean remove(Object o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean addAll(Collection<? extends K> coll) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean removeAll(Collection<?> coll) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean retainAll(Collection<?> coll) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean removeIf(Predicate<? super K> filter) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Deeper copy of the descriptor. Copies all slots in the descriptor, but only their
     * {@linkplain FrameSlot#getIdentifier() identifier} and {@linkplain FrameSlot#getInfo() info}
     * but not their {@linkplain FrameDescriptor#getFrameSlotKind(FrameSlot) kind}!
     *
     * @return new instance of a descriptor with copies of values from this one
     * @since 0.8 or earlier
     */
    public FrameDescriptor copy() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            FrameDescriptor clonedFrameDescriptor = new FrameDescriptor(this.defaultValue);
            for (int i = 0; i < slots.size(); i++) {
                FrameSlot slot = slots.get(i);
                clonedFrameDescriptor.addFrameSlot(slot.getIdentifier(), slot.getInfo(), FrameSlotKind.Illegal);
            }
            return clonedFrameDescriptor;
        }
    }

    /**
     * Shallow copy of the descriptor. Re-uses the existing slots in new descriptor. As a result, if
     * you {@link FrameSlot#setKind(FrameSlotKind) change kind} of one of the slots it is changed in
     * the original as well as in the shallow copy.
     *
     * @return new instance of a descriptor with copies of values from this one
     * @since 0.8 or earlier
     * @deprecated in 1.0 without replacement. The implementation is broken and would require a fix
     *             that is too complex.
     */
    @Deprecated
    public FrameDescriptor shallowCopy() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            FrameDescriptor clonedFrameDescriptor = new FrameDescriptor(this.defaultValue, lock);
            clonedFrameDescriptor.slots.addAll(slots);
            clonedFrameDescriptor.identifierToSlotMap.putAll(identifierToSlotMap);
            for (FrameSlot slot : slots) {
                slot.shareWith(clonedFrameDescriptor);
            }
            clonedFrameDescriptor.size = size;
            return clonedFrameDescriptor;
        }
    }

    /**
     * Invalidates the current, and create a new version assumption.
     */
    private void updateVersion() {
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
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
            if (identifierToSlotMap.containsKey(identifier)) {
                throw new IllegalArgumentException("Cannot get not-in-frame assumption for existing frame slot!");
            }

            if (identifierToNotInFrameAssumptionMap == null) {
                identifierToNotInFrameAssumptionMap = EconomicMap.create();
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
    }

    private void invalidateNotInFrameAssumption(Object identifier) {
        if (identifierToNotInFrameAssumptionMap != null) {
            Assumption assumption = identifierToNotInFrameAssumptionMap.get(identifier);
            if (assumption != null) {
                assumption.invalidate();
                identifierToNotInFrameAssumptionMap.removeKey(identifier);
            }
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (lock) {
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
