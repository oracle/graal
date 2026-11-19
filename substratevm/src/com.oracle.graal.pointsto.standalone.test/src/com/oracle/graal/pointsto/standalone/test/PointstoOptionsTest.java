/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test;

import static com.oracle.graal.pointsto.api.PointstoOptions.TrackPrimitiveValues;
import static com.oracle.graal.pointsto.api.PointstoOptions.UsePredicates;
import static org.junit.Assert.assertEquals;

import org.graalvm.collections.EconomicMap;
import org.junit.Test;

import jdk.graal.compiler.options.OptionKey;

public class PointstoOptionsTest {

    @Test
    public void testPrimitiveTrackingAndPredicateOptionOrder() {
        EconomicMap<OptionKey<?>, Object> values = EconomicMap.create();

        TrackPrimitiveValues.update(values, false);
        assertEquals(false, values.get(TrackPrimitiveValues));
        assertEquals(false, values.get(UsePredicates));

        UsePredicates.update(values, true);
        assertEquals(true, values.get(TrackPrimitiveValues));
        assertEquals(true, values.get(UsePredicates));

        TrackPrimitiveValues.update(values, false);
        assertEquals(false, values.get(TrackPrimitiveValues));
        assertEquals(false, values.get(UsePredicates));

        TrackPrimitiveValues.update(values, true);
        assertEquals(true, values.get(TrackPrimitiveValues));
        assertEquals(false, values.get(UsePredicates));
    }

    @Test
    public void testDisablingPredicatesKeepsPrimitiveTracking() {
        EconomicMap<OptionKey<?>, Object> values = EconomicMap.create();

        UsePredicates.update(values, false);
        assertEquals(true, values.get(TrackPrimitiveValues, true));
        assertEquals(false, values.get(UsePredicates));
    }
}
