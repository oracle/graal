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
package com.oracle.svm.configure.trace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ForeignConfiguration;
import com.oracle.svm.util.LogUtils;

import jdk.vm.ci.meta.MetaUtil;

class ForeignProcessor extends AbstractProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public void processEntry(EconomicMap<String, Object> entry, ConfigurationSet configurationSet) {
        boolean invalidResult = Boolean.FALSE.equals(entry.get("result"));
        if (invalidResult) {
            return;
        }

        String function = (String) entry.get("function");
        List<?> args = (List<?>) entry.get("args");

        ForeignConfiguration foreignConfiguration = configurationSet.getForeignConfiguration();
        switch (function) {
            case "downcallHandle0" -> {
                // returnType, parameterTypes, linkerOptions
                expectSize(args, 3);
                String returnType = (String) args.get(0);
                List<String> parameterTypes = (List<String>) args.get(1);
                List<String> linkerOptions = (List<String>) args.get(2);
                foreignConfiguration.addDowncall(returnType, parameterTypes, processLinkerOptions(linkerOptions));
            }
            case "upcallStub" -> {
                // returnType, parameterTypes, linkerOptions, target
                expectSize(args, 4);
                String returnType = (String) args.get(0);
                List<String> parameterTypes = (List<String>) args.get(1);
                List<String> linkerOptions = (List<String>) args.get(2);
                Object targetDesc = args.get(3);
                ClassAndMethodName classAndMethodName = targetDesc instanceof String targetDescString ? getClassAndMethodName(targetDescString) : null;
                if (classAndMethodName != null) {
                    foreignConfiguration.addDirectUpcall(returnType, parameterTypes, processLinkerOptions(linkerOptions), classAndMethodName.className, classAndMethodName.methodName);
                } else {
                    foreignConfiguration.addUpcall(returnType, parameterTypes, processLinkerOptions(linkerOptions));
                }
            }
        }
    }

    private static Map<String, Object> processLinkerOptions(List<String> linkerOptions) {
        Map<String, Object> result = new HashMap<>();
        for (String linkerOption : linkerOptions) {
            Map.Entry<String, Object> processed = processLinkerOption(linkerOption);
            if (processed != null) {
                result.put(processed.getKey(), processed.getValue());
            }
        }
        return result;
    }

    private static Map.Entry<String, Object> processLinkerOption(String linkerOption) {
        if (linkerOption == null || linkerOption.isEmpty()) {
            return null;
        }
        // since jdk-25+21: 'critical' became an Enum
        switch (linkerOption) {
            case "ALLOW_HEAP":
                return critical(true);
            case "DONT_ALLOW_HEAP":
                return critical(false);
        }

        // e.g. "FirstVariadicArg[index=123]"
        int argStart = linkerOption.indexOf('[');
        int argEnd = linkerOption.lastIndexOf(']');
        if (argStart == -1 || argEnd == -1 || argEnd != linkerOption.length() - 1) {
            LogUtils.warning("Ignoring invalid Linker.Option: " + linkerOption);
            return null;
        }

        return switch (linkerOption.substring(0, argStart)) {
            /*
             * Special case: 'captureCallState' is just a Boolean in the configuration file. Also,
             * even if no state was specified to capture (i.e. no parameters were given to
             * `Linker.Option.captureCallState()`), we need to set the options otherwise the stub
             * won't be found.
             */
            case "CaptureCallState" -> Map.entry("captureCallState", true);
            case "FirstVariadicArg" -> {
                // e.g. "FirstVariadicArg[index=123]"
                try {
                    int eqIndex = linkerOption.indexOf('=', argStart);
                    int index = Integer.parseInt(linkerOption.substring(eqIndex + 1, argEnd));
                    yield Map.entry("firstVariadicArg", index);
                } catch (NumberFormatException e) {
                    LogUtils.warning("Invalid parameter in Linker.Option: " + linkerOption);
                    yield null;
                }
            }
            case "Critical" -> {
                // e.g. "Critical[allowHeapAccess=true]"
                try {
                    int eqIndex = linkerOption.indexOf('=', argStart);
                    boolean allowHeapAccess = Boolean.parseBoolean(linkerOption.substring(eqIndex + 1, argEnd));
                    yield critical(allowHeapAccess);
                } catch (NumberFormatException e) {
                    LogUtils.warning("Invalid parameter in Linker.Option: " + linkerOption);
                    yield null;
                }
            }
            default -> null;
        };
    }

    private static Map.Entry<String, Object> critical(boolean allowHeapAccess) {
        return Map.entry("critical", Map.of("allowHeapAccess", allowHeapAccess));
    }

    private static ClassAndMethodName getClassAndMethodName(String targetDesc) {

        // Example: Method "String foo(long)" of class "org.my.MyClass$InnerClass"
        // Lorg/my/MyClass$InnerClass::foo
        int sep;
        if (targetDesc == null || (sep = targetDesc.indexOf("::")) == -1) {
            // don't output a warning; it is valid if upcalls do not bind a DirectMethodHandle
            return null;
        }

        /*
         * @formatter:off
         * Lorg/my/MyClass$InnerClass::foo
         *                           ^
         *                          sep
         * @formatter:on
         */
        String className = MetaUtil.internalNameToJava(targetDesc.substring(0, sep), true, false);
        String methodName = targetDesc.substring(sep + 2);
        return new ClassAndMethodName(className, methodName);
    }

    private record ClassAndMethodName(String className, String methodName) {
    }
}
