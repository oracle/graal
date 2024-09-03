/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;

/**
 * Base class for nodes that represent large intrinsic methods without side effects, such as
 * {@link ArrayEqualsNode}. These methods are compiled as stubs and later called via foreign call.
 */
@NodeInfo
public abstract class PureFunctionStubIntrinsicNode extends FixedWithNextNode implements MemoryAccess, IntrinsicMethodNodeInterface {

    public static final NodeClass<PureFunctionStubIntrinsicNode> TYPE = NodeClass.create(PureFunctionStubIntrinsicNode.class);

    protected final EnumSet<?> runtimeCheckedCPUFeatures;
    protected final LocationIdentity locationIdentity;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public PureFunctionStubIntrinsicNode(NodeClass<? extends PureFunctionStubIntrinsicNode> c, Stamp stamp, EnumSet<?> runtimeCheckedCPUFeatures, LocationIdentity locationIdentity) {
        super(c, stamp);
        this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
        this.locationIdentity = locationIdentity;
    }

    /**
     * On SVM, we AOT-compile additional stub versions with more modern CPU flags enabled. We can
     * emit foreign calls to these variants in JIT code when these flags are available.
     */
    public EnumSet<?> getRuntimeCheckedCPUFeatures() {
        return runtimeCheckedCPUFeatures;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }
}
