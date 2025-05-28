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

import java.math.BigInteger;

/**
 * Java representation of a JavaScript {@code Number} value.
 */
public final class JSNumber extends JSValue {

    JSNumber() {
    }

    @JS("return conversion.extractJavaScriptNumber(d[runtime.symbol.javaNative]);")
    public static native JSNumber of(double d);

    @Override
    public String typeof() {
        return "number";
    }

    @JS("return conversion.toProxy(conversion.createJavaDouble(this));")
    private native Double javaDouble();

    @Override
    protected String stringValue() {
        return String.valueOf(javaDouble());
    }

    @Override
    public Byte asByte() {
        return javaDouble().byteValue();
    }

    @Override
    public Short asShort() {
        return javaDouble().shortValue();
    }

    @Override
    public Character asChar() {
        return (char) javaDouble().intValue();
    }

    @Override
    public Integer asInt() {
        return javaDouble().intValue();
    }

    @Override
    public Float asFloat() {
        return javaDouble().floatValue();
    }

    @Override
    public Long asLong() {
        return javaDouble().longValue();
    }

    @Override
    public Double asDouble() {
        return javaDouble().doubleValue();
    }

    @Override
    public BigInteger asBigInteger() {
        return BigInteger.valueOf(javaDouble().longValue());
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof JSNumber) {
            return this.javaDouble().equals(((JSNumber) that).javaDouble());
        }
        return super.equals(that);
    }

    @Override
    public int hashCode() {
        return javaDouble().hashCode();
    }
}
