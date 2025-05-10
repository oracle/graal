/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.configure.ConditionalConfigurationParser.CONDITIONAL_KEY;
import static com.oracle.svm.configure.UnresolvedConfigurationCondition.TYPE_REACHABLE_KEY;
import static com.oracle.svm.configure.UnresolvedConfigurationCondition.TYPE_REACHED_KEY;

import java.io.IOException;

import com.oracle.svm.configure.UnresolvedConfigurationCondition;

import jdk.graal.compiler.util.json.JsonWriter;

final class ConfigurationConditionPrintable {
    static void printConditionAttribute(UnresolvedConfigurationCondition condition, JsonWriter writer, boolean combinedFile) throws IOException {
        if (!condition.isAlwaysTrue()) {
            writer.quote(CONDITIONAL_KEY).appendFieldSeparator().appendObjectStart();
            /*
             * typeReachable conditions are emitted as typeReached in reachability-metadata.json.
             * typeReached conditions are emitted as typeReachable in resource-config.json
             */
            writer.quote(combinedFile ? TYPE_REACHED_KEY : TYPE_REACHABLE_KEY).appendFieldSeparator().quote(condition.getTypeName());
            writer.appendObjectEnd().appendSeparator();
        }
    }
}
