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

import org.graalvm.compiler.truffle.pelang.PELangNull;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;

public abstract class PELangPropertyReadCacheNode extends PELangPropertyCacheNode {

    public abstract Object executeRead(DynamicObject receiver, String name);

    @Specialization(limit = "CACHE_LIMIT", //
                    guards = {
                                    "cachedName.equals(name)",
                                    "checkShape(shape, receiver)"
                    }, //
                    assumptions = {
                                    "shape.getValidAssumption()"
                    })
    protected static Object readCached(
                    DynamicObject receiver,
                    @SuppressWarnings("unused") String name,
                    @SuppressWarnings("unused") @Cached("name") String cachedName,
                    @Cached("lookupShape(receiver)") Shape shape,
                    @Cached("lookupLocation(shape, name)") Location location) {
        return (location == null) ? PELangNull.getInstance() : location.get(receiver, shape);
    }

    @TruffleBoundary
    @Specialization(replaces = {"readCached"}, guards = "receiver.getShape().isValid()")
    protected Object readUncached(DynamicObject receiver, String name) {
        Object result = receiver.get(name);
        return (result == null) ? PELangNull.getInstance() : result;
    }

    @Specialization(guards = "!receiver.getShape().isValid()")
    protected Object updateShape(DynamicObject receiver, String name) {
        CompilerDirectives.transferToInterpreter();
        receiver.updateShape();
        return readUncached(receiver, name);
    }

    public static PELangPropertyReadCacheNode createNode() {
        return PELangPropertyReadCacheNodeGen.create();
    }

}
