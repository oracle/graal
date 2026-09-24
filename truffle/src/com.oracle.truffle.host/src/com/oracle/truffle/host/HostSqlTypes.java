/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The {@code java.sql} types that host interop maps to dates and times, {@link java.sql.Date} and
 * {@link java.sql.Time}.
 * <p>
 * The {@code java.sql} module is optional: {@code org.graalvm.truffle} requires it statically, so
 * that the image builder and Truffle can run on a JDK that does not have it. The types are only
 * touched if the module is present, as a reference to a class of a missing module fails with a
 * {@link NoClassDefFoundError}. Without the module no host object is an instance of them.
 */
final class HostSqlTypes {

    private static final boolean AVAILABLE = ModuleLayer.boot().findModule("java.sql").isPresent();

    private HostSqlTypes() {
    }

    static boolean isDate(Object value) {
        return AVAILABLE && Types.isDate(value);
    }

    static LocalDate toLocalDate(Object date) {
        assert AVAILABLE;
        return Types.toLocalDate(date);
    }

    static boolean isTime(Object value) {
        return AVAILABLE && Types.isTime(value);
    }

    static LocalTime toLocalTime(Object time) {
        assert AVAILABLE;
        return Types.toLocalTime(time);
    }

    /**
     * The only class that refers to {@code java.sql}: it is not loaded unless the module is present.
     */
    private static final class Types {

        static boolean isDate(Object value) {
            return value instanceof java.sql.Date;
        }

        static LocalDate toLocalDate(Object date) {
            return ((java.sql.Date) date).toLocalDate();
        }

        static boolean isTime(Object value) {
            return value instanceof java.sql.Time;
        }

        static LocalTime toLocalTime(Object time) {
            return ((java.sql.Time) time).toLocalTime();
        }
    }
}
