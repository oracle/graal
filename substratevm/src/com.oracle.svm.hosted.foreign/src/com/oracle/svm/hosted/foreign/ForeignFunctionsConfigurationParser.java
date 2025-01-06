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
import java.lang.foreign.Linker.Option;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.graal.compiler.util.json.JsonParserException;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/jdk/internal/foreign/abi/LinkerOptions.java")
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsConfigurationParser extends ConfigurationParser {
    private static final String DOWNCALL_OPTION_CAPTURE_CALL_STATE = "captureCallState";
    private static final String DOWNCALL_OPTION_FIRST_VARIADIC_ARG = "firstVariadicArg";
    private static final String DOWNCALL_OPTION_CRITICAL = "critical";
    private static final String DOWNCALL_OPTION_ALLOW_HEAP_ACCESS = "allowHeapAccess";

    private final ImageClassLoader imageClassLoader;
    private final RuntimeForeignAccessSupport accessSupport;

    public ForeignFunctionsConfigurationParser(ImageClassLoader imageClassLoader, RuntimeForeignAccessSupport access) {
        super(true);
        this.imageClassLoader = imageClassLoader;
        this.accessSupport = access;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        var topLevel = asMap(json, "first level of document must be a map");
        checkAttributes(topLevel, "foreign methods categories", List.of(), List.of("downcalls", "upcalls", "directUpcalls"));

        var downcalls = asList(topLevel.get("downcalls", List.of()), "downcalls must be an array of function descriptor and linker options");
        for (Object downcall : downcalls) {
            parseAndRegisterForeignCall(downcall, this::parseDowncallOptions, (descriptor, options) -> accessSupport.registerForDowncall(ConfigurationCondition.alwaysTrue(), descriptor, options));
        }

        var upcalls = asList(topLevel.get("upcalls", List.of()), "upcalls must be an array of function descriptor and linker options");
        for (Object upcall : upcalls) {
            parseAndRegisterForeignCall(upcall, this::parseUpcallOptions, (descriptor, options) -> accessSupport.registerForUpcall(ConfigurationCondition.alwaysTrue(), descriptor, options));
        }

        var directUpcalls = asList(topLevel.get("directUpcalls", List.of()), "direct upcalls must be an array of method references, function descriptors, and linker options");
        for (Object upcall : directUpcalls) {
            parseAndRegisterDirectUpcall(upcall);
        }
    }

    private void parseAndRegisterForeignCall(Object call, Function<EconomicMap<String, Object>, List<Option>> optionsParser, BiConsumer<Object, Object[]> register) {
        var map = asMap(call, "a foreign call must be a map");
        checkAttributes(map, "foreign call", List.of("descriptor"), List.of("options"));
        var descriptor = parseDescriptor(map.get("descriptor"));
        var options = map.get("options", EconomicMap.emptyMap());
        List<Option> parsedOptions = optionsParser.apply(asMap(options, "options must be a map"));
        register.accept(descriptor, parsedOptions.toArray());
    }

    private void parseAndRegisterDirectUpcall(Object call) {
        var map = asMap(call, "a foreign call must be a map");
        checkAttributes(map, "foreign call", List.of("class", "method", "descriptor"), List.of("options"));
        String className = asString(map.get("class"));
        String methodName = asString(map.get("method"));
        FunctionDescriptor descriptor = parseDescriptor(map.get("descriptor"));
        var options = parseUpcallOptions(asMap(map.get("options", EconomicMap.emptyMap()), "options must be a map"));

        MethodType methodType = descriptor.toMethodType();
        try {
            Class<?> aClass = imageClassLoader.forName(className);
            MethodHandle methodHandle = MethodHandles.publicLookup().findStatic(aClass, methodName, methodType);
            accessSupport.registerForDirectUpcall(ConfigurationCondition.alwaysTrue(), methodHandle, descriptor, options.toArray());
        } catch (ClassNotFoundException e) {
            throw UserError.abort(e, "Cannot find class '%s' used to register method '%s' for a direct upcall. ",
                            className, methodName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw UserError.abort(e, "Method '%s.%s(%s)' could not be registered as an upcall target method. " +
                            "Please verify that the method is public, static and that the parameter types match.",
                            className, methodName, methodType);
        }
    }

    private FunctionDescriptor parseDescriptor(Object signature) {
        String input = asString(signature, "a function descriptor must be a string");
        return FunctionDescriptorParser.parse(input);
    }

    /**
     * Parses the options allowed for downcalls. This needs to be consistent with
     * 'jdk.internal.foreign.abi.LinkerOptions.forDowncall'.
     */
    private List<Linker.Option> parseDowncallOptions(EconomicMap<String, Object> map) {
        checkAttributes(map, "options", List.of(), List.of(DOWNCALL_OPTION_FIRST_VARIADIC_ARG, DOWNCALL_OPTION_CAPTURE_CALL_STATE, DOWNCALL_OPTION_CRITICAL));

        ArrayList<Linker.Option> res = new ArrayList<>();
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
                throw new JsonParserException(DOWNCALL_OPTION_ALLOW_HEAP_ACCESS + " should be a boolean or a map");
            }
        }

        return res;
    }

    /**
     * Parses the options allowed for upcalls (currently, no options are allowed). This needs to be
     * consistent with 'jdk.internal.foreign.abi.LinkerOptions.forUpcall'.
     */
    private List<Linker.Option> parseUpcallOptions(EconomicMap<String, Object> map) {
        checkAttributes(map, "options", List.of(), List.of());
        return List.of();
    }
}
