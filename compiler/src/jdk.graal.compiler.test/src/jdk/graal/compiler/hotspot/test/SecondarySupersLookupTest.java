/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import org.junit.Test;

public class SecondarySupersLookupTest extends HotSpotGraalCompilerTest {

    interface I01 {
    }

    interface I02 extends I01 {
    }

    interface I03 extends I02 {
    }

    interface I04 extends I03 {
    }

    interface I05 extends I04 {
    }

    interface I06 extends I05 {
    }

    interface I07 extends I06 {
    }

    interface I08 extends I07 {
    }

    interface I09 extends I08 {
    }

    interface I10 extends I09 {
    }

    interface I11 extends I10 {
    }

    interface I12 extends I11 {
    }

    interface I13 extends I12 {
    }

    interface I14 extends I13 {
    }

    interface I15 extends I14 {
    }

    interface I16 extends I15 {
    }

    interface I17 extends I16 {
    }

    interface I18 extends I17 {
    }

    interface I19 extends I18 {
    }

    interface I20 extends I19 {
    }

    interface I21 extends I20 {
    }

    interface I22 extends I21 {
    }

    interface I23 extends I22 {
    }

    interface I24 extends I23 {
    }

    interface I25 extends I24 {
    }

    interface I26 extends I25 {
    }

    interface I27 extends I26 {
    }

    interface I28 extends I27 {
    }

    interface I29 extends I28 {
    }

    interface I30 extends I29 {
    }

    interface I31 extends I30 {
    }

    interface I32 extends I31 {
    }

    interface I33 extends I32 {
    }

    interface I34 extends I33 {
    }

    interface I35 extends I34 {
    }

    interface I36 extends I35 {
    }

    interface I37 extends I36 {
    }

    interface I38 extends I37 {
    }

    interface I39 extends I38 {
    }

    interface I40 extends I39 {
    }

    interface I41 extends I40 {
    }

    interface I42 extends I41 {
    }

    interface I43 extends I42 {
    }

    interface I44 extends I43 {
    }

    interface I45 extends I44 {
    }

    interface I46 extends I45 {
    }

    interface I47 extends I46 {
    }

    interface I48 extends I47 {
    }

    interface I49 extends I48 {
    }

    interface I50 extends I49 {
    }

    interface I51 extends I50 {
    }

    interface I52 extends I51 {
    }

    interface I53 extends I52 {
    }

    interface I54 extends I53 {
    }

    interface I55 extends I54 {
    }

    interface I56 extends I55 {
    }

    interface I57 extends I56 {
    }

    interface I58 extends I57 {
    }

    interface I59 extends I58 {
    }

    interface I60 extends I59 {
    }

    interface I61 extends I60 {
    }

    interface I62 extends I61 {
    }

    interface I63 extends I62 {
    }

    interface I64 extends I63 {
    }

    final Object obj01 = new I01() {
    };
    final Object obj08 = new I08() {
    };
    final Object obj34 = new I34() {
    };
    final Object obj60 = new I60() {
    };
    final Object obj64 = new I64() {
    };

    public static boolean instanceOfI01(Object o) {
        return o instanceof I01;
    }

    @Test
    public void testHashHitMiss() {
        // hash miss
        test("instanceOfI01", new Object());
        // hash hit
        test("instanceOfI01", obj01);
    }

    public static boolean instanceOfI08(Object o) {
        return o instanceof I08;
    }

    public static boolean instanceOfI34(Object o) {
        return o instanceof I34;
    }

    public static boolean instanceOfI60(Object o) {
        return o instanceof I60;
    }

    @Test
    public void testHashCollision() {
        // hash collision, call into slow path, more than 64 secondary supers
        test("instanceOfI01", obj64);
        // I08, I34 and I60 have the same hash value because
        // the hashing algorithm depends on the type name.
        // hash collision, call into slow path, next slot hit
        test("instanceOfI08", obj34);
        // hash collision, next slot empty
        test("instanceOfI34", obj08);
        // hash collision, call into slow path, next slot miss
        test("instanceOfI60", obj34);

        // other tests for completion
        test("instanceOfI08", obj60);
        test("instanceOfI34", obj60);
        test("instanceOfI60", obj08);
    }

    public static boolean instanceOfI08Array(Object o) {
        return o instanceof I08[];
    }

    public static boolean instanceOfI34Array(Object o) {
        return o instanceof I08[];
    }

    public static boolean instanceOfI60Array(Object o) {
        return o instanceof I08[];
    }

    @Test
    public void testInterfaceArray() {
        Object i08 = new I08[0];
        test("instanceOfI08Array", i08);
        test("instanceOfI34Array", i08);
        test("instanceOfI60Array", i08);

        Object i60 = new I60[0];
        test("instanceOfI08Array", i60);
        test("instanceOfI34Array", i60);
        test("instanceOfI60Array", i60);
    }
}
