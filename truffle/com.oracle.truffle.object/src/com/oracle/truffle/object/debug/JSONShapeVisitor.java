/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.debug;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.truffle.api.object.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.oracle.truffle.object.*;
import com.oracle.truffle.object.Transition.PropertyTransition;

public class JSONShapeVisitor extends DebugShapeVisitor<JSONObjectBuilder> {
    @Override
    public JSONObjectBuilder visitShape(Shape shape, Map<? extends Transition, ? extends Shape> transitions) {
        JSONObjectBuilder sb = JSONHelper.object();
        JSONArrayBuilder transitionarray = JSONHelper.array();
        for (Entry<? extends Transition, ? extends Shape> entry : transitions.entrySet()) {
            transitionarray.add(JSONHelper.object().add("transition", dumpTransition(entry.getKey())).add("successor", getId(entry.getValue())));
        }
        JSONArrayBuilder propertiesarray = JSONHelper.array();
        for (Property p : shape.getPropertyList()) {
            propertiesarray.add(dumpProperty(p));
        }
        sb.add("id", getId(shape));
        sb.add("properties", propertiesarray);
        sb.add("transitions", transitionarray);
        sb.add("predecessor", shape.getParent() != null ? getId(shape.getParent()) : null);
        sb.add("valid", shape.isValid());
        return sb;
    }

    public JSONObjectBuilder dumpProperty(Property property) {
        return JSONHelper.object().add("id", property.getKey().toString()).add("location", dumpLocation(property.getLocation())).add("flags", property.getFlags());
    }

    public JSONObjectBuilder dumpTransition(Transition transition) {
        JSONObjectBuilder sb = JSONHelper.object().add("type", transition.getShortName());
        if (transition instanceof PropertyTransition) {
            sb.add("property", dumpProperty(((PropertyTransition) transition).getProperty()));
        }
        return sb;
    }

    public JSONObjectBuilder dumpLocation(Location location) {
        JSONObjectBuilder obj = JSONHelper.object();
        obj.add("type", (location instanceof TypedLocation ? ((TypedLocation) location).getType() : Object.class).getName());
        // if (location instanceof Locations.FieldLocation) {
        // obj.add("offset", ((Locations.FieldLocation) location).getOffset());
        // }
        // if (location instanceof Locations.ArrayLocation) {
        // obj.add("index", ((Locations.ArrayLocation) location).getIndex());
        // }
        if (location instanceof Locations.ValueLocation) {
            obj.add("value", String.valueOf(((Locations.ValueLocation) location).get(null, false)));
        }
        return obj;
    }
}
