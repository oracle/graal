/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_ARRAY_KLASS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_JAVA_THREAD_CONT_ENTRY_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_CURRENT_THREAD_OBJECT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_SCOPED_VALUE_CACHE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_MISC_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_SUPER_KLASS_LOCATION;

import java.util.function.Function;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.nodes.CurrentJavaThreadNode;
import jdk.graal.compiler.hotspot.nodes.type.KlassPointerStamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.replacements.InvocationPluginHelper;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A helper class for HotSpot specific invocation plugins. In particular it adds helpers for
 * correctly performing reads of HotSpot specific fields.
 */
public class HotSpotInvocationPluginHelper extends InvocationPluginHelper {

    private final GraalHotSpotVMConfig config;
    private final BarrierSet barrierSet;

    public HotSpotInvocationPluginHelper(GraphBuilderContext b, ResolvedJavaMethod targetMethod, GraalHotSpotVMConfig config) {
        super(b, targetMethod);
        this.config = config;
        this.barrierSet = b.getPlatformConfigurationProvider().getBarrierSet();
    }

    public ValueNode readKlassFromClass(ValueNode clazz) {
        return b.add(ClassGetHubNode.create(clazz, b.getMetaAccess(), b.getConstantReflection()));
    }

    public ValueNode readLocation(ValueNode base, HotSpotVMConfigField field) {
        return readLocation(base, field, null);
    }

    public ValueNode readLocation(ValueNode base, HotSpotVMConfigField field, GuardingNode guard) {
        return readLocation(base, field.getOffset(config), field.location, field.getStamp(getWordKind()), guard);
    }

    private ValueNode readLocation(ValueNode base, int offset, LocationIdentity location, Stamp stamp, GuardingNode guard) {
        assert StampTool.isPointerNonNull(base) || base.stamp(NodeView.DEFAULT).getStackKind() == getWordKind() : "must be null guarded";
        AddressNode address = makeOffsetAddress(base, asWord(offset));
        ReadNode read = b.add(new ReadNode(address, location, stamp, barrierSet.readBarrierType(location, address, stamp), MemoryOrderMode.PLAIN));
        ValueNode returnValue = ReadNode.canonicalizeRead(read, read.getAddress(), read.getLocationIdentity(), b, NodeView.DEFAULT);
        if (read == returnValue && guard != null) {
            read.setGuard(guard);
        }
        return b.add(returnValue);
    }

    /**
     * An abstraction for fields of {@link GraalHotSpotVMConfig} to be used with
     * {@link #readLocation}. It encapsulates the offset, stamp and LocationIdentity for an internal
     * HotSpot field to ensure they are used consistently in plugins.
     */
    public enum HotSpotVMConfigField {
        KLASS_SUPER_KLASS(config -> config.klassSuperKlassOffset, KLASS_SUPER_KLASS_LOCATION, KlassPointerStamp.klass()),
        CLASS_ARRAY_KLASS(config -> config.arrayKlassOffset, CLASS_ARRAY_KLASS_LOCATION, KlassPointerStamp.klassNonNull()),
        /** JavaThread::_vthread. */
        JAVA_THREAD_CURRENT_THREAD_OBJECT(config -> config.javaThreadVthreadOffset, JAVA_THREAD_CURRENT_THREAD_OBJECT_LOCATION),
        /** JavaThread::_threadObj. */
        JAVA_THREAD_CARRIER_THREAD_OBJECT(config -> config.javaThreadThreadObjOffset, JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION),
        JAVA_THREAD_SCOPED_VALUE_CACHE(config -> config.javaThreadScopedValueCacheOffset, JAVA_THREAD_SCOPED_VALUE_CACHE_LOCATION),
        KLASS_ACCESS_FLAGS(
                        config -> config.klassAccessFlagsOffset,
                        KLASS_ACCESS_FLAGS_LOCATION,
                        StampFactory.forUnsignedInteger(JavaKind.Char.getBitCount())),
        KLASS_MISC_FLAGS(
                        config -> config.klassMiscFlagsOffset,
                        KLASS_MISC_FLAGS_LOCATION,
                        StampFactory.forUnsignedInteger(JavaKind.Byte.getBitCount())),
        HOTSPOT_JAVA_THREAD_CONT_ENTRY(config -> config.contEntryOffset, HOTSPOT_JAVA_THREAD_CONT_ENTRY_LOCATION),
        HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT(config -> config.pinCountOffset, HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT_LOCATION, StampFactory.forKind(JavaKind.Int));

        private final Function<GraalHotSpotVMConfig, Integer> getter;
        private final LocationIdentity location;

        private final Stamp stamp;
        private final boolean isWord;

        /**
         * Creates a field with a valid specified stamp. Word type fields can be created with the
         * constructor below.
         */
        HotSpotVMConfigField(Function<GraalHotSpotVMConfig, Integer> getter, LocationIdentity location, Stamp stamp) {
            this.getter = getter;
            this.location = location;
            this.stamp = stamp;
            assert stamp != null;
            this.isWord = false;
        }

        /**
         * Creates a field with a word stamp. The word stamp is a runtime value so it's not possible
         * to embed it in the enum value.
         */
        HotSpotVMConfigField(Function<GraalHotSpotVMConfig, Integer> getter, LocationIdentity location) {
            this.getter = getter;
            this.location = location;
            this.stamp = null;
            this.isWord = true;
        }

        public int getOffset(GraalHotSpotVMConfig config) {
            int offset = getter.apply(config);
            GraalError.guarantee(offset != -1, "%s is not supported", this);
            return offset;
        }

        public Stamp getStamp(JavaKind wordKind) {
            if (isWord) {
                return StampFactory.forKind(wordKind);
            }
            return stamp;
        }

    }

    /**
     * Read {@code Klass::_access_flags} as int.
     */
    public ValueNode readKlassAccessFlags(ValueNode klass) {
        return ZeroExtendNode.create(readLocation(klass, HotSpotVMConfigField.KLASS_ACCESS_FLAGS), JavaKind.Int.getBitCount(), NodeView.DEFAULT);
    }

    /**
     * Read {@code Klass::_misc_flags} as int.
     */
    public ValueNode readKlassMiscFlags(ValueNode klass) {
        return ZeroExtendNode.create(readLocation(klass, HotSpotVMConfigField.KLASS_MISC_FLAGS), JavaKind.Int.getBitCount(), NodeView.DEFAULT);
    }

    /**
     * Read {@code Klass:_layout_helper}.
     */
    public ValueNode klassLayoutHelper(ValueNode klass) {
        return b.add(KlassLayoutHelperNode.create(config, klass, b.getConstantReflection()));
    }

    /**
     * Read {@code Klass::_super}.
     */
    public ValueNode readKlassSuperKlass(PiNode klassNonNull) {
        return readLocation(klassNonNull, HotSpotVMConfigField.KLASS_SUPER_KLASS);
    }

    public PiNode emitNullReturnGuard(ValueNode pointer, ValueNode returnValue, double probability) {
        GuardingNode nonnullGuard = emitReturnIf(IsNullNode.create(pointer), returnValue, probability);
        return piCast(pointer, nonnullGuard, ((AbstractPointerStamp) pointer.stamp(NodeView.DEFAULT)).asNonNull());
    }

    /**
     * Reads the injected field {@code java.lang.Class.array_klass}.
     */
    public ValueNode loadArrayKlass(ValueNode componentType) {
        return readLocation(componentType, HotSpotVMConfigField.CLASS_ARRAY_KLASS);
    }

    /**
     * Read {@code JavaThread::_threadObj}.
     */
    public ValueNode readJavaThreadThreadObj(CurrentJavaThreadNode javaThread) {
        return readLocation(javaThread, HotSpotVMConfigField.JAVA_THREAD_CARRIER_THREAD_OBJECT);
    }

    /**
     * Read {@code JavaThread::_vthread}.
     */
    public ValueNode readJavaThreadVthread(CurrentJavaThreadNode javaThread) {
        return readLocation(javaThread, HotSpotVMConfigField.JAVA_THREAD_CURRENT_THREAD_OBJECT);
    }

    /**
     * Read {@code JavaThread::_scopedValueCache}.
     */
    public ValueNode readJavaThreadScopedValueCache(CurrentJavaThreadNode javaThread) {
        return readLocation(javaThread, HotSpotVMConfigField.JAVA_THREAD_SCOPED_VALUE_CACHE);
    }
}
