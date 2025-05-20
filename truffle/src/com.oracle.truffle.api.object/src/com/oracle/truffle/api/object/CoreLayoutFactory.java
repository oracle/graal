/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.object;

import java.lang.invoke.MethodHandles.Lookup;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.Assumption;

class CoreLayoutFactory implements LayoutFactory {

    @Override
    public final Property createProperty(Object id, Location location, int flags) {
        return new PropertyImpl(id, location, flags);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    protected void registerLayoutClass(Class<? extends DynamicObject> subclass, Lookup layoutLookup) {
        DefaultLayout.registerLayoutClass(subclass, layoutLookup);
    }

    public LayoutImpl createLayout(Class<? extends DynamicObject> layoutClass, Lookup layoutLookup, int implicitCastFlags) {
        return DefaultLayout.createCoreLayout(layoutClass, layoutLookup, implicitCastFlags);
    }

    @Override
    public final Shape createShape(Class<? extends DynamicObject> layoutClass,
                    int implicitCastFlags,
                    Object dynamicType,
                    Object sharedData,
                    int shapeFlags,
                    EconomicMap<Object, Pair<Object, Integer>> constantProperties,
                    Assumption singleContextAssumption,
                    Lookup layoutLookup) {

        LayoutImpl impl = createLayout(layoutClass, layoutLookup, implicitCastFlags);
        ShapeImpl shape = impl.newShape(dynamicType, sharedData, shapeFlags, singleContextAssumption);

        if (constantProperties != null) {
            var cursor = constantProperties.getEntries();
            while (cursor.advance()) {
                shape = shape.addProperty(createProperty(cursor.getKey(), impl.createAllocator().constantLocation(cursor.getValue().getLeft()), cursor.getValue().getRight()));
            }
        }

        return shape;
    }
}
