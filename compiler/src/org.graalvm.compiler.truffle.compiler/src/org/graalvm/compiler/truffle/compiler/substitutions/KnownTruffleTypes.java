/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.substitutions;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class KnownTruffleTypes extends AbstractKnownTruffleTypes {

    public final ResolvedJavaType classFrameClass = lookupType("org.graalvm.compiler.truffle.runtime.FrameWithoutBoxing");
    public final ResolvedJavaType classFrameDescriptor = lookupType("com.oracle.truffle.api.frame.FrameDescriptor");
    public final ResolvedJavaType classFrameSlot = lookupType("com.oracle.truffle.api.frame.FrameSlot");
    public final ResolvedJavaType classFrameSlotKind = lookupType("com.oracle.truffle.api.frame.FrameSlotKind");
    public final ResolvedJavaType classExactMath = lookupType("com.oracle.truffle.api.ExactMath");
    public final ResolvedJavaType classArrayUtils = lookupType("com.oracle.truffle.api.ArrayUtils");
    public final ResolvedJavaType classMethodHandle = lookupType(MethodHandle.class);

    public final ResolvedJavaField fieldFrameDescriptorDefaultValue = findField(classFrameDescriptor, "defaultValue");
    public final ResolvedJavaField fieldFrameDescriptorVersion = findField(classFrameDescriptor, "version");
    public final ResolvedJavaField fieldFrameDescriptorMaterializeCalled = findField(classFrameDescriptor, "materializeCalled");
    public final ResolvedJavaField fieldFrameDescriptorSlots = findField(classFrameDescriptor, "slots");
    public final ResolvedJavaField fieldFrameDescriptorSize = findField(classFrameDescriptor, "size");

    public final ResolvedJavaField fieldArrayListElementData = findField(lookupType(ArrayList.class), "elementData");

    public final ResolvedJavaField fieldFrameSlotKind = findField(classFrameSlot, "kind");
    public final ResolvedJavaField fieldFrameSlotIndex = findField(classFrameSlot, "index");

    public final ResolvedJavaField fieldFrameSlotKindTag = findField(classFrameSlotKind, "tag");

    public final ResolvedJavaField fieldOptimizedAssumptionIsValid = findField(lookupType("com.oracle.truffle.api.impl.AbstractAssumption"), "isValid");

    public final ResolvedJavaField fieldStringValue = findField(lookupType(String.class), "value");

    public KnownTruffleTypes(MetaAccessProvider metaAccess) {
        super(metaAccess);
    }
}
