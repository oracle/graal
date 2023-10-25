/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.ZBarrierSet;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Specialization of {@link ZBarrierSet} that adds support for read barriers on handle locations.
 */
public class HotSpotZBarrierSet extends ZBarrierSet {
    public HotSpotZBarrierSet(ResolvedJavaField referentField) {
        super(referentField);
    }

    @Override
    protected BarrierType barrierForLocation(BarrierType currentBarrier, LocationIdentity location, JavaKind storageKind) {
        if (location instanceof HotSpotReplacementsUtil.OopHandleLocationIdentity) {
            return BarrierType.READ;
        }
        return super.barrierForLocation(currentBarrier, location, storageKind);
    }

    @Override
    public BarrierType readBarrierType(LocationIdentity location, ValueNode address, Stamp loadStamp) {
        if (location instanceof HotSpotReplacementsUtil.OopHandleLocationIdentity) {
            assert loadStamp instanceof AbstractObjectStamp : loadStamp;
            return BarrierType.READ;
        }
        return super.readBarrierType(location, address, loadStamp);
    }
}
