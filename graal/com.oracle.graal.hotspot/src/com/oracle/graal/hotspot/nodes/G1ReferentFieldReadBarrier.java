/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

/**
 * The {@code G1ReferentFieldReadBarrier} is added when a read access is performed to the referent
 * field of a {@link java.lang.ref.Reference} object (through a {@code LoadFieldNode} or an
 * {@code UnsafeLoadNode}). The return value of the read is passed to the snippet implementing the
 * read barrier and consequently is added to the SATB queue if the concurrent marker is enabled.
 */
@NodeInfo
public class G1ReferentFieldReadBarrier extends WriteBarrier {

    protected final boolean doLoad;

    public static G1ReferentFieldReadBarrier create(ValueNode object, ValueNode expectedObject, LocationNode location, boolean doLoad) {
        return USE_GENERATED_NODES ? new G1ReferentFieldReadBarrierGen(object, expectedObject, location, doLoad) : new G1ReferentFieldReadBarrier(object, expectedObject, location, doLoad);
    }

    protected G1ReferentFieldReadBarrier(ValueNode object, ValueNode expectedObject, LocationNode location, boolean doLoad) {
        super(object, expectedObject, location, true);
        this.doLoad = doLoad;
    }

    public ValueNode getExpectedObject() {
        return getValue();
    }

    public boolean doLoad() {
        return doLoad;
    }
}
