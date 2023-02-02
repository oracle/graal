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
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Indicates an exception that occurred during class loading.
 */
public abstract class EspressoClassLoadingException extends Exception {

    private static final long serialVersionUID = 1598679948708713831L;

    public static EspressoClassLoadingException.ClassCircularityError classCircularityError() throws EspressoClassLoadingException.ClassCircularityError {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoClassLoadingException.ClassCircularityError("Class circularity detected");
    }

    public static EspressoClassLoadingException.IncompatibleClassChangeError incompatibleClassChangeError(String msg) throws EspressoClassLoadingException.IncompatibleClassChangeError {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoClassLoadingException.IncompatibleClassChangeError(msg);
    }

    public static EspressoClassLoadingException.SecurityException securityException(String msg) throws EspressoClassLoadingException.SecurityException {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoClassLoadingException.SecurityException(msg);
    }

    public static EspressoClassLoadingException.ClassDefNotFoundError classDefNotFoundError(String msg) throws EspressoClassLoadingException.ClassDefNotFoundError {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoClassLoadingException.ClassDefNotFoundError(msg);
    }

    public static EspressoClassLoadingException.LinkageError linkageError(String msg) throws EspressoClassLoadingException.LinkageError {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoClassLoadingException.LinkageError(msg);
    }

    public static EspressoClassLoadingException.IllegalAccessError illegalAccessError(String msg) throws EspressoClassLoadingException.IllegalAccessError {
        CompilerDirectives.transferToInterpreter();
        throw new EspressoClassLoadingException.IllegalAccessError(msg);
    }

    public static EspressoException wrapClassNotFoundGuestException(ClassLoadingEnv env, EspressoException e) {
        Meta meta = env.getMeta();
        if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getGuestException().getKlass())) {
            // NoClassDefFoundError has no <init>(Throwable cause). Set cause manually.
            StaticObject ncdfe = Meta.initException(meta.java_lang_NoClassDefFoundError);
            meta.java_lang_Throwable_cause.set(ncdfe, e.getGuestException());
            throw meta.throwException(ncdfe);
        }
        throw e;
    }

    private EspressoClassLoadingException(String msg) {
        super(msg);
    }

    public abstract EspressoException asGuestException(Meta meta);

    public static final class ClassCircularityError extends EspressoClassLoadingException {

        private static final long serialVersionUID = 2598679948708713801L;

        private ClassCircularityError(String msg) {
            super(msg);
        }

        @Override
        public EspressoException asGuestException(Meta meta) {
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassCircularityError, getMessage());
        }
    }

    public static final class IncompatibleClassChangeError extends EspressoClassLoadingException {

        private static final long serialVersionUID = -412429500300133184L;

        private IncompatibleClassChangeError(String msg) {
            super(msg);
        }

        @Override
        public EspressoException asGuestException(Meta meta) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, getMessage());
        }
    }

    public static final class SecurityException extends EspressoClassLoadingException {

        private static final long serialVersionUID = -4134684427619639549L;

        private SecurityException(String msg) {
            super(msg);
        }

        @Override
        public EspressoException asGuestException(Meta meta) {
            throw meta.throwExceptionWithMessage(meta.java_lang_SecurityException, getMessage());
        }
    }

    public static final class ClassDefNotFoundError extends EspressoClassLoadingException {

        private static final long serialVersionUID = 1820085678127928882L;

        private ClassDefNotFoundError(String msg) {
            super(msg);
        }

        @Override
        public EspressoException asGuestException(Meta meta) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, getMessage());
        }
    }

    public static final class LinkageError extends EspressoClassLoadingException {

        private static final long serialVersionUID = 1820087778127928882L;

        private LinkageError(String msg) {
            super(msg);
        }

        @Override
        public EspressoException asGuestException(Meta meta) {
            throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, getMessage());
        }
    }

    public static final class IllegalAccessError extends EspressoClassLoadingException {

        private static final long serialVersionUID = 1820087878127928882L;

        private IllegalAccessError(String msg) {
            super(msg);
        }

        @Override
        public EspressoException asGuestException(Meta meta) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, getMessage());
        }
    }
}
