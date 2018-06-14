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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.CContext.Directives;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.CConstantValueSupport;
import org.graalvm.nativeimage.impl.SizeOfSupport;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.StructInfo;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class NativeLibraries {

    private final MetaAccessProvider metaAccess;

    private final SnippetReflectionProvider snippetReflection;
    private final TargetDescription target;

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

    private final List<String> libraries;
    private final List<String> libraryPaths;

    private final List<CInterfaceError> errors;
    private final ConstantReflectionProvider constantReflection;

    private final CAnnotationProcessorCache cache;

    static final class SizeOfSupportImpl implements SizeOfSupport {
        private final NativeLibraries nativeLibraries;

        SizeOfSupportImpl(NativeLibraries nativeLibraries) {
            this.nativeLibraries = nativeLibraries;
        }

        @Override
        public int sizeof(Class<? extends PointerBase> clazz) {
            ResolvedJavaType type = nativeLibraries.metaAccess.lookupJavaType(clazz);
            ElementInfo typeInfo = nativeLibraries.findElementInfo(type);
            if (typeInfo instanceof StructInfo && ((StructInfo) typeInfo).isIncomplete()) {
                throw UserError.abort("Class parameter " + type.toJavaName(true) + " of call to " + SizeOf.class.getSimpleName() + " is an incomplete structure, so no size is available");
            } else if (typeInfo instanceof SizableInfo) {
                return ((SizableInfo) typeInfo).getSizeInfo().getProperty();
            } else {
                throw UserError.abort("Class parameter " + type.toJavaName(true) + " of call to " + SizeOf.class.getSimpleName() + " is not an annotated C interface type");
            }
        }
    }

    static final class CConstantValueSupportImpl implements CConstantValueSupport {
        private final NativeLibraries nativeLibraries;

        CConstantValueSupportImpl(NativeLibraries nativeLibraries) {
            this.nativeLibraries = nativeLibraries;
        }

        @Override
        public <T> T getCConstantValue(Class<?> declaringClass, String methodName, Class<T> returnType) {
            ResolvedJavaMethod method;
            try {
                method = nativeLibraries.metaAccess.lookupJavaMethod(declaringClass.getMethod(methodName));
            } catch (NoSuchMethodException | SecurityException e) {
                throw VMError.shouldNotReachHere("Method not found: " + declaringClass.getName() + "." + methodName);
            }
            if (method.getAnnotation(CConstant.class) == null) {
                throw VMError.shouldNotReachHere("Method " + declaringClass.getName() + "." + methodName + " is not annotated with @" + CConstant.class.getSimpleName());
            }

            ConstantInfo constantInfo = (ConstantInfo) nativeLibraries.findElementInfo(method);
            Object value = constantInfo.getValueInfo().getProperty();
            switch (constantInfo.getKind()) {
                case INTEGER:
                case POINTER:
                    Long longValue = (Long) value;
                    if (returnType == Boolean.class) {
                        return returnType.cast(Boolean.valueOf(longValue.longValue() != 0));
                    } else if (returnType == Integer.class) {
                        return returnType.cast(Integer.valueOf((int) longValue.longValue()));
                    } else if (returnType == Long.class) {
                        return returnType.cast(value);
                    }
                    break;

                case FLOAT:
                    if (returnType == Double.class) {
                        return returnType.cast(value);
                    }
                    break;

                case STRING:
                    if (returnType == String.class) {
                        return returnType.cast(value);
                    }
                    break;

                case BYTEARRAY:
                    if (returnType == byte[].class) {
                        return returnType.cast(value);
                    }
                    break;
            }

            throw VMError.shouldNotReachHere("Unexpected returnType: " + returnType.getName());
        }
    }

    public NativeLibraries(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, TargetDescription target) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.snippetReflection = snippetReflection;
        this.target = target;

        this.elementToInfo = new HashMap<>();
        this.errors = new ArrayList<>();
        this.compilationUnitToContext = new HashMap<>();

        this.wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        this.signedType = metaAccess.lookupJavaType(SignedWord.class);
        this.unsignedType = metaAccess.lookupJavaType(UnsignedWord.class);
        this.pointerBaseType = metaAccess.lookupJavaType(PointerBase.class);
        this.stringType = metaAccess.lookupJavaType(String.class);
        this.byteArrayType = metaAccess.lookupJavaType(byte[].class);
        this.enumType = metaAccess.lookupJavaType(Enum.class);
        this.locationIdentityType = metaAccess.lookupJavaType(LocationIdentity.class);

        this.libraries = new ArrayList<>();
        this.libraryPaths = new ArrayList<>();

        ImageSingletons.add(SizeOfSupport.class, new SizeOfSupportImpl(this));
        ImageSingletons.add(CConstantValueSupport.class, new CConstantValueSupportImpl(this));

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

    public void addLibrary(String library) {
        libraries.add(library);
    }

    public Collection<String> getLibraries() {
        return libraries;
    }

    public List<String> getLibraryPaths() {
        return libraryPaths;
    }

    private NativeCodeContext makeContext(Class<? extends CContext.Directives> compilationUnit) {
        NativeCodeContext result = compilationUnitToContext.get(compilationUnit);
        if (result == null) {
            try {
                Constructor<? extends Directives> constructor = compilationUnit.getDeclaredConstructor();
                constructor.setAccessible(true);
                CContext.Directives unit = constructor.newInstance();
                result = new NativeCodeContext(unit);
                compilationUnitToContext.put(compilationUnit, result);
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
                throw UserError.abort("can't construct compilation unit " + compilationUnit.getCanonicalName() + ": " + e);
            }
        }
        return result;
    }

    private static Object unwrap(Object e) {
        Object element = e;
        assert element instanceof ResolvedJavaType || element instanceof ResolvedJavaMethod;
        while (element instanceof WrappedElement) {
            element = ((WrappedElement) element).getWrapped();
        }
        assert element instanceof ResolvedJavaType || element instanceof ResolvedJavaMethod;
        return element;
    }

    public void registerElementInfo(Object e, ElementInfo elementInfo) {
        Object element = unwrap(e);
        assert !elementToInfo.containsKey(element);
        elementToInfo.put(element, elementInfo);
    }

    public ElementInfo findElementInfo(Object e) {
        Object element = unwrap(e);
        ElementInfo result = elementToInfo.get(element);
        if (result == null && element instanceof ResolvedJavaType && ((ResolvedJavaType) element).getInterfaces().length == 1) {
            result = findElementInfo(((ResolvedJavaType) element).getInterfaces()[0]);
        }
        return result;
    }

    private static Class<? extends CContext.Directives> getDirectives(CContext useUnit) {
        return useUnit.value();
    }

    private Class<? extends CContext.Directives> getDirectives(ResolvedJavaMethod method) {
        CContext useUnit = method.getAnnotation(CContext.class);
        if (useUnit != null) {
            return getDirectives(useUnit);
        } else {
            return getDirectives(method.getDeclaringClass());
        }
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
        libraryPaths.addAll(Arrays.asList(SubstrateOptions.CLibraryPath.getValue().split(",")));
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
