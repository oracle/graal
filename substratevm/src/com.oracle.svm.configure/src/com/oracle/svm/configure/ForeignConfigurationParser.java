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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;

/**
 * A base class for parsing FFM API configurations.
 *
 * @param <FD> the type of the function descriptor
 * @param <LO> the type of the linker options
 */
public abstract class ForeignConfigurationParser<FD, LO> extends ConditionalConfigurationParser {
    private static final String PARAMETER_TYPES = "parameterTypes";
    private static final String RETURN_TYPE = "returnType";

    public ForeignConfigurationParser(EnumSet<ConfigurationParserOption> parserOptions) {
        super(parserOptions);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        var foreignJson = getFromGlobalFile(json, FOREIGN_KEY);
        if (foreignJson == null) {
            return;
        }
        var topLevel = asMap(foreignJson, "first level of document must be a map");
        checkAttributes(topLevel, "foreign methods categories", List.of(), List.of("downcalls", "upcalls", "directUpcalls"));

        var downcalls = asList(topLevel.get("downcalls", List.of()), "downcalls must be an array of function descriptor and linker options");
        for (Object downcall : downcalls) {
            parseAndRegisterForeignCall(downcall, false);
        }

        var upcalls = asList(topLevel.get("upcalls", List.of()), "upcalls must be an array of function descriptor and linker options");
        for (Object upcall : upcalls) {
            parseAndRegisterForeignCall(upcall, true);
        }

        var directUpcalls = asList(topLevel.get("directUpcalls", List.of()), "direct upcalls must be an array of method references, function descriptors, and linker options");
        for (Object upcall : directUpcalls) {
            parseAndRegisterDirectUpcall(upcall);
        }
    }

    private void parseAndRegisterForeignCall(Object call, boolean forUpcall) {
        var map = asMap(call, "a foreign call must be a map");
        checkAttributes(map, "foreign call", List.of(RETURN_TYPE, PARAMETER_TYPES), List.of(CONDITIONAL_KEY, "options"));
        var condition = parseCondition(map, true);
        var descriptor = createFunctionDescriptor(map);
        var optionsMap = asMap(map.get("options", EconomicMap.emptyMap()), "options must be a map");
        if (forUpcall) {
            LO upcallOptions = createUpcallOptions(optionsMap, descriptor);
            try {
                registerUpcall(condition, descriptor, upcallOptions);
            } catch (Exception e) {
                handleRegistrationError(e, map);
            }
        } else {
            LO downcallOptions = createDowncallOptions(optionsMap, descriptor);
            try {
                registerDowncall(condition, descriptor, downcallOptions);
            } catch (Exception e) {
                handleRegistrationError(e, map);
            }
        }
    }

    private void parseAndRegisterDirectUpcall(Object call) {
        var map = asMap(call, "a foreign call must be a map");
        checkAttributes(map, "foreign call", List.of("class", "method"), List.of(CONDITIONAL_KEY, RETURN_TYPE, PARAMETER_TYPES, "options"));

        var condition = parseCondition(map, true);
        String className = asString(map.get("class"), "class");
        String methodName = asString(map.get("method"), "method");
        Object returnTypeInput = map.get(RETURN_TYPE);
        Object parameterTypesInput = map.get(PARAMETER_TYPES);
        var optionsMap = asMap(map.get("options", EconomicMap.emptyMap()), "options must be a map");

        if (returnTypeInput != null || parameterTypesInput != null) {
            FD descriptor = createFunctionDescriptor(map);
            LO upcallOptions = createUpcallOptions(optionsMap, descriptor);
            try {
                registerDirectUpcallWithDescriptor(condition, className, methodName, descriptor, upcallOptions);
            } catch (Exception e) {
                handleRegistrationError(e, map);
            }
        } else {
            try {
                registerDirectUpcallWithoutDescriptor(condition, className, methodName, optionsMap);
            } catch (Exception e) {
                handleRegistrationError(e, map);
            }
        }
    }

    private FD createFunctionDescriptor(EconomicMap<String, Object> map) {
        String returnTypeInput = asString(map.get(RETURN_TYPE), RETURN_TYPE);
        List<?> tmpParameterTypes = asList(map.get(PARAMETER_TYPES), "Element '" + PARAMETER_TYPES + "' must be a list");

        String[] parameterTypes = new String[tmpParameterTypes.size()];
        for (int i = 0; i < tmpParameterTypes.size(); i++) {
            parameterTypes[i] = asString(tmpParameterTypes.get(i), String.format("%s[%d]", PARAMETER_TYPES, i));
        }
        return createFunctionDescriptor(returnTypeInput, List.of(parameterTypes));
    }

    /**
     * Parses the descriptor based on the provided return type input and parameter types.
     *
     * @param returnType the return type of the descriptor
     * @param parameterTypes the parameter types of the descriptor
     * @return the parsed descriptor
     */
    protected abstract FD createFunctionDescriptor(String returnType, List<String> parameterTypes);

    /** Parses the options allowed for downcalls. */
    protected abstract LO createDowncallOptions(EconomicMap<String, Object> map, FD desc);

    /** Parses the options allowed for upcalls. */
    protected abstract LO createUpcallOptions(EconomicMap<String, Object> map, FD desc);

    protected abstract void registerDowncall(UnresolvedConfigurationCondition configurationCondition, FD descriptor, LO options);

    protected abstract void registerUpcall(UnresolvedConfigurationCondition configurationCondition, FD descriptor, LO options);

    protected abstract void registerDirectUpcallWithoutDescriptor(UnresolvedConfigurationCondition configurationCondition, String className, String methodName, EconomicMap<String, Object> optionsMap);

    protected abstract void registerDirectUpcallWithDescriptor(UnresolvedConfigurationCondition configurationCondition, String className, String methodName, FD descriptor, LO options);

    protected abstract void handleRegistrationError(Exception e, EconomicMap<String, Object> map);
}
