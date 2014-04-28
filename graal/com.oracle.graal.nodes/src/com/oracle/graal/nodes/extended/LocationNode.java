/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A location for a memory access in terms of the kind of value accessed and how to access it. All
 * locations have the form [base + location], where base is a node and location is defined by
 * subclasses of the {@link LocationNode}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Association})
public abstract class LocationNode extends FloatingNode implements LIRLowerable, ValueNumberable {

    /**
     * Marker interface for locations in snippets.
     */
    public interface Location {
    }

    protected LocationNode(Stamp stamp) {
        super(stamp);
    }

    /**
     * Returns the kind of the accessed memory value.
     */
    public abstract Kind getValueKind();

    /**
     * Returns the identity of the accessed memory location.
     */
    public abstract LocationIdentity getLocationIdentity();

    @Override
    public final void generate(NodeLIRBuilderTool generator) {
        // nothing to do...
    }

    public abstract Value generateAddress(NodeMappableLIRBuilder builder, LIRGeneratorTool gen, Value base);
}
