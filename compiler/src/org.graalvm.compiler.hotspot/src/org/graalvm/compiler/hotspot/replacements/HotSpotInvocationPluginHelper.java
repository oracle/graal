/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.ARRAY_KLASS_COMPONENT_MIRROR;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_ARRAY_KLASS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_OOP_HANDLE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_CURRENT_THREAD_OBJECT_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_OSTHREAD_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_MODIFIER_FLAGS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_SUPER_KLASS_LOCATION;
import static org.graalvm.word.LocationIdentity.any;

import java.util.function.Function;

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.InvocationPluginHelper;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A helper class for HotSpot specific invocation plugins. In particular it adds helpers for
 * correctly performing reads of HotSpot specific fields.
 */
public class HotSpotInvocationPluginHelper extends InvocationPluginHelper {

    private final GraalHotSpotVMConfig config;

    public HotSpotInvocationPluginHelper(GraphBuilderContext b, ResolvedJavaMethod targetMethod, GraalHotSpotVMConfig config) {
        super(b, targetMethod);
        this.config = config;
    }

    private Stamp getClassStamp(boolean nonNull) {
        ResolvedJavaType toType = b.getMetaAccess().lookupJavaType(Class.class);
        return StampFactory.object(TypeReference.createExactTrusted(toType), nonNull);
    }

    public ValueNode readKlassFromClass(ValueNode clazz) {
        return b.add(ClassGetHubNode.create(clazz, b.getMetaAccess(), b.getConstantReflection()));
    }

    private ValueNode readLocation(ValueNode base, HotSpotVMConfigField field) {
        return readLocation(base, field.getOffset(config), field.location, field.getStamp(getWordKind()), null);
    }

    private ValueNode readLocation(ValueNode base, HotSpotVMConfigField field, Stamp stamp) {
        assert field.getStamp(getWordKind()) == null;
        return readLocation(base, field.getOffset(config), field.location, stamp, null);
    }

    private ValueNode readLocation(ValueNode base, int offset, LocationIdentity location, Stamp stamp, GuardingNode guard) {
        assert StampTool.isPointerNonNull(base) || base.stamp(NodeView.DEFAULT).getStackKind() == getWordKind() : "must be null guarded";
        AddressNode address = makeOffsetAddress(base, asWord(offset));
        ReadNode value = b.add(new ReadNode(address, location, stamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        ValueNode returnValue = ReadNode.canonicalizeRead(value, value.getAddress(), value.getLocationIdentity(), b, NodeView.DEFAULT);
        if (value != returnValue) {
            // We could clean up the dead nodes here
        } else {
            if (guard != null) {
                value.setGuard(guard);
            }
        }
        return b.add(returnValue);
    }

    /**
     * An abstraction for fields of {@link GraalHotSpotVMConfig}.
     */
    enum HotSpotVMConfigField {
        KLASS_MODIFIER_FLAGS_OFFSET(config -> config.klassModifierFlagsOffset, KLASS_MODIFIER_FLAGS_LOCATION, StampFactory.forKind(JavaKind.Int)),
        KLASS_SUPER_KLASS_OFFSET(config -> config.klassSuperKlassOffset, KLASS_SUPER_KLASS_LOCATION, KlassPointerStamp.klass()),
        CLASS_ARRAY_KLASS_OFFSET(config -> config.arrayKlassOffset, CLASS_ARRAY_KLASS_LOCATION, KlassPointerStamp.klassNonNull()),
        OS_THREAD_INTERRUPTED_OFFSET(config -> config.osThreadInterruptedOffset, any(), StampFactory.forKind(JavaKind.Int)),
        JAVA_THREAD_OSTHREAD_OFFSET(config -> config.osThreadOffset, JAVA_THREAD_OSTHREAD_LOCATION),
        JAVA_THREAD_THREAD_OBJECT(config -> config.threadCurrentThreadObjectOffset, JAVA_THREAD_CURRENT_THREAD_OBJECT_LOCATION, null),
        KLASS_ACCESS_FLAGS_OFFSET(config -> config.klassAccessFlagsOffset, KLASS_ACCESS_FLAGS_LOCATION, StampFactory.forKind(JavaKind.Int)),
        HOTSPOT_OOP_HANDLE_VALUE(config -> 0, HOTSPOT_OOP_HANDLE_LOCATION, StampFactory.forKind(JavaKind.Object));

        private final Function<GraalHotSpotVMConfig, Integer> getter;
        private final LocationIdentity location;

        private final Stamp stamp;
        private final boolean isWord;

        HotSpotVMConfigField(Function<GraalHotSpotVMConfig, Integer> getter, LocationIdentity location, Stamp stamp) {
            this.getter = getter;
            this.location = location;
            this.stamp = stamp;
            this.isWord = false;
        }

        HotSpotVMConfigField(Function<GraalHotSpotVMConfig, Integer> getter, LocationIdentity location) {
            this.getter = getter;
            this.location = location;
            this.stamp = null;
            this.isWord = true;
        }

        public int getOffset(GraalHotSpotVMConfig config) {
            return getter.apply(config);
        }

        public Stamp getStamp(JavaKind wordKind) {
            if (isWord) {
                return StampFactory.forKind(wordKind);
            }
            return stamp;
        }

    }

    /**
     * Read {@code Klass::_modifier_flags}.
     */
    public ValueNode readKlassModifierFlags(ValueNode klass) {
        return readLocation(klass, HotSpotVMConfigField.KLASS_MODIFIER_FLAGS_OFFSET);
    }

    /**
     * Read {@code Klass::_access_flags}.
     */
    public ValueNode readKlassAccessFlags(ValueNode klass) {
        return readLocation(klass, HotSpotVMConfigField.KLASS_ACCESS_FLAGS_OFFSET);
    }

    /**
     * Read {@code Klass:_layout_helper}.
     */
    public ValueNode klassLayoutHelper(ValueNode klass) {
        return b.add(KlassLayoutHelperNode.create(config, klass, b.getConstantReflection(), b.getMetaAccess()));
    }

    /**
     * Read {@code ArrayKlass::_component_mirror}.
     */
    public ValueNode readArrayKlassComponentMirror(ValueNode klass, GuardingNode guard) {
        int offset = config.getFieldOffset("ArrayKlass::_component_mirror", Integer.class, "oop");
        Stamp stamp = getClassStamp(true);
        return readLocation(klass, offset, ARRAY_KLASS_COMPONENT_MIRROR, stamp, guard);
    }

    /**
     * Read {@code Klass::_super}.
     */
    public ValueNode readKlassSuperKlass(PiNode klassNonNull) {
        return readLocation(klassNonNull, HotSpotVMConfigField.KLASS_SUPER_KLASS_OFFSET);
    }

    public PiNode emitNullReturnGuard(ValueNode klass, ValueNode returnValue, double probability) {
        GuardingNode nonnullGuard = emitReturnIf(IsNullNode.create(klass), returnValue, probability);
        return piCast(klass, nonnullGuard, KlassPointerStamp.klassNonNull());
    }

    /**
     * Read the injected field {@code java.lang.Class.array_klass}.
     */
    public ValueNode loadArrayKlass(ValueNode componentType) {
        return readLocation(componentType, HotSpotVMConfigField.CLASS_ARRAY_KLASS_OFFSET);
    }

    public ValueNode readCurrentThreadObject() {
        return readCurrentThreadObject(b.add(new CurrentJavaThreadNode(getWordKind())));
    }

    public ValueNode readCurrentThreadObject(CurrentJavaThreadNode thread) {
        // JavaThread::_threadObj is never compressed
        ObjectStamp threadStamp = StampFactory.objectNonNull(TypeReference.create(b.getAssumptions(), b.getMetaAccess().lookupJavaType(Thread.class)));
        Stamp fieldStamp = config.threadObjectFieldIsHandle ? StampFactory.forKind(getWordKind()) : threadStamp;
        ValueNode value = readLocation(thread, HotSpotVMConfigField.JAVA_THREAD_THREAD_OBJECT, fieldStamp);
        if (config.threadObjectFieldIsHandle) {
            // Read the Object from the OopHandle
            ValueNode handleOffset = ConstantNode.forIntegerKind(getWordKind(), 0, b.getGraph());
            AddressNode handleAddress = b.add(new OffsetAddressNode(value, handleOffset));
            value = b.add(new ReadNode(handleAddress, HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION, threadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        }
        return value;
    }

    /**
     * Reads {@code JavaThread::_osthread}.
     */
    public ValueNode readOsThread(CurrentJavaThreadNode thread) {
        return readLocation(thread, HotSpotVMConfigField.JAVA_THREAD_OSTHREAD_OFFSET);
    }

    /**
     * Reads {@code OSThread::_interrupted}.
     */
    public ValueNode readOsThreadInterrupted(ValueNode osThread) {
        return readLocation(osThread, HotSpotVMConfigField.OS_THREAD_INTERRUPTED_OFFSET);
    }
}
