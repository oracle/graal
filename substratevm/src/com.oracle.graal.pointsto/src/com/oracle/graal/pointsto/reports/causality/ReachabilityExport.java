/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;

public final class ReachabilityExport {
    // TODO: unshare counter, assign ids when writing
    private static final AtomicInteger idCounter = new AtomicInteger();

    public abstract static class HierarchyNode {
        public int id = idCounter.incrementAndGet();

        EconomicMap<String, Object> serialize() {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put("id", id);
            return map;
        }
    }

    private static class Method extends HierarchyNode {
        public final boolean synthetic;
        public boolean isMain;
        public int codeSize;

        Method(boolean synthetic) {
            this.synthetic = synthetic;
        }

        @Override
        EconomicMap<String, Object> serialize() {
            EconomicMap<String, Object> map = super.serialize();
            ArrayList<String> flagsList = new ArrayList<>();

            if (synthetic) {
                flagsList.add("synthetic");
            }
            if (isMain) {
                flagsList.add("main");
            }
            if (!flagsList.isEmpty()) {
                map.put("flags", flagsList.toArray());
            }
            map.put("size", codeSize);
            return map;
        }
    }

    private static class Field extends HierarchyNode {
        public final boolean synthetic;

        Field(boolean synthetic) {
            this.synthetic = synthetic;
        }

        @Override
        EconomicMap<String, Object> serialize() {
            EconomicMap<String, Object> map = super.serialize();
            ArrayList<String> flagsList = new ArrayList<>();
            if (synthetic) {
                flagsList.add("synthetic");
            }
            if (!flagsList.isEmpty()) {
                map.put("flags", flagsList.toArray());
            }
            return map;
        }
    }

    private static class Type extends HierarchyNode {
        public final Map<String, Method> methods = new HashMap<>();
        public final Map<String, ReachabilityExport.Field> fields = new HashMap<>();
        public final boolean synthetic;

        Type(Class<?> type) {
            synthetic = type.isSynthetic();
        }

        @Override
        EconomicMap<String, Object> serialize() {
            EconomicMap<String, Object> map = super.serialize();

            EconomicMap<String, Object> jsonMethods = EconomicMap.create();
            EconomicMap<String, Object> jsonFields = EconomicMap.create();

            for (var m : methods.entrySet()) {
                jsonMethods.put(m.getKey(), m.getValue().serialize());
            }

            for (var f : fields.entrySet()) {
                jsonFields.put(f.getKey(), f.getValue().serialize());
            }

            map.put("methods", jsonMethods);
            map.put("fields", jsonFields);

            ArrayList<String> flagsList = new ArrayList<>();
            if (synthetic) {
                flagsList.add("synthetic");
            }
            if (!flagsList.isEmpty()) {
                map.put("flags", flagsList.toArray());
            }
            return map;
        }
    }

    private static class Package extends HierarchyNode {
        public final HashMap<String, Type> types = new HashMap<>();

        @Override
        public EconomicMap<String, Object> serialize() {
            EconomicMap<String, Object> map = super.serialize();
            EconomicMap<String, Object> typeMap = EconomicMap.create(types.size());

            for (Map.Entry<String, Type> t : types.entrySet()) {
                typeMap.put(t.getKey(), t.getValue().serialize());
            }

            map.put("types", typeMap);
            return map;
        }
    }

    private static class File extends HierarchyNode {
        @Override
        public EconomicMap<String, Object> serialize() {
            return super.serialize();
        }
    }

    private static class TopLevelOrigin extends HierarchyNode {
        public final String path;
        public final String module;
        public final boolean isSystem;

        public final HashMap<String, Package> packages = new HashMap<>();
        public final HashMap<String, File> files = new HashMap<>();

        TopLevelOrigin(String path, String module, boolean isSystem) {
            this.path = path;
            this.module = module;
            this.isSystem = isSystem;
        }

        @Override
        public EconomicMap<String, Object> serialize() {
            EconomicMap<String, Object> map = super.serialize();
            if (path != null) {
                map.put("path", path);
            }
            if (module != null) {
                map.put("module", module);
            }
            if (isSystem) {
                map.put("flags", new Object[]{"system"});
            }

            EconomicMap<String, Object> filesMap = EconomicMap.create();
            for (var f : files.entrySet()) {
                filesMap.put(f.getKey(), f.getValue().serialize());
            }
            map.put("files", filesMap);

            EconomicMap<String, Object> packagesMap = EconomicMap.create();
            for (Map.Entry<String, Package> p : packages.entrySet()) {
                packagesMap.put(p.getKey(), p.getValue().serialize());
            }
            map.put("packages", packagesMap);
            return map;
        }
    }

    private static String findTopLevelOriginName(Class<?> clazz) {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL url = codeSource.getLocation();
            if (url != null && "file".equals(url.getProtocol())) {
                return url.getPath();
            }
        }
        return null;
    }

    private static String findModuleName(Class<?> clazz) {
        return clazz.getModule().getName();
    }

    private final HashMap<Pair<String, String>, TopLevelOrigin> topLevelOrigins = new HashMap<>();

    private final Predicate<ClassLoader> isImageClassLoader;

    public ReachabilityExport(AnalysisUniverse universe, Predicate<ClassLoader> isImageClassLoader) {
        this.isImageClassLoader = isImageClassLoader;
        universe.getTypes().stream().filter(AnalysisType::isReachable).filter(t -> !t.isArray()).forEach(this::computeIfAbsent);
        universe.getMethods().stream().filter(AnalysisMethod::isReachable).forEach(this::computeIfAbsent);
        universe.getFields().stream().filter(AnalysisField::isReachable).forEach(this::computeIfAbsent);
    }

    public void addCodeSize(AnalysisMethod m, int nBytes) {
        computeIfAbsent(m).codeSize += nBytes;
    }

    public void setMainMethod(AnalysisMethod mainMethod) {
        computeIfAbsent(mainMethod).isMain = true;
    }

    private Type computeIfAbsent(Class<?> clazz, String unqualifiedName) {
        String topLevelOriginName = findTopLevelOriginName(clazz);
        String moduleName = findModuleName(clazz);
        boolean isSystemCode = !isImageClassLoader.test(clazz.getClassLoader());
        TopLevelOrigin tlo = topLevelOrigins.computeIfAbsent(Pair.create(topLevelOriginName, moduleName), pair -> new TopLevelOrigin(pair.getLeft(), pair.getRight(), isSystemCode));
        assert tlo.isSystem == isSystemCode : "Class loader is expected to be the same for all classes of the module.";
        Package p = tlo.packages.computeIfAbsent(clazz.getPackageName(), name -> new Package());

        return p.types.computeIfAbsent(unqualifiedName, name -> new Type(clazz));
    }

    public Type computeIfAbsent(AnalysisType type) {
        return computeIfAbsent(type.getJavaClass(), type.toJavaName(false));
    }

    public Method computeIfAbsent(AnalysisMethod m) {
        Type t = computeIfAbsent(m.getDeclaringClass());
        return t.methods.computeIfAbsent(m.format("%n(%P):%R"), name -> new Method(m.isSynthetic()));
    }

    public Field computeIfAbsent(AnalysisField f) {
        Type t = computeIfAbsent(f.getDeclaringClass());
        return t.fields.computeIfAbsent(f.getName(), name -> new Field(f.isSynthetic()));
    }

    public Type computeIfAbsent(AnalysisMetaAccess metaAccess, Class<?> clazz) {
        try {
            return computeIfAbsent(metaAccess.lookupJavaType(clazz));
        } catch (UnsupportedFeatureException ex) {
            String qualifiedName = clazz.getTypeName();
            int packageSeparatorIndex = qualifiedName.lastIndexOf('.');
            String unqalifiedName = packageSeparatorIndex == -1 ? qualifiedName : qualifiedName.substring(packageSeparatorIndex + 1);
            return computeIfAbsent(clazz, unqalifiedName);
        }
    }

    public Method computeIfAbsent(AnalysisMetaAccess metaAccess, java.lang.reflect.Executable executable) {
        try {
            return computeIfAbsent(metaAccess.lookupJavaMethod(executable));
        } catch (UnsupportedFeatureException ex) {
            Type t = computeIfAbsent(metaAccess, executable.getDeclaringClass());
            return t.methods.computeIfAbsent(toGraalLikeString(executable), name -> new Method(executable.isSynthetic()));
        }
    }

    public Field computeIfAbsent(AnalysisMetaAccess metaAccess, java.lang.reflect.Field f) {
        try {
            return computeIfAbsent(metaAccess.lookupJavaField(f));
        } catch (UnsupportedFeatureException ex) {
            Type t = computeIfAbsent(metaAccess, f.getDeclaringClass());
            return t.fields.computeIfAbsent(toGraalLikeString(f), name -> new Field(f.isSynthetic()));
        }
    }

    public File computeIfAbsent(URI independentFile) {
        String topLevelOriginPath;
        String filePath;

        if (independentFile.getPath() != null) {
            topLevelOriginPath = null;
            filePath = independentFile.getPath();
        } else {
            String path = independentFile.toString();
            if (!path.startsWith("jar:file:")) {
                throw new RuntimeException("Unexpected URI");
            }

            path = path.substring(9);
            int splitIndex = path.indexOf('!');
            if (splitIndex == -1) {
                throw new RuntimeException("Unexpected URI");
            }
            topLevelOriginPath = path.substring(0, splitIndex);
            filePath = path.substring(splitIndex + 1);
        }

        var tlo = topLevelOrigins.computeIfAbsent(Pair.empty(), pair -> new TopLevelOrigin(null, null, false));
        return tlo.files.computeIfAbsent(filePath, p -> new File());
    }

    public Object serialize() {
        return topLevelOrigins.values().stream().sorted(Comparator.comparing((TopLevelOrigin tlo) -> tlo.path != null ? tlo.path : "").thenComparing(tlo -> tlo.module != null ? tlo.module : ""))
                        .map(TopLevelOrigin::serialize).toArray();
    }

    public static String toGraalLikeString(java.lang.reflect.Method m) {
        return m.getDeclaringClass().getTypeName() + '.' + m.getName() + '(' + Arrays.stream(m.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + "):" +
                        m.getReturnType().getTypeName();
    }

    public static String toGraalLikeString(java.lang.reflect.Constructor<?> c) {
        return c.getDeclaringClass().getTypeName() + ".<init>(" + Arrays.stream(c.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + "):void";
    }

    public static String toGraalLikeString(Executable executable) {
        if (executable instanceof java.lang.reflect.Method m) {
            return toGraalLikeString(m);
        } else {
            return toGraalLikeString((java.lang.reflect.Constructor<?>) executable);
        }
    }

    public static String toGraalLikeString(java.lang.reflect.Field f) {
        return f.getDeclaringClass().getTypeName() + '.' + f.getName();
    }

    public static String reflectionObjectToString(AnnotatedElement reflectionObject) {
        if (reflectionObject instanceof Class<?> clazz) {
            return clazz.getTypeName();
        } else if (reflectionObject instanceof Constructor<?> c) {
            return toGraalLikeString(c);
        } else if (reflectionObject instanceof java.lang.reflect.Method m) {
            return toGraalLikeString(m);
        } else {
            java.lang.reflect.Field f = ((java.lang.reflect.Field) reflectionObject);
            return toGraalLikeString(f);
        }
    }
}
