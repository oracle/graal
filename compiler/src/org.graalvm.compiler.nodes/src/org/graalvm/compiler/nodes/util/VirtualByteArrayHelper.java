/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.nodes.util;

import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public final class VirtualByteArrayHelper {
    private VirtualByteArrayHelper() {
    }

    private static boolean isVirtualArray(VirtualObjectNode virtual) {
        return virtual instanceof VirtualArrayNode;
    }

    public static boolean isVirtualByteArray(VirtualObjectNode virtual) {
        return isVirtualArray(virtual) && virtual.entryCount() > 0 && virtual.entryKind(0) == JavaKind.Byte;
    }

    public static boolean isVirtualByteArrayAccess(VirtualObjectNode virtual, JavaKind accessKind) {
        return accessKind.isPrimitive() && isVirtualByteArray(virtual);
    }

    public static boolean isIllegalConstant(ValueNode node) {
        return node.isConstant() && node.asConstant().equals(JavaConstant.forIllegal());
    }

    public static ValueNode virtualizeRead(ValueNode entry, JavaKind accessKind, Stamp targetStamp) {
        assert !isIllegalConstant(entry);
        assert targetStamp.getStackKind().isPrimitive();
        int entryBits = accessKind.getBitCount();
        int targetBits = PrimitiveStamp.getBits(targetStamp);
        assert entryBits <= targetBits;
        return entry;
    }

    /**
     * Checks that a read in a virtual object is a candidate for byte array virtualization.
     *
     * Virtualizing reads in byte arrays can happen iff all of these hold true:
     * <li>The virtualized object is a virtualized byte array
     * <li>Both the virtualized entry and the access kind are primitives
     * <li>The number of bytes actually occupied by the entry is equal to the number of bytes of the
     * access kind
     */
    public static boolean canVirtualizeRead(VirtualObjectNode virtual, ValueNode entry, int index, JavaKind accessKind, VirtualizerTool tool) {
        return !isIllegalConstant(entry) && entry.getStackKind() == accessKind.getStackKind() &&
                        isVirtualByteArrayAccess(virtual, accessKind) &&
                        accessKind.getByteCount() == entryByteCount((VirtualArrayNode) virtual, index, tool);
    }

    /**
     * Returns the number of bytes that the entry at a given index actually occupies.
     */
    public static int entryByteCount(VirtualArrayNode virtual, int index, VirtualizerTool tool) {
        int i = index + 1;
        while (i < virtual.entryCount() && VirtualByteArrayHelper.isIllegalConstant(tool.getEntry(virtual, i))) {
            i++;
        }
        return (i - index);
    }

}
