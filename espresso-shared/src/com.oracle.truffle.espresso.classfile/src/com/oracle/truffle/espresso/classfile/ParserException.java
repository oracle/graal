/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.io.Serial;

public abstract sealed class ParserException extends RuntimeException permits ParserException.ClassFormatError, ParserException.NoClassDefFoundError, ParserException.UnsupportedClassVersionError {

    @Serial private static final long serialVersionUID = -198220634150786215L;

    public ParserException(String message) {
        super(message);
    }

    public static final class ClassFormatError extends ParserException {
        @Serial private static final long serialVersionUID = 8526425503590655600L;

        public ClassFormatError(String message) {
            super(message);
        }
    }

    public static final class UnsupportedClassVersionError extends ParserException {
        @Serial private static final long serialVersionUID = 842124216349055395L;

        public UnsupportedClassVersionError(String message) {
            super(message);
        }
    }

    public static final class NoClassDefFoundError extends ParserException {
        @Serial private static final long serialVersionUID = 2283932446927965805L;

        public NoClassDefFoundError(String message) {
            super(message);
        }
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
