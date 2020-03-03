/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public abstract class AbstractBoxNode extends FixedWithNextNode implements Virtualizable, Lowerable, Canonicalizable.Unary<ValueNode>, MemoryAccess {
    public static final NodeClass<AbstractBoxNode> TYPE = NodeClass.create(AbstractBoxNode.class);

    @Input protected ValueNode value;
    protected final JavaKind boxingKind;
    protected final LocationIdentity accessedLocation;

    public AbstractBoxNode(NodeClass<? extends AbstractBoxNode> c, ValueNode value, JavaKind boxingKind, Stamp s, LocationIdentity accessedLocation) {
        super(c, s);
        this.value = value;
        this.boxingKind = boxingKind;
        this.accessedLocation = accessedLocation;
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
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(newlla));
        lastLocationAccess = newlla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return accessedLocation;
    }

    public static FieldLocationIdentity createLocationIdentity(MetaAccessProvider metaAccess, JavaKind boxingKind) {
        try {
            switch (boxingKind) {
                case Byte:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Byte.class.getDeclaredField("value")));
                case Boolean:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("value")));
                case Short:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Short.class.getDeclaredField("value")));
                case Char:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Character.class.getDeclaredField("value")));
                case Float:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Float.class.getDeclaredField("value")));
                case Int:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Integer.class.getDeclaredField("value")));
                case Long:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Long.class.getDeclaredField("value")));
                case Double:
                    return new FieldLocationIdentity(metaAccess.lookupJavaField(Double.class.getDeclaredField("value")));
                default:
                    throw GraalError.unimplemented();
            }
        } catch (NoSuchFieldException | SecurityException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

}
