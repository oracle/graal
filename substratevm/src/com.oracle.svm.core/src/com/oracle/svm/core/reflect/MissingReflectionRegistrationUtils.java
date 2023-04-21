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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.MissingReflectionRegistrationError;

import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.ExitStatus;

public final class MissingReflectionRegistrationUtils {
    public static class Options {
        @Option(help = {"Select the mode in which the missing reflection registrations will be reported.",
                        "Possible values are:",
                        "\"Throw\" (default): Throw a MissingReflectionRegistrationError;",
                        "\"Exit\": Call System.exit() to avoid accidentally catching the error;",
                        "\"Warn\": Print a message to stdout, including a stack trace to see what caused the issue."})//
        public static final HostedOptionKey<ReportingMode> MissingRegistrationReportingMode = new HostedOptionKey<>(ReportingMode.Throw);
    }

    public enum ReportingMode {
        Warn,
        Throw,
        ExitTest,
        Exit
    }

    public static boolean throwMissingRegistrationErrors() {
        return SubstrateOptions.ThrowMissingRegistrationErrors.hasBeenSet();
    }

    public static ReportingMode missingRegistrationReportingMode() {
        return Options.MissingRegistrationReportingMode.getValue();
    }

    public static void forClass(String className) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access class", className),
                        Class.class, null, className, null);
        report(exception);
    }

    public static void forField(Class<?> declaringClass, String fieldName) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access field",
                        declaringClass.getTypeName() + "#" + fieldName),
                        Field.class, declaringClass, fieldName, null);
        report(exception);
    }

    public static void forMethod(Class<?> declaringClass, String methodName, Class<?>[] paramTypes) {
        StringJoiner paramTypeNames = new StringJoiner(", ", "(", ")");
        for (Class<?> paramType : paramTypes) {
            paramTypeNames.add(paramType.getTypeName());
        }
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access method",
                        declaringClass.getTypeName() + "#" + methodName + paramTypeNames),
                        Method.class, declaringClass, methodName, paramTypes);
        report(exception);
    }

    public static void forQueriedOnlyExecutable(Executable executable) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("invoke method", executable.toString()),
                        executable.getClass(), executable.getDeclaringClass(), executable.getName(), executable.getParameterTypes());
        report(exception);
        /*
         * If report doesn't throw, we throw the exception anyway since this is a Native
         * Image-specific error that is unrecoverable in any case.
         */
        throw exception;
    }

    public static void forBulkQuery(Class<?> declaringClass, String methodName) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access",
                        declaringClass.getTypeName() + "." + methodName + "()"),
                        null, declaringClass, methodName, null);
        report(exception);
    }

    private static String errorMessage(String failedAction, String elementDescriptor) {
        return "The program tried to reflectively " + failedAction + " " + elementDescriptor +
                        " without it being registered for runtime reflection. Add it to the reflection metadata to solve this problem. " +
                        "See https://www.graalvm.org/latest/reference-manual/native-image/metadata/#reflection for help.";
    }

    private static final int CONTEXT_LINES = 4;

    private static final Set<String> seenOutputs = Options.MissingRegistrationReportingMode.getValue() == ReportingMode.Warn ? ConcurrentHashMap.newKeySet() : null;

    private static void report(MissingReflectionRegistrationError exception) {
        StackTraceElement responsibleClass = getResponsibleClass(exception);
        if (responsibleClass != null && !MissingRegistrationSupport.singleton().reportMissingRegistrationErrors(responsibleClass)) {
            return;
        }
        switch (missingRegistrationReportingMode()) {
            case Throw -> {
                throw exception;
            }
            case Exit -> {
                exception.printStackTrace(System.out);
                System.exit(ExitStatus.MISSING_METADATA.getValue());
            }
            case ExitTest -> {
                throw new ExitException(exception);
            }
            case Warn -> {
                StackTraceElement[] stackTrace = exception.getStackTrace();
                int printed = 0;
                StackTraceElement entryPoint = null;
                StringBuilder sb = new StringBuilder(exception.toString());
                sb.append("\n");
                for (StackTraceElement stackTraceElement : stackTrace) {
                    if (printed == 0) {
                        String moduleName = stackTraceElement.getModuleName();
                        /*
                         * Skip internal stack trace entries to include only the relevant part of
                         * the trace in the output. The heuristic used is that any JDK and Graal
                         * code is excluded except the first element, so that the rest of the trace
                         * consists of meaningful application code entries.
                         */
                        if (moduleName != null && (moduleName.equals("java.base") || moduleName.startsWith("org.graalvm"))) {
                            entryPoint = stackTraceElement;
                        } else {
                            printLine(sb, entryPoint);
                            printed++;
                        }
                    }
                    if (printed > 0) {
                        printLine(sb, stackTraceElement);
                        printed++;
                    }
                    if (printed >= CONTEXT_LINES) {
                        break;
                    }
                }
                if (seenOutputs.isEmpty()) {
                    /* First output, we print an explanation message */
                    System.out.println("Note: this run will print partial stack traces of the locations where a MissingReflectionRegistrationError would be thrown " +
                                    "when the -H:+ThrowMissingRegistrationErrors option is set. The trace stops at the first entry of JDK code and provides 4 lines of context.");
                }
                String output = sb.toString();
                if (seenOutputs.add(output)) {
                    System.out.print(output);
                }
            }
        }
    }

    private static void printLine(StringBuilder sb, Object object) {
        sb.append("  ").append(object).append(System.lineSeparator());
    }

    /*
     * This is a list of all public JDK methods that end up potentially throwing missing
     * registration errors. This should be implemented using wrapping substitutions once they are
     * available.
     */
    private static final Map<String, Set<String>> reflectionEntryPoints = Map.of(
                    Class.class.getTypeName(), Set.of(
                                    "forName",
                                    "getClasses",
                                    "getDeclaredClasses",
                                    "getConstructor",
                                    "getConstructors",
                                    "getDeclaredConstructor",
                                    "getDeclaredConstructors",
                                    "getField",
                                    "getFields",
                                    "getDeclaredField",
                                    "getDeclaredFields",
                                    "getMethod",
                                    "getMethods",
                                    "getDeclaredMethod",
                                    "getDeclaredMethods",
                                    "getNestMembers",
                                    "getPermittedSubclasses",
                                    "getRecordComponents",
                                    "getSigners",
                                    "arrayType",
                                    "newInstance"),
                    Method.class.getTypeName(), Set.of("invoke"),
                    Constructor.class.getTypeName(), Set.of("newInstance"),
                    "java.lang.reflect.ReflectAccess", Set.of("newInstance"),
                    "jdk.internal.access.JavaLangAccess", Set.of("getDeclaredPublicMethods"),
                    sun.misc.Unsafe.class.getName(), Set.of("allocateInstance"),
                    /* For jdk.internal.misc.Unsafe.allocateInstance(), which is intrinsified */
                    SubstrateAllocationSnippets.class.getName(), Set.of("instanceHubErrorStub"));

    private static StackTraceElement getResponsibleClass(Throwable t) {
        StackTraceElement[] stackTrace = t.getStackTrace();
        boolean returnNext = false;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (reflectionEntryPoints.getOrDefault(stackTraceElement.getClassName(), Set.of()).contains(stackTraceElement.getMethodName())) {
                /*
                 * Multiple functions with the same name can be called in succession, like the
                 * Class.forName caller-sensitive adapters. We skip those until we find a method
                 * that is not a monitored reflection entry point.
                 */
                returnNext = true;
            } else if (returnNext) {
                return stackTraceElement;
            }
        }
        return null;
    }

    public static final class ExitException extends Error {
        @Serial//
        private static final long serialVersionUID = -3638940737396726143L;

        private ExitException(Throwable cause) {
            super(cause);
        }
    }
}
