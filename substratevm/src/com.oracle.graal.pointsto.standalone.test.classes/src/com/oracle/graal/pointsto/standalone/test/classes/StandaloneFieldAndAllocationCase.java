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

package com.oracle.graal.pointsto.standalone.test.classes;

/**
 * Compact field-flow fixture used to validate allocation, instance-field propagation, nullability,
 * and result precision in standalone analysis.
 */
public class StandaloneFieldAndAllocationCase {

    /**
     * Entry point that drives two independent holder instances through the same helper methods so
     * the analysis must merge their observed field states.
     */
    public static void main(String[] args) {
        Holder first = new Holder();
        Holder second = new Holder();

        publish(storeAndReturn(new A(), first, new UnusedA(), false));
        publish(storeAndReturn(new B(), second, new UnusedB(), true));

        publishNullable(readNullable(first));
        publishNullable(readNullable(second));
    }

    /**
     * Stores the incoming value into the holder and optionally clears the nullable field so the
     * merged field state contains both an exact object set and a nullable path.
     */
    public static Value storeAndReturn(Value value, Holder holder, @SuppressWarnings("unused") Object unusedValue, boolean clearNullable) {
        holder.exactField = value;
        holder.nullableField = clearNullable ? null : value;
        return holder.exactField;
    }

    /**
     * Loads the nullable field to keep the field-read path reachable.
     */
    public static Value readNullable(Holder holder) {
        return holder.nullableField;
    }

    private static void publish(@SuppressWarnings("unused") Value value) {
    }

    private static void publishNullable(@SuppressWarnings("unused") Value value) {
    }

    /**
     * Common supertype for the stored values.
     */
    public interface Value {
    }

    /**
     * Holder whose instance fields are populated through the shared helper methods.
     */
    public static final class Holder {
        public Value exactField;
        public Value nullableField;
    }

    /**
     * First concrete value type.
     */
    public static final class A implements Value {
    }

    /**
     * Second concrete value type.
     */
    public static final class B implements Value {
    }

    /**
     * Marker type used only to verify that an unused parameter does not collect type flow.
     */
    public static final class UnusedA {
    }

    /**
     * Second marker type used only to verify that an unused parameter does not collect type flow.
     */
    public static final class UnusedB {
    }
}
