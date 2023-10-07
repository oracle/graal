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
package com.oracle.svm.hosted;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.hosted.jni.JNIAccessFeature;
import jdk.vm.ci.meta.MetaUtil;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.InitKind;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;

@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
public class ReachabilityExporter implements InternalFeature {

    public final Path reachabilityJsonPath =
            NativeImageGenerator
            .generatedFiles(HostedOptionValues.singleton())
            .resolve(SubstrateOptions.Name.getValue() + "." + SubstrateOptions.REACHABILITY_FILE_NAME);

    private static class Export {
        private static class Method {
            public final boolean reflection;
            public final boolean jni;
            public final boolean synthetic;
            public final boolean isMain;
            public final int codeSize;

            public Method(AnalysisMethod m, Function<AnalysisMethod, Integer> compilations,
                          Map<AnalysisMethod, Executable> reflectionExecutables, Set<AnalysisMethod> jniMethods,
                          AnalysisMethod mainMethod) {
                Integer codeSize = compilations.apply(m);
                this.codeSize = codeSize != null ? codeSize : 0;
                reflection = reflectionExecutables.containsKey(m);
                jni = jniMethods.contains(m);
                synthetic = m.isSynthetic();
                isMain = m == mainMethod;
            }

            EconomicMap<String, Object> serialize() {
                EconomicMap<String, Object> map = EconomicMap.create();
                ArrayList<String> flagsList = new ArrayList<>();

                if(reflection)
                    flagsList.add("reflection");
                if(jni)
                    flagsList.add("jni");
                if(synthetic)
                    flagsList.add("synthetic");
                if(isMain)
                    flagsList.add("main");

                if(!flagsList.isEmpty())
                    map.put("flags", flagsList.toArray());
                map.put("size", codeSize);
                return map;
            }
        }

        private static class Field {
            public final boolean reflection;
            public final boolean jni;
            public final boolean synthetic;

            public Field(AnalysisField f, Map<AnalysisField, java.lang.reflect.Field> reflectionFields) {
                reflection = reflectionFields.containsKey(f);
                jni = f.isJNIAccessed();
                synthetic = f.isSynthetic();
            }

            EconomicMap<String, Object> serialize() {
                EconomicMap<String, Object> map = EconomicMap.create();
                ArrayList<String> flagsList = new ArrayList<>();

                if(reflection)
                    flagsList.add("reflection");
                if(jni)
                    flagsList.add("jni");
                if(synthetic)
                    flagsList.add("synthetic");

                if(!flagsList.isEmpty())
                    map.put("flags", flagsList.toArray());

                return map;
            }
        }

        private static class Type {
            public final ArrayList<Pair<String, Method>> methods = new ArrayList<>();
            public final ArrayList<Pair<String, Export.Field>> fields = new ArrayList<>();
            public final boolean buildTimeInit;
            public final boolean runTimeInit;
            public final boolean synthetic;
            public final boolean jni;
            public final boolean reflection;

            public Type(AnalysisType type, Map<Class<?>, InitKind> classInitKinds, Set<AnalysisType> reflectionTypes, Set<AnalysisType> jniTypes) {
                synthetic = type.getJavaClass().isSynthetic();
                jni = jniTypes.contains(type);
                reflection = reflectionTypes.contains(type);

                if(type.getWrapped().getClassInitializer() != null) {
                    InitKind initKind = classInitKinds.get(type.getJavaClass());

                    if (initKind != null) {
                        runTimeInit = initKind == InitKind.RUN_TIME || initKind == InitKind.RERUN;
                        buildTimeInit = initKind == InitKind.BUILD_TIME || initKind == InitKind.RERUN;
                        return;
                    }
                }

                runTimeInit = false;
                buildTimeInit = false;
            }

            EconomicMap<String, Object> serialize() {
                EconomicMap<String, Object> map = EconomicMap.create();
                ArrayList<String> initKindList = new ArrayList<>();

                if (runTimeInit)
                    initKindList.add("run-time");
                if (buildTimeInit)
                    initKindList.add("build-time");

                if (!initKindList.isEmpty())
                    map.put("init-kind", initKindList.toArray());

                EconomicMap<String, Object> jsonMethods = EconomicMap.create();
                EconomicMap<String, Object> jsonFields = EconomicMap.create();

                for(Pair<String, Method> m : methods) {
                    jsonMethods.put(m.getLeft(), m.getRight().serialize());
                }

                for(Pair<String, Export.Field> f : fields) {
                    jsonFields.put(f.getLeft(), f.getRight().serialize());
                }

                map.put("methods", jsonMethods);
                map.put("fields", jsonFields);

                ArrayList<String> flagsList = new ArrayList<>();

                if(reflection)
                    flagsList.add("reflection");
                if(jni)
                    flagsList.add("jni");
                if(synthetic)
                    flagsList.add("synthetic");

                if(!flagsList.isEmpty())
                    map.put("flags", flagsList.toArray());

                return map;
            }
        }

        private static class Package {
            public final HashMap<String, Type> types = new HashMap<>();

            public EconomicMap<String, Object> serialize() {
                EconomicMap<String, Object> map = EconomicMap.create(1);
                EconomicMap<String, Object> typeMap = EconomicMap.create(types.size());

                for(Map.Entry<String, Type> t : types.entrySet())
                    typeMap.put(t.getKey(), t.getValue().serialize());

                map.put("types", typeMap);
                return map;
            }
        }

        private static class TopLevelOrigin {
            public final String path;
            public final String module;
            public final boolean isSystem;

            public final HashMap<String, Package> packages = new HashMap<>();

            public TopLevelOrigin(String path, String module, boolean isSystem) {
                this.path = path;
                this.module = module;
                this.isSystem = isSystem;
            }

            public EconomicMap<String, Object> serialize() {
                EconomicMap<String, Object> map = EconomicMap.create();
                if (path != null)
                    map.put("path", path);
                if (module != null)
                    map.put("module", module);
                if (isSystem)
                    map.put("flags", new Object[] { "system" });

                EconomicMap<String, Object> packagesMap = EconomicMap.create();

                for(Map.Entry<String, Package> p : packages.entrySet())
                    packagesMap.put(p.getKey(), p.getValue().serialize());

                map.put("packages", packagesMap);
                return map;
            }
        }

        private static String findTopLevelOriginName(Class<?> clazz) {
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                URL url = codeSource.getLocation();
                if("file".equals(url.getProtocol())) {
                    return url.getPath();
                }
            }
            return null;
        }

        private static String findModuleName(Class<?> clazz) {
            return clazz.getModule().getName();
        }

        private final HashMap<Pair<String, String>, TopLevelOrigin> topLevelOrigins = new HashMap<>();

        private static AnalysisMethod getMainMethod(AnalysisUniverse universe)
        {
            if(!ImageSingletons.contains(JavaMainWrapper.JavaMainSupport.class))
                return null;
            JavaMainWrapper.JavaMainSupport jms = ImageSingletons.lookup(JavaMainWrapper.JavaMainSupport.class);
            java.lang.reflect.Method m = jms.getMainMethod();
            return universe.getBigbang().getMetaAccess().lookupJavaMethod(m);
        }

        public Export(AnalysisUniverse universe, Function<AnalysisMethod, Integer> compilations) {
            Map<Class<?>, InitKind> classInitKinds = ((ClassInitializationSupport) ImageSingletons.lookup(RuntimeClassInitializationSupport.class)).getClassInitKinds();
            ReflectionHostedSupport reflectionHostedSupport = ImageSingletons.lookup(ReflectionHostedSupport.class);
            Map<AnalysisMethod, Executable> reflectionExecutables = reflectionHostedSupport.getReflectionExecutables();
            Map<AnalysisField, java.lang.reflect.Field> reflectionFields = reflectionHostedSupport.getReflectionFields();
            JNIAccessFeature jniAccessFeature = JNIAccessFeature.singleton();
            Set<AnalysisMethod> jniMethods = Arrays.stream(jniAccessFeature.getRegisteredMethods()).map(universe::lookup).collect(Collectors.toSet());
            AnalysisMethod mainMethod = getMainMethod(universe);
            Set<AnalysisType> jniTypes = Arrays.stream(jniAccessFeature.getRegisteredClasses()).map(universe.getBigbang().getMetaAccess()::optionalLookupJavaType).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
            Set<AnalysisType> reflectionTypes = Arrays.stream(ClassForNameSupport.getSuccessfullyRegisteredClasses()).map(universe.getBigbang().getMetaAccess()::optionalLookupJavaType).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());

            Path buildPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());
            Path targetPath = buildPath.resolve(SubstrateOptions.REACHABILITY_FILE_NAME);

            for (AnalysisType t : universe.getTypes()) {
                if(!t.isReachable())
                    continue;

                if(t.isArray())
                    continue;

                getType(t, classInitKinds, reflectionTypes, jniTypes);
            }
            for (AnalysisMethod m : universe.getMethods()) {
                if(!m.isReachable())
                    continue;

                Type t = getType(m.getDeclaringClass(), classInitKinds, reflectionTypes, jniTypes);

                t.methods.add(Pair.create(m.format("%n(%P):%R"), new Method(m, compilations, reflectionExecutables, jniMethods, mainMethod)));
            }
            for (AnalysisField f : universe.getFields()) {
                if(!f.isReachable())
                    continue;

                Type t = getType(f.getDeclaringClass(), classInitKinds, reflectionTypes, jniTypes);
                t.fields.add(Pair.create(f.getName(), new Field(f, reflectionFields)));
            }
        }

        public void write(JsonWriter writer) throws IOException {
            writer.print(topLevelOrigins.values().stream().sorted(Comparator.comparing((TopLevelOrigin tlo) -> tlo.path != null ? tlo.path : "").thenComparing(tlo -> tlo.module != null ? tlo.module : "")).map(TopLevelOrigin::serialize).toArray());
        }

        private Type getType(AnalysisType type, Map<Class<?>, InitKind> classInitKinds, Set<AnalysisType> reflectionTypes, Set<AnalysisType> jniTypes) {
            String topLevelOriginName = findTopLevelOriginName(type.getJavaClass());
            String moduleName = findModuleName(type.getJavaClass());
            boolean isSystemCode = !ImageSingletons.lookup(ClassLoaderSupport.class).isNativeImageClassLoader(type.getJavaClass().getClassLoader());
            TopLevelOrigin tlo = topLevelOrigins.computeIfAbsent(Pair.create(topLevelOriginName, moduleName), pair -> new TopLevelOrigin(pair.getLeft(), pair.getRight(), isSystemCode));
            assert tlo.isSystem == isSystemCode : "Class loader is expected to be the same for all classes of the module.";
            Package p = tlo.packages.computeIfAbsent(type.getJavaClass().getPackageName(), name -> new Package());

            // TODO: Remove once name fix has been merged from main
            String stableNameUnqualified = MetaUtil.internalNameToJava(type.getName(), false, false);

            return p.types.computeIfAbsent(stableNameUnqualified, name -> new Type(type, classInitKinds, reflectionTypes, jniTypes));
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.GenerateReachabilityFile.getValue() || AnalysisReportsOptions.PrintCausalityGraph.getValue(HostedOptionValues.singleton());
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (NativeImageOptions.ExitAfterAnalysis.getValue()) {
            FeatureImpl.AfterAnalysisAccessImpl impl = (FeatureImpl.AfterAnalysisAccessImpl) access;
            write(new Export(impl.getUniverse(), m -> 0));
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        AfterCompilationAccessImpl accessImpl = (AfterCompilationAccessImpl) access;

        Map<AnalysisMethod, Integer> compilations = new HashMap<>();
        for (var pair : accessImpl.getCompilations().entrySet()) {
            compilations.compute(pair.getKey().getWrapped(), (m, size) -> {
                if (size == null)
                    size = 0;
                size += pair.getValue().result.getTargetCodeSize();
                return size;
            });
        }

        write(new Export(accessImpl.aUniverse, compilations::get));
    }

    private void write(Export e) {
        try (JsonWriter writer = new JsonWriter(reachabilityJsonPath)) {
            e.write(writer);
            BuildArtifacts.singleton().add(ArtifactType.BUILD_INFO, reachabilityJsonPath);
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere("Unable to create " + SubstrateOptions.REACHABILITY_FILE_NAME, ex);
        }
    }
}
