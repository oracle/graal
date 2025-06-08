/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
