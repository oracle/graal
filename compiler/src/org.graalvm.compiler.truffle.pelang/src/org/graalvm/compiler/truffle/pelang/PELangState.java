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
package org.graalvm.compiler.truffle.pelang;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;

public final class PELangState {

    private static final Object NULL = new PELangNull();
    private static final EconomicMap<String, Object> GLOBALS = EconomicMap.create();
    private static final Layout LAYOUT = Layout.createLayout();
    private static final Shape EMPTY_SHAPE = LAYOUT.createShape(PELangObjectType.SINGLETON);

    public static Object getNullObject() {
        return NULL;
    }

    public static Object readGlobal(String identifier) {
        return getGlobal(identifier);
    }

    public static Object writeGlobal(String identifier, Object value) {
        GLOBALS.put(identifier, value);
        return value;
    }

    public static long readLongGlobal(String identifier) {
        return (long) getGlobal(identifier);
    }

    public static long writeLongGlobal(String identifier, long value) {
        GLOBALS.put(identifier, value);
        return value;
    }

    public static boolean isLongGlobal(String identifier) {
        return getGlobal(identifier) instanceof Long;
    }

    private static Object getGlobal(String identifier) {
        return GLOBALS.get(identifier, NULL);
    }

    public static DynamicObject createObject() {
        return EMPTY_SHAPE.newInstance();
    }

    public static boolean isPELangObject(Object object) {
        return LAYOUT.getType().isInstance(object) && LAYOUT.getType().cast(object).getShape().getObjectType() == PELangObjectType.SINGLETON;
    }

    private static final class PELangNull {

        private PELangNull() {
        }

        @Override
        public String toString() {
            return "PELangNull";
        }

    }

    private static final class PELangObjectType extends ObjectType {

        public static final ObjectType SINGLETON = new PELangObjectType();

        private PELangObjectType() {
        }

    }

}
