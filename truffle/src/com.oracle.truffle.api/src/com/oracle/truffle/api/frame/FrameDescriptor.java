/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.frame;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.TVMCI;

/**
 * Descriptor of the slots of frame objects. Multiple frame instances are associated with one such
 * descriptor. The FrameDescriptor is thread-safe.
 *
 * @since 0.8 or earlier
 */
@SuppressWarnings("deprecation")
public final class FrameDescriptor implements Cloneable {

    private final Object defaultValue;
    private final ArrayList<FrameSlot> slots = new ArrayList<>();
    private final EconomicMap<Object, FrameSlot> identifierToSlotMap = EconomicMap.create();
    @CompilationFinal private volatile Assumption version;
    private EconomicMap<Object, Assumption> identifierToNotInFrameAssumptionMap;
    @CompilationFinal private volatile int size;

    @CompilationFinal(dimensions = 1) private final byte[] indexedSlotTags;
    @CompilationFinal(dimensions = 1) private final Object[] indexedSlotNames;
    @CompilationFinal(dimensions = 1) private final Object[] indexedSlotInfos;

    private volatile EconomicMap<Object, Integer> auxiliarySlotMap;
    private volatile BitSet disabledAuxiliarySlots;

    /**
     * Number of entries (starting at index 0) that need to be allocated to encompass all active
     * auxiliary slots.
     */
    @CompilationFinal private volatile int activeAuxiliarySlotCount;

    /**
     * Index of the next allocated auxiliary slot.
     */
    private volatile int auxiliarySlotCount;

    /**
     * Flag that can be used by the runtime to track that {@link Frame#materialize()} was called on
     * a frame that has this descriptor. Since the flag is not public API, access is encapsulated
     * via {@link TVMCI}.
     *
     * @since 0.14
     */
    boolean materializeCalled;

    private static final String NEVER_PART_OF_COMPILATION_MESSAGE = "interpreter-only. includes hashmap operations.";

    private static final byte[] EMPTY_BYTE_ARRAY = {};

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
        this.indexedSlotTags = EMPTY_BYTE_ARRAY;
        this.indexedSlotNames = null;
        this.indexedSlotInfos = null;

        this.defaultValue = defaultValue;
        newVersion(this);
    }

    private FrameDescriptor(Object defaultValue, byte[] indexedSlotTags, Object[] indexedSlotNames, Object[] indexedSlotInfos) {
        CompilerAsserts.neverPartOfCompilation("do not create a FrameDescriptor from compiled code");
        this.indexedSlotTags = indexedSlotTags;
        this.indexedSlotNames = indexedSlotNames;
        this.indexedSlotInfos = indexedSlotInfos;

        this.defaultValue = defaultValue;
        newVersion(this);
    }

    /**
     * Adds frame slot. Delegates to addFrameSlot (identifier, <code>null</code>,
     * {@link FrameSlotKind#Illegal}). This is a slow operation that switches to interpreter mode.
     * Note that even if it is checked that the FrameDescriptor does not have the slot for a given
     * identifier before adding the slot for the given identifier it can still fail with an
     * {@link IllegalArgumentException} since the FrameDescriptor can be modified concurrently. In
     * such case consider using findOrAddFrameSlot(Object) instead.
     *
     * @param identifier key for the slot - must not be {@code null} and needs proper
     *            {@link #equals(java.lang.Object)} and {@link Object#hashCode()} implementations
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @throws NullPointerException if {@code identifier} is {@code null}
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public FrameSlot addFrameSlot(Object identifier) {
        return addFrameSlot(identifier, null, FrameSlotKind.Illegal);
    }

    /**
     * Adds frame slot. Delegates to addFrameSlot (identifier, <code>null</code>,
     * <code>kind</code>). This is a slow operation that switches to interpreter mode. Note that
     * even if it is checked that the FrameDescriptor does not have the slot for a given identifier
     * before adding the slot for the given identifier it can still fail with an
     * {@link IllegalArgumentException} since the FrameDescriptor can be modified concurrently. In
     * such case consider using findOrAddFrameSlot(Object, FrameSlotKind) instead.
     *
     * @param identifier key for the slot - must not be {@code null} and needs proper
     *            {@link #equals(java.lang.Object)} and {@link Object#hashCode()} implementations
     * @param kind the kind of the new slot
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @throws NullPointerException if {@code identifier} or {@code kind} is {@code null}
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public FrameSlot addFrameSlot(Object identifier, FrameSlotKind kind) {
        return addFrameSlot(identifier, null, kind);
    }

    /**
     * Adds new frame slot to getSlots() list. This is a slow operation that switches to interpreter
     * mode. Note that even if it is checked that the FrameDescriptor does not have the slot for a
     * given identifier before adding the slot for the given identifier it can still fail with an
     * {@link IllegalArgumentException} since the FrameDescriptor can be modified concurrently. In
     * such case consider using findOrAddFrameSlot(Object, Object, FrameSlotKind) instead.
     *
     * @param identifier key for the slot - must not be {@code null} and needs proper
     *            {@link #equals(java.lang.Object)} and {@link Object#hashCode()} implementations
     * @param info additional information for the slot, may be null
     * @param kind the kind of the new slot
     * @return the newly created slot
     * @throws IllegalArgumentException if a frame slot with the same identifier exists
     * @throws NullPointerException if {@code identifier} or {@code kind} is {@code null}
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "All increments and decrements are synchronized.")
    public FrameSlot addFrameSlot(Object identifier, Object info, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(kind, "kind");
        synchronized (this) {
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
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public FrameSlot findFrameSlot(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public FrameSlot findOrAddFrameSlot(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public FrameSlot findOrAddFrameSlot(Object identifier, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public FrameSlot findOrAddFrameSlot(Object identifier, Object info, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public void removeFrameSlot(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
     * Kind of the provided slot. Specified either at creation time or updated via
     * setFrameSlotKind(FrameSlot, FrameSlotKind).
     *
     * @param frameSlot the slot
     * @return current kind of this slot
     * @since 19.0
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
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
     * @since 19.0
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public void setFrameSlotKind(final FrameSlot frameSlot, final FrameSlotKind kind) {
        if (frameSlot.kind != kind) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setFrameSlotKindSlow(frameSlot, kind);
        }
    }

    private void setFrameSlotKindSlow(FrameSlot frameSlot, FrameSlotKind kind) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
                invalidateVersion(this);
                frameSlot.kind = kind;
                newVersion(this);
            }
        }
    }

    private boolean checkFrameSlotOwnershipUnsafe(FrameSlot frameSlot) {
        return frameSlot.descriptor == this;
    }

    @TruffleBoundary
    private boolean checkFrameSlotOwnership(FrameSlot frameSlot) {
        synchronized (this) {
            return checkFrameSlotOwnershipUnsafe(frameSlot);
        }
    }

    /**
     * Returns the size of an array which is needed for storing all the frame slots. (The number may
     * be bigger than the number of slots, if some slots are removed.)
     *
     * @return the size of the frame
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
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
     * @return the unmodifiable snapshot list of FrameSlot
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public List<? extends FrameSlot> getSlots() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }

    /**
     * Retrieve the current set of all the identifiers associated with this frame descriptor.
     * Further changes are not reflected in the returned collection.
     *
     * @return the unmodifiable snapshot set of all the identifiers in this frame descriptor
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public Set<Object> getIdentifiers() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
     * Deeper copy of the descriptor. Copies all slots in the descriptor, but only their identifier
     * and info but not their kind!
     *
     * @return new instance of a descriptor with copies of values from this one
     * @since 0.8 or earlier
     */
    public FrameDescriptor copy() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
            FrameDescriptor clonedFrameDescriptor = new FrameDescriptor(this.defaultValue, indexedSlotTags == null ? null : indexedSlotTags.clone(),
                            indexedSlotNames == null ? null : indexedSlotNames.clone(), indexedSlotInfos == null ? null : indexedSlotInfos.clone());
            for (int i = 0; i < slots.size(); i++) {
                FrameSlot slot = slots.get(i);
                clonedFrameDescriptor.addFrameSlot(slot.getIdentifier(), slot.getInfo(), FrameSlotKind.Illegal);
            }
            clonedFrameDescriptor.auxiliarySlotCount = auxiliarySlotCount;
            clonedFrameDescriptor.activeAuxiliarySlotCount = activeAuxiliarySlotCount;
            if (auxiliarySlotMap != null) {
                clonedFrameDescriptor.auxiliarySlotMap = EconomicMap.create(auxiliarySlotMap);
            }
            if (disabledAuxiliarySlots != null) {
                clonedFrameDescriptor.disabledAuxiliarySlots = new BitSet();
                clonedFrameDescriptor.disabledAuxiliarySlots.or(disabledAuxiliarySlots);
            }

            return clonedFrameDescriptor;
        }
    }

    /**
     * Invalidates the current, and create a new version assumption.
     */
    private void updateVersion() {
        invalidateVersion(this);
        newVersion(this);
    }

    private static void newVersion(FrameDescriptor descriptor) {
        descriptor.version = Truffle.getRuntime().createAssumption("frame version");
    }

    private static void invalidateVersion(FrameDescriptor descriptor) {
        descriptor.version.invalidate();
    }

    /**
     * Returns an assumption reflecting the frame's current version, which is updated every time a
     * slot is added or removed, or an existing slot's kind is changed. This assumption is
     * associated with compiled code that depends on the internal frame layout.
     *
     * @return an assumption invalidated when a slot is added or removed, or a slot kind changed.
     * @since 0.8 or earlier
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public Assumption getVersion() {
        return version;
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
     * @deprecated use index-based and auxiliary slots instead
     */
    @Deprecated
    public Assumption getNotInFrameAssumption(Object identifier) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
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
        synchronized (this) {
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
                sb.append(slot.index).append(":").append(slot.getIdentifier());
            }
            for (int slot = 0; slot < indexedSlotTags.length; slot++) {
                if (comma) {
                    sb.append(", ");
                } else {
                    comma = true;
                }
                sb.append('#').append(slot);
                if (getSlotName(slot) != null) {
                    sb.append(":").append(getSlotName(slot));
                }
            }
            EconomicMap<Object, Integer> map = auxiliarySlotMap;
            if (map != null) {
                MapCursor<Object, Integer> entries = map.getEntries();
                while (entries.advance()) {
                    if (comma) {
                        sb.append(", ");
                    } else {
                        comma = true;
                    }
                    sb.append('@').append(entries.getKey()).append(":").append(entries.getValue());
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * @return the number of indexed slots in this frame descriptor
     *
     * @since 22.0
     */
    public int getNumberOfSlots() {
        return indexedSlotTags.length;
    }

    /**
     * Returns the {@link FrameSlotKind} associated with the given indexed slot.
     *
     * @param slot index of the slot
     * @return a non-null {@link FrameSlotKind}
     * @since 22.0
     */
    public FrameSlotKind getSlotKind(int slot) {
        return FrameSlotKind.fromTag(indexedSlotTags[slot]);
    }

    /**
     * Sets the {@link FrameSlotKind} of the given indexed slot.
     *
     * @param slot index of the slot
     * @param kind new non-null {@link FrameSlotKind}
     * @since 22.0
     */
    public void setSlotKind(int slot, FrameSlotKind kind) {
        if (indexedSlotTags[slot] != kind.tag) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexedSlotTags[slot] = kind.tag;
        }
    }

    /**
     * Queries the name for a given indexed slot.
     *
     * @param slot index of the slot
     * @return the name of the slot (may be {@code null})
     * @since 22.0
     */
    public Object getSlotName(int slot) {
        return indexedSlotNames == null ? null : indexedSlotNames[slot];
    }

    /**
     * Queries the info object for a given indexed slot.
     *
     * @param slot index of the slot
     * @return the info object of the slot (may be {@code null})
     * @since 22.0
     */
    public Object getSlotInfo(int slot) {
        return indexedSlotInfos == null ? null : indexedSlotInfos[slot];
    }

    /**
     * Find or adds an auxiliary slot to this frame descriptor. Returns an integer index that can be
     * used to access the auxiliary slot in the frame.
     *
     * @param key used as a hash map entry
     * @return an integer that can be used to access the auxiliary slot in the frame
     * @since 22.0
     */
    public int findOrAddAuxiliarySlot(Object key) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
            EconomicMap<Object, Integer> map = auxiliarySlotMap;
            if (map == null) {
                auxiliarySlotMap = map = EconomicMap.create();
            }
            Integer index = map.get(key);
            if (index == null) {
                map.put(key, index = auxiliarySlotCount++);
                activeAuxiliarySlotCount = auxiliarySlotCount;
            } else {
                // re-adding an auxiliary slot - set to active
                if (disabledAuxiliarySlots != null) {
                    disabledAuxiliarySlots.clear(index);
                    recalculateAuxiliarySlotSize();
                }
            }
            return index;
        }
    }

    /**
     * Disables the auxiliary slot with the given key, so that subsequently created {@link Frame}
     * instances may avoid allocating storage for it. Depending on the internal storage layout, it
     * may not be possible to remove the storage for individual slots, so it is important to disable
     * _all_ unused slots.
     *
     * @param key the key, as passed to {@link #findOrAddAuxiliarySlot(Object)}
     * @since 22.0
     */
    public void disableAuxiliarySlot(Object key) {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
            EconomicMap<Object, Integer> map = auxiliarySlotMap;
            if (map != null) {
                Integer index = map.get(key);
                if (index != null) {
                    BitSet set = disabledAuxiliarySlots;
                    if (set == null) {
                        disabledAuxiliarySlots = set = new BitSet();
                    }
                    set.set(index);
                    recalculateAuxiliarySlotSize();
                }
            }
        }
    }

    /**
     * Returns all currently active auxiliary slots along with their indexes. This is a slow
     * operation that returns a data structure that is not backed by the frame descriptor, i.e.,
     * which does not reflect changes to the set of active auxiliary slots in either direction.
     *
     * @since 22.0
     */
    public Map<Object, Integer> getAuxiliarySlots() {
        CompilerAsserts.neverPartOfCompilation(NEVER_PART_OF_COMPILATION_MESSAGE);
        synchronized (this) {
            Map<Object, Integer> result = new HashMap<>();
            EconomicMap<Object, Integer> map = auxiliarySlotMap;
            BitSet disabled = disabledAuxiliarySlots;
            if (map != null) {
                MapCursor<Object, Integer> cursor = map.getEntries();
                while (cursor.advance()) {
                    Object identifier = cursor.getKey();
                    int index = cursor.getValue();
                    if (disabled == null || !disabled.get(index)) {
                        result.put(identifier, index);
                    }
                }
            }
            return result;
        }

    }

    private void recalculateAuxiliarySlotSize() {
        BitSet set = disabledAuxiliarySlots;
        int i;
        for (i = auxiliarySlotCount; i > 0; i--) {
            if (!set.get(i - 1)) {
                // stop at the first active aux slot
                break;
            }
        }
        activeAuxiliarySlotCount = i;
    }

    /**
     * @return the current number of auxiliary slots in this frame descriptor
     *
     * @since 22.0
     */
    public int getNumberOfAuxiliarySlots() {
        return activeAuxiliarySlotCount;
    }

    /**
     * Builds a new frame descriptor with index-based frame slots.
     *
     * @since 22.0
     */
    public static Builder newBuilder() {
        return new Builder(Builder.DEFAULT_CAPACITY);
    }

    /**
     * Builds a new frame descriptor with index-based frame slots.
     *
     * @param capacity the expected number of index-based frame slots (taken as a hint when
     *            allocating internal data structures)
     * @since 22.0
     */
    public static Builder newBuilder(int capacity) {
        return new Builder(capacity);
    }

    /**
     * Builder API for frame descriptors with indexed slots.
     *
     * @since 22.0
     */
    public static final class Builder {

        private static final int DEFAULT_CAPACITY = 8;

        private Object defaultValue;
        private byte[] tags;
        private Object[] names;
        private Object[] infos;
        private int size;

        private Builder(int capacity) {
            this.tags = new byte[capacity];
        }

        private void ensureCapacity(int count) {
            if (tags.length < size + count) {
                int newLength = Math.max(size + count, size * 2);
                tags = Arrays.copyOf(tags, newLength);
                if (names != null) {
                    names = Arrays.copyOf(names, newLength);
                }
                if (infos != null) {
                    infos = Arrays.copyOf(infos, newLength);
                }
            }
        }

        /**
         * Sets the default value for the frame slots in this frame descriptor.
         *
         * @param newDefaultValue the default value for the resulting frame descriptor
         * @since 22.0
         */
        public Builder defaultValue(Object newDefaultValue) {
            this.defaultValue = newDefaultValue;
            return this;
        }

        /**
         * Adds the given number of consecutive indexed slots to the {@link FrameDescriptor}, and
         * initializes them with the given kind.
         *
         * @param count number of slots to be added
         * @param kind default type of the newly added frame slots
         * @return index of the first slot
         * @since 22.0
         */
        public int addSlots(int count, FrameSlotKind kind) {
            if (count < 0 || size + count < 0) {
                throw new IllegalArgumentException("invalid slot count: " + count);
            }
            ensureCapacity(count);
            Arrays.fill(tags, size, size + count, kind.tag);
            int newIndex = size;
            size += count;
            return newIndex;
        }

        /**
         * Adds an indexed frame slot to the {@link FrameDescriptor}. The frame descriptor's
         * internal arrays for storing {@code name} and {@code info} are allocated only when needed,
         * so using only {@code null} reduces memory footprint.
         *
         * @param kind default type of the newly added frame slot
         * @param name Name of the newly added frame slot. Can (and should, if possible) be null.
         * @param info Info object for the newly added frame slot. Can (and should, if possible) be
         *            null.
         * @since 22.0
         */
        public int addSlot(FrameSlotKind kind, Object name, Object info) {
            ensureCapacity(1);
            tags[size] = kind.tag;
            if (name != null) {
                if (names == null) {
                    names = new Object[tags.length];
                }
                names[size] = name;
            }
            if (info != null) {
                if (infos == null) {
                    infos = new Object[tags.length];
                }
                infos[size] = info;
            }
            tags[size] = kind.tag;
            int newIndex = size;
            size++;
            return newIndex;
        }

        /**
         * Uses the data provided to this builder to create a new {@link FrameDescriptor}.
         *
         * @return the newly created {@link FrameDescriptor}
         * @since 22.0
         */
        public FrameDescriptor build() {
            return new FrameDescriptor(defaultValue, Arrays.copyOf(tags, size), names == null ? null : Arrays.copyOf(names, size), infos == null ? null : Arrays.copyOf(infos, size));
        }
    }
}
