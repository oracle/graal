/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.Linker.Option;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.foreign.MemoryLayoutParser.MemoryLayoutParserException;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.util.json.JsonFormatter;
import jdk.graal.compiler.util.json.JsonParserException;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.layout.ValueLayouts;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/java.base/share/classes/jdk/internal/foreign/abi/LinkerOptions.java")
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsConfigurationParser extends ConfigurationParser {
    private static final String DOWNCALL_OPTION_CAPTURE_CALL_STATE = "captureCallState";
    private static final String DOWNCALL_OPTION_FIRST_VARIADIC_ARG = "firstVariadicArg";
    private static final String DOWNCALL_OPTION_CRITICAL = "critical";
    private static final String DOWNCALL_OPTION_ALLOW_HEAP_ACCESS = "allowHeapAccess";
    private static final String PARAMETER_TYPES = "parameterTypes";
    private static final String RETURN_TYPE = "returnType";

    private final ImageClassLoader imageClassLoader;
    private final RuntimeForeignAccessSupport accessSupport;
    private final Map<String, MemoryLayout> canonicalLayouts;

    private Lookup implLookup;

    public ForeignFunctionsConfigurationParser(ImageClassLoader imageClassLoader, RuntimeForeignAccessSupport access, Map<String, MemoryLayout> canonicalLayouts) {
        super(EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION));
        this.imageClassLoader = imageClassLoader;
        this.accessSupport = access;
        this.canonicalLayouts = canonicalLayouts;
    }

    @Override
    protected EnumSet<ConfigurationParserOption> supportedOptions() {
        EnumSet<ConfigurationParserOption> base = super.supportedOptions();
        base.add(ConfigurationParserOption.PRINT_MISSING_ELEMENTS);
        return base;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        var topLevel = asMap(json, "first level of document must be a map");
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
        checkAttributes(map, "foreign call", List.of(RETURN_TYPE, PARAMETER_TYPES), List.of("options"));
        var descriptor = parseDescriptor(map);
        var optionsMap = asMap(map.get("options", EconomicMap.emptyMap()), "options must be a map");
        try {
            if (forUpcall) {
                var options = parseUpcallOptions(optionsMap);
                accessSupport.registerForUpcall(ConfigurationCondition.alwaysTrue(), descriptor, options.toArray());
            } else {
                var options = parseDowncallOptions(optionsMap, descriptor);
                accessSupport.registerForDowncall(ConfigurationCondition.alwaysTrue(), descriptor, options.toArray());
            }
        } catch (IllegalArgumentException e) {
            handleRegistrationError(e, map);
        }
    }

    private void parseAndRegisterDirectUpcall(Object call) {
        var map = asMap(call, "a foreign call must be a map");
        checkAttributes(map, "foreign call", List.of("class", "method"), List.of(RETURN_TYPE, PARAMETER_TYPES, "options"));

        String className = asString(map.get("class"), "class");
        String methodName = asString(map.get("method"), "method");
        Class<?> aClass;
        try {
            aClass = imageClassLoader.forName(className);
        } catch (ClassNotFoundException e) {
            handleMissingElement(e, "Cannot find class '%s' used to register method(s) '%s' for a direct upcall(s). ", className, methodName);
            return;
        }

        List<Pair<FunctionDescriptor, MethodHandle>> descriptors;
        Object returnTypeInput = map.get(RETURN_TYPE);
        Object parameterTypesInput = map.get(PARAMETER_TYPES);
        if (returnTypeInput != null || parameterTypesInput != null) {
            /*
             * A FunctionDescriptor was provided, so we use it to create the MethodType and to
             * lookup the method. Since we have a MethodType, there should be exactly one method.
             */
            FunctionDescriptor descriptor = parseDescriptor(map);
            MethodType methodType = descriptor.toMethodType();
            try {
                descriptors = List.of(Pair.create(descriptor, getImplLookup().findStatic(aClass, methodName, methodType)));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                handleMissingElement(e, "Method '%s.%s(%s)' could not be registered as an upcall target method. " +
                                "Please verify that the method is static and that the parameter types match.",
                                className, methodName, methodType);
                return;
            }
        } else {
            // FunctionDescriptor was not provided; derive from method signature(s)
            try {
                descriptors = new LinkedList<>();
                for (Method method : findStaticMethods(aClass, methodName)) {
                    try {
                        descriptors.add(Pair.create(deriveFunctionDescriptor(method), getImplLookup().unreflect(method)));
                    } catch (AmbiguousParameterType | InvalidCarrierType e) {
                        handleMissingElement(e);
                    } catch (IllegalAccessException e) {
                        handleMissingElement(e, "Method '%s.%s' and all its possible overloads could not be registered as upcall target methods. " +
                                        "Please verify that all overloads of the method are static.",
                                        className, methodName);
                    }
                }
            } catch (NoSuchMethodException e) {
                handleMissingElement(e, "Method '%s.%s' and all its possible overloads could not be registered as upcall target methods. " +
                                "Please verify that all overloads of the method are static.",
                                className, methodName);
                return;
            }
        }

        for (Pair<FunctionDescriptor, MethodHandle> pair : descriptors) {
            var optionsMap = asMap(map.get("options", EconomicMap.emptyMap()), "options must be a map");
            try {
                var options = parseUpcallOptions(optionsMap);
                accessSupport.registerForDirectUpcall(ConfigurationCondition.alwaysTrue(), pair.getRight(), pair.getLeft(), options.toArray());
            } catch (IllegalArgumentException e) {
                handleRegistrationError(e, map);
            }
        }
    }

    private static List<Method> findStaticMethods(Class<?> clazz, String methodName) throws NoSuchMethodException {
        List<Method> result = new LinkedList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && methodName.equals(method.getName())) {
                result.add(method);
            }
        }
        if (result.isEmpty()) {
            throw new NoSuchMethodException(methodName);
        }
        return result;
    }

    private static FunctionDescriptor deriveFunctionDescriptor(Method method) throws AmbiguousParameterType {
        MemoryLayout resLayout = deriveMemoryLayout(method, null, method.getReturnType());
        MemoryLayout[] argLayouts = Arrays.stream(method.getParameters()).map(p -> ForeignFunctionsConfigurationParser.deriveMemoryLayout(method, p.getName(), p.getType()))
                        .toArray(MemoryLayout[]::new);
        return resLayout != null ? FunctionDescriptor.of(resLayout, argLayouts) : FunctionDescriptor.ofVoid(argLayouts);
    }

    private static MemoryLayout deriveMemoryLayout(Method method, String parameterName, Class<?> type) throws AmbiguousParameterType {
        // return type 'void' is allowed
        if (parameterName == null && type == void.class) {
            return null;
        }
        try {
            ValueLayout valueLayout = ValueLayouts.valueLayout(type, ByteOrder.nativeOrder());
            if (valueLayout instanceof AddressLayout) {
                assert type == MemorySegment.class;
                throw new AmbiguousParameterType(String.format("" +
                                "%s type \"%s\" of method \"%s.%s\" requires additional information. " +
                                "Please specify the layout (such as 'struct', or void* for a pointer) for the parameter and return types.",
                                describe(parameterName), MemorySegment.class.getName(), method.getDeclaringClass().getName(), method.getName()));
            }
            return valueLayout;
        } catch (IllegalArgumentException e) {
            throw new InvalidCarrierType(String.format("" +
                            "%s type \"%s\" of method \"%s.%s\" cannot be mapped to a ValueLayout. " +
                            "This method cannot be used for a direct upcall. " +
                            "If there are multiple overloads of this method and another overload should be use, you need to provide the parameter and return types.",
                            describe(parameterName), type.getTypeName(), method.getDeclaringClass().getName(), method.getName()));
        }
    }

    private static String describe(String parameterName) {
        return parameterName != null ? "Parameter \"" + parameterName + "\" with" : "Return";
    }

    private Optional<MemoryLayout> parseReturnType(Object signature) throws MemoryLayoutParserException {
        String input = asString(signature, RETURN_TYPE);
        return MemoryLayoutParser.parseAllowVoid(input, canonicalLayouts);
    }

    private MemoryLayout[] parseParameterTypes(Object parameterTypesObject) throws MemoryLayoutParserException {
        List<?> parameterTypesList = asList(parameterTypesObject, "Element '" + PARAMETER_TYPES + "' must be a list");
        MemoryLayout[] parameterTypes = new MemoryLayout[parameterTypesList.size()];
        for (int i = 0; i < parameterTypes.length; i++) {
            String parameterTypeString = asString(parameterTypesList.get(i), String.format("%s[%d]", PARAMETER_TYPES, i));
            parameterTypes[i] = MemoryLayoutParser.parse(parameterTypeString, canonicalLayouts);
        }
        return parameterTypes;
    }

    private FunctionDescriptor parseDescriptor(EconomicMap<String, Object> map) {
        return parseDescriptor(map.get(RETURN_TYPE), map.get(PARAMETER_TYPES));
    }

    private FunctionDescriptor parseDescriptor(Object returnTypeInput, Object parameterTypeInput) {
        try {
            Optional<MemoryLayout> returnType = parseReturnType(returnTypeInput);
            MemoryLayout[] parameterTypes = parseParameterTypes(parameterTypeInput);
            return returnType.map(memoryLayout -> FunctionDescriptor.of(memoryLayout, parameterTypes)).orElseGet(() -> FunctionDescriptor.ofVoid(parameterTypes));
        } catch (MemoryLayoutParserException e) {
            throw new JsonParserException(e.getMessage());
        }
    }

    /**
     * Parses the options allowed for downcalls. This needs to be consistent with
     * 'jdk.internal.foreign.abi.LinkerOptions.forDowncall'.
     */
    private List<Linker.Option> parseDowncallOptions(EconomicMap<String, Object> map, FunctionDescriptor desc) {
        checkAttributes(map, "options", List.of(), List.of(DOWNCALL_OPTION_FIRST_VARIADIC_ARG, DOWNCALL_OPTION_CAPTURE_CALL_STATE, DOWNCALL_OPTION_CRITICAL));

        ArrayList<Option> res = new ArrayList<>();
        if (map.containsKey(DOWNCALL_OPTION_FIRST_VARIADIC_ARG)) {
            int firstVariadic = (int) asLong(map.get(DOWNCALL_OPTION_FIRST_VARIADIC_ARG), "");
            if (firstVariadic < 0 || firstVariadic > desc.argumentLayouts().size()) {
                throw new JsonParserException(DOWNCALL_OPTION_FIRST_VARIADIC_ARG + ": Index '" + firstVariadic + "' not in bounds for desc: " + desc);
            }
            res.add(Linker.Option.firstVariadicArg(firstVariadic));
        }
        if (map.containsKey(DOWNCALL_OPTION_CAPTURE_CALL_STATE)) {
            if (asBoolean(map.get(DOWNCALL_OPTION_CAPTURE_CALL_STATE, ""), DOWNCALL_OPTION_CAPTURE_CALL_STATE)) {
                /*
                 * We just need to know if a call state will be captured. For creating the stub, it
                 * doesn't matter which state exactly is captured because the stub will get this
                 * information from the run-time NativeEntryPoint object. So, we always use
                 * 'Linker.Option.captureCallState("errno")' here.
                 */
                res.add(Linker.Option.captureCallState("errno"));
            }
        }
        if (map.containsKey(DOWNCALL_OPTION_CRITICAL)) {
            var criticalOpt = map.get(DOWNCALL_OPTION_CRITICAL, "");
            if (criticalOpt instanceof EconomicMap<?, ?>) {
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

    private Lookup getImplLookup() {
        if (implLookup == null) {
            implLookup = ReflectionUtil.readStaticField(MethodHandles.Lookup.class, "IMPL_LOOKUP");
        }
        return implLookup;
    }

    protected void handleRegistrationError(Throwable cause, EconomicMap<String, Object> map) {
        handleMissingElement(cause, "Could not register foreign stub '%s'", JsonFormatter.formatJson(map));
    }

    protected void handleMissingElement(Throwable cause, String format, Object... args) {
        if (checkOption(ConfigurationParserOption.PRINT_MISSING_ELEMENTS)) {
            String message = format.formatted(args);
            if (cause != null) {
                message += " Reason: " + cause.getClass().getTypeName() + ": " + cause.getMessage() + '.';
            }
            LogUtils.warning(message);
        }
    }

    protected void handleMissingElement(Throwable reason) {
        if (checkOption(ConfigurationParserOption.PRINT_MISSING_ELEMENTS)) {
            LogUtils.warning(reason.getClass().getTypeName() + ": " + reason.getMessage());
        }
    }

    @SuppressWarnings("serial")
    private static final class AmbiguousParameterType extends RuntimeException {

        AmbiguousParameterType(String message) {
            super(message);
        }
    }

    @SuppressWarnings("serial")
    private static final class InvalidCarrierType extends RuntimeException {

        InvalidCarrierType(String message) {
            super(message);
        }
    }
}
