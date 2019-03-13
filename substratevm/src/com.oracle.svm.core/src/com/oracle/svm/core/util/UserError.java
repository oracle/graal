/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.Collections;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * SVM mechanism for handling user errors and warnings that should be reported to the command line.
 */
public class UserError {

    /**
     * UserException type for all errors that should be reported to the SVM users.
     */
    public static class UserException extends Error {
        static final long serialVersionUID = 75431290632980L;
        private final Iterable<String> messages;

        protected UserException(String msg) {
            this(Collections.singletonList(msg));
        }

        protected UserException(Iterable<String> messages) {
            super(String.join(System.lineSeparator(), messages));
            this.messages = messages;
        }

        public Iterable<String> getMessages() {
            return messages;
        }
    }

    /**
     * Stop compilation immediately and report the message to the user.
     *
     * @param message the error message to be reported to the user.
     */
    public static UserException abort(String message) {
        throw new UserException(message);
    }

    /**
     * Stop compilation immediately and report the message to the user.
     *
     * @param message the error message to be reported to the user.
     * @param ex the exception that caused the abort.
     */
    public static UserException abort(String message, Throwable ex) {
        throw ((UserException) new UserException(message).initCause(ex));
    }

    /**
     * Concisely reports user errors.
     *
     * @param message the error message to be reported to the user.
     */
    public static void guarantee(boolean condition, String message, Object... args) {
        if (!condition) {
            // Checkstyle: stop
            throw UserError.abort(String.format(message, formatArguments(args)));
            // Checkstyle: resume
        }
    }

    private static Object[] formatArguments(Object... args) {
        Object[] stringArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof ResolvedJavaType) {
                stringArgs[i] = ((ResolvedJavaType) arg).toJavaName(true);
            } else if (arg instanceof ResolvedJavaMethod) {
                stringArgs[i] = ((ResolvedJavaMethod) arg).format("%H.%n(%p)");
            } else if (arg instanceof ResolvedJavaField) {
                stringArgs[i] = ((ResolvedJavaField) arg).format("%H.%n");
            } else {
                stringArgs[i] = String.valueOf(arg);
            }
        }
        return stringArgs;
    }

    /**
     * Stop compilation immediately and report the message to the user.
     *
     * @param messages the error message to be reported to the user.
     */
    public static UserException abort(Iterable<String> messages) {
        throw new UserException(messages);
    }

}
