/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.code;

import static jdk.vm.ci.meta.MetaUtil.identityHashCodeString;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.code.DataSection.Data;
import jdk.graal.compiler.code.DataSection.EmittedItems.ConstantPatch;
import jdk.graal.compiler.code.DataSection.EmittedItems.EmittedItem;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.SerializableConstant;
import jdk.vm.ci.meta.VMConstant;

public final class DataSection implements Iterable<Data> {

    static class Options {
        // @formatter:off
        @Option(help = "Place N-byte constants in the data section such that they are misaligned with respect to N*2. " +
                "For example, place 4 byte constants at offset 4, 12 or 20, etc. " +
                "This layout is used to detect instructions that load constants with alignment smaller " +
                "than the fetch size. For instance, an XORPS instruction that does a 16-byte fetch of a " +
                "4-byte float not aligned to 16 bytes will cause a segfault.",
                type = OptionType.Debug)
        static final OptionKey<Boolean> ForceAdversarialLayout = new OptionKey<>(false);
        // @formatter:on
    }

    public interface Patches {

        void registerPatch(int position, VMConstant c);
    }

    public abstract static class Data {

        private int alignment;
        private final int size;

        private DataSectionReference ref;

        protected Data(int alignment, int size) {
            this.alignment = alignment;
            this.size = size;

            // initialized in DataSection.insertData(Data)
            ref = null;
        }

        protected abstract void emit(ByteBuffer buffer, Patches patches);

        /**
         * Updates the alignment of the current data segment. This does not guarantee that the
         * underlying runtime actually supports this alignment.
         *
         * @param newAlignment The new alignment
         */
        public void updateAlignment(int newAlignment) {
            if (newAlignment == alignment) {
                return;
            }
            alignment = lcm(alignment, newAlignment);
        }

        public int getAlignment() {
            return alignment;
        }

        public int getSize() {
            return size;
        }

        @Override
        public int hashCode() {
            // Data instances should not be used as hash map keys
            throw new UnsupportedOperationException("hashCode"); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public String toString() {
            return identityHashCodeString(this);
        }

        public boolean isMutable() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            assert ref != null;
            if (obj == this) {
                return true;
            }
            if (obj instanceof Data) {
                Data that = (Data) obj;
                if (this.alignment == that.alignment && this.size == that.size && this.ref.equals(that.ref)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class RawData extends Data {

        private final byte[] data;

        public RawData(byte[] data, int alignment) {
            super(alignment, data.length);
            this.data = data;
        }

        @Override
        protected void emit(ByteBuffer buffer, Patches patches) {
            buffer.put(data);
        }
    }

    public static final class SerializableData extends Data {

        private final SerializableConstant constant;

        public SerializableData(SerializableConstant constant, int alignment) {
            super(alignment, constant.getSerializedSize());
            this.constant = constant;
        }

        @Override
        protected void emit(ByteBuffer buffer, Patches patches) {
            int position = buffer.position();
            constant.serialize(buffer);
            assert buffer.position() - position == constant.getSerializedSize() : "wrong number of bytes written";
        }

        @Override
        public String toString() {
            return "SerializableData{" +
                            "alignment=" + getAlignment() +
                            ", size=" + getSize() +
                            ", constant=" + constant +
                            '}';
        }
    }

    public static class ZeroData extends Data {

        public ZeroData(int alignment, int size) {
            super(alignment, size);
        }

        @Override
        protected void emit(ByteBuffer buffer, Patches patches) {
            int rest = getSize();
            while (rest > 8) {
                buffer.putLong(0L);
                rest -= 8;
            }
            while (rest > 0) {
                buffer.put((byte) 0);
                rest--;
            }
        }
    }

    public static final class PackedData extends Data {

        private final Data[] nested;

        public PackedData(int alignment, int size, Data[] nested) {
            super(alignment, size);
            this.nested = nested;
        }

        @Override
        protected void emit(ByteBuffer buffer, Patches patches) {
            for (Data data : nested) {
                data.emit(buffer, patches);
            }
        }

        @Override
        public String toString() {
            return "PackedData{" +
                            "alignment=" + getAlignment() +
                            ", size=" + getSize() +
                            ", nested=" + Arrays.toString(nested) +
                            '}';
        }
    }

    private final ArrayList<Data> dataItems = new ArrayList<>();

    private boolean closed;
    private int sectionAlignment;
    private int sectionSize;

    @Override
    public int hashCode() {
        // DataSection instances should not be used as hash map keys
        throw new UnsupportedOperationException("hashCode");
    }

    @Override
    public String toString() {
        return identityHashCodeString(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DataSection) {
            DataSection that = (DataSection) obj;
            if (this.closed == that.closed && this.sectionAlignment == that.sectionAlignment && this.sectionSize == that.sectionSize && Objects.equals(this.dataItems, that.dataItems)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inserts a {@link Data} item into the data section. If the item is already in the data
     * section, the same {@link DataSectionReference} is returned.
     *
     * @param data the {@link Data} item to be inserted
     * @return a unique {@link DataSectionReference} identifying the {@link Data} item
     */
    public DataSectionReference insertData(Data data) {
        checkOpen();
        synchronized (data) {
            if (data.ref == null) {
                data.ref = new DataSectionReference();
                dataItems.add(data);
            }
            return data.ref;
        }
    }

    /**
     * Transfers all {@link Data} from the provided other {@link DataSection} to this
     * {@link DataSection}, and empties the other section.
     */
    public void addAll(DataSection other) {
        checkOpen();
        other.checkOpen();

        for (Data data : other.dataItems) {
            assert data.ref != null;
            dataItems.add(data);
        }
        other.dataItems.clear();
    }

    /**
     * Computes the layout of the data section and closes this object to further updates.
     *
     * This must be called exactly once.
     *
     * @param minDataAlignment minimum alignment for an item in the data section
     */
    public void close(OptionValues option, int minDataAlignment) {
        checkOpen();
        closed = true;

        sortDataItems(dataItems);

        int position = 0;
        int alignment = 1;
        for (Data d : dataItems) {
            int itemAlignment = Math.max(minDataAlignment, d.alignment);

            alignment = lcm(alignment, itemAlignment);
            int itemOffset = alignedItemOffset(option, position, itemAlignment);
            d.ref.setOffset(itemOffset);
            position = itemOffset + d.size;
        }

        sectionAlignment = alignment;
        sectionSize = position;
    }

    /**
     * Gets the size of the data section.
     *
     * This must only be called once this object has been {@linkplain #checkClosed() closed}.
     */
    public int getSectionSize() {
        checkClosed();
        return sectionSize;
    }

    /**
     * Gets the minimum alignment requirement of the data section.
     *
     * This must only be called once this object has been {@linkplain #checkClosed() closed}.
     */
    public int getSectionAlignment() {
        checkClosed();
        return sectionAlignment;
    }

    /**
     * Builds the data section into a given buffer.
     *
     * This must only be called once this object has been {@linkplain #checkClosed() closed}.
     *
     * @param buffer the {@link ByteBuffer} where the data section should be built. The buffer must
     *            hold at least {@link #getSectionSize()} bytes.
     * @param patch a {@link Patches} instance to receive {@link VMConstant constants} for
     *            relocations in the data section
     */
    public void buildDataSection(ByteBuffer buffer, Patches patch) {
        buildDataSection(buffer, patch, (r, s) -> {
        });
    }

    /**
     * Builds the data section into a given buffer.
     *
     * This must only be called once this object has been {@linkplain #checkClosed() closed}. When
     * this method returns, the buffers' position is just after the last data item.
     *
     * @param buffer the {@link ByteBuffer} where the data section should be built. The buffer must
     *            hold at least {@link #getSectionSize()} bytes.
     * @param patch a {@link Patches} instance to receive {@link VMConstant constants} for
     * @param onEmit a function that is called before emitting each data item with the
     *            {@link DataSectionReference} and the size of the data.
     */
    public void buildDataSection(ByteBuffer buffer, Patches patch, BiConsumer<DataSectionReference, Integer> onEmit) {
        checkClosed();
        assert buffer.remaining() >= sectionSize : buffer + " " + sectionSize;
        int start = buffer.position();
        for (Data d : dataItems) {
            buffer.position(start + d.ref.getOffset());
            onEmit.accept(d.ref, d.getSize());
            d.emit(buffer, patch);
        }
        buffer.position(start + sectionSize);
    }

    /**
     * Non-mutating view of emitted data-section items keyed by their
     * {@link DataSectionReference}. Callers use it to inspect the computed layout offset, emitted
     * bytes, alignment, and nested constant patches for selected items while preserving the normal
     * image-build ownership of the {@link DataSection}.
     */
    public static final class EmittedItems {
        public record ConstantPatch(int position, VMConstant constant) {
        }

        public record EmittedItem(int offset, int size, int alignment, byte[] bytes, List<ConstantPatch> constantPatches) {
            public int constantPatchCount() {
                return constantPatches.size();
            }
        }

        private final EconomicMap<DataSectionReference, EmittedItem> itemsByReference;

        private EmittedItems(EconomicMap<DataSectionReference, EmittedItem> itemsByReference) {
            this.itemsByReference = itemsByReference;
        }

        public EmittedItem getItem(DataSectionReference reference) {
            EmittedItem item = itemsByReference.get(reference);
            if (item == null) {
                throw new IllegalArgumentException("Unknown data section reference: " + reference);
            }
            return item;
        }
    }

    /**
     * Builds a per-item emitted view using the same item ordering and offset rules as
     * {@link #close(OptionValues, int)}, without mutating this instance.
     * <p>
     * The supplied byte order must match the final data-section emission target. The returned view
     * is intentionally item-scoped rather than a complete section snapshot.
     */
    public EmittedItems buildEmittedItems(OptionValues option, int minDataAlignment, ByteOrder byteOrder) {
        ArrayList<Data> items = new ArrayList<>(dataItems);
        sortDataItems(items);

        EconomicMap<DataSectionReference, EmittedItem> itemsByReference = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        int position = 0;
        for (Data data : items) {
            int itemAlignment = Math.max(minDataAlignment, data.alignment);
            int itemOffset = alignedItemOffset(option, position, itemAlignment);

            byte[] itemBytes = new byte[data.getSize()];
            List<ConstantPatch> itemConstantPatches = new ArrayList<>();
            ByteBuffer itemBuffer = ByteBuffer.wrap(itemBytes).order(byteOrder);
            data.emit(itemBuffer, (patchPosition, constant) -> itemConstantPatches.add(new ConstantPatch(patchPosition, constant)));

            itemsByReference.put(data.ref, new EmittedItem(itemOffset, data.getSize(), itemAlignment, itemBytes, itemConstantPatches));

            position = itemOffset + data.size;
        }
        return new EmittedItems(itemsByReference);
    }

    /**
     * Applies the same item ordering used by {@link #close(OptionValues, int)} and
     * {@link #buildEmittedItems(OptionValues, int, ByteOrder)} so non-mutating item emission
     * observes the layout that closing the section would compute.
     */
    private static void sortDataItems(List<Data> items) {
        // Workaround JVMCI bug with nmethod entry barriers on aarch64 by forcing mutable data items
        // at the beginning of the data section. Within that partition, use the existing simple
        // heuristic that puts smaller alignments first.
        items.sort((a, b) -> {
            if (a.isMutable() != b.isMutable()) {
                return Boolean.compare(b.isMutable(), a.isMutable());
            }
            return a.alignment - b.alignment;
        });
    }

    /**
     * Computes the next data-item offset, including the optional adversarial-layout displacement
     * used to expose loads that require stronger alignment than the emitted item provides.
     */
    private static int alignedItemOffset(OptionValues option, int position, int itemAlignment) {
        int alignedPosition = align(position, itemAlignment);
        if (Options.ForceAdversarialLayout.getValue(option)) {
            if (alignedPosition % (itemAlignment * 2) == 0) {
                alignedPosition += itemAlignment;
                assert alignedPosition % itemAlignment == 0 : Assertions.errorMessage(alignedPosition, itemAlignment);
            }
        }
        return alignedPosition;
    }

    @Override
    public Iterator<Data> iterator() {
        return dataItems.iterator();
    }

    public static int lcm(int x, int y) {
        if (x == 0) {
            return y;
        } else if (y == 0) {
            return x;
        }

        int a = Math.max(x, y);
        int b = Math.min(x, y);
        while (b > 0) {
            int tmp = a % b;
            a = b;
            b = tmp;
        }

        int gcd = a;
        return x * y / gcd;
    }

    public static int align(int position, int alignment) {
        return NumUtil.roundUp(position, alignment);
    }

    private void checkClosed() {
        if (!closed) {
            throw new IllegalStateException(); // ExcludeFromJacocoGeneratedReport
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException(); // ExcludeFromJacocoGeneratedReport
        }
    }

    public void clear() {
        checkOpen();
        this.dataItems.clear();
        this.sectionAlignment = 0;
        this.sectionSize = 0;
    }

}
