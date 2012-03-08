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

import com.oracle.max.cri.ci.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public class LocationNode extends FloatingNode implements LIRLowerable, ValueNumberable {

    @Data private int displacement;
    @Data private CiKind valueKind;
    @Data private Object locationIdentity;

    public static final Object ANY_LOCATION = new Object() {
        @Override
        public String toString() {
            return "ANY_LOCATION";
        }
    };
    public static final Object FINAL_LOCATION = new Object();

    public static Object getArrayLocation(CiKind elementKind) {
        return elementKind;
    }

    public int displacement() {
        return displacement;
    }

    public static LocationNode create(Object identity, CiKind kind, int displacement, Graph graph) {
        return graph.unique(new LocationNode(identity, kind, displacement));
    }

    protected LocationNode(Object identity, CiKind kind, int displacement) {
        super(StampFactory.illegal());
        assert kind != CiKind.Illegal && kind != CiKind.Void;
        this.displacement = displacement;
        this.valueKind = kind;
        this.locationIdentity = identity;
    }

    public CiKind getValueKind() {
        return valueKind;
    }

    public Object locationIdentity() {
        return locationIdentity;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        // nothing to do...
    }
}
