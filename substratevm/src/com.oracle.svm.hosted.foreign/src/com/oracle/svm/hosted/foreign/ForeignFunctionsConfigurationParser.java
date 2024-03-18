/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import com.oracle.svm.core.configure.ConfigurationParser;

import jdk.graal.compiler.util.json.JSONParserException;

@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsConfigurationParser extends ConfigurationParser {
    private static final String DOWNCALL_OPTION_CAPTURE_CALL_STATE = "captureCallState";
    private static final String DOWNCALL_OPTION_FIRST_VARIADIC_ARG = "firstVariadicArg";
    private static final String DOWNCALL_OPTION_CRITICAL = "critical";
    private static final String DOWNCALL_OPTION_ALLOW_HEAP_ACCESS = "allowHeapAccess";

    private final RuntimeForeignAccessSupport accessSupport;

    public ForeignFunctionsConfigurationParser(RuntimeForeignAccessSupport access) {
        super(true);
        this.accessSupport = access;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        var topLevel = asMap(json, "first level of document must be a map");
        checkAttributes(topLevel, "foreign methods categories", List.of(), List.of("downcalls"));

        var downcalls = asList(topLevel.get("downcalls", List.of()), "downcalls must be an array of method signatures");
        for (Object downcall : downcalls) {
            parseAndRegisterForeignCall(downcall, (descriptor, options) -> accessSupport.registerForDowncall(ConfigurationCondition.alwaysTrue(), descriptor, options));
        }

        var upcalls = asList(topLevel.get("upcalls", List.of()), "upcalls must be an array of method signatures");
        for (Object upcall : upcalls) {
            parseAndRegisterForeignCall(upcall, (descriptor, options) -> accessSupport.registerForUpcall(ConfigurationCondition.alwaysTrue(), descriptor, options));
        }
    }

    private void parseAndRegisterForeignCall(Object call, BiConsumer<Object, Object[]> register) {
        var map = asMap(call, "a foreign call must be a map");
        checkAttributes(map, "foreign call", List.of("descriptor"), List.of("options"));
        var descriptor = parseDescriptor(map.get("descriptor"));
        var options = parseOptions(map.get("options", null));
        register.accept(descriptor, options.toArray());
    }

    private FunctionDescriptor parseDescriptor(Object signature) {
        String input = asString(signature, "a function descriptor must be a string");
        return FunctionDescriptorParser.parse(input);
    }

    private List<Linker.Option> parseOptions(Object options) {
        if (options == null) {
            return List.of();
        }

        ArrayList<Linker.Option> res = new ArrayList<>();
        var map = asMap(options, "options must be a map");
        checkAttributes(map, "options", List.of(), List.of(DOWNCALL_OPTION_FIRST_VARIADIC_ARG, DOWNCALL_OPTION_CAPTURE_CALL_STATE, DOWNCALL_OPTION_CRITICAL));

        if (map.containsKey(DOWNCALL_OPTION_FIRST_VARIADIC_ARG)) {
            int firstVariadic = (int) asLong(map.get(DOWNCALL_OPTION_FIRST_VARIADIC_ARG), "");
            res.add(Linker.Option.firstVariadicArg(firstVariadic));
        }
        if (map.containsKey(DOWNCALL_OPTION_CAPTURE_CALL_STATE)) {
            if (asBoolean(map.get(DOWNCALL_OPTION_CAPTURE_CALL_STATE, ""), DOWNCALL_OPTION_CAPTURE_CALL_STATE)) {
                /*
                 * Dirty hack: we need the entrypoint to have a captured state, whatever said state
                 * is, so that the generated stub handles capture.
                 */
                res.add(Linker.Option.captureCallState("errno"));
            }
        }
        if (map.containsKey(DOWNCALL_OPTION_CRITICAL)) {
            var criticalOpt = map.get(DOWNCALL_OPTION_CRITICAL, "");
            if (criticalOpt instanceof Boolean b) {
                if (b) {
                    res.add(Linker.Option.critical(false));
                }
            } else if (criticalOpt instanceof EconomicMap<?, ?>) {
                @SuppressWarnings("unchecked")
                var criticalMap = (EconomicMap<String, Object>) criticalOpt;
                checkAttributes(criticalMap, DOWNCALL_OPTION_CRITICAL, List.of(), List.of(DOWNCALL_OPTION_ALLOW_HEAP_ACCESS));
                var allowHeapAccess = false;
                if (criticalMap.containsKey(DOWNCALL_OPTION_ALLOW_HEAP_ACCESS)) {
                    allowHeapAccess = asBoolean(criticalMap.get(DOWNCALL_OPTION_ALLOW_HEAP_ACCESS), DOWNCALL_OPTION_ALLOW_HEAP_ACCESS);
                }
                res.add(Linker.Option.critical(allowHeapAccess));
            } else {
                throw new JSONParserException(DOWNCALL_OPTION_ALLOW_HEAP_ACCESS + " should be a boolean or a map");
            }
        }

        return res;
    }
}
