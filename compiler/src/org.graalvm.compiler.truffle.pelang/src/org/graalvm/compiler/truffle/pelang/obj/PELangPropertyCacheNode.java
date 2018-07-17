/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.obj;

import org.graalvm.compiler.truffle.pelang.PELangState;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

public abstract class PELangPropertyCacheNode extends Node {

    protected static final int CACHE_LIMIT = 3;

    protected static boolean checkShape(Shape shape, DynamicObject receiver) {
        return shape != null && shape.check(receiver);
    }

    protected static Shape lookupShape(DynamicObject receiver) {
        CompilerAsserts.neverPartOfCompilation();
        assert PELangState.isPELangObject(receiver);
        return receiver.getShape();
    }

    protected static Location lookupLocation(Shape shape, String name) {
        // Initialization of cached values always happens in a slow path
        CompilerAsserts.neverPartOfCompilation();
        Property property = shape.getProperty(name);
        return (property == null) ? null : property.getLocation();
    }

    protected static Location lookupLocation(Shape shape, String name, Object value) {
        Location location = lookupLocation(shape, name);
        return (location == null || !location.canSet(value)) ? null : location;
    }

    protected static Shape defineProperty(Shape oldShape, String name, Object value) {
        return oldShape.defineProperty(name, value, 0);
    }

}
