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
package com.oracle.svm.hosted.image;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public class PreserveOptionsSupport extends IncludeOptionsSupport {

    public static final String PRESERVE_ALL = "all";
    public static final String PRESERVE_NONE = "none";

    /**
     * All Java modules, except:
     * <ul>
     * <li><code>jdk.localedata</code> that pulls in 250 MB of code into the image.</li>
     * <li>All internal modules.</li>
     * <li>All tooling modules such as the java.compiler.</li>
     * <li>Modules that are currently not supported with Native Image (e.g.,
     * <code>java.management</code>.</li>
     * </ul>
     */
    public static final Set<String> JDK_MODULES_TO_PRESERVE = Set.of(
                    "java.base",
                    "java.desktop",
                    "java.xml",
                    "java.xml.crypto",
                    "java.rmi",
                    "jdk.net",
                    "jdk.random",
                    "java.smartcardio",
                    "jdk.charsets",
                    "java.sql",
                    "java.sql.rowset",
                    "java.transaction.xa",
                    "java.datatransfer",
                    "java.security.sasl",
                    "jdk.security.jgss",
                    "jdk.security.auth",
                    "jdk.crypto.cryptoki",
                    "jdk.crypto.ec",
                    "java.logging",
                    "java.naming",
                    "jdk.naming.dns",
                    "jdk.httpserver",
                    "jdk.zipfs",
                    "jdk.nio.mapmode",
                    "java.instrument",
                    "java.prefs",
                    "jdk.unsupported",
                    "jdk.accessibility");

    private static String preservePossibleOptions() {
        return String.format("[%s, %s, %s]",
                        PRESERVE_ALL, PRESERVE_NONE, IncludeOptionsSupport.possibleExtendedOptions());

    }

    public static void parsePreserveOption(EconomicMap<OptionKey<?>, Object> hostedValues, NativeImageClassLoaderSupport classLoaderSupport) {
        AccumulatingLocatableMultiOptionValue.Strings preserve = SubstrateOptions.Preserve.getValue(new OptionValues(hostedValues));
        Stream<LocatableMultiOptionValue.ValueWithOrigin<String>> valuesWithOrigins = preserve.getValuesWithOrigins();
        valuesWithOrigins.forEach(valueWithOrigin -> {
            String optionArgument = SubstrateOptionsParser.commandArgument(SubstrateOptions.Preserve, valueWithOrigin.value(), true, false);
            if (!valueWithOrigin.origin().commandLineLike()) {
                throw UserError.abort("Using %s is only allowed on command line. The option was used from %s", optionArgument, valueWithOrigin.origin());
            }

            var options = Arrays.stream(valueWithOrigin.value().split(",")).toList();
            for (String option : options) {
                UserError.guarantee(!option.isEmpty(), "Option %s from %s cannot be passed an empty string. The possible options are: %s",
                                optionArgument, valueWithOrigin.origin(), preservePossibleOptions());
                switch (option) {
                    case PRESERVE_ALL -> classLoaderSupport.setPreserveAll();
                    case PRESERVE_NONE -> classLoaderSupport.clearPreserveSelectors();
                    default -> parseIncludeSelector(optionArgument, valueWithOrigin, classLoaderSupport.getPreserveSelectors(), ExtendedOption.parse(option), preservePossibleOptions());
                }
            }
        });
    }
}
