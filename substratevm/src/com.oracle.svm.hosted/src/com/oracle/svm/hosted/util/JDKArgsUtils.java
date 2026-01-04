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
package com.oracle.svm.hosted.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Ported utils from JDK21's java.base/share/native/libjli/args.c. */
public class JDKArgsUtils {

    public static List<String> parseArgsFromEnvVar(String envVarValue, String envVarName, Function<String, Error> errorFunction) {
        List<String> result = new ArrayList<>();
        int envVarValueLength = envVarValue.length();
        int i = 0;
        while (i < envVarValueLength) {
            while (i < envVarValueLength && isspace(envVarValue.charAt(i))) {
                i++;
            }

            // Trailing space
            if (i >= envVarValueLength) {
                break;
            }

            char currentChar;
            StringBuilder argChars = new StringBuilder();
            while (i < envVarValueLength && !isspace(currentChar = envVarValue.charAt(i))) {
                if (currentChar == '"' || currentChar == '\'') {
                    char quote = currentChar;
                    i++;
                    while (i < envVarValueLength && envVarValue.charAt(i) != quote) {
                        argChars.append(envVarValue.charAt(i++));
                    }
                    if (i >= envVarValueLength) {
                        throw errorFunction.apply("Unmatched quote in environment variable " + envVarName);
                    }
                    i++;
                } else {
                    argChars.append(envVarValue.charAt(i++));
                }
            }
            String argument = argChars.toString();
            // This port is more restrictive as it forbids arg files to be passed via an env var
            boolean isArgFileOption = argument.startsWith("@") && !argument.startsWith("@@");
            if (isArgFileOption || isTerminalOpt(argument)) {
                throw errorFunction.apply("Option '" + argument + "' is not allowed in environment variable " + envVarName);
            }
            result.add(argument);
            assert i >= envVarValueLength || isspace(envVarValue.charAt(i));
        }
        return result;
    }

    public static boolean isspace(char value) {
        // \v not supported in Java
        return value == ' ' || value == '\f' || value == '\n' || value == '\r' || value == '\t';
    }

    private static boolean isTerminalOpt(String arg) {
        return switch (arg) {
            /* JDK terminal options supported by SVM */
            case "-jar", "-m", "--module", "--dry-run", "--help", "--help-extra", "--version" -> true;
            /* JDK terminal options not (yet) supported by SVM */
            case "-h", "-?", "-help", "-X", "-version", "-fullversion", "--full-version" -> true;
            /* SVM-only terminal options */
            case "--expert-options", "--expert-options-all", "--expert-options-detail" -> true;
            default -> arg.startsWith("--module=");
        };
    }
}
