/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.impl.TVMCI;

/**
 * Descriptor of the slots of frame objects. Multiple frame instances are associated with one such
 * descriptor. The FrameDescriptor is thread-safe.
 *
 * @since 0.8 or earlier
 */
public final class FrameDescriptor implements Cloneable {
    /**
     * @since 22.2
     * @deprecated since 24.1
     */
    @Deprecated static final int NO_STATIC_MODE = 1;
    /**
     * @since 22.2
     * @deprecated since 24.1
     */
    @Deprecated static final int ALL_STATIC_MODE = 2;
    /**
     * @since 22.2
     * @deprecated since 24.1
     */
    @Deprecated static final int MIXED_STATIC_MODE = NO_STATIC_MODE | ALL_STATIC_MODE;

    private static final boolean NULL_TAGS_SUPPORTED = Runtime.version().feature() >= 25;

    // Do not rename or remove. This field is read by the compiler.
    static final Object ILLEGAL_DEFAULT_VALUE = new Object();

    /**
     * Flag that defines the assignment strategy of initial {@link FrameSlotKind}s to slots in a
     * frame.
     *
     * @since 22.2
     * @deprecated since 24.1
     */
    @Deprecated final int staticMode = MIXED_STATIC_MODE;

    private final Object defaultValue;

    private final int indexedSlotCount;
    @CompilationFinal(dimensions = 1) private final byte[] indexedSlotTags;
    @CompilationFinal(dimensions = 1) private final Object[] indexedSlotNames;
    @CompilationFinal(dimensions = 1) private final Object[] indexedSlotInfos;

    private volatile EconomicMap<Object, Integer> auxiliarySlotMap;
    private volatile BitSet disabledAuxiliarySlots;

    private final Object descriptorInfo;

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
        this.indexedSlotCount = 0;
        this.indexedSlotNames = null;
        this.indexedSlotInfos = null;
        this.descriptorInfo = null;

        this.defaultValue = defaultValue;
    }

    private FrameDescriptor(Object defaultValue, int indexedSlotCount, byte[] indexedSlotTags, Object[] indexedSlotNames, Object[] indexedSlotInfos, Object info) {
        CompilerAsserts.neverPartOfCompilation("do not create a FrameDescriptor from compiled code");
        this.indexedSlotCount = indexedSlotCount;
        this.indexedSlotTags = createCompatibilitySlots(indexedSlotCount, indexedSlotTags);
        this.indexedSlotNames = indexedSlotNames;
        this.indexedSlotInfos = indexedSlotInfos;
        this.descriptorInfo = info;
        this.defaultValue = defaultValue;
    }

    private static byte[] createCompatibilitySlots(int indexedSlotCount, byte[] indexedSlotTags) {
        byte[] tags = indexedSlotTags;
        if (!NULL_TAGS_SUPPORTED) {
            if (tags == null) {
                /*
                 * Older JDK versions expect this byte[] to be available. To avoid crashes in older
                 * compiler versions we fill it if we are not in the newest version.
                 */
                tags = new byte[indexedSlotCount];
                Arrays.fill(tags, FrameSlotKind.Illegal.tag);
            }
        }
        return tags;
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
            FrameDescriptor clonedFrameDescriptor = new FrameDescriptor(this.defaultValue, this.indexedSlotCount,
                            indexedSlotTags == null ? null : indexedSlotTags.clone(),
                            indexedSlotNames == null ? null : indexedSlotNames.clone(),
                            indexedSlotInfos == null ? null : indexedSlotInfos.clone(), descriptorInfo);
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
     * Default value for the created slots.
     *
     * @return value provided to {@link #FrameDescriptor(java.lang.Object)}
     * @since 0.8 or earlier
     */
    public Object getDefaultValue() {
        return defaultValue;
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
            for (int slot = 0; slot < indexedSlotCount; slot++) {
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
        return indexedSlotCount;
    }

    /**
     * Returns the {@link FrameSlotKind} associated with the given indexed slot.
     *
     * @param slot index of the slot
     * @return a non-null {@link FrameSlotKind}
     * @since 22.0
     */
    public FrameSlotKind getSlotKind(int slot) {
        if (indexedSlotTags == null) {
            return FrameSlotKind.Illegal;
        } else {
            return FrameSlotKind.fromTag(indexedSlotTags[slot]);
        }
    }

    /**
     * Sets the {@link FrameSlotKind} of the given indexed slot.
     *
     * @param slot index of the slot
     * @param kind new non-null {@link FrameSlotKind}
     * @since 22.0
     */
    public void setSlotKind(int slot, FrameSlotKind kind) {
        if (indexedSlotTags == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException("Cannot set slot kind if frame slot tags are not initialized.");
        }
        assert (indexedSlotTags[slot] == FrameSlotKind.Static.tag && kind == FrameSlotKind.Static) ||
                        (indexedSlotTags[slot] != FrameSlotKind.Static.tag && kind != FrameSlotKind.Static) : "Cannot switch between static and non-static slot kind";
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
     * @return the user-defined info object associated with this frame descriptor
     *
     * @since 22.1
     */
    public Object getInfo() {
        return descriptorInfo;
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
        private Object descriptorInfo;
        private boolean useSlotKinds = true;

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
         * Sets the default value to illegal, this means that an {@link FrameSlotTypeException} is
         * thrown if a slot is read before it is written. A frame descriptor can either have a
         * concrete {@link #defaultValue(Object) default value} or an {@link #defaultValueIllegal()
         * illegal default value}, but not both.
         *
         * @since 24.2
         */
        public Builder defaultValueIllegal() {
            this.defaultValue = ILLEGAL_DEFAULT_VALUE;
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
         * Enables the use of slot kinds like {@link FrameDescriptor#getSlotKind(int)}. By default
         * slot kinds are enabled. If they are disabled then
         * {@link FrameDescriptor#getSlotKind(int)} always returns {@link FrameSlotKind#Illegal} and
         * no slot with a different slot kind than {@link FrameSlotKind#Illegal} must be added.
         *
         * @since 24.2
         */
        public Builder useSlotKinds(boolean b) {
            this.useSlotKinds = b;
            return this;
        }

        /**
         * Adds the given number of consecutive indexed slots to the {@link FrameDescriptor}, and
         * initializes them without a kind. If the kind is read anyway then Illegal will be returned
         * and setting the kind will throw an {@link UnsupportedOperationException}.
         *
         * @param count the number of slots to add
         * @see #useSlotKinds(boolean) to disable slot kinds all together.
         * @since 24.2
         */
        public int addSlots(int count) {
            return addSlots(count, FrameSlotKind.Illegal);
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
         * Adds a user-defined info object to the frame descriptor. The contents of this object are
         * strongly referenced from the frame descriptor and can be queried using
         * {@link FrameDescriptor#getInfo()}. They do not influence the semantics of the frame
         * descriptor in any other way.
         *
         * @param info the user-defined info object
         *
         * @since 22.1
         */
        public Builder info(Object info) {
            this.descriptorInfo = info;
            return this;
        }

        /**
         * Uses the data provided to this builder to create a new {@link FrameDescriptor}.
         *
         * @return the newly created {@link FrameDescriptor}
         * @since 22.0
         */
        public FrameDescriptor build() {
            byte[] useTags;
            if (useSlotKinds) {
                useTags = Arrays.copyOf(tags, size);
            } else {
                validateTags();
                useTags = null;
            }
            Object[] useNames = names != null ? Arrays.copyOf(names, size) : null;
            Object[] useInfos = infos != null ? Arrays.copyOf(infos, size) : null;
            return new FrameDescriptor(defaultValue, size, useTags, useNames, useInfos, descriptorInfo);
        }

        private void validateTags() {
            for (int i = 0; i < size; i++) {
                if (tags[i] != FrameSlotKind.Illegal.tag) {
                    throw new IllegalStateException(
                                    "If frame slot kinds are disabled with useSlotKinds(false) then only FrameSlotKind.Illegal is allowed to be used as frame slot kind. " +
                                                    "Change all added slots to FrameSlotKind.Illegal or enable slot kinds to resolve this.");
                }
            }
        }
    }
}
