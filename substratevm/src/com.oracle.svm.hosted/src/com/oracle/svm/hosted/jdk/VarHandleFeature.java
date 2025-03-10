/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.VarHandleSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This file together with {@link VarHandleSupport} contains most of the code necessary for
 * supporting VarHandle (and DirectMethodHandle field accessors) in native images. The actual
 * intrinsification of the accessors happens in hosted-only code during inlining before analysis.
 *
 * The VarHandle implementation in the JDK uses some invokedynamic and method handles, but also a
 * lot of explicit Java code (a lot of it automatically generated): The main entry point from the
 * point of view of the user is the class VarHandle, which contains signature-polymorphic method
 * prototypes for the various access modes. However, we do not need to do anything special for the
 * VarHandle class: when we parse bytecode, all the bootstrapping has already happened on the Java
 * HotSpot VM, and the bytecode parser already sees calls to guard methods defined in
 * VarHandleGuards. Methods of that class are method handle intrinsification roots for inlining
 * before analysis. The intrinsification removes all the method handle invocation logic and reduces
 * the logic to a single call to the actual access logic. This logic is in various automatically
 * generated accessor classes named
 * "VarHandle{Booleans|Bytes|Chars|Doubles|Floats|Ints|Longs|Shorts|Objects}.{Array|FieldInstanceReadOnly|FieldInstanceReadWrite|FieldStaticReadOnly|FieldStaticReadWrite}".
 * The intrinsification might be able to inline these methods and even transform unsafe accesses by
 * offset to field accesses, but we cannot rely on it always being able to do in every case.
 *
 * The accessor classes for field access (both instance and static field access) store the offset of
 * the field that is used for Unsafe memory access. We need to 1) properly register these fields as
 * unsafe accessed so that our static analysis is correct, and 2) recompute the field offsets from
 * the hosted offsets to the runtime offsets. Luckily, we have all information to reconstruct the
 * original {@link Field}. But since there can be VarHandle for fields that are hidden for
 * reflection, we actually do not reconstruct the {@link Field} but the {@link ResolvedJavaField}
 * (see {@link #findVarHandleOriginalField}).
 *
 * The registration for unsafe access happens in {@link #registerReachableHandle} which is called
 * for every relevant object once it becomes reachable and so part of the image heap.
 *
 * The field offset recomputations are registered for all classes manually (a bit of code
 * duplication on our side), but all recomputations use the same custom field value recomputation
 * handler. For static fields, also the base of the Unsafe access needs to be changed to the static
 * field holder arrays defined in {@link StaticFieldsSupport}. VarHandle access to arrays is the
 * simplest case: we only need field value recomputations for the array base offset and array index
 * shift.
 */
@AutomaticallyRegisteredFeature
public class VarHandleFeature implements InternalFeature {

    class VarHandleSupportImpl extends VarHandleSupport {
        @Override
        protected ResolvedJavaField findVarHandleField(Object varHandle, boolean guaranteeUnsafeAccessed) {
            ResolvedJavaField result;
            if (hUniverse != null) {
                result = findVarHandleHostedField(varHandle);
            } else {
                result = findVarHandleAnalysisField(varHandle);
            }

            if (guaranteeUnsafeAccessed) {
                AnalysisField aField = result instanceof AnalysisField ? (AnalysisField) result : ((HostedField) result).getWrapped();
                AnalysisError.guarantee(aField.isUnsafeAccessed(), "Field not registered as unsafe accessed %s", aField);
            }

            return result;
        }
    }

    private Map<Class<?>, VarHandleInfo> infos;
    private AnalysisUniverse aUniverse;
    private HostedUniverse hUniverse;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        var access = (DuringSetupAccessImpl) a;
        aUniverse = access.getUniverse();
        infos = buildInfos();
        ImageSingletons.add(VarHandleSupport.class, new VarHandleSupportImpl());

        /*
         * Initialize fields of VarHandle instances that are @Stable eagerly, so that during method
         * handle intrinsification loads of those fields and array elements can be constant-folded.
         * 
         * Note that we do this on purpose here in an object replacer, and not in an object
         * reachability handler: Intrinsification happens as part of method inlining before
         * analysis, i.e., before the static analysis, i.e., before the VarHandle object itself is
         * marked as reachable. The goal of intrinsification is to actually avoid making the
         * VarHandle object itself reachable.
         */
        access.registerObjectReplacer(VarHandleFeature::eagerlyInitializeVarHandle);

        access.registerObjectReachableCallback(VarHandle.class, (a1, obj, reason) -> registerReachableHandle(obj, reason));
        access.registerObjectReachableCallback(access.findClassByName("java.lang.invoke.DirectMethodHandle"), (a1, obj, reason) -> registerReachableHandle(obj, reason));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        var access = (BeforeCompilationAccessImpl) a;
        hUniverse = access.getUniverse();
    }

    private static Object eagerlyInitializeVarHandle(Object obj) {
        if (obj instanceof VarHandle varHandle) {
            eagerlyInitializeVarHandle(varHandle);
        }
        return obj;
    }

    private static final Field varHandleVFormField = ReflectionUtil.lookupField(VarHandle.class, "vform");
    private static final Method varFormInitMethod = ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass(false, "java.lang.invoke.VarForm"), "getMethodType_V", int.class);
    private static final Method varHandleGetMethodHandleMethod = ReflectionUtil.lookupMethod(VarHandle.class, "getMethodHandle", int.class);

    public static void eagerlyInitializeVarHandle(VarHandle varHandle) {
        try {
            /*
             * The field VarHandle.vform.methodType_V_table is a @Stable field but initialized
             * lazily on first access. Therefore, constant folding can happen only after
             * initialization has happened. We force initialization by invoking the method
             * VarHandle.vform.getMethodType_V(0).
             */
            Object varForm = varHandleVFormField.get(varHandle);
            varFormInitMethod.invoke(varForm, 0);

            /*
             * The AccessMode used for the access that we are going to intrinsify is hidden in a
             * AccessDescriptor object that is also passed in as a parameter to the intrinsified
             * method. Initializing all AccessMode enum values is easier than trying to extract the
             * actual AccessMode.
             */
            for (VarHandle.AccessMode accessMode : VarHandle.AccessMode.values()) {
                /*
                 * Force initialization of the @Stable field VarHandle.vform.memberName_table.
                 */
                boolean isAccessModeSupported = varHandle.isAccessModeSupported(accessMode);
                /*
                 * Force initialization of the @Stable field
                 * VarHandle.typesAndInvokers.methodType_table.
                 */
                varHandle.accessModeType(accessMode);

                if (isAccessModeSupported) {
                    /* Force initialization of the @Stable field VarHandle.methodHandleTable. */
                    varHandleGetMethodHandleMethod.invoke(varHandle, accessMode.ordinal());
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static Map<Class<?>, VarHandleInfo> buildInfos() {
        Map<Class<?>, VarHandleInfo> infos = new HashMap<>();
        for (Map.Entry<String, JavaKind> type : Map.of(
                        "Booleans", JavaKind.Boolean,
                        "Bytes", JavaKind.Byte,
                        "Chars", JavaKind.Char,
                        "Doubles", JavaKind.Double,
                        "Floats", JavaKind.Float,
                        "Ints", JavaKind.Int,
                        "Longs", JavaKind.Long,
                        "Shorts", JavaKind.Short,
                        "References", JavaKind.Object).entrySet()) {
            String typeName = type.getKey();
            JavaKind kind = type.getValue();
            Function<Object, JavaKind> kindGetter = o -> kind;
            buildInfo(infos, false, kindGetter,
                            ReflectionUtil.lookupClass(false, "java.lang.invoke.VarHandle" + typeName + "$FieldInstanceReadOnly"),
                            ReflectionUtil.lookupClass(false, "java.lang.invoke.VarHandle" + typeName + "$FieldInstanceReadWrite"));
            buildInfo(infos, true, kindGetter,
                            ReflectionUtil.lookupClass(false, "java.lang.invoke.VarHandle" + typeName + "$FieldStaticReadOnly"),
                            ReflectionUtil.lookupClass(false, "java.lang.invoke.VarHandle" + typeName + "$FieldStaticReadWrite"));
        }

        Class<?> staticAccessorClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$StaticAccessor");
        infos.put(staticAccessorClass, new StaticVarHandleInfo(createKindGetter(staticAccessorClass, "fieldType"), createOffsetFieldGetter(staticAccessorClass, "staticOffset"),
                        createBaseFieldGetter(staticAccessorClass, "staticBase")));

        Class<?> accessorClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$Accessor");
        Function<Object, Class<?>> accessorTypeGetter = obj -> ((MethodHandle) obj).type().parameterType(0);
        infos.put(accessorClass, new InstanceVarHandleInfo(createKindGetter(accessorClass, "fieldType"), createOffsetFieldGetter(accessorClass, "fieldOffset"), accessorTypeGetter));
        return infos;
    }

    private static void buildInfo(Map<Class<?>, VarHandleInfo> infos, boolean isStatic, Function<Object, JavaKind> kindGetter, Class<?> readOnlyClass, Class<?> readWriteClass) {
        assert readOnlyClass.isAssignableFrom(readWriteClass);
        ToLongFunction<Object> offsetGetter = createOffsetFieldGetter(readOnlyClass, "fieldOffset");
        VarHandleInfo info;
        if (isStatic) {
            info = new StaticVarHandleInfo(kindGetter, offsetGetter, createBaseFieldGetter(readOnlyClass, "base"));
        } else {
            info = new InstanceVarHandleInfo(kindGetter, offsetGetter, createTypeFieldGetter(readOnlyClass, "receiverType"));
        }
        infos.put(readOnlyClass, info);
        infos.put(readWriteClass, info);
    }

    private static Function<Object, JavaKind> createKindGetter(Class<?> accessorClass, String fieldName) {
        Field classField = ReflectionUtil.lookupField(accessorClass, fieldName);
        return obj -> {
            try {
                Class<?> c = (Class<?>) classField.get(obj);
                if (!c.isPrimitive()) {
                    return JavaKind.Object;
                } else if (c == boolean.class) {
                    return JavaKind.Boolean;
                } else if (c == byte.class) {
                    return JavaKind.Byte;
                } else if (c == char.class) {
                    return JavaKind.Char;
                } else if (c == short.class) {
                    return JavaKind.Short;
                } else if (c == int.class) {
                    return JavaKind.Int;
                } else if (c == long.class) {
                    return JavaKind.Long;
                } else if (c == float.class) {
                    return JavaKind.Float;
                } else if (c == double.class) {
                    return JavaKind.Double;
                } else {
                    throw VMError.shouldNotReachHere(c.toString());
                }
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };
    }

    private static ToLongFunction<Object> createOffsetFieldGetter(Class<?> clazz, String offsetFieldName) {
        Field offsetField = ReflectionUtil.lookupField(clazz, offsetFieldName);
        return obj -> {
            try {
                return offsetField.getLong(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };
    }

    private static Function<Object, Object> createBaseFieldGetter(Class<?> clazz, String baseFieldName) {
        Field typeField = ReflectionUtil.lookupField(clazz, baseFieldName);
        return obj -> {
            try {
                return typeField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };
    }

    private static Function<Object, Class<?>> createTypeFieldGetter(Class<?> clazz, String typeFieldName) {
        Field typeField = ReflectionUtil.lookupField(clazz, typeFieldName);
        return obj -> {
            try {
                return (Class<?>) typeField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };
    }

    /**
     * Register all fields accessed by a reachable VarHandle for an instance field or a static field
     * as unsafe accessed, which is necessary for correctness of the static analysis.
     */
    private Object registerReachableHandle(Object obj, ObjectScanner.ScanReason reason) {
        VarHandleInfo info = infos.get(obj.getClass());
        if (info != null) {
            AnalysisField field = findVarHandleAnalysisField(obj);
            /*
             * It is OK if we see a new VarHandle after analysis, as long as the field itself was
             * already registered as Unsafe accessed by another VarHandle during analysis. This can
             * happen when the late class initializer analysis determines that a class is safe for
             * initialization at build time after the analysis.
             */
            if (!field.isUnsafeAccessed()) {
                VMError.guarantee(hUniverse == null, "New VarHandle %s found after static analysis for field %s", obj, field);
                field.registerAsUnsafeAccessed(reason);
            }
        }
        return obj;
    }

    /**
     * Find the original {@linkplain ResolvedJavaField field} referenced by a VarHandle that
     * accesses an instance field or a static field. The VarHandle stores the field offset and the
     * holder type. We iterate all fields of that type and look for the field with a matching
     * offset.
     */
    ResolvedJavaField findVarHandleOriginalField(Object varHandle) {
        VarHandleInfo info = infos.get(varHandle.getClass());
        /*
         * Search the declared fields for a field with a matching offset. The field search needs to
         * be done in the original universe of the hosting VM, because the field offsets in the
         * objects at image build time are also from those original fields.
         */
        return info.findOriginalField(varHandle);
    }

    AnalysisField findVarHandleAnalysisField(Object varHandle) {
        return aUniverse.lookup(findVarHandleOriginalField(varHandle));
    }

    HostedField findVarHandleHostedField(Object varHandle) {
        return hUniverse.lookup(findVarHandleAnalysisField(varHandle));
    }

}

abstract sealed class VarHandleInfo permits StaticVarHandleInfo, InstanceVarHandleInfo {
    final Function<Object, JavaKind> kindGetter;
    final ToLongFunction<Object> offsetGetter;

    VarHandleInfo(Function<Object, JavaKind> kindGetter, ToLongFunction<Object> offsetGetter) {
        this.kindGetter = kindGetter;
        this.offsetGetter = offsetGetter;
    }

    abstract ResolvedJavaField findOriginalField(Object varHandle);
}

final class StaticVarHandleInfo extends VarHandleInfo {
    final Function<Object, Object> baseGetter;

    StaticVarHandleInfo(Function<Object, JavaKind> kindGetter, ToLongFunction<Object> offsetGetter, Function<Object, Object> baseGetter) {
        super(kindGetter, offsetGetter);
        this.baseGetter = baseGetter;
    }

    @Override
    ResolvedJavaField findOriginalField(Object varHandle) {
        long offset = offsetGetter.applyAsLong(varHandle);
        Object base = baseGetter.apply(varHandle);
        JavaKind kind = kindGetter.apply(varHandle);
        JavaConstant baseHandle = GraalAccess.getOriginalProviders().getSnippetReflection().forObject(base);
        ResolvedJavaField result = GraalAccess.getOriginalProviders().getMetaAccessExtensionProvider().getStaticFieldForAccess(baseHandle, offset, kind);
        if (result == null) {
            throw VMError.shouldNotReachHere("Could not find static field referenced in VarHandle: base = " + base + ", offset = " + offset + ", kind = " + kind);
        }
        return result;
    }
}

final class InstanceVarHandleInfo extends VarHandleInfo {
    final Function<Object, Class<?>> typeGetter;

    InstanceVarHandleInfo(Function<Object, JavaKind> kindGetter, ToLongFunction<Object> offsetGetter, Function<Object, Class<?>> typeGetter) {
        super(kindGetter, offsetGetter);
        this.typeGetter = typeGetter;
    }

    @Override
    ResolvedJavaField findOriginalField(Object varHandle) {
        long offset = offsetGetter.applyAsLong(varHandle);
        Class<?> clazz = typeGetter.apply(varHandle);
        ResolvedJavaType type = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(clazz);
        JavaKind kind = kindGetter.apply(varHandle);
        ResolvedJavaField result = type.findInstanceFieldWithOffset(offset, kind);
        if (result == null) {
            throw VMError.shouldNotReachHere("Could not find instance field referenced in VarHandle: type = " + type + ", offset = " + offset + ", kind = " + kind);
        }
        return result;
    }
}
