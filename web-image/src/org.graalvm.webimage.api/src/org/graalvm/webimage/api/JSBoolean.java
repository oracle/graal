/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.webimage.api;

/**
 * Java representation of a JavaScript {@code Boolean} value.
 */
public final class JSBoolean extends JSValue {

    JSBoolean() {
    }

    @JS("return true;")
    private static native JSBoolean createTrue();

    @JS("return false;")
    private static native JSBoolean createFalse();

    public static JSBoolean of(boolean b) {
        return b ? createTrue() : createFalse();
    }

    @Override
    public String typeof() {
        return "boolean";
    }

    @JS("return conversion.toProxy(conversion.createJavaBoolean(this));")
    private native Boolean javaBoolean();

    @Override
    protected String stringValue() {
        return String.valueOf(javaBoolean());
    }

    @Override
    public Boolean asBoolean() {
        return javaBoolean();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof JSBoolean) {
            return this.javaBoolean().equals(((JSBoolean) that).javaBoolean());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return javaBoolean().hashCode();
    }
}
