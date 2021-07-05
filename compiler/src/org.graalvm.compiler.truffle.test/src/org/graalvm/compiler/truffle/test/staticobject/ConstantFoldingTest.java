/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.staticobject;

import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticPropertyKind;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Test;

public class ConstantFoldingTest extends PartialEvaluationTest {
    static class FieldBasedStorage {
        final int finalProperty = 42;
        int property;
    }

    @Test
    public void simplePropertyAccesses() {
        // Field-based storage
        try (StaticObjectTestEnvironment te = new StaticObjectTestEnvironment(false)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty finalProperty = new DefaultStaticProperty("finalProperty", StaticPropertyKind.Int, true);
            StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
            builder.property(finalProperty).property(property);
            Object staticObject = builder.build().getFactory().create();

            FieldBasedStorage fbs = new FieldBasedStorage();

            // Property set
            assertPartialEvalEquals(toRootNode((f) -> fbs.property = 42), toRootNode((f) -> {
                finalProperty.setInt(staticObject, 42);
                return null;
            }), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> fbs.property = 42), toRootNode((f) -> {
                property.setInt(staticObject, 42);
                return null;
            }), new Object[0]);

            finalProperty.setInt(staticObject, 42);
            // Property get
            assertPartialEvalEquals(toRootNode((f) -> 42), toRootNode((f) -> finalProperty.getInt(staticObject)), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> fbs.finalProperty), toRootNode((f) -> finalProperty.getInt(staticObject)), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> fbs.property), toRootNode((f) -> property.getInt(staticObject)), new Object[0]);
        }
    }

    @Test
    public void propertyAccessesInHierarchy() {
        // Field-based storage
        try (StaticObjectTestEnvironment te = new StaticObjectTestEnvironment(false)) {
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s1p1 = new DefaultStaticProperty("property", StaticPropertyKind.Int, true);
            b1.property(s1p1);
            StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s2p1 = new DefaultStaticProperty("property", StaticPropertyKind.Int, true);
            b2.property(s2p1);
            StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
            Object o2 = s2.getFactory().create();

            s1p1.setInt(o2, 24);
            s2p1.setInt(o2, 42);

            assertPartialEvalEquals(toRootNode((f) -> 24), toRootNode((f) -> s1p1.getInt(o2)), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> 42), toRootNode((f) -> s2p1.getInt(o2)), new Object[0]);
        }
    }
}
