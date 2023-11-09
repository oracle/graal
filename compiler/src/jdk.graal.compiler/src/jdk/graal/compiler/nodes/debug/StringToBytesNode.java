/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.debug;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code StringToBytesNode} transforms a compilation-time String into a byte array in the
 * compiled code.
 */
@NodeInfo(allowedUsageTypes = InputType.Memory, cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class StringToBytesNode extends FixedWithNextNode implements Lowerable, SingleMemoryKill {

    public static final NodeClass<StringToBytesNode> TYPE = NodeClass.create(StringToBytesNode.class);

    private final String value;

    public StringToBytesNode(String value, Stamp stamp) {
        super(TYPE, stamp);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Byte);
    }
}
