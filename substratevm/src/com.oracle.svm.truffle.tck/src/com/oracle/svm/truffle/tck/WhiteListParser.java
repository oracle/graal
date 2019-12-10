/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.tck;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.ImageClassLoader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import org.graalvm.nativeimage.Platforms;

final class WhiteListParser extends ConfigurationParser {

    private static final String CONSTRUCTOR_NAME = "<init>";

    private final ImageClassLoader imageClassLoader;
    private final BigBang bigBang;
    private Set<AnalysisMethod> whiteList;

    WhiteListParser(ImageClassLoader imageClassLoader, BigBang bigBang) {
        this.imageClassLoader = Objects.requireNonNull(imageClassLoader, "ImageClassLoader must be non null");
        this.bigBang = Objects.requireNonNull(bigBang, "BigBang must be non null");
    }

    Set<AnalysisMethod> getLoadedWhiteList() {
        if (whiteList == null) {
            throw new IllegalStateException("Not parsed yet.");
        }
        return whiteList;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        if (whiteList == null) {
            whiteList = new HashSet<>();
        }
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();
        parseClassArray(castList(json, "First level of document must be an array of class descriptors"));
    }

    private void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(castMap(clazz, "Second level of document must be class descriptor objects"));
        }
    }

    private void parseClass(Map<String, Object> data) {
        Object classObject = data.get("name");
        if (classObject == null) {
            throw new JSONParserException("Missing attribute 'name' in class descriptor object");
        }
        String className = castProperty(classObject, String.class, "name");

        try {
            AnalysisType clazz = resolve(className);
            if (clazz == null) {
                throw new JSONParserException("Class " + className + " not found");
            }

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (name.equals("name")) {
                    /* Already handled. */
                } else if (name.equals("justification")) {
                    /* Used only to document the whitelist file. */
                } else if (name.equals("allDeclaredConstructors")) {
                    if (castProperty(value, Boolean.class, "allDeclaredConstructors")) {
                        registerDeclaredConstructors(clazz);
                    }
                } else if (name.equals("allDeclaredMethods")) {
                    if (castProperty(value, Boolean.class, "allDeclaredMethods")) {
                        registerDeclaredMethods(clazz);
                    }
                } else if (name.equals("methods")) {
                    parseMethods(castList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
                } else {
                    throw new JSONParserException("Unknown attribute '" + name +
                                    "' (supported attributes: allDeclaredConstructors, allDeclaredMethods, methods, justification) in defintion of class " + className);
                }
            }
        } catch (UnsupportedPlatformException unsupportedPlatform) {
            // skip the type not available on active platform
        }
    }

    private void parseMethods(List<Object> methods, AnalysisType clazz) {
        for (Object method : methods) {
            parseMethod(castMap(method, "Elements of 'methods' array must be method descriptor objects"), clazz);
        }
    }

    private void parseMethod(Map<String, Object> data, AnalysisType clazz) {
        String methodName = null;
        List<AnalysisType> methodParameterTypes = null;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.equals("name")) {
                methodName = castProperty(entry.getValue(), String.class, "name");
            } else if (propertyName.equals("justification")) {
                /* Used only to document the whitelist file. */
            } else if (propertyName.equals("parameterTypes")) {
                methodParameterTypes = parseTypes(castList(entry.getValue(), "Attribute 'parameterTypes' must be a list of type names"));
            } else {
                throw new JSONParserException(
                                "Unknown attribute '" + propertyName + "' (supported attributes: 'name', 'parameterTypes', 'justification') in definition of method for class '" + clazz.toJavaName() +
                                                "'");
            }
        }

        if (methodName == null) {
            throw new JSONParserException("Missing attribute 'name' in definition of method for class '" + clazz.toJavaName() + "'");
        }

        boolean isConstructor = CONSTRUCTOR_NAME.equals(methodName);
        boolean found;
        if (methodParameterTypes != null) {
            if (isConstructor) {
                found = registerConstructor(clazz, methodParameterTypes);
            } else {
                found = registerMethod(clazz, methodName, methodParameterTypes);
            }
        } else {
            if (isConstructor) {
                found = registerDeclaredConstructors(clazz);
            } else {
                found = registerAllMethodsWithName(clazz, methodName);
            }
        }
        if (!found) {
            throw new JSONParserException("Method " + clazz.toJavaName() + "." + methodName + " not found");
        }
    }

    private List<AnalysisType> parseTypes(List<Object> types) {
        List<AnalysisType> result = new ArrayList<>();
        for (Object type : types) {
            String typeName = castProperty(type, String.class, "types");
            try {
                AnalysisType clazz = resolve(typeName);
                if (clazz == null) {
                    throw new JSONParserException("Parameter type " + typeName + " not found");
                }
                result.add(clazz);
            } catch (UnsupportedPlatformException unsupportedPlatform) {
                throw new JSONParserException("Parameter type " + typeName + " is not available on active platform");
            }
        }
        return result;
    }

    private AnalysisType resolve(String type) throws UnsupportedPlatformException {
        String useType;
        if (type.indexOf('[') != -1) {
            useType = MetaUtil.internalNameToJava(MetaUtil.toInternalName(type), true, true);
        } else {
            useType = type;
        }
        Class<?> clz = imageClassLoader.findClassByName(useType, false);
        verifySupportedOnActivePlatform(clz);
        return bigBang.forClass(clz);
    }

    private void verifySupportedOnActivePlatform(Class<?> clz) throws UnsupportedPlatformException {
        AnalysisUniverse universe = bigBang.getUniverse();
        Package pkg = clz.getPackage();
        if (pkg != null && !universe.platformSupported(pkg)) {
            throw new UnsupportedPlatformException(clz.getPackage());
        }
        Class<?> current = clz;
        do {
            if (!universe.platformSupported(current)) {
                throw new UnsupportedPlatformException(current);
            }
            current = current.getEnclosingClass();
        } while (current != null);
    }

    private boolean registerMethod(AnalysisType type, String methodName, List<AnalysisType> formalParameters) {
        Predicate<ResolvedJavaMethod> p = (m) -> methodName.equals(m.getName());
        p = p.and(new SignaturePredicate(type, formalParameters, bigBang));
        Set<AnalysisMethod> methods = PermissionsFeature.findMethods(bigBang, type, p);
        for (AnalysisMethod method : methods) {
            whiteList.add(method);
        }
        return !methods.isEmpty();
    }

    private boolean registerAllMethodsWithName(AnalysisType type, String name) {
        Set<AnalysisMethod> methods = PermissionsFeature.findMethods(bigBang, type, (m) -> name.equals(m.getName()));
        for (AnalysisMethod method : methods) {
            whiteList.add(method);
        }
        return !methods.isEmpty();
    }

    private boolean registerConstructor(AnalysisType type, List<AnalysisType> formalParameters) {
        Predicate<ResolvedJavaMethod> p = new SignaturePredicate(type, formalParameters, bigBang);
        Set<AnalysisMethod> methods = PermissionsFeature.findConstructors(bigBang, type, p);
        for (AnalysisMethod method : methods) {
            whiteList.add(method);
        }
        return !methods.isEmpty();
    }

    private boolean registerDeclaredConstructors(AnalysisType type) {
        for (AnalysisMethod method : type.getDeclaredConstructors()) {
            whiteList.add(method);
        }
        return true;
    }

    private boolean registerDeclaredMethods(AnalysisType type) {
        for (AnalysisMethod method : type.getDeclaredMethods()) {
            whiteList.add(method);
        }
        return true;
    }

    private static <T> T cast(Object obj, Class<T> type, String errorMessage) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        throw new JSONParserException(errorMessage);
    }

    private static <T> T castProperty(Object obj, Class<T> type, String propertyName) {
        return cast(obj, type, "Invalid string value \"" + obj + "\" for element '" + propertyName + "'");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object obj, String errorMessage) {
        return cast(obj, List.class, errorMessage);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object obj, String errorMessage) {
        return cast(obj, Map.class, errorMessage);
    }

    private static final class SignaturePredicate implements Predicate<ResolvedJavaMethod> {

        private final ResolvedJavaType owner;
        private final List<? extends ResolvedJavaType> params;
        private final BigBang bigBang;

        SignaturePredicate(AnalysisType owner, List<? extends ResolvedJavaType> params, BigBang bigBang) {
            this.owner = Objects.requireNonNull(owner, "Owner must be non null.").getWrappedWithoutResolve();
            this.params = Objects.requireNonNull(params, "Params must be non null.");
            this.bigBang = Objects.requireNonNull(bigBang, "BigBang must be non null.");
        }

        @Override
        public boolean test(ResolvedJavaMethod t) {
            Signature signaure = t.getSignature();
            if (params.size() != signaure.getParameterCount(false)) {
                return false;
            }
            for (int i = 0; i < signaure.getParameterCount(false); i++) {
                ResolvedJavaType st = bigBang.getUniverse().lookup(signaure.getParameterType(i, owner));
                ResolvedJavaType pt = params.get(i);
                if (!pt.equals(st)) {
                    return false;
                }
            }
            return true;
        }
    }

    @SuppressWarnings("serial")
    private static final class UnsupportedPlatformException extends Exception {

        UnsupportedPlatformException(Class<?> clazz) {
            super(String.format("The class %s is supported only on platforms: %s",
                            clazz.getName(),
                            Arrays.toString(clazz.getAnnotation(Platforms.class).value())));
        }

        UnsupportedPlatformException(Package pkg) {
            super(String.format("The package %s is supported only on platforms: %s",
                            pkg.getName(),
                            Arrays.toString(pkg.getAnnotation(Platforms.class).value())));
        }

    }
}
