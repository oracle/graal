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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.UserError;
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

    private final LinkedHashSet<String> libraries;
    private final LinkedHashSet<String> staticLibraries;
    private final LinkedHashSet<String> libraryPaths;

    private final List<CInterfaceError> errors;
    private final ConstantReflectionProvider constantReflection;

    private final CAnnotationProcessorCache cache;

    public NativeLibraries(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, TargetDescription target,
                    ClassInitializationSupport classInitializationSupport) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.snippetReflection = snippetReflection;
        this.target = target;
        this.classInitializationSupport = classInitializationSupport;

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

        libraries = new LinkedHashSet<>();
        staticLibraries = new LinkedHashSet<>();
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

    private static LinkedHashSet<String> initCLibraryPath() {
        LinkedHashSet<String> libraryPaths = new LinkedHashSet<>();

        Path staticLibsDir = null;

        /* Probe for static JDK libraries in JDK lib directory */
        try {
            Path jdkLibDir = Paths.get(System.getProperty("java.home")).resolve("lib").toRealPath();
            if (Files.isDirectory(jdkLibDir)) {
                if (Files.list(jdkLibDir).filter(path -> {
                    if (Files.isDirectory(path)) {
                        return false;
                    }
                    String libName = path.getFileName().toString();
                    if (!(libName.startsWith(libPrefix) && libName.endsWith(libSuffix))) {
                        return false;
                    }
                    String lib = libName.substring(libPrefix.length(), libName.length() - libSuffix.length());
                    return PlatformNativeLibrarySupport.defaultBuiltInLibraries.contains(lib);
                }).count() == PlatformNativeLibrarySupport.defaultBuiltInLibraries.size()) {
                    staticLibsDir = jdkLibDir;
                }
            }
        } catch (Exception e) {
            /* Fallthrough to next strategy */
        }

        if (staticLibsDir == null) {
            /* TODO: Implement other strategies to get static JDK libraries (download + caching) */
        }

        if (staticLibsDir != null) {
            libraryPaths.add(staticLibsDir.toString());
        } else {
            UserError.guarantee(OS.getCurrent() != OS.WINDOWS,
                            "Building images for " + OS.getCurrent().className +
                                            " requires static JDK libraries." +
                                            "\nUse JDK from https://github.com/graalvm/openjdk8-jvmci-builder/releases");
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

    public void addLibrary(String library, boolean requireStatic) {
        (requireStatic ? staticLibraries : libraries).add(library);
    }

    public Collection<String> getLibraries() {
        return libraries;
    }

    public Collection<Path> getStaticLibraries() {
        Map<Path, Path> allStaticLibs = new LinkedHashMap<>();
        for (String libraryPath : getLibraryPaths()) {
            try {
                Files.list(Paths.get(libraryPath))
                                .filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(libSuffix))
                                .forEachOrdered(candidate -> allStaticLibs.put(candidate.getFileName(), candidate));
            } catch (IOException e) {
                UserError.abort("Invalid library path " + libraryPath, e);
            }
        }
        List<Path> staticLibs = new ArrayList<>();
        for (String staticLibraryName : staticLibraries) {
            Path libraryPath = allStaticLibs.get(Paths.get(libPrefix + staticLibraryName + libSuffix));
            if (libraryPath == null) {
                continue;
            }
            staticLibs.add(libraryPath);
        }
        return staticLibs;
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
                throw UserError.abort("can't construct compilation unit " + compilationUnit.getCanonicalName(), ex.getCause());
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

    public void finish(Path tempDirectory) {
        libraryPaths.addAll(OptionUtils.flatten(",", SubstrateOptions.CLibraryPath.getValue()));
        for (NativeCodeContext context : compilationUnitToContext.values()) {
            if (context.isInConfiguration()) {
                libraries.addAll(context.getDirectives().getLibraries());
                libraryPaths.addAll(context.getDirectives().getLibraryPaths());

                new CAnnotationProcessor(this, context, tempDirectory).process(cache);
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
}
