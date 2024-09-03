/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.truffle.tck.PermissionsFeature.AnalysisMethodNode;

import jdk.graal.compiler.util.json.JsonParserException;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

final class WhiteListParser extends ConfigurationParser {

    private static final String CONSTRUCTOR_NAME = "<init>";

    private final ImageClassLoader imageClassLoader;
    private final BigBang bb;
    private Set<AnalysisMethodNode> whiteList;

    WhiteListParser(ImageClassLoader imageClassLoader, BigBang bb) {
        super(true);
        this.imageClassLoader = Objects.requireNonNull(imageClassLoader, "ImageClassLoader must be non null");
        this.bb = Objects.requireNonNull(bb, "BigBang must be non null");
    }

    Set<AnalysisMethodNode> getLoadedWhiteList() {
        if (whiteList == null) {
            throw new IllegalStateException("Not parsed yet.");
        }
        return whiteList;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        if (whiteList == null) {
            whiteList = new HashSet<>();
        }
        parseClassArray(castList(json, "First level of document must be an array of class descriptors"));
    }

    private void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(castMap(clazz, "Second level of document must be class descriptor objects"));
        }
    }

    private void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "class descriptor object", Collections.singleton("name"), Arrays.asList("justification", "allDeclaredConstructors", "allDeclaredMethods", "methods"));
        Object classObject = data.get("name");
        String className = castProperty(classObject, String.class, "name");

        try {
            AnalysisType clazz = resolve(className);
            if (clazz == null) {
                throw new JsonParserException("Class " + className + " not found");
            }

            MapCursor<String, Object> cursor = data.getEntries();
            while (cursor.advance()) {
                String name = cursor.getKey();
                Object value = cursor.getValue();
                switch (name) {
                    case "allDeclaredConstructors":
                        if (castProperty(value, Boolean.class, "allDeclaredConstructors")) {
                            registerDeclaredConstructors(clazz);
                        }
                        break;
                    case "allDeclaredMethods":
                        if (castProperty(value, Boolean.class, "allDeclaredMethods")) {
                            registerDeclaredMethods(clazz);
                        }
                        break;
                    case "methods":
                        parseMethods(castList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
                        break;
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

    private void parseMethod(EconomicMap<String, Object> data, AnalysisType clazz) {
        checkAttributes(data, "method descriptor object", Collections.singleton("name"), Arrays.asList("justification", "parameterTypes"));
        String methodName = castProperty(data.get("name"), String.class, "name");
        List<AnalysisType> methodParameterTypes = null;

        Object parameterTypes = data.get("parameterTypes");
        if (parameterTypes != null) {
            methodParameterTypes = parseTypes(castList(parameterTypes, "Attribute 'parameterTypes' must be a list of type names"));
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
            throw new JsonParserException("Method " + clazz.toJavaName() + "." + methodName + " not found");
        }
    }

    private List<AnalysisType> parseTypes(List<Object> types) {
        List<AnalysisType> result = new ArrayList<>();
        for (Object type : types) {
            String typeName = castProperty(type, String.class, "types");
            try {
                AnalysisType clazz = resolve(typeName);
                if (clazz == null) {
                    throw new JsonParserException("Parameter type " + typeName + " not found");
                }
                result.add(clazz);
            } catch (UnsupportedPlatformException unsupportedPlatform) {
                throw new JsonParserException("Parameter type " + typeName + " is not available on active platform");
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
        Class<?> clz = imageClassLoader.findClass(useType).get();
        if (clz == null) {
            return null;
        }
        verifySupportedOnActivePlatform(clz);
        return bb.getMetaAccess().lookupJavaType(clz);
    }

    private void verifySupportedOnActivePlatform(Class<?> clz) throws UnsupportedPlatformException {
        AnalysisUniverse universe = bb.getUniverse();
        Package pkg = clz.getPackage();
        if (pkg != null && !universe.hostVM().platformSupported(pkg)) {
            throw new UnsupportedPlatformException(clz.getPackage());
        }
        Class<?> current = clz;
        do {
            if (!universe.hostVM().platformSupported(current)) {
                throw new UnsupportedPlatformException(current);
            }
            current = current.getEnclosingClass();
        } while (current != null);
    }

    private boolean registerMethod(AnalysisType type, String methodName, List<AnalysisType> formalParameters) {
        Predicate<ResolvedJavaMethod> p = (m) -> methodName.equals(m.getName());
        p = p.and(new SignaturePredicate(type, formalParameters, bb));
        Set<AnalysisMethodNode> methods = PermissionsFeature.findMethods(bb, type, p);
        whiteList.addAll(methods);
        return !methods.isEmpty();
    }

    private boolean registerAllMethodsWithName(AnalysisType type, String name) {
        Set<AnalysisMethodNode> methods = PermissionsFeature.findMethods(bb, type, (m) -> name.equals(m.getName()));
        whiteList.addAll(methods);
        return !methods.isEmpty();
    }

    private boolean registerConstructor(AnalysisType type, List<AnalysisType> formalParameters) {
        Predicate<ResolvedJavaMethod> p = new SignaturePredicate(type, formalParameters, bb);
        Set<AnalysisMethodNode> methods = PermissionsFeature.findConstructors(bb, type, p);
        whiteList.addAll(methods);
        return !methods.isEmpty();
    }

    private boolean registerDeclaredConstructors(AnalysisType type) {
        for (AnalysisMethod method : type.getDeclaredConstructors(false)) {
            whiteList.add(new AnalysisMethodNode(method));
        }
        return true;
    }

    private boolean registerDeclaredMethods(AnalysisType type) {
        for (AnalysisMethod method : type.getDeclaredMethods(false)) {
            whiteList.add(new AnalysisMethodNode(method));
        }
        return true;
    }

    private static <T> T cast(Object obj, Class<T> type, String errorMessage) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        throw new JsonParserException(errorMessage);
    }

    private static <T> T castProperty(Object obj, Class<T> type, String propertyName) {
        return cast(obj, type, "Invalid string value \"" + obj + "\" for element '" + propertyName + "'");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object obj, String errorMessage) {
        return cast(obj, List.class, errorMessage);
    }

    @SuppressWarnings("unchecked")
    private static EconomicMap<String, Object> castMap(Object obj, String errorMessage) {
        return cast(obj, EconomicMap.class, errorMessage);
    }

    private static final class SignaturePredicate implements Predicate<ResolvedJavaMethod> {

        private final ResolvedJavaType owner;
        private final List<? extends ResolvedJavaType> params;
        private final BigBang bb;

        SignaturePredicate(AnalysisType owner, List<? extends ResolvedJavaType> params, BigBang bb) {
            this.owner = OriginalClassProvider.getOriginalType(Objects.requireNonNull(owner, "Owner must be non null."));
            this.params = Objects.requireNonNull(params, "Params must be non null.");
            this.bb = Objects.requireNonNull(bb, "BigBang must be non null.");
        }

        @Override
        public boolean test(ResolvedJavaMethod t) {
            Signature signature = t.getSignature();
            if (params.size() != signature.getParameterCount(false)) {
                return false;
            }
            for (int i = 0; i < signature.getParameterCount(false); i++) {
                ResolvedJavaType st = bb.getUniverse().lookup(signature.getParameterType(i, owner));
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
