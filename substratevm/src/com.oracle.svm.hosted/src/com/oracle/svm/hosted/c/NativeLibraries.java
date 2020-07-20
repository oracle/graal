/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class NativeLibraries {

    private final MetaAccessProvider metaAccess;

    private final SnippetReflectionProvider snippetReflection;
    private final TargetDescription target;
    private ClassInitializationSupport classInitializationSupport;

    private final Map<Object, ElementInfo> elementToInfo;
    private final Map<Class<? extends CContext.Directives>, NativeCodeContext> compilationUnitToContext;

    private final ResolvedJavaType wordBaseType;
    private final ResolvedJavaType signedType;
    private final ResolvedJavaType unsignedType;
    private final ResolvedJavaType pointerBaseType;
    private final ResolvedJavaType stringType;
    private final ResolvedJavaType byteArrayType;
    private final ResolvedJavaType enumType;
    private final ResolvedJavaType locationIdentityType;

    private final LinkedHashSet<CLibrary> annotated;
    private final List<String> libraries;
    private final DependencyGraph dependencyGraph;
    private final List<String> jniStaticLibraries;
    private final LinkedHashSet<String> libraryPaths;

    private final List<CInterfaceError> errors;
    private final ConstantReflectionProvider constantReflection;

    private final CAnnotationProcessorCache cache;

    public final Path tempDirectory;
    public final DebugContext debug;

    public static final class DependencyGraph {

        private static final class Dependency {
            private final String name;
            private final Set<Dependency> dependencies;

            Dependency(String name, Set<Dependency> dependencies) {
                assert dependencies != null;
                this.name = name;
                this.dependencies = dependencies;
            }

            public String getName() {
                return name;
            }

            public Set<Dependency> getDependencies() {
                return dependencies;
            }

            @Override
            public String toString() {
                String depString = dependencies.stream().map(Dependency::getName).collect(Collectors.joining());
                return "Dependency{" +
                                "name='" + name + '\'' +
                                ", dependencies=[" + depString +
                                "]}";
            }
        }

        private final Map<String, Dependency> allDependencies;

        public DependencyGraph() {
            allDependencies = new ConcurrentHashMap<>();
        }

        public void add(String library, Collection<String> dependencies) {
            UserError.guarantee(library != null, "The library name must be not null and not empty");

            Dependency libraryDependency = putWhenAbsent(library, new Dependency(library, new HashSet<>()));
            Set<Dependency> collectedDependencies = libraryDependency.getDependencies();

            for (String dependency : dependencies) {
                collectedDependencies.add(putWhenAbsent(
                                dependency, new Dependency(dependency, new HashSet<>())));
            }
        }

        public List<String> sort() {
            final Set<Dependency> discovered = new HashSet<>();
            final Set<Dependency> processed = new LinkedHashSet<>();

            for (Dependency dep : allDependencies.values()) {
                visit(dep, discovered, processed);
            }

            LinkedList<String> names = new LinkedList<>();
            processed.forEach(n -> names.push(n.getName()));
            return names;
        }

        private Dependency putWhenAbsent(String libName, Dependency dep) {
            if (!allDependencies.containsKey(libName)) {
                allDependencies.put(libName, dep);
            }
            return allDependencies.get(libName);
        }

        private void visit(Dependency dep, Set<Dependency> discovered, Set<Dependency> processed) {
            if (processed.contains(dep)) {
                return;
            }
            if (discovered.contains(dep)) {
                String message = String.format("While building list of static libraries dependencies a cycle was discovered for dependency: %s ", dep.getName());
                UserError.abort(message);
            }

            discovered.add(dep);
            dep.getDependencies().forEach(d -> visit(d, discovered, processed));
            processed.add(dep);
        }

        @Override
        public String toString() {
            String depsStr = allDependencies.values()
                            .stream()
                            .map(Dependency::toString)
                            .collect(Collectors.joining("\n"));
            return "DependencyGraph{\n" +
                            depsStr +
                            '}';
        }
    }

    public NativeLibraries(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, TargetDescription target,
                    ClassInitializationSupport classInitializationSupport, Path tempDirectory, DebugContext debug) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.snippetReflection = snippetReflection;
        this.target = target;
        this.classInitializationSupport = classInitializationSupport;
        this.tempDirectory = tempDirectory;
        this.debug = debug;

        elementToInfo = new HashMap<>();
        errors = new ArrayList<>();
        compilationUnitToContext = new HashMap<>();

        wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        signedType = metaAccess.lookupJavaType(SignedWord.class);
        unsignedType = metaAccess.lookupJavaType(UnsignedWord.class);
        pointerBaseType = metaAccess.lookupJavaType(PointerBase.class);
        stringType = metaAccess.lookupJavaType(String.class);
        byteArrayType = metaAccess.lookupJavaType(byte[].class);
        enumType = metaAccess.lookupJavaType(Enum.class);
        locationIdentityType = metaAccess.lookupJavaType(LocationIdentity.class);

        annotated = new LinkedHashSet<>();

        /*
         * Libraries can be added during the static analysis, which runs multi-threaded. So the
         * lists must be synchronized.
         *
         * Also note that it is necessary to support duplicate entries, i.e., it must remain a List
         * and not a Set. The list is passed to the linker, and duplicate entries allow linking of
         * libraries that have cyclic dependencies.
         */
        libraries = Collections.synchronizedList(new ArrayList<>());
        dependencyGraph = new DependencyGraph();
        jniStaticLibraries = Collections.synchronizedList(new ArrayList<>());

        libraryPaths = initCLibraryPath();

        this.cache = new CAnnotationProcessorCache();
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    public TargetDescription getTarget() {
        return target;
    }

    private static final String libPrefix = OS.getCurrent() == OS.WINDOWS ? "" : "lib";
    private static final String libSuffix = OS.getCurrent() == OS.WINDOWS ? ".lib" : ".a";

    private static Path getPlatformDependentJDKStaticLibraryPath() throws IOException {
        Path baseSearchPath = Paths.get(System.getProperty("java.home")).resolve("lib").toRealPath();
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Path staticLibPath = baseSearchPath.resolve("static");
            SubstrateTargetDescription target = ConfigurationValues.getTarget();
            Path platformDependentPath = staticLibPath.resolve((OS.getCurrent().className + "-" + target.arch.getName()).toLowerCase());
            if (OS.getCurrent() == OS.LINUX) {
                platformDependentPath = platformDependentPath.resolve(LibCBase.singleton().getName());
                if (LibCBase.singleton().requiresLibCSpecificStaticJDKLibraries()) {
                    return platformDependentPath;
                }
            }

            if (Files.exists(platformDependentPath)) {
                return platformDependentPath;
            } else {
                System.err.println("WARNING: Using an older version of the labsjdk-11.");
            }
        }
        return baseSearchPath;
    }

    private static LinkedHashSet<String> initCLibraryPath() {
        LinkedHashSet<String> libraryPaths = new LinkedHashSet<>();

        Path staticLibsDir = null;
        String hint = null;

        /* Probe for static JDK libraries in JDK lib directory */
        try {
            Path jdkLibDir = getPlatformDependentJDKStaticLibraryPath();

            List<String> defaultBuiltInLibraries = Arrays.asList(PlatformNativeLibrarySupport.defaultBuiltInLibraries);
            Predicate<String> hasStaticLibrary = s -> Files.isRegularFile(jdkLibDir.resolve(libPrefix + s + libSuffix));
            if (defaultBuiltInLibraries.stream().allMatch(hasStaticLibrary)) {
                staticLibsDir = jdkLibDir;
            } else {
                hint = defaultBuiltInLibraries.stream().filter(hasStaticLibrary.negate()).collect(Collectors.joining(", ", "Missing libraries:", ""));
            }
        } catch (IOException e) {
            /* Fallthrough to next strategy */
            hint = e.getMessage();
        }

        if (staticLibsDir == null) {
            /* TODO: Implement other strategies to get static JDK libraries (download + caching) */
        }

        if (staticLibsDir != null) {
            libraryPaths.add(staticLibsDir.toString());
        } else {
            if (!NativeImageOptions.ExitAfterRelocatableImageWrite.getValue()) {
                /* Fail if we will statically link JDK libraries but do not have them available */
                String libCMessage = "";
                if (Platform.includedIn(Platform.LINUX.class)) {
                    libCMessage = "(target libc: " + LibCBase.singleton().getName() + ")";
                }
                String jdkDownloadURL = (JavaVersionUtil.JAVA_SPEC > 8 ? JVMCIVersionCheck.JVMCI11_RELEASES_URL : JVMCIVersionCheck.JVMCI8_RELEASES_URL);
                UserError.guarantee(!Platform.includedIn(InternalPlatform.PLATFORM_JNI.class),
                                "Building images for %s%s requires static JDK libraries.%Use the JDK from %s%n%s",
                                ImageSingletons.lookup(Platform.class).getClass().getName(),
                                libCMessage,
                                jdkDownloadURL,
                                hint);
            }
        }
        return libraryPaths;
    }

    public void addError(String msg, Object... context) {
        getErrors().add(new CInterfaceError(msg, context));
    }

    public List<CInterfaceError> getErrors() {
        return errors;
    }

    public void reportErrors() {
        if (errors.size() > 0) {
            throw UserError.abort(errors.stream().map(CInterfaceError::getMessage).collect(Collectors.toList()));
        }
    }

    public void loadJavaMethod(ResolvedJavaMethod method) {
        Class<? extends CContext.Directives> directives = getDirectives(method);
        NativeCodeContext context = makeContext(directives);

        if (!context.isInConfiguration()) {
            /* Nothing to do, all elements in context are ignored. */
        } else if (method.getAnnotation(CConstant.class) != null) {
            context.appendConstantAccessor(method);
        } else if (method.getAnnotation(CFunction.class) != null) {
            /* Nothing to do, handled elsewhere but the NativeCodeContext above is important. */
        } else {
            addError("Method is not annotated with supported C interface annotation", method);
        }
    }

    public void loadJavaType(ResolvedJavaType type) {
        NativeCodeContext context = makeContext(getDirectives(type));

        if (!context.isInConfiguration()) {
            /* Nothing to do, all elements in context are ignored. */
        } else if (type.getAnnotation(CStruct.class) != null) {
            context.appendStructType(type);
        } else if (type.getAnnotation(RawStructure.class) != null) {
            context.appendRawStructType(type);
        } else if (type.getAnnotation(CPointerTo.class) != null) {
            context.appendPointerToType(type);
        } else if (type.getAnnotation(CEnum.class) != null) {
            context.appendEnumType(type);
        } else {
            addError("Type is not annotated with supported C interface annotation", type);
        }
    }

    public void addAnnotated(CLibrary library) {
        annotated.add(library);
    }

    public void addStaticJniLibrary(String library, String... dependencies) {
        jniStaticLibraries.add(library);
        List<String> allDeps = new ArrayList<>(Arrays.asList(dependencies));
        /* "jvm" is a basic dependence for static JNI libs */
        allDeps.add("jvm");
        dependencyGraph.add(library, allDeps);
    }

    public void addDynamicNonJniLibrary(String library) {
        libraries.add(library);
    }

    public void addStaticNonJniLibrary(String library, String... dependencies) {
        dependencyGraph.add(library, Arrays.asList(dependencies));
    }

    public Collection<String> getLibraries() {
        return libraries;
    }

    public Collection<Path> getStaticLibraries() {
        Map<Path, Path> allStaticLibs = getAllStaticLibs();
        List<Path> staticLibs = new ArrayList<>();
        List<String> sortedList = dependencyGraph.sort();

        for (String staticLibraryName : sortedList) {
            Path libraryPath = getStaticLibraryPath(allStaticLibs, staticLibraryName);
            if (libraryPath == null) {
                continue;
            }
            staticLibs.add(libraryPath);
        }
        return staticLibs;
    }

    public Path getStaticLibraryPath(String staticLibraryName) {
        return getStaticLibraryPath(getAllStaticLibs(), staticLibraryName);
    }

    private static Path getStaticLibraryPath(Map<Path, Path> allStaticLibs, String staticLibraryName) {
        return allStaticLibs.get(Paths.get(libPrefix + staticLibraryName + libSuffix));
    }

    private Map<Path, Path> getAllStaticLibs() {
        Map<Path, Path> allStaticLibs = new LinkedHashMap<>();
        for (String libraryPath : getLibraryPaths()) {
            try (Stream<Path> paths = Files.list(Paths.get(libraryPath))) {
                paths.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(libSuffix))
                                .forEachOrdered(candidate -> allStaticLibs.put(candidate.getFileName(), candidate));
            } catch (IOException e) {
                UserError.abort(e, "Invalid library path " + libraryPath);
            }
        }
        return allStaticLibs;
    }

    public Collection<String> getLibraryPaths() {
        return libraryPaths;
    }

    private NativeCodeContext makeContext(Class<? extends CContext.Directives> compilationUnit) {
        NativeCodeContext result = compilationUnitToContext.get(compilationUnit);
        if (result == null) {
            CContext.Directives unit;
            try {
                unit = ReflectionUtil.newInstance(compilationUnit);
            } catch (ReflectionUtilError ex) {
                throw UserError.abort(ex.getCause(), "can't construct compilation unit " + compilationUnit.getCanonicalName());
            }

            if (classInitializationSupport != null) {
                classInitializationSupport.initializeAtBuildTime(unit.getClass(), "CContext.Directives must be eagerly initialized");
            }
            result = new NativeCodeContext(unit);
            compilationUnitToContext.put(compilationUnit, result);
        }
        return result;
    }

    private static Object unwrap(AnnotatedElement e) {
        Object element = e;
        assert element instanceof ResolvedJavaType || element instanceof ResolvedJavaMethod;
        while (element instanceof WrappedElement) {
            element = ((WrappedElement) element).getWrapped();
        }
        assert element instanceof ResolvedJavaType || element instanceof ResolvedJavaMethod;
        return element;
    }

    public void registerElementInfo(AnnotatedElement e, ElementInfo elementInfo) {
        Object element = unwrap(e);
        assert !elementToInfo.containsKey(element);
        elementToInfo.put(element, elementInfo);
    }

    public ElementInfo findElementInfo(AnnotatedElement element) {
        Object element1 = unwrap(element);
        ElementInfo result = elementToInfo.get(element1);
        if (result == null && element1 instanceof ResolvedJavaType && ((ResolvedJavaType) element1).getInterfaces().length == 1) {
            result = findElementInfo(((ResolvedJavaType) element1).getInterfaces()[0]);
        }
        return result;
    }

    private static Class<? extends CContext.Directives> getDirectives(CContext useUnit) {
        return useUnit.value();
    }

    private Class<? extends CContext.Directives> getDirectives(ResolvedJavaMethod method) {
        return getDirectives(method.getDeclaringClass());
    }

    private Class<? extends CContext.Directives> getDirectives(ResolvedJavaType type) {
        CContext useUnit = type.getAnnotation(CContext.class);
        if (useUnit != null) {
            return getDirectives(useUnit);
        } else if (type.getEnclosingType() != null) {
            return getDirectives(type.getEnclosingType());
        } else {
            return BuiltinDirectives.class;
        }
    }

    public void finish() {
        libraryPaths.addAll(OptionUtils.flatten(",", SubstrateOptions.CLibraryPath.getValue()));
        for (NativeCodeContext context : compilationUnitToContext.values()) {
            if (context.isInConfiguration()) {
                libraries.addAll(context.getDirectives().getLibraries());
                libraryPaths.addAll(context.getDirectives().getLibraryPaths());
                new CAnnotationProcessor(this, context).process(cache);
            }
        }
    }

    public boolean isWordBase(ResolvedJavaType type) {
        return wordBaseType.isAssignableFrom(type);
    }

    public boolean isPointerBase(ResolvedJavaType type) {
        return pointerBaseType.isAssignableFrom(type);
    }

    public boolean isSigned(ResolvedJavaType type) {
        /*
         * No assignable check, we only go for exact match since Word (which implements Signed,
         * Unsigned, and Pointer) should not match here.
         */
        return signedType.equals(type);
    }

    public boolean isUnsigned(ResolvedJavaType type) {
        /*
         * No assignable check, we only go for exact match since Word (which implements Signed,
         * Unsigned, and Pointer) should not match here.
         */
        return unsignedType.equals(type);
    }

    public boolean isString(ResolvedJavaType type) {
        return stringType.isAssignableFrom(type);
    }

    public boolean isByteArray(ResolvedJavaType type) {
        return byteArrayType.isAssignableFrom(type);
    }

    public boolean isEnum(ResolvedJavaType type) {
        return enumType.isAssignableFrom(type);
    }

    public ResolvedJavaType getPointerBaseType() {
        return pointerBaseType;
    }

    public ResolvedJavaType getLocationIdentityType() {
        return locationIdentityType;
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    public boolean processAnnotated() {
        if (annotated.isEmpty()) {
            return false;
        }
        for (CLibrary lib : annotated) {
            if (lib.requireStatic()) {
                addStaticNonJniLibrary(lib.value(), lib.dependsOn());
            } else {
                addDynamicNonJniLibrary(lib.value());
            }
        }
        annotated.clear();
        return true;
    }

    public List<String> getJniStaticLibraries() {
        return jniStaticLibraries;
    }
}
