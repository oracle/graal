/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.io.Serial;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.StringJoiner;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.MissingReflectionRegistrationError;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.ExitStatus;

public final class MissingReflectionRegistrationUtils {
    public static class Options {
        @Option(help = "Enable termination caused by missing metadata.")//
        public static final HostedOptionKey<Boolean> ExitOnMissingReflectionRegistration = new HostedOptionKey<>(false);

        @Option(help = "Simulate exiting the program with an exception instead of calling System.exit() (for testing)")//
        public static final HostedOptionKey<Boolean> ExitWithExceptionOnMissingReflectionRegistration = new HostedOptionKey<>(false);
    }

    public static boolean throwMissingRegistrationErrors() {
        return SubstrateOptions.ThrowMissingRegistrationErrors.getValue();
    }

    public static MissingReflectionRegistrationError forClass(String className) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access class", className),
                        Class.class, null, className, null);
        if (MissingReflectionRegistrationUtils.Options.ExitOnMissingReflectionRegistration.getValue()) {
            exitOnMissingMetadata(exception);
        }
        return exception;
    }

    public static MissingReflectionRegistrationError forField(Class<?> declaringClass, String fieldName) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access field",
                        declaringClass.getTypeName() + "#" + fieldName),
                        Field.class, declaringClass, fieldName, null);
        if (MissingReflectionRegistrationUtils.Options.ExitOnMissingReflectionRegistration.getValue()) {
            exitOnMissingMetadata(exception);
        }
        return exception;
    }

    public static MissingReflectionRegistrationError forMethod(Class<?> declaringClass, String methodName, Class<?>[] paramTypes) {
        StringJoiner paramTypeNames = new StringJoiner(", ", "(", ")");
        for (Class<?> paramType : paramTypes) {
            paramTypeNames.add(paramType.getTypeName());
        }
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access method",
                        declaringClass.getTypeName() + "#" + methodName + paramTypeNames),
                        Method.class, declaringClass, methodName, paramTypes);
        if (MissingReflectionRegistrationUtils.Options.ExitOnMissingReflectionRegistration.getValue()) {
            exitOnMissingMetadata(exception);
        }
        return exception;
    }

    public static MissingReflectionRegistrationError forQueriedOnlyExecutable(Executable executable) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("invoke method", executable.toString()),
                        executable.getClass(), executable.getDeclaringClass(), executable.getName(), executable.getParameterTypes());
        if (MissingReflectionRegistrationUtils.Options.ExitOnMissingReflectionRegistration.getValue()) {
            exitOnMissingMetadata(exception);
        }
        return exception;
    }

    public static MissingReflectionRegistrationError forBulkQuery(Class<?> declaringClass, String methodName) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access",
                        declaringClass.getTypeName() + "." + methodName + "()"),
                        null, declaringClass, methodName, null);
        if (MissingReflectionRegistrationUtils.Options.ExitOnMissingReflectionRegistration.getValue()) {
            exitOnMissingMetadata(exception);
        }
        return exception;
    }

    private static String errorMessage(String failedAction, String elementDescriptor) {
        return "The program tried to reflectively " + failedAction + " " + elementDescriptor +
                        " without it being registered for runtime reflection. Add it to the reflection metadata to solve this problem. " +
                        "See https://www.graalvm.org/latest/reference-manual/native-image/metadata/#reflection for help.";
    }

    private static void exitOnMissingMetadata(MissingReflectionRegistrationError exception) {
        if (Options.ExitWithExceptionOnMissingReflectionRegistration.getValue()) {
            throw new ExitException(exception);
        } else {
            exception.printStackTrace(System.out);
            System.exit(ExitStatus.MISSING_METADATA.getValue());
        }
    }

    public static final class ExitException extends Error {
        @Serial//
        private static final long serialVersionUID = -3638940737396726143L;

        private ExitException(Throwable cause) {
            super(cause);
        }
    }
}
