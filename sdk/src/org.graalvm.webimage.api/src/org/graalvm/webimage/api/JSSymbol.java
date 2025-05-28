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
 * Java representation of a JavaScript {@code Symbol} value.
 */
public final class JSSymbol extends JSValue {

    JSSymbol() {
    }

    @JS("return Symbol(conversion.extractJavaScriptString(s[runtime.symbol.javaNative]));")
    public static native JSSymbol of(String s);

    @JS("return Symbol.for(conversion.extractJavaScriptString(s[runtime.symbol.javaNative]));")
    public static native JSSymbol forString(String s);

    @JS("return sym0 === sym1;")
    private static native JSBoolean referenceEquals(JSSymbol sym0, JSSymbol sym1);

    @Override
    public String typeof() {
        return "symbol";
    }

    @JS("return conversion.toProxy(toJavaString(this.toString()));")
    private native String javaString();

    @Override
    protected String stringValue() {
        return javaString();
    }

    public String description() {
        return javaString();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof JSSymbol) {
            return referenceEquals(this, (JSSymbol) that).asBoolean();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return javaString().hashCode();
    }
}
