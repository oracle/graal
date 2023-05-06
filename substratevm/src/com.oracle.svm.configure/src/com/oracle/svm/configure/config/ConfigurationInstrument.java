/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.configure.config;

import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

import java.io.IOException;

public class ConfigurationInstrument {

    public static class Class implements JsonPrintable {
        private final String name;
        private final String type;

        public Class(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public void printJson(JsonWriter writer) throws IOException {
            writer.append("{ ");
            if (type != null) {
                writer.quote("type").append(":").quote(type).append(", ");
            }
            if (name != null) {
                writer.quote("nameInfo").append(':').quote(name);
            }
            writer.append(" }");
        }

        public String getName() {
            return name;
        }
    }

    public static class Method implements JsonPrintable {
        private final String className;
        private final String methodName = "premain";
        private final int index;
        private final String optionString;

        public Method(String className, int index, String optionString) {
            this.className = className;
            this.index = index;
            this.optionString = optionString;
        }

        @Override
        public void printJson(JsonWriter writer) throws IOException {
            writer.append("{ ");
            writer.quote("class").append(":").quote(className).append(", ");
            writer.quote("method").append(':').quote(methodName).append(", ");
            writer.quote("index").append(':').quote(String.valueOf(index)).append(", ");
            writer.quote("option").append(':').quote(optionString);
            writer.append(" }");
        }

        public String getClassName() {
            return className;
        }
    }
}
