/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * This node is used by the {@link NewInstanceSnippets} to give a formatted new instance its exact type.
 */
public final class CastFromHub extends FloatingNode implements Canonicalizable {

    @Input private ValueNode object;
    @Input private ValueNode hub;

    public ValueNode object() {
        return object;
    }

    public CastFromHub(ValueNode object, ValueNode hubObject) {
        // TODO: the non-nullness should really be derived from 'object' but until
        // control flow sensitive type analysis is implemented, the object coming
        // from the TLAB fast path is not non-null
        super(StampFactory.objectNonNull());
        this.object = object;
        this.hub = hubObject;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (hub.isConstant()) {
            ResolvedJavaType type = ((HotSpotKlassOop) this.hub.asConstant().asObject()).type;
            return graph().unique(new UnsafeCastNode(object, type, true, true));
        }
        return this;
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <T> T castFromHub(Object object, Object hub) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }
}
