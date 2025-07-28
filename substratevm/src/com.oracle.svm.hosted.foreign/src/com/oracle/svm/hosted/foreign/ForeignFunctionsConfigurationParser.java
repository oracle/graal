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

import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.ForeignConfigurationParser;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.foreign.MemoryLayoutParser.MemoryLayoutParserException;
import com.oracle.svm.hosted.reflect.NativeImageConditionResolver;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.util.json.JsonFormatter;
import jdk.graal.compiler.util.json.JsonParserException;
import jdk.internal.foreign.layout.ValueLayouts;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+22/src/java.base/share/classes/jdk/internal/foreign/abi/LinkerOptions.java")
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsConfigurationParser extends ForeignConfigurationParser<FunctionDescriptor, Linker.Option[]> {
    private static final String DOWNCALL_OPTION_CAPTURE_CALL_STATE = "captureCallState";
    private static final String DOWNCALL_OPTION_FIRST_VARIADIC_ARG = "firstVariadicArg";
    private static final String DOWNCALL_OPTION_CRITICAL = "critical";
    private static final String DOWNCALL_OPTION_ALLOW_HEAP_ACCESS = "allowHeapAccess";

    private static final Linker.Option[] EMPTY_OPTIONS = new Linker.Option[0];

    private final ImageClassLoader imageClassLoader;
    private final NativeImageConditionResolver conditionResolver;
    private final RuntimeForeignAccessSupport accessSupport;
    private final Map<String, MemoryLayout> canonicalLayouts;

    private Lookup implLookup;

    public ForeignFunctionsConfigurationParser(ImageClassLoader imageClassLoader, RuntimeForeignAccessSupport access, Map<String, MemoryLayout> canonicalLayouts) {
        super(EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION));
        this.imageClassLoader = imageClassLoader;
        this.conditionResolver = new NativeImageConditionResolver(imageClassLoader, ClassInitializationSupport.singleton());
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
    protected void registerDowncall(UnresolvedConfigurationCondition configurationCondition, FunctionDescriptor descriptor, Option[] options) {
        TypeResult<ConfigurationCondition> typeResult = conditionResolver.resolveCondition(configurationCondition);
        if (!typeResult.isPresent()) {
            return;
        }
        accessSupport.registerForDowncall(typeResult.get(), descriptor, (Object[]) options);
    }

    @Override
    protected void registerUpcall(UnresolvedConfigurationCondition configurationCondition, FunctionDescriptor descriptor, Option[] options) {
        TypeResult<ConfigurationCondition> typeResult = conditionResolver.resolveCondition(configurationCondition);
        if (!typeResult.isPresent()) {
            return;
        }
        accessSupport.registerForUpcall(typeResult.get(), descriptor, (Object[]) options);
    }

    @Override
    protected void registerDirectUpcallWithDescriptor(UnresolvedConfigurationCondition configurationCondition, String className, String methodName, FunctionDescriptor descriptor, Option[] options) {
        TypeResult<ConfigurationCondition> typeResult = conditionResolver.resolveCondition(configurationCondition);
        if (!typeResult.isPresent()) {
            return;
        }

        Class<?> aClass;
        try {
            aClass = imageClassLoader.forName(className);
        } catch (ClassNotFoundException e) {
            handleMissingElement(e, "Cannot find class '%s' used to register method(s) '%s' for a direct upcall(s). ", className, methodName);
            return;
        }

        /*
         * A FunctionDescriptor was provided, so we use it to create the MethodType and to lookup
         * the method. Since we have a MethodType, there should be exactly one method.
         */
        MethodType methodType = descriptor.toMethodType();
        MethodHandle target;
        try {
            target = getImplLookup().findStatic(aClass, methodName, methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            handleMissingElement(e, "Method '%s.%s(%s)' could not be registered as an upcall target method. " +
                            "Please verify that the method is static and that the parameter types match.",
                            className, methodName, methodType);
            return;
        }
        accessSupport.registerForDirectUpcall(typeResult.get(), target, descriptor, (Object[]) options);
    }

    @Override
    protected void registerDirectUpcallWithoutDescriptor(UnresolvedConfigurationCondition configurationCondition, String className, String methodName, EconomicMap<String, Object> optionsMap) {
        TypeResult<ConfigurationCondition> typeResult = conditionResolver.resolveCondition(configurationCondition);
        if (!typeResult.isPresent()) {
            return;
        }

        Class<?> aClass;
        try {
            aClass = imageClassLoader.forName(className);
        } catch (ClassNotFoundException e) {
            handleMissingElement(e, "Cannot find class '%s' used to register method(s) '%s' for a direct upcall. ", className, methodName);
            return;
        }

        List<Pair<FunctionDescriptor, MethodHandle>> descriptors;
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

        for (Pair<FunctionDescriptor, MethodHandle> pair : descriptors) {
            var options = createUpcallOptions(optionsMap, pair.getLeft());
            try {
                accessSupport.registerForDirectUpcall(typeResult.get(), pair.getRight(), pair.getLeft(), (Object[]) options);
            } catch (IllegalArgumentException e) {
                handleMissingElement(e, "Could not register direct upcall stub '%s.%s%s'", className, methodName, pair.getLeft().toMethodType());
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

    @Override
    protected FunctionDescriptor createFunctionDescriptor(String returnType, List<String> parameterTypes) {
        try {
            Optional<MemoryLayout> returnLayout = MemoryLayoutParser.parseAllowVoid(returnType, canonicalLayouts);
            MemoryLayout[] parameterLayouts = parseParameterTypes(parameterTypes);
            return returnLayout.map(memoryLayout -> FunctionDescriptor.of(memoryLayout, parameterLayouts)).orElseGet(() -> FunctionDescriptor.ofVoid(parameterLayouts));
        } catch (MemoryLayoutParserException e) {
            throw new JsonParserException(e.getMessage());
        }
    }

    private MemoryLayout[] parseParameterTypes(List<String> parameterTypes) throws MemoryLayoutParserException {
        MemoryLayout[] parameterLayouts = new MemoryLayout[parameterTypes.size()];
        for (int i = 0; i < parameterLayouts.length; i++) {
            parameterLayouts[i] = MemoryLayoutParser.parse(parameterTypes.get(i), canonicalLayouts);
        }
        return parameterLayouts;
    }

    /**
     * Parses the options allowed for downcalls. This needs to be consistent with
     * {@link jdk.internal.foreign.abi.LinkerOptions#forDowncall}.
     */
    @Override
    protected Option[] createDowncallOptions(EconomicMap<String, Object> map, FunctionDescriptor desc) {
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

        return res.toArray(Linker.Option[]::new);
    }

    /**
     * Parses the options allowed for upcalls (currently, no options are allowed). This needs to be
     * consistent with {@link jdk.internal.foreign.abi.LinkerOptions#forUpcall}.
     */
    @Override
    protected Option[] createUpcallOptions(EconomicMap<String, Object> map, FunctionDescriptor desc) {
        checkAttributes(map, "options", List.of(), List.of());
        return EMPTY_OPTIONS;
    }

    private Lookup getImplLookup() {
        if (implLookup == null) {
            implLookup = ReflectionUtil.readStaticField(MethodHandles.Lookup.class, "IMPL_LOOKUP");
        }
        return implLookup;
    }

    @Override
    protected void handleRegistrationError(Exception cause, EconomicMap<String, Object> map) {
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
