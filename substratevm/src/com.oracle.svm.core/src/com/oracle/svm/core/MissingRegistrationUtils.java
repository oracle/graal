/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.SubstrateOptions.ThrowMissingRegistrationErrors;

import java.io.IOException;
import java.io.Serial;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

public class MissingRegistrationUtils {

    public static boolean throwMissingRegistrationErrors() {
        return ThrowMissingRegistrationErrors.hasBeenSet();
    }

    public static SubstrateOptions.ReportingMode missingRegistrationReportingMode() {
        return SubstrateOptions.MissingRegistrationReportingMode.getValue();
    }

    private static final AtomicReference<Set<String>> seenOutputs = new AtomicReference<>(null);

    public static void report(Error exception, StackTraceElement responsibleClass) {
        if (missingRegistrationErrorsSuspended.get() || (responsibleClass != null && !MissingRegistrationSupport.singleton().reportMissingRegistrationErrors(responsibleClass))) {
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
                sb.append(System.lineSeparator());
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
                    if (printed >= SubstrateOptions.MissingRegistrationWarnContextLines.getValue()) {
                        break;
                    }
                }
                if (seenOutputs.get() == null && seenOutputs.compareAndSet(null, ConcurrentHashMap.newKeySet())) {
                    /* First output, we print an explanation message */
                    System.out.println("Note: this run will print partial stack traces of the locations where a " + exception.getClass() + " would be thrown " +
                                    "when the '-XX:MissingRegistrationReportingMode=Warn'" +
                                    " option is set. The trace stops at the first entry of JDK code and provides " +
                                    SubstrateOptions.MissingRegistrationWarnContextLines.getValue() + " lines of context.");
                }
                String output = sb.toString();
                if (seenOutputs.get().add(output)) {
                    System.out.print(output);
                }
            }
        }
    }

    private static final ThreadLocal<Boolean> missingRegistrationErrorsSuspended = ThreadLocal.withInitial(() -> false);

    /**
     * Code executing inside this function will temporarily revert to throwing JDK exceptions like
     * ({@code ClassNotFoundException} when encountering a situation that would normally cause a
     * missing registration error. This is currently required during resource bundle lookups, where
     * encountering an unregistered class can mean that the corresponding locale isn't included in
     * the image, and is not a reason to abort the lookup completely.
     */
    public static <T> T runIgnoringMissingRegistrations(Supplier<T> callback) {
        VMError.guarantee(!missingRegistrationErrorsSuspended.get());
        try {
            missingRegistrationErrorsSuspended.set(true);
            return callback.get();
        } finally {
            missingRegistrationErrorsSuspended.set(false);
        }
    }

    private static void printLine(StringBuilder sb, Object object) {
        sb.append("  ").append(object).append(System.lineSeparator());
    }

    protected static JsonWriter getJSONWriter(StringWriter json) throws IOException {
        return new JsonPrettyWriter(json)
                        .indent().indent() // match indentation of reachability-metadata.json
                        .appendIndentation();
    }

    protected static String elementToJSON(JsonPrintable element) {
        var json = new StringWriter();
        try {
            element.printJson(getJSONWriter(json));
        } catch (IOException e) {
            VMError.shouldNotReachHere("Writing to JSON to memory");
        }
        return json.toString();
    }

    protected static String quote(String element) {
        return "'" + element + "'";
    }

    protected static String registrationMessage(String failedAction, String elementDescriptor, String json, String accessManner, String section, String helpLink) {
        /* Can't use multi-line strings as they pull in format and bloat "Hello, World!" */
        String optionalSpace = accessManner.isEmpty() ? "" : " ";
        return "Cannot" + optionalSpace + accessManner + " " + failedAction + " " + elementDescriptor + ". To allow this operation, add the following to the '" + section +
                        "' section of 'reachability-metadata.json' and rebuild the native image:" + System.lineSeparator() +
                        System.lineSeparator() +
                        json + System.lineSeparator() +
                        System.lineSeparator() +
                        "The 'reachability-metadata.json' file should be located in 'META-INF/native-image/<group-id>/<artifact-id>/' of your project. For further help, see https://www.graalvm.org/latest/reference-manual/native-image/metadata/#" +
                        helpLink;
    }

    protected static ConfigurationType namedConfigurationType(String typeName) {
        return new ConfigurationType(UnresolvedConfigurationCondition.alwaysTrue(), new NamedConfigurationTypeDescriptor(typeName), true);
    }

    protected static void addField(ConfigurationType type, String fieldName) {
        type.addField(fieldName, ConfigurationMemberInfo.ConfigurationMemberDeclaration.PRESENT, false);
    }

    protected static void addMethod(ConfigurationType type, String methodName, Class<?>[] paramTypes) {
        List<ConfigurationType> params = new ArrayList<>();
        if (paramTypes != null) {
            for (Class<?> paramType : paramTypes) {
                params.add(namedConfigurationType(paramType.getTypeName()));
            }
        }
        type.addMethod(methodName, ConfigurationMethod.toInternalParamsSignature(params), ConfigurationMemberInfo.ConfigurationMemberDeclaration.PRESENT);
    }

    protected static ConfigurationType getConfigurationType(Class<?> declaringClass) {
        return new ConfigurationType(UnresolvedConfigurationCondition.alwaysTrue(), ConfigurationTypeDescriptor.fromClass(declaringClass), true);
    }

    protected static String typeDescriptor(Class<?> clazz) {
        if (Proxy.isProxyClass(clazz)) {
            return "proxy class inheriting " + interfacesString(clazz.getInterfaces());
        } else if (LambdaUtils.isLambdaClass(clazz)) {
            String declaringClass = StringUtil.split(clazz.getTypeName(), LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING)[0];
            return "lambda-proxy class declared in " + quote(declaringClass) + " inheriting " + interfacesString(clazz.getInterfaces());
        } else {
            return quote(clazz.getTypeName());
        }
    }

    protected static String interfacesString(Class<?>[] classes) {
        return Arrays.stream(classes)
                        .map(Class::getTypeName)
                        .map(MissingRegistrationUtils::quote)
                        .collect(Collectors.joining(",", "[", "]"));
    }

    protected static StackTraceElement getResponsibleClass(Throwable t, Map<String, Set<String>> entryPoints) {
        StackTraceElement[] stackTrace = t.getStackTrace();
        boolean returnNext = false;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (entryPoints.getOrDefault(stackTraceElement.getClassName(), Set.of()).contains(stackTraceElement.getMethodName())) {
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

        public ExitException(Throwable cause) {
            super(cause);
        }
    }
}
