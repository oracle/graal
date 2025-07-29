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
package com.oracle.svm.core.util;

import com.oracle.svm.core.OS;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EnvVariableUtils {
    private static final Set<String> REQUIRED_ENV_VARIABLE_KEYS_COMMON = Set.of(
                    "PATH",
                    "PWD",
                    "HOME",
                    "LANG",
                    "LANGUAGE");

    private static final Set<String> REQUIRED_ENV_VARIABLE_KEYS_WINDOWS = Set.of(
                    "TEMP",
                    "INCLUDE",
                    "LIB");

    private static final Set<String> REQUIRED_ENV_VARIABLE_KEYS = getRequiredEnvVariableKeys();

    private static Set<String> getRequiredEnvVariableKeys() {
        Set<String> requiredEnvVariableKeys = new HashSet<>(REQUIRED_ENV_VARIABLE_KEYS_COMMON);
        if (OS.WINDOWS.isCurrent()) {
            requiredEnvVariableKeys.addAll(REQUIRED_ENV_VARIABLE_KEYS_WINDOWS);
        }
        return requiredEnvVariableKeys;
    }

    public record EnvironmentVariable(String key, String value) {
        public EnvironmentVariable {
            key = mapKey(key);
        }

        public static EnvironmentVariable of(Map.Entry<String, String> entry) {
            return new EnvironmentVariable(entry.getKey(), entry.getValue());
        }

        public static EnvironmentVariable of(String s) {
            String[] envVarArr = s.split("=", 2);
            return new EnvironmentVariable(envVarArr[0], envVarArr[1]);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

        public boolean keyEquals(String otherKey) {
            return key.equals(mapKey(otherKey));
        }

        public boolean isKeyRequired() {
            return isKeyRequiredCondition(key);
        }

        public static boolean isKeyRequired(String key) {
            return isKeyRequiredCondition(mapKey(key));
        }

        private static String mapKey(String key) {
            if (OS.WINDOWS.isCurrent()) {
                return key.toUpperCase(Locale.ROOT);
            }
            return key;
        }

        private static boolean isKeyRequiredCondition(String key) {
            // LC_* are locale vars that override LANG for specific categories (or all, with LC_ALL)
            return REQUIRED_ENV_VARIABLE_KEYS.contains(key) || key.startsWith("LC_");
        }
    }
}
