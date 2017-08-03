/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.substitutions;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.impl.AbstractAssumption;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.truffle.FrameWithBoxing;
import org.graalvm.compiler.truffle.FrameWithoutBoxing;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleUseFrameWithoutBoxing;

public class KnownTruffleFields {

    public final ResolvedJavaType classFrameClass;
    public final ResolvedJavaType classFrameDescriptor;
    public final ResolvedJavaType classMethodHandle;

    public final ResolvedJavaField fieldFrameDescriptorDefaultValue;
    public final ResolvedJavaField fieldFrameDescriptorVersion;
    public final ResolvedJavaField fieldFrameDescriptorMaterializeCalled;
    public final ResolvedJavaField fieldFrameDescriptorSlots;

    public final ResolvedJavaField fieldArrayListElementData;

    public final ResolvedJavaField fieldFrameSlotKind;
    public final ResolvedJavaField fieldFrameSlotIndex;

    public final ResolvedJavaField fieldFrameSlotKindTag;

    public final ResolvedJavaField fieldOptimizedAssumptionIsValid;

    public KnownTruffleFields(MetaAccessProvider metaAccess) {
        try {
            final Class<?> frameClass = TruffleCompilerOptions.getValue(TruffleUseFrameWithoutBoxing) ? FrameWithoutBoxing.class : FrameWithBoxing.class;
            classFrameClass = metaAccess.lookupJavaType(frameClass);

            classFrameDescriptor = metaAccess.lookupJavaType(FrameDescriptor.class);
            classMethodHandle = metaAccess.lookupJavaType(MethodHandle.class);

            fieldFrameDescriptorDefaultValue = metaAccess.lookupJavaField(FrameDescriptor.class.getDeclaredField("defaultValue"));
            fieldFrameDescriptorVersion = metaAccess.lookupJavaField(FrameDescriptor.class.getDeclaredField("version"));
            fieldFrameDescriptorMaterializeCalled = metaAccess.lookupJavaField(FrameDescriptor.class.getDeclaredField("materializeCalled"));
            fieldFrameDescriptorSlots = metaAccess.lookupJavaField(FrameDescriptor.class.getDeclaredField("slots"));

            fieldArrayListElementData = metaAccess.lookupJavaField(ArrayList.class.getDeclaredField("elementData"));
            fieldFrameSlotKind = metaAccess.lookupJavaField(FrameSlot.class.getDeclaredField("kind"));
            fieldFrameSlotIndex = metaAccess.lookupJavaField(FrameSlot.class.getDeclaredField("index"));
            fieldFrameSlotKindTag = metaAccess.lookupJavaField(FrameSlotKind.class.getDeclaredField("tag"));

            fieldOptimizedAssumptionIsValid = metaAccess.lookupJavaField(AbstractAssumption.class.getDeclaredField("isValid"));
        } catch (NoSuchFieldException ex) {
            throw GraalError.shouldNotReachHere(ex);
        }
    }
}
