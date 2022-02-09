/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Indicates an exception that occurred during class loading.
 */
public class EspressoClassLoadingException extends RuntimeException {

    private static final long serialVersionUID = 1598679948708713831L;

    public static ClassCircularityError classCircularityError() throws ClassCircularityError {
        CompilerDirectives.transferToInterpreter();
        throw new ClassCircularityError("Class circularity detected");
    }

    public static IncompatibleClassChangeError incompatibleClassChangeError(String msg) throws IncompatibleClassChangeError {
        CompilerDirectives.transferToInterpreter();
        throw new IncompatibleClassChangeError(msg);
    }

    public static SecurityException securityException(String msg) throws SecurityException {
        CompilerDirectives.transferToInterpreter();
        throw new SecurityException(msg);
    }

    public static ClassDefNotFoundError classDefNotFoundError(String msg) throws ClassDefNotFoundError {
        CompilerDirectives.transferToInterpreter();
        throw new ClassDefNotFoundError(msg);
    }

    private EspressoClassLoadingException(String msg) {
        super(msg);
    }

    public static final class ClassCircularityError extends EspressoClassLoadingException {

        private static final long serialVersionUID = 2598679948708713801L;

        private ClassCircularityError(String msg) {
            super(msg);
        }
    }

    public static final class IncompatibleClassChangeError extends EspressoClassLoadingException {

        private static final long serialVersionUID = -412429500300133184L;

        private IncompatibleClassChangeError(String msg) {
            super(msg);
        }
    }

    public static final class SecurityException extends EspressoClassLoadingException {

        private static final long serialVersionUID = -4134684427619639549L;

        private SecurityException(String msg) {
            super(msg);
        }
    }

    public static final class ClassDefNotFoundError extends EspressoClassLoadingException {

        private static final long serialVersionUID = 1820085678127928882L;

        private ClassDefNotFoundError(String msg) {
            super(msg);
        }
    }
}
