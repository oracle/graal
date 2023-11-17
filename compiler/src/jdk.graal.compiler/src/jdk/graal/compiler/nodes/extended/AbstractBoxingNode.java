/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public abstract class AbstractBoxingNode extends FixedWithNextNode implements Virtualizable, Lowerable, Canonicalizable.Unary<ValueNode>, MemoryAccess {
    public static final NodeClass<AbstractBoxingNode> TYPE = NodeClass.create(AbstractBoxingNode.class);

    @Input protected ValueNode value;
    protected final JavaKind boxingKind;
    protected final LocationIdentity accessedLocation;

    public AbstractBoxingNode(NodeClass<? extends AbstractBoxingNode> c, ValueNode value, JavaKind boxingKind, Stamp s, LocationIdentity accessedLocation) {
        super(c, s);
        this.value = value;
        this.boxingKind = boxingKind;
        this.accessedLocation = accessedLocation;
    }

    public static ResolvedJavaField getValueField(ResolvedJavaType resultType) {
        for (ResolvedJavaField f : resultType.getInstanceFields(false)) {
            if (f.getName().equals("value")) {
                return f;
            }
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(resultType); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    public JavaKind getBoxingKind() {
        return boxingKind;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    @Override
    public void setLastLocationAccess(MemoryKill newlla) {
        updateUsagesInterface(lastLocationAccess, newlla);
        lastLocationAccess = newlla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return accessedLocation;
    }
}
