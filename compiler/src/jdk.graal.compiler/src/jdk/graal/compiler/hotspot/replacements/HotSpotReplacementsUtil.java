/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.VERIFY_OOP;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HotSpotFieldLocationIdentity.CLASS_INIT_STATE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HotSpotFieldLocationIdentity.MARK_WORD_LOCATION;
import static jdk.graal.compiler.nodes.CompressionNode.CompressionOp.Compress;

import java.lang.ref.Reference;
import java.util.function.ToIntFunction;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.HotSpotCompressionNode;
import jdk.graal.compiler.hotspot.nodes.type.KlassPointerStamp;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodes.CanonicalizableLocation;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubOrNullNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.StoreHubNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.nodes.ReadRegisterNode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

//JaCoCo Exclude

/**
 * A collection of methods used in HotSpot snippets, substitutions and stubs.
 */
public class HotSpotReplacementsUtil {

    /**
     * Base class for location specific read optimizations. Many of these optimizations are normally
     * performed on high level nodes before lowering but opportunities can arise once they have been
     * lowered into reads. By examining the values involved in those reads it may be possible to
     * infer exact types.
     */
    abstract static class HotSpotOptimizingLocationIdentity extends NamedLocationIdentity implements CanonicalizableLocation {

        private HotSpotOptimizingLocationIdentity(String name) {
            this(name, true);
        }

        private HotSpotOptimizingLocationIdentity(String name, boolean immutable) {
            super(name, immutable);
        }

        @Override
        public abstract ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool);

    }

    private static ValueNode findReadHub(ValueNode object) {
        ValueNode base = object;
        if (base instanceof CompressionNode) {
            base = ((CompressionNode) base).getValue();
        }
        if (base instanceof AddressableMemoryAccess) {
            AddressableMemoryAccess access = (AddressableMemoryAccess) base;
            if (access.getLocationIdentity().equals(HUB_LOCATION) || access.getLocationIdentity().equals(COMPRESSED_HUB_LOCATION)) {
                AddressNode address = access.getAddress();
                if (address instanceof OffsetAddressNode) {
                    OffsetAddressNode offset = (OffsetAddressNode) address;
                    return offset.getBase();
                }
            }
        } else if (base instanceof LoadHubNode) {
            LoadHubNode loadhub = (LoadHubNode) base;
            return loadhub.getValue();
        }
        return null;
    }

    /**
     * Fold reads that convert from Class -> Hub -> Class or vice versa.
     *
     * @param read
     * @param object
     * @param otherLocation
     * @return an earlier read or the original {@code read}
     */
    private static ValueNode foldIndirection(ValueNode read, ValueNode object, LocationIdentity otherLocation) {
        if (object instanceof AddressableMemoryAccess) {
            AddressableMemoryAccess access = (AddressableMemoryAccess) object;
            if (access.getLocationIdentity().equals(otherLocation)) {
                AddressNode address = access.getAddress();
                if (address instanceof OffsetAddressNode) {
                    OffsetAddressNode offset = (OffsetAddressNode) address;
                    assert offset.getBase().stamp(NodeView.DEFAULT).isCompatible(read.stamp(NodeView.DEFAULT));
                    return offset.getBase();
                }
            }
        }
        return read;
    }

    /**
     * A HotSpot VM config field location, providing node-construction metadata and snippet memory
     * operations backed by the same offset and location identity.
     * <p>
     * A physical HotSpot field must have exactly one location identity. Accesses whose config
     * offsets alias a field in a given VM configuration must reuse that identity so their memory
     * dependency is represented in the graph.
     */
    public static class HotSpotFieldLocationIdentity extends NamedLocationIdentity {

        public static final HotSpotFieldLocationIdentity BASICLOCK_METADATA_LOCATION = new HotSpotFieldLocationIdentity("BasicLock::_metadata", false,
                        config -> config.basicLockMetadataOffset);

        public static final HotSpotFieldLocationIdentity CLASS_INIT_STATE_LOCATION = new HotSpotFieldLocationIdentity("ClassInitState", false,
                        config -> config.instanceKlassInitStateOffset, StampFactory.forUnsignedInteger(Byte.SIZE));

        public static final HotSpotFieldLocationIdentity CLASS_INIT_THREAD_LOCATION = new HotSpotFieldLocationIdentity("ClassInitThread", false,
                        config -> config.instanceKlassInitThreadOffset);

        public static final HotSpotFieldLocationIdentity EXCEPTION_OOP_LOCATION = new HotSpotFieldLocationIdentity("ExceptionOop", false,
                        config -> config.threadExceptionOopOffset, StampFactory.object());

        public static final HotSpotFieldLocationIdentity EXCEPTION_PC_LOCATION = new HotSpotFieldLocationIdentity("ExceptionPc", false,
                        config -> config.threadExceptionPcOffset);

        public static final HotSpotFieldLocationIdentity HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT_LOCATION = new HotSpotFieldLocationIdentity("ContinuationEntry::_pin_count", false,
                        config -> config.pinCountOffset, StampFactory.forKind(JavaKind.Int));

        public static final HotSpotFieldLocationIdentity HOTSPOT_JAVA_THREAD_CONT_ENTRY_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_cont_entry", false,
                        config -> config.contEntryOffset);

        public static final HotSpotFieldLocationIdentity JAVA_LANG_THREAD_JFR_EPOCH = new HotSpotFieldLocationIdentity("java/lang/Thread.jfrEpoch", false,
                        config -> config.javaLangThreadJFREpochOffset, StampFactory.forUnsignedInteger(Short.SIZE));

        public static final HotSpotFieldLocationIdentity JAVA_LANG_THREAD_TID = new HotSpotFieldLocationIdentity("java/lang/Thread.tid", false,
                        config -> config.javaLangThreadTIDOffset);

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_threadObj", false,
                        config -> config.javaThreadThreadObjOffset);

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_CURRENT_THREAD_OBJECT_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_vthread", false,
                        config -> config.javaThreadVthreadOffset);

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_held_monitor_count", false,
                        config -> config.threadHeldMonitorCountOffset);

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_LOCK_STACK_TOP_LOCATION = new HotSpotFieldLocationIdentity("LockStack::_top", false,
                        config -> config.threadLockStackOffset + config.lockStackTopOffset, StampFactory.forKind(JavaKind.Int));

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_MONITOR_OWNER_ID_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_monitor_owner_id", false,
                        config -> config.javaThreadMonitorOwnerIDOffset);

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_SCOPED_VALUE_CACHE_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_scopedValueCache", true,
                        config -> config.javaThreadScopedValueCacheOffset);

        public static final HotSpotFieldLocationIdentity JAVA_THREAD_UNLOCKED_INFLATED_MONITOR_LOCATION = new HotSpotFieldLocationIdentity("JavaThread::_unlocked_inflated_monitor", false,
                        config -> config.threadUnlockedInflatedMonitorOffset);

        public static final HotSpotFieldLocationIdentity JFR_THREAD_LOCAL_VTHREAD = new HotSpotFieldLocationIdentity("JfrThreadLocal::_vthread", false,
                        config -> config.threadJFRThreadLocalOffset + config.jfrThreadLocalVthreadOffset, StampFactory.forUnsignedInteger(Byte.SIZE));

        public static final HotSpotFieldLocationIdentity JFR_THREAD_LOCAL_VTHREAD_EPOCH = new HotSpotFieldLocationIdentity("JfrThreadLocal::_vthread_epoch", false,
                        config -> config.threadJFRThreadLocalOffset + config.jfrThreadLocalVthreadEpochOffset, StampFactory.forUnsignedInteger(Short.SIZE));

        public static final HotSpotFieldLocationIdentity JFR_THREAD_LOCAL_VTHREAD_EXCLUDED = new HotSpotFieldLocationIdentity("JfrThreadLocal::_vthread_excluded", false,
                        config -> config.threadJFRThreadLocalOffset + config.jfrThreadLocalVthreadExcludedOffset, StampFactory.forUnsignedInteger(Byte.SIZE));

        public static final HotSpotFieldLocationIdentity JFR_THREAD_LOCAL_VTHREAD_ID = new HotSpotFieldLocationIdentity("JfrThreadLocal::_vthread_id", false,
                        config -> config.threadJFRThreadLocalOffset + config.jfrThreadLocalVthreadIDOffset);

        public static final HotSpotFieldLocationIdentity KLASS_ACCESS_FLAGS_LOCATION = new HotSpotFieldLocationIdentity("Klass::_access_flags", true,
                        config -> config.klassAccessFlagsOffset, StampFactory.forUnsignedInteger(JavaKind.Char.getBitCount()));

        public static final HotSpotFieldLocationIdentity KLASS_BITMAP_LOCATION = new HotSpotFieldLocationIdentity("Klass::_bitmap", true,
                        config -> config.klassBitmapOffset, StampFactory.forKind(JavaKind.Long));

        public static final HotSpotFieldLocationIdentity KLASS_HASH_SLOT_LOCATION = new HotSpotFieldLocationIdentity("Klass::_hash_slot", true,
                        config -> config.klassHashSlotOffset, StampFactory.forUnsignedInteger(Byte.SIZE));

        public static final HotSpotFieldLocationIdentity KLASS_MISC_FLAGS_LOCATION = new HotSpotFieldLocationIdentity("Klass::_misc_flags", true,
                        config -> config.klassMiscFlagsOffset, StampFactory.forUnsignedInteger(Byte.SIZE));

        public static final HotSpotFieldLocationIdentity KLASS_SUPER_CHECK_OFFSET_LOCATION = new HotSpotFieldLocationIdentity("Klass::_super_check_offset", true,
                        config -> config.superCheckOffsetOffset, StampFactory.forKind(JavaKind.Int));

        public static final HotSpotFieldLocationIdentity KLASS_SUPER_KLASS_LOCATION = new HotSpotFieldLocationIdentity("Klass::_super", true,
                        config -> config.klassSuperKlassOffset, KlassPointerStamp.klass());

        public static final HotSpotFieldLocationIdentity MARK_WORD_LOCATION = new HotSpotFieldLocationIdentity("MarkWord", false,
                        config -> config.markOffset);

        public static final HotSpotFieldLocationIdentity METASPACE_ARRAY_LENGTH_LOCATION = new HotSpotFieldLocationIdentity("MetaspaceArrayLength", true,
                        config -> config.metaspaceArrayLengthOffset, StampFactory.forKind(JavaKind.Int));

        public static final HotSpotFieldLocationIdentity OBJECT_MONITOR_ENTRY_LIST_LOCATION = new HotSpotFieldLocationIdentity("ObjectMonitor::_EntryList", false,
                        config -> config.objectMonitorEntryList);

        public static final HotSpotFieldLocationIdentity OBJECT_MONITOR_OWNER_LOCATION = new HotSpotFieldLocationIdentity("ObjectMonitor::_owner", false,
                        config -> config.objectMonitorOwner);

        public static final HotSpotFieldLocationIdentity OBJECT_MONITOR_RECURSION_LOCATION = new HotSpotFieldLocationIdentity("ObjectMonitor::_recursions", false,
                        config -> config.objectMonitorRecursions);

        public static final HotSpotFieldLocationIdentity OBJECT_MONITOR_SUCC_LOCATION = new HotSpotFieldLocationIdentity("ObjectMonitor::_succ", false,
                        config -> config.objectMonitorSucc);

        public static final HotSpotFieldLocationIdentity PENDING_EXCEPTION_LOCATION = new HotSpotFieldLocationIdentity("PendingException", false,
                        config -> config.pendingExceptionOffset, StampFactory.object());

        public static final HotSpotFieldLocationIdentity SECONDARY_SUPER_CACHE_LOCATION = new HotSpotFieldLocationIdentity("SecondarySuperCache", false,
                        config -> config.secondarySuperCacheOffset, KlassPointerStamp.klass());

        public static final HotSpotFieldLocationIdentity SECONDARY_SUPERS_LOCATION = new HotSpotFieldLocationIdentity("SecondarySupers", true,
                        config -> config.secondarySupersOffset);

        public static final HotSpotFieldLocationIdentity TLAB_END_LOCATION = new HotSpotFieldLocationIdentity("TlabEnd", false,
                        config -> config.threadTlabEndOffset);

        private final ToIntFunction<GraalHotSpotVMConfig> offsetProvider;
        private final Stamp stamp;

        HotSpotFieldLocationIdentity(String name, boolean immutable, ToIntFunction<GraalHotSpotVMConfig> offsetProvider) {
            this(name, immutable, offsetProvider, null);
        }

        HotSpotFieldLocationIdentity(String name, boolean immutable, ToIntFunction<GraalHotSpotVMConfig> offsetProvider, Stamp stamp) {
            super(name, immutable);
            this.offsetProvider = offsetProvider;
            this.stamp = stamp;
        }

        @Fold
        final int getOffset(@InjectedParameter GraalHotSpotVMConfig config) {
            return offsetProvider.applyAsInt(config);
        }

        final Stamp stampOrDefault(JavaKind wordKind) {
            return stamp == null ? StampFactory.forKind(wordKind) : stamp;
        }

        public final OffsetAddressNode asOffsetAddress(GraalHotSpotVMConfig config, ValueNode base) {
            return new OffsetAddressNode(base, ConstantNode.forIntegerKind(getWordKind(), getOffset(config)));
        }

        // Snippet memory operations.

        public final byte readByte(Word base) {
            return base.readByte(getOffset(INJECTED_VMCONFIG), this);
        }

        public final byte readByte(KlassPointer base) {
            return base.readByte(getOffset(INJECTED_VMCONFIG), this);
        }

        public final byte readByteVolatile(KlassPointer base) {
            return base.readByteVolatile(getOffset(INJECTED_VMCONFIG), this);
        }

        public final int readUnsignedByte(KlassPointer base) {
            return readByte(base) & 0xff;
        }

        public final boolean matchesOffset(int candidateOffset) {
            return candidateOffset == getOffset(INJECTED_VMCONFIG);
        }

        public final void writeByte(Word base, byte value) {
            base.writeByte(getOffset(INJECTED_VMCONFIG), value, this);
        }

        public final int readShort(Word base) {
            return base.readShort(getOffset(INJECTED_VMCONFIG), this);
        }

        public final void writeChar(Word base, char value) {
            base.writeChar(getOffset(INJECTED_VMCONFIG), value, this);
        }

        public final int readInt(Word base) {
            return base.readInt(getOffset(INJECTED_VMCONFIG), this);
        }

        public final int readInt(KlassPointer base) {
            return base.readInt(getOffset(INJECTED_VMCONFIG), this);
        }

        public final void writeInt(Word base, int value) {
            base.writeInt(getOffset(INJECTED_VMCONFIG), value, this);
        }

        public final long readLong(KlassPointer base) {
            return base.readLong(getOffset(INJECTED_VMCONFIG), this);
        }

        public final Object readObject(Word base) {
            return base.readObject(getOffset(INJECTED_VMCONFIG), this);
        }

        public final void writeObject(Word base, Object value) {
            base.writeObject(getOffset(INJECTED_VMCONFIG), value, this);
        }

        public final Word readWord(Object base) {
            int offset = getOffset(INJECTED_VMCONFIG);
            ReplacementsUtil.staticAssert(useCompactObjectHeaders(INJECTED_VMCONFIG) || offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadWordFromObject");
            return loadWordFromObjectIntrinsic(base, offset, this, getWordKind());
        }

        public final Word readWord(Word base) {
            return base.readWord(getOffset(INJECTED_VMCONFIG), this);
        }

        public final Word readWord(KlassPointer base) {
            return base.readWord(getOffset(INJECTED_VMCONFIG), this);
        }

        public final void writeWord(Word base, Word value) {
            base.writeWord(getOffset(INJECTED_VMCONFIG), value, this);
        }

        public final Word compareAndSwapWord(Pointer base, Word expectedValue, Word newValue) {
            return base.compareAndSwapWord(getOffset(INJECTED_VMCONFIG), expectedValue, newValue, this);
        }

        public final boolean logicCompareAndSwapWord(Pointer base, Word expectedValue, Word newValue) {
            return base.logicCompareAndSwapWord(getOffset(INJECTED_VMCONFIG), expectedValue, newValue, this);
        }

        public final KlassPointer readKlassPointer(KlassPointer base) {
            return base.readKlassPointer(getOffset(INJECTED_VMCONFIG), this);
        }

        public final void writeKlassPointer(KlassPointer base, KlassPointer value) {
            base.writeKlassPointer(getOffset(INJECTED_VMCONFIG), value, this);
        }

        public final KlassPointer readKlassPointer(Object base) {
            int offset = getOffset(INJECTED_VMCONFIG);
            ReplacementsUtil.staticAssert(useCompactObjectHeaders(INJECTED_VMCONFIG) || offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadKlassFromObjectIntrinsic");
            return loadKlassFromObjectIntrinsic(base, offset, this, getWordKind());
        }
    }

    /**
     * A HotSpot field location whose reads can be canonicalized after lowering.
     */
    public abstract static class HotSpotOptimizingFieldLocationIdentity extends HotSpotFieldLocationIdentity implements CanonicalizableLocation {

        public static final HotSpotOptimizingFieldLocationIdentity CLASS_ARRAY_KLASS_LOCATION = new HotSpotOptimizingFieldLocationIdentity("Class._array_klass",
                        config -> config.arrayKlassOffset, KlassPointerStamp.klassNonNull()) {
            @Override
            public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
                return foldIndirection(read, object, ARRAY_KLASS_COMPONENT_MIRROR);
            }
        };

        public static final HotSpotOptimizingFieldLocationIdentity KLASS_LAYOUT_HELPER_LOCATION = new HotSpotOptimizingFieldLocationIdentity("Klass::_layout_helper",
                        config -> config.klassLayoutHelperOffset, StampFactory.forKind(JavaKind.Int)) {
            @Override
            public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
                ValueNode javaObject = findReadHub(object);
                if (javaObject != null) {
                    if (javaObject.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                        ObjectStamp stamp = (ObjectStamp) javaObject.stamp(NodeView.DEFAULT);
                        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) stamp.javaType(tool.getMetaAccess());
                        if (type.isArray() && !type.getComponentType().isPrimitive()) {
                            int layout = type.layoutHelper();
                            return ConstantNode.forInt(layout);
                        }
                    }
                }
                return read;
            }
        };

        public static final HotSpotOptimizingFieldLocationIdentity OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION = new HotSpotOptimizingFieldLocationIdentity(
                        "ObjArrayKlass::_element_klass",
                        config -> config.arrayClassElementOffset, KlassPointerStamp.klassNonNull()) {
            @Override
            public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
                ValueNode javaObject = findReadHub(object);
                if (javaObject != null) {
                    ResolvedJavaType type = StampTool.typeOrNull(javaObject);
                    if (type != null && type.isArray()) {
                        ResolvedJavaType element = type.getComponentType();
                        if (element != null && !element.isPrimitive() && !element.getElementalType().isInterface()) {
                            Assumptions assumptions = object.graph().getAssumptions();
                            AssumptionResult<ResolvedJavaType> leafType = element.findLeafConcreteSubtype();
                            if (leafType != null && leafType.canRecordTo(assumptions)) {
                                leafType.recordTo(assumptions);
                                return ConstantNode.forConstant(read.stamp(NodeView.DEFAULT), tool.getConstantReflection().asObjectHub(leafType.getResult()), tool.getMetaAccess());
                            }
                        }
                    }
                }
                return read;
            }
        };

        public static final HotSpotOptimizingFieldLocationIdentity TLAB_TOP_LOCATION = new HotSpotOptimizingFieldLocationIdentity("TlabTop", false,
                        config -> config.threadTlabTopOffset, null) {
            @Override
            public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
                // The TLAB top is always aligned to -XX:ObjectAlignment, reflect this in its stamp.
                if (read instanceof ReadNode readNode) {
                    IntegerStamp readStamp = (IntegerStamp) readNode.stamp(view);
                    GraalHotSpotVMConfig config = ((HotSpotProviders) tool.getReplacements().getProviders()).getConfig();
                    long alignmentMask = -config.objectAlignment;
                    IntegerStamp alignedStamp = IntegerStamp.stampForMask(readStamp.getBits(), readStamp.mustBeSet(), alignmentMask);
                    readNode.setStamp(readStamp.join(alignedStamp));
                }
                return read;
            }
        };

        HotSpotOptimizingFieldLocationIdentity(String name, ToIntFunction<GraalHotSpotVMConfig> offsetProvider, Stamp stamp) {
            this(name, true, offsetProvider, stamp);
        }

        HotSpotOptimizingFieldLocationIdentity(String name, boolean immutable, ToIntFunction<GraalHotSpotVMConfig> offsetProvider, Stamp stamp) {
            super(name, immutable, offsetProvider, stamp);
        }
    }

    /**
     * A location identity for word-addressed array storage whose base offset is VM-configured.
     */
    public static final class HotSpotWordArrayLocationIdentity extends NamedLocationIdentity {

        public static final HotSpotWordArrayLocationIdentity SECONDARY_SUPERS_ELEMENT_LOCATION = new HotSpotWordArrayLocationIdentity("SecondarySupersElement", true,
                        config -> config.metaspaceArrayBaseOffset);

        private final ToIntFunction<GraalHotSpotVMConfig> baseOffsetProvider;

        private HotSpotWordArrayLocationIdentity(String name, boolean immutable, ToIntFunction<GraalHotSpotVMConfig> baseOffsetProvider) {
            super(name, immutable);
            this.baseOffsetProvider = baseOffsetProvider;
        }

        @Fold
        int getBaseOffset(@InjectedParameter GraalHotSpotVMConfig config) {
            return baseOffsetProvider.applyAsInt(config);
        }

        /**
         * Reads the word at {@code index}, measured in word elements rather than bytes.
         */
        public Word read(Word arrayBase, long index) {
            return arrayBase.readWord(Word.signed(getBaseOffset(INJECTED_VMCONFIG) + index * wordSize()), this);
        }

        public KlassPointer readKlassPointer(Word arrayBase, long index) {
            return KlassPointer.fromWord(read(arrayBase, index));
        }
    }

    public static ResolvedJavaType getType(ResolvedJavaType accessingClass, String typeName) {
        try {
            return UnresolvedJavaType.create(typeName).resolve(accessingClass);
        } catch (LinkageError e) {
            throw new GraalError(e);
        }
    }

    @Fold
    public static int getFieldOffset(ResolvedJavaType type, String fieldName) {
        return getField(type, fieldName).getOffset();
    }

    public static ResolvedJavaField getField(ResolvedJavaType type, String fieldName) {
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        throw new GraalError("missing field " + fieldName + " in type " + type);
    }

    public static HotSpotJVMCIRuntime runtime() {
        return HotSpotJVMCIRuntime.runtime();
    }

    @Fold
    public static int getHeapWordSize(@InjectedParameter GraalHotSpotVMConfig injectedVMConfig) {
        return injectedVMConfig.heapWordSize;
    }

    @Fold
    public static int klassLayoutHelperNeutralValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassLayoutHelperNeutralValue;
    }

    @Fold
    public static boolean useTLAB(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useTLAB;
    }

    @Fold
    public static boolean useG1GC(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useG1GC();
    }

    @Fold
    public static boolean useSerialGC(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useSerialGC();
    }

    /**
     * The location identity for the {@code JavaThread} field containing the reference to the
     * current thread. As far as Java code without virtual threads is concerned this can be
     * considered immutable: it is set just after the JavaThread is created, before it is published.
     * After that, it is never changed. In the presence of virtual threads from JDK 19 onwards, this
     * value can change when a virtual thread is unmounted and then mounted again.
     */

    public static final LocationIdentity JAVA_THREAD_LOCK_STACK_LOCATION = NamedLocationIdentity.mutable("JavaThread::_lock_stack");

    public static final LocationIdentity JAVA_THREAD_OM_CACHE_LOCATION = NamedLocationIdentity.mutable("JavaThread::_om_cache");

    @Fold
    public static JavaKind getWordKind() {
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordJavaKind;
    }

    @Fold
    public static int wordSize() {
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordSize;
    }

    @Fold
    public static int pageSize(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.vmPageSize;
    }

    public static final LocationIdentity PROTOTYPE_MARK_WORD_LOCATION = NamedLocationIdentity.mutable("PrototypeMarkWord");

    @Fold
    public static int jvmAccHasFinalizer(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.jvmAccHasFinalizer;
    }

    @Fold
    public static int allocatePrefetchStyle(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchStyle;
    }

    @Fold
    public static int allocatePrefetchLines(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchLines;
    }

    @Fold
    public static int allocatePrefetchDistance(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchDistance;
    }

    @Fold
    public static int allocateInstancePrefetchLines(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocateInstancePrefetchLines;
    }

    @Fold
    public static int allocatePrefetchStepSize(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchStepSize;
    }

    @NodeIntrinsic(value = KlassLayoutHelperNode.class)
    public static native int readLayoutHelper(KlassPointer object);

    /**
     * Checks if class {@code klass} is an array.
     *
     * See: Klass::layout_helper_is_array
     *
     * @param klassNonNull the class to be checked
     * @return true if klassNonNull is an array, false otherwise
     */
    public static boolean klassIsArray(KlassPointer klassNonNull) {
        /*
         * The less-than check only works if both values are ints. We use local variables to make
         * sure these are still ints and haven't changed.
         */
        final int layoutHelper = readLayoutHelper(klassNonNull);
        final int layoutHelperNeutralValue = klassLayoutHelperNeutralValue(INJECTED_VMCONFIG);
        return layoutHelper < layoutHelperNeutralValue;
    }

    public static final LocationIdentity ARRAY_KLASS_COMPONENT_MIRROR = NamedLocationIdentity.immutable("ArrayKlass::_component_mirror");

    // With compact object headers, the hub shares this word and must use this identity.

    public static final LocationIdentity HUB_WRITE_LOCATION = NamedLocationIdentity.mutable("Hub:write");

    public static final LocationIdentity HUB_LOCATION = new HotSpotOptimizingLocationIdentity("Hub") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
            TypeReference constantType = StampTool.typeReferenceOrNull(object);
            if (constantType != null && constantType.isExact()) {
                return ConstantNode.forConstant(read.stamp(NodeView.DEFAULT), tool.getConstantReflection().asObjectHub(constantType.getType()), tool.getMetaAccess());
            }
            return read;
        }
    };

    public static final LocationIdentity COMPRESSED_HUB_LOCATION = new HotSpotOptimizingLocationIdentity("CompressedHub") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
            TypeReference constantType = StampTool.typeReferenceOrNull(object);
            if (constantType != null && constantType.isExact()) {
                return ConstantNode.forConstant(read.stamp(NodeView.DEFAULT), ((HotSpotMetaspaceConstant) tool.getConstantReflection().asObjectHub(constantType.getType())).compress(),
                                tool.getMetaAccess());
            }
            return read;
        }
    };

    @Fold
    public static boolean useCompactObjectHeaders(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useCompactObjectHeaders;
    }

    @Fold
    public static int markWordKlassShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.markWordKlassShift;
    }

    @NodeIntrinsic(HotSpotCompressionNode.class)
    private static native KlassPointer compress(@ConstantNodeParameter CompressionOp op, KlassPointer hub, @ConstantNodeParameter CompressEncoding encoding);

    @Fold
    static int hubOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.hubOffset;
    }

    @Fold
    static CompressEncoding klassEncoding(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.getKlassEncoding();
    }

    public static void initializeObjectHeader(Word memory, Word markWord, KlassPointer hub) {
        if (useCompactObjectHeaders(INJECTED_VMCONFIG)) {
            Word compressedHub = Word.unsigned(compress(Compress, hub, klassEncoding(INJECTED_VMCONFIG)).asInt());
            Word hubInPlace = compressedHub.shiftLeft(markWordKlassShift(INJECTED_VMCONFIG));
            Word newMarkWord = markWord.or(hubInPlace);
            MARK_WORD_LOCATION.writeWord(memory, newMarkWord);
        } else {
            MARK_WORD_LOCATION.writeWord(memory, markWord);
            StoreHubNode.write(memory, hub);
        }
    }

    @Fold
    public static boolean useStackLocking(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.lockingMode == config.lockingModeStack;
    }

    @Fold
    public static boolean useLightweightLocking(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.lockingMode == config.lockingModeLightweight;
    }

    @Fold
    public static boolean useObjectMonitorTable(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useObjectMonitorTable;
    }

    @Fold
    public static int unlockedValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.unlockedValue;
    }

    @Fold
    public static int monitorValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.monitorValue;
    }

    @Fold
    public static int unusedMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.unusedMark;
    }

    /**
     * Mask for a biasable, locked or unlocked mark word. It is the least significant 3 bits prior
     * to Java 18 (1 bit for biased locking and 2 bits for stack locking or heavy locking), and 2
     * bits afterwards due to elimination of the biased locking.
     */
    @Fold
    public static long markWordLockMaskInPlace(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.markWordLockMaskInPlace;
    }

    @Fold
    static int arrayLengthOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.arrayLengthOffsetInBytes;
    }

    @Fold
    public static boolean verifyBeforeOrAfterGC(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyBeforeGC || config.verifyAfterGC;
    }

    /**
     * Idiom for making {@link GraalHotSpotVMConfig} a constant.
     */
    @Fold
    public static int objectAlignment(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectAlignment;
    }

    @Fold
    public static int instanceHeaderSize(@InjectedParameter GraalHotSpotVMConfig config) {
        if (config.useCompactObjectHeaders) {
            return wordSize();
        }
        return config.useCompressedClassPointers ? (2 * wordSize()) - 4 : 2 * wordSize();
    }

    @Fold
    public static boolean supportsG1LowLatencyBarriers(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1LowLatencyPostWriteBarrierSupport;
    }

    @Fold
    public static byte cleanCardValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cleanCardValue;
    }

    @Fold
    public static byte dirtyCardValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.dirtyCardValue;
    }

    @Fold
    public static byte g1YoungCardValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1YoungCardValue;
    }

    @Fold
    public static int cardTableShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cardtableShift;
    }

    @Fold
    public static long cardTableStart(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cardtableStartAddress;
    }

    @Fold
    public static int g1CardTableBaseOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardTableBaseOffset;
    }

    @Fold
    public static int g1CardQueueIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardQueueIndexOffset;
    }

    @Fold
    public static int g1CardQueueBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardQueueBufferOffset;
    }

    @Fold
    public static int logOfHeapRegionGrainBytes(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.logOfHRGrainBytes;
    }

    @Fold
    public static int g1SATBQueueMarkingActiveOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueMarkingActiveOffset;
    }

    @Fold
    public static int g1SATBQueueIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueIndexOffset;
    }

    @Fold
    public static int g1SATBQueueBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueBufferOffset;
    }

    @Fold
    public static boolean useCondCardMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useCondCardMark;
    }

    @Fold
    public static int shenandoahGCStateOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.shenandoahGCStateOffset;
    }

    @Fold
    public static int shenandoahSATBIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.shenandoahSATBIndexOffset;
    }

    @Fold
    public static int shenandoahSATBBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.shenandoahSATBBufferOffset;
    }

    @Fold
    public static int shenandoahCardTableOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.shenandoahCardTableOffset;
    }

    @Fold
    public static int shenandoahGCRegionSizeBytesShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.shenandoahGCRegionSizeBytesShift;
    }

    @Fold
    public static long shenandoahGCCSetFastTestAddr(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.shenandoahGCCSetFastTestAddress;
    }

    @Fold
    public static boolean useSecondarySupersCache(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useSecondarySupersCache;
    }

    @Fold
    public static boolean useSecondarySupersTable(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useSecondarySupersTable;
    }

    public static final LocationIdentity OBJECT_MONITOR_CXQ_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_cxq");

    public static final LocationIdentity OBJECT_MONITOR_STACK_LOCKER_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_stack_locker");

    @Fold
    static int javaThreadLockStackEndOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.lockStackEndOffset;
    }

    @Fold
    static int javaThreadOomCacheOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadOmCacheOffset;
    }

    @Fold
    static int omCacheOopToOopDifference(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.omCacheOopToOopDifference;
    }

    @Fold
    static int omCacheOopToMonitorDifference(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.omCacheOopToMonitorDifference;
    }

    @Fold
    static boolean isCAssertEnabled(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cAssertions;
    }

    @Fold
    public static boolean diagnoseSyncOnValueBasedClasses(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.diagnoseSyncOnValueBasedClasses != 0 && config.jvmAccIsValueBasedClass != 0;
    }

    @Fold
    public static int jvmAccIsValueBasedClass(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.jvmAccIsValueBasedClass;
    }

    @Fold
    public static long defaultPrototypeMarkWord(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.markWordNoHashInPlace | config.markWordNoLockInPlace;
    }

    @Fold
    static int uninitializedIdentityHashCodeValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.uninitializedIdentityHashCodeValue;
    }

    @Fold
    static int markWordHashCodeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.markWordHashCodeShift;
    }

    @Fold
    static long markWordHashMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.markWordHashMask;
    }

    /**
     * Loads the hub of an object (without null checking it first).
     */
    public static KlassPointer loadHub(Object object) {
        return loadHubIntrinsic(object);
    }

    @Fold
    public static boolean verifyOops(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOops;
    }

    public static Object verifyOop(Object object) {
        if (verifyOops(INJECTED_VMCONFIG)) {
            verifyOopStub(VERIFY_OOP, object);
        }
        return object;
    }

    @Fold
    public static long verifyOopBits(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopBits;
    }

    @Fold
    public static long verifyOopMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopMask;
    }

    @Fold
    public static long verifyOopCounterAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopCounterAddress;
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native Object verifyOopStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    /**
     * Reads the value of a given register.
     *
     * @param register a register which must not be available to the register allocator
     * @return the value of {@code register} as a word
     */
    public static Word registerAsWord(@ConstantNodeParameter Register register) {
        return registerAsWord(register, true, false);
    }

    @NodeIntrinsic(value = ReadRegisterNode.class)
    public static native Word registerAsWord(@ConstantNodeParameter Register register, @ConstantNodeParameter boolean directUse, @ConstantNodeParameter boolean incoming);

    @NodeIntrinsic(value = RawLoadNode.class)
    private static native Word loadWordFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter JavaKind wordKind);

    @NodeIntrinsic(value = RawLoadNode.class)
    private static native KlassPointer loadKlassFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter JavaKind wordKind);

    @NodeIntrinsic(value = LoadHubNode.class)
    public static native KlassPointer loadHubIntrinsic(Object object);

    @NodeIntrinsic(value = LoadHubOrNullNode.class)
    public static native KlassPointer loadHubOrNullIntrinsic(Object object);

    @Fold
    public static int instanceKlassStateFullyInitialized(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.instanceKlassStateFullyInitialized;
    }

    @Fold
    public static int instanceKlassStateBeingInitialized(@InjectedParameter GraalHotSpotVMConfig config) {
        assert config.instanceKlassStateBeingInitialized != -1;
        return config.instanceKlassStateBeingInitialized;
    }

    /**
     *
     * @param hub the hub of an InstanceKlass
     * @return true is the InstanceKlass represented by hub is fully initialized
     */
    public static boolean isInstanceKlassFullyInitialized(KlassPointer hub) {
        return CLASS_INIT_STATE_LOCATION.readByteVolatile(hub) == instanceKlassStateFullyInitialized(INJECTED_VMCONFIG);
    }

    public static final LocationIdentity CLASS_KLASS_LOCATION = new HotSpotOptimizingLocationIdentity("Class._klass") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode location, NodeView view, CoreProviders tool) {
            return foldIndirection(read, object, CLASS_MIRROR_LOCATION);
        }
    };

    /**
     * HotSpot oop handle memory locations.
     */
    public static class OopHandleLocationIdentity extends NamedLocationIdentity {
        public OopHandleLocationIdentity(String name, boolean immutable) {
            super(name, immutable);
        }

        /**
         * @see NamedLocationIdentity#immutable(String)
         */
        public static NamedLocationIdentity immutable(String name) {
            return new OopHandleLocationIdentity(name, true);
        }

        /**
         * @see NamedLocationIdentity#mutable(String)
         */
        public static NamedLocationIdentity mutable(String name) {
            return new OopHandleLocationIdentity(name, false);
        }
    }

    public static final LocationIdentity CLASS_MIRROR_LOCATION = NamedLocationIdentity.immutable("Klass::_java_mirror");

    /**
     * This represents the contents of OopHandles used for some internal fields.
     */
    public static final LocationIdentity HOTSPOT_OOP_HANDLE_LOCATION = OopHandleLocationIdentity.immutable("OopHandle contents");

    /**
     * This represents the contents of the OopHandle used to store the current thread. Virtual
     * thread support makes this mutable.
     */
    public static final LocationIdentity HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION = OopHandleLocationIdentity.mutable("_vthread OopHandle contents");

    public static final LocationIdentity HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION = OopHandleLocationIdentity.mutable("_threadObj OopHandle contents");

    public static final LocationIdentity HOTSPOT_JAVA_THREAD_SCOPED_VALUE_CACHE_HANDLE_LOCATION = OopHandleLocationIdentity.mutable("_scopedValueCache OopHandle contents");

    public static final LocationIdentity HOTSPOT_VTMS_NOTIFY_JVMTI_EVENTS = NamedLocationIdentity.mutable("JvmtiVTMSTransitionDisabler::_VTMS_notify_jvmti_events");
    public static final LocationIdentity HOTSPOT_JAVA_THREAD_IS_IN_VTMS_TRANSITION = NamedLocationIdentity.mutable("JavaThread::_is_in_VTMS_transition");
    public static final LocationIdentity HOTSPOT_JAVA_THREAD_IS_IN_TMP_VTMS_TRANSITION = NamedLocationIdentity.mutable("JavaThread::_is_in_tmp_VTMS_transition");
    public static final LocationIdentity HOTSPOT_JAVA_THREAD_IS_DISABLE_SUSPEND = NamedLocationIdentity.mutable("JavaThread::_is_disable_suspend");
    public static final LocationIdentity HOTSPOT_JAVA_LANG_THREAD_IS_IN_VTMS_TRANSITION = NamedLocationIdentity.mutable("Thread::_is_in_VTMS_transition");

    @Fold
    public static int layoutHelperHeaderSizeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperHeaderSizeShift;
    }

    @Fold
    public static int layoutHelperHeaderSizeMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperHeaderSizeMask;
    }

    @Fold
    public static int layoutHelperLog2ElementSizeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperLog2ElementSizeShift;
    }

    @Fold
    public static int layoutHelperLog2ElementSizeMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperLog2ElementSizeMask;
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native int identityHashCode(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @Fold
    public static long gcTotalCollectionsAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.gcTotalCollectionsAddress();
    }

    public static String referentFieldName() {
        return "referent";
    }

    public static long referentOffset(@InjectedParameter MetaAccessProvider metaAccessProvider) {
        return referentField(metaAccessProvider).getOffset();
    }

    public static ResolvedJavaField referentField(@InjectedParameter MetaAccessProvider metaAccessProvider) {
        return getField(metaAccessProvider.lookupJavaType(Reference.class), referentFieldName());
    }

    public static boolean supportsVirtualThreadUpdateJFR(GraalHotSpotVMConfig config) {
        return config.threadJFRThreadLocalOffset != -1 && config.jfrThreadLocalVthreadIDOffset != -1 && config.jfrThreadLocalVthreadEpochOffset != -1 &&
                        config.jfrThreadLocalVthreadExcludedOffset != -1 && config.jfrThreadLocalVthreadOffset != -1 && config.javaLangThreadJFREpochOffset != -1 &&
                        config.javaLangThreadTIDOffset != -1;
    }

    public static final LocationIdentity PRIMARY_SUPERS_LOCATION = NamedLocationIdentity.immutable("PrimarySupers");

    /**
     * This location identity is intended for accesses to {@code Klass::_primary_supers}, which is
     * immutable. However, in {@link TypeCheckSnippetUtils#checkUnknownSubType}, it is possible to
     * trigger context insensitive constant folding of the corresponding read in the dead code where
     * the read's displacement is {@link GraalHotSpotVMConfig#secondarySuperCacheOffset}, i.e.,
     * pointing to the mutable {@code Klass::_secondary_super_cache}. Hence, we only fold
     * corresponding reads when the displacement is not
     * {@link GraalHotSpotVMConfig#secondarySuperCacheOffset}.
     */
    public static final LocationIdentity OPTIMIZING_PRIMARY_SUPERS_LOCATION = new HotSpotOptimizingLocationIdentity("PrimarySupersOrSecondarySuperCache", false) {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, ValueNode object, ValueNode offset, NodeView view, CoreProviders tool) {
            int secondarySuperCacheOffset = ((HotSpotLoweringProvider) tool.getLowerer()).getVMConfig().secondarySuperCacheOffset;

            if (object instanceof ConstantNode && offset instanceof ConstantNode) {
                long displacement = offset.asJavaConstant().asLong();
                if (displacement != secondarySuperCacheOffset) {
                    Stamp accessStamp = read.stamp(view);
                    Constant constant = accessStamp.readConstant(tool.getConstantReflection().getMemoryAccessProvider(), object.asConstant(), displacement, accessStamp);
                    return ConstantNode.forConstant(accessStamp, constant, 0, false, tool.getMetaAccess());
                }
            }
            return read;
        }
    };

}
