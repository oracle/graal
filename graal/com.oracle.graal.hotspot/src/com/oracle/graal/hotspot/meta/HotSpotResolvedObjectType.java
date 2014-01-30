/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static java.lang.reflect.Modifier.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link JavaType} for resolved non-primitive HotSpot classes.
 */
public final class HotSpotResolvedObjectType extends HotSpotResolvedJavaType {

    private static final long serialVersionUID = 3481514353553840471L;

    /**
     * The Java class this type represents.
     */
    private final Class<?> javaClass;

    /**
     * Used for implemented a lazy binding from a {@link Node} type to a {@link NodeClass} value.
     */
    private NodeClass nodeClass;

    private HashMap<Long, ResolvedJavaField> fieldCache;
    private HashMap<Long, HotSpotResolvedJavaMethod> methodCache;
    private HotSpotResolvedJavaField[] instanceFields;
    private ResolvedJavaType[] interfaces;
    private ConstantPool constantPool;
    private ResolvedJavaType arrayOfType;

    /**
     * Gets the Graal mirror from a HotSpot metaspace Klass native object.
     * 
     * @param metaspaceKlass a metaspace Klass object boxed in a {@link Constant}
     * @return the {@link ResolvedJavaType} corresponding to {@code klassConstant}
     */
    public static ResolvedJavaType fromMetaspaceKlass(Constant metaspaceKlass) {
        assert metaspaceKlass.getKind() == Kind.Long;
        return fromMetaspaceKlass(metaspaceKlass.asLong());
    }

    /**
     * Gets the Graal mirror from a HotSpot metaspace Klass native object.
     * 
     * @param metaspaceKlass a metaspace Klass object
     * @return the {@link ResolvedJavaType} corresponding to {@code metaspaceKlass}
     */
    public static ResolvedJavaType fromMetaspaceKlass(long metaspaceKlass) {
        assert metaspaceKlass != 0;
        Class javaClass = (Class) runtime().getCompilerToVM().readUnsafeUncompressedPointer(null, metaspaceKlass + runtime().getConfig().classMirrorOffset);
        assert javaClass != null;
        return fromClass(javaClass);
    }

    /**
     * Creates the Graal mirror for a {@link Class} object.
     * 
     * <p>
     * <b>NOTE</b>: Creating an instance of this class does not install the mirror for the
     * {@link Class} type. Use {@link #fromClass(Class)}, {@link #fromMetaspaceKlass(Constant)} or
     * {@link #fromMetaspaceKlass(long)} instead.
     * </p>
     * 
     * @param javaClass the Class to create the mirror for
     */
    public HotSpotResolvedObjectType(Class<?> javaClass) {
        super(getSignatureName(javaClass));
        this.javaClass = javaClass;
        assert getName().charAt(0) != '[' || isArray() : getName();
    }

    /**
     * Returns the name of this type as it would appear in a signature.
     */
    private static String getSignatureName(Class<?> javaClass) {
        if (javaClass.isArray()) {
            return javaClass.getName().replace('.', '/');
        }
        return "L" + javaClass.getName().replace('.', '/') + ";";
    }

    /**
     * Gets the address of the C++ Klass object for this type.
     */
    private long metaspaceKlass() {
        return HotSpotGraalRuntime.unsafeReadWord(javaClass, runtime().getConfig().klassOffset);
    }

    @Override
    public int getModifiers() {
        return javaClass.getModifiers();
    }

    public int getAccessFlags() {
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getInt(metaspaceKlass() + config.klassAccessFlagsOffset);
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        if (arrayOfType == null) {
            arrayOfType = fromClass(Array.newInstance(javaClass, 0).getClass());
        }
        return arrayOfType;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        Class javaComponentType = javaClass.getComponentType();
        return javaComponentType == null ? null : fromClass(javaComponentType);
    }

    @Override
    public ResolvedJavaType findUniqueConcreteSubtype() {
        HotSpotVMConfig config = runtime().getConfig();
        if (isArray()) {
            return isFinal(getElementalType(this).getModifiers()) ? this : null;
        } else if (isInterface()) {
            final long implementorMetaspaceKlass = runtime().getCompilerToVM().getKlassImplementor(metaspaceKlass());

            // No implementor.
            if (implementorMetaspaceKlass == 0) {
                return null;
            }

            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) fromMetaspaceKlass(implementorMetaspaceKlass);

            /*
             * If the implementor field contains itself that indicates that the interface has more
             * than one implementors (see: InstanceKlass::add_implementor). The isInterface check
             * takes care of this fact since this class is an interface.
             */
            if (isAbstract(type.getModifiers()) || type.isInterface() || !type.isLeafClass()) {
                return null;
            }
            return type;
        } else {
            HotSpotResolvedObjectType type = this;
            while (isAbstract(type.getModifiers())) {
                long subklass = type.getSubklass();
                if (subklass == 0 || unsafeReadWord(subklass + config.nextSiblingOffset) != 0) {
                    return null;
                }
                type = (HotSpotResolvedObjectType) fromMetaspaceKlass(subklass);
            }
            if (isAbstract(type.getModifiers()) || type.isInterface() || !type.isLeafClass()) {
                return null;
            }
            return type;
        }
    }

    /**
     * Returns if type {@code type} is a leaf class. This is the case if the
     * {@code Klass::_subklass} field of the underlying class is zero.
     * 
     * @return true if the type is a leaf class
     */
    private boolean isLeafClass() {
        return getSubklass() == 0;
    }

    /**
     * Returns the {@code Klass::_subklass} field of the underlying metaspace klass for the given
     * type {@code type}.
     * 
     * @return value of the subklass field as metaspace klass pointer
     */
    private long getSubklass() {
        return unsafeReadWord(metaspaceKlass() + runtime().getConfig().subklassOffset);
    }

    @Override
    public HotSpotResolvedObjectType getSuperclass() {
        Class javaSuperclass = javaClass.getSuperclass();
        return javaSuperclass == null ? null : (HotSpotResolvedObjectType) fromClass(javaSuperclass);
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        if (interfaces == null) {
            Class[] javaInterfaces = javaClass.getInterfaces();
            ResolvedJavaType[] result = new ResolvedJavaType[javaInterfaces.length];
            for (int i = 0; i < javaInterfaces.length; i++) {
                result[i] = fromClass(javaInterfaces[i]);
            }
            interfaces = result;
        }
        return interfaces;
    }

    public HotSpotResolvedObjectType getSupertype() {
        if (isArray()) {
            ResolvedJavaType componentType = getComponentType();
            if (javaClass == Object[].class || componentType.isPrimitive()) {
                return (HotSpotResolvedObjectType) fromClass(Object.class);
            }
            return (HotSpotResolvedObjectType) ((HotSpotResolvedObjectType) componentType).getSupertype().getArrayClass();
        }
        if (isInterface()) {
            return (HotSpotResolvedObjectType) fromClass(Object.class);
        }
        return getSuperclass();
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (otherType.isPrimitive()) {
            return null;
        } else {
            HotSpotResolvedObjectType t1 = this;
            HotSpotResolvedObjectType t2 = (HotSpotResolvedObjectType) otherType;
            while (true) {
                if (t1.isAssignableFrom(t2)) {
                    return t1;
                }
                if (t2.isAssignableFrom(t1)) {
                    return t2;
                }
                t1 = t1.getSupertype();
                t2 = t2.getSupertype();
            }
        }
    }

    @Override
    public ResolvedJavaType asExactType() {
        if (isArray()) {
            return getComponentType().asExactType() != null ? this : null;
        }
        return isFinal(getModifiers()) ? this : null;
    }

    @Override
    public Constant getEncoding(Representation r) {
        switch (r) {
            case JavaClass:
                return Constant.forObject(javaClass);
            case ObjectHub:
                return klass();
            default:
                throw GraalInternalError.shouldNotReachHere("unexpected representation " + r);
        }
    }

    @Override
    public boolean hasFinalizableSubclass() {
        assert !isArray();
        return runtime().getCompilerToVM().hasFinalizableSubclass(this);
    }

    @Override
    public boolean hasFinalizer() {
        HotSpotVMConfig config = runtime().getConfig();
        return (getAccessFlags() & config.klassHasFinalizerFlag) != 0;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isArray() {
        return javaClass.isArray();
    }

    @Override
    public boolean isInitialized() {
        final int state = getState();
        return state == runtime().getConfig().klassStateFullyInitialized;
    }

    @Override
    public boolean isLinked() {
        final int state = getState();
        return state >= runtime().getConfig().klassStateLinked;
    }

    /**
     * Returns the value of the state field {@code InstanceKlass::_init_state} of the metaspace
     * klass.
     * 
     * @return state field value of this type
     */
    private int getState() {
        return unsafe.getByte(metaspaceKlass() + runtime().getConfig().klassStateOffset) & 0xFF;
    }

    @Override
    public void initialize() {
        if (!isInitialized()) {
            unsafe.ensureClassInitialized(javaClass);
            assert isInitialized();
        }
    }

    @Override
    public boolean isInstance(Constant obj) {
        if (obj.getKind() == Kind.Object && !obj.isNull()) {
            return javaClass.isInstance(obj.asObject());
        }
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return !isArray() && !isInterface();
    }

    @Override
    public boolean isInterface() {
        return javaClass.isInterface();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        assert other != null;
        if (other instanceof HotSpotResolvedObjectType) {
            HotSpotResolvedObjectType otherType = (HotSpotResolvedObjectType) other;
            return javaClass.isAssignableFrom(otherType.javaClass);
        }
        return false;
    }

    @Override
    public Kind getKind() {
        return Kind.Object;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method) {
        assert method instanceof HotSpotMethod;
        final long resolvedMetaspaceMethod = runtime().getCompilerToVM().resolveMethod(this, method.getName(), ((HotSpotSignature) method.getSignature()).getMethodDescriptor());
        if (resolvedMetaspaceMethod == 0) {
            return null;
        }
        HotSpotResolvedJavaMethod resolvedMethod = HotSpotResolvedJavaMethod.fromMetaspace(resolvedMetaspaceMethod);
        if (isAbstract(resolvedMethod.getModifiers())) {
            return null;
        }
        return resolvedMethod;
    }

    public ConstantPool constantPool() {
        if (constantPool == null) {
            final long metaspaceConstantPool = unsafe.getAddress(metaspaceKlass() + runtime().getConfig().instanceKlassConstantsOffset);
            constantPool = new HotSpotConstantPool(metaspaceConstantPool);
        }
        return constantPool;
    }

    /**
     * Gets the instance size of this type. If an instance of this type cannot be fast path
     * allocated, then the returned value is negative (its absolute value gives the size). Must not
     * be called if this is an array or interface type.
     */
    public int instanceSize() {
        assert !isArray();
        assert !isInterface();

        HotSpotVMConfig config = runtime().getConfig();
        final int layoutHelper = unsafe.getInt(metaspaceKlass() + config.klassLayoutHelperOffset);
        assert layoutHelper > config.klassLayoutHelperNeutralValue : "must be instance";

        // See: Klass::layout_helper_size_in_bytes
        int size = layoutHelper & ~config.klassLayoutHelperInstanceSlowPathBit;

        // See: Klass::layout_helper_needs_slow_path
        boolean needsSlowPath = (layoutHelper & config.klassLayoutHelperInstanceSlowPathBit) != 0;

        return needsSlowPath ? -size : size;
    }

    public synchronized HotSpotResolvedJavaMethod createMethod(long metaspaceMethod) {
        HotSpotResolvedJavaMethod method = null;
        if (methodCache == null) {
            methodCache = new HashMap<>(8);
        } else {
            method = methodCache.get(metaspaceMethod);
        }
        if (method == null) {
            method = new HotSpotResolvedJavaMethod(this, metaspaceMethod);
            methodCache.put(metaspaceMethod, method);
        }
        return method;
    }

    /**
     * Gets the mask used to filter out HotSpot internal flags for fields when a {@link Field}
     * object is created. This is the value of {@code JVM_RECOGNIZED_FIELD_MODIFIERS} in
     * {@code jvm.h}, <b>not</b> {@link Modifier#fieldModifiers()}.
     */
    public static int getReflectionFieldModifiers() {
        return runtime().getConfig().recognizedFieldModifiers;
    }

    public synchronized ResolvedJavaField createField(String fieldName, JavaType type, long offset, int rawFlags, boolean internal) {
        ResolvedJavaField result = null;

        int flags = rawFlags & getReflectionFieldModifiers();

        long id = offset + ((long) flags << 32);

        // (thomaswue) Must cache the fields, because the local load elimination only works if the
        // objects from two field lookups are identical.
        if (fieldCache == null) {
            fieldCache = new HashMap<>(8);
        } else {
            result = fieldCache.get(id);
        }

        if (result == null) {
            result = new HotSpotResolvedJavaField(this, fieldName, type, offset, flags, internal);
            fieldCache.put(id, result);
        } else {
            assert result.getName().equals(fieldName);
            assert result.getModifiers() == flags;
        }

        return result;
    }

    @Override
    public ResolvedJavaMethod findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return ((HotSpotResolvedJavaMethod) method).uniqueConcreteMethod();
    }

    private static class OffsetComparator implements Comparator<HotSpotResolvedJavaField> {

        @Override
        public int compare(HotSpotResolvedJavaField o1, HotSpotResolvedJavaField o2) {
            return o1.offset() - o2.offset();
        }
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        if (instanceFields == null) {
            if (isArray() || isInterface()) {
                instanceFields = new HotSpotResolvedJavaField[0];
            } else {
                HotSpotResolvedJavaField[] myFields = runtime().getCompilerToVM().getInstanceFields(this);
                Arrays.sort(myFields, new OffsetComparator());
                if (javaClass != Object.class) {
                    HotSpotResolvedJavaField[] superFields = (HotSpotResolvedJavaField[]) getSuperclass().getInstanceFields(true);
                    HotSpotResolvedJavaField[] fields = Arrays.copyOf(superFields, superFields.length + myFields.length);
                    System.arraycopy(myFields, 0, fields, superFields.length, myFields.length);
                    instanceFields = fields;
                } else {
                    assert myFields.length == 0 : "java.lang.Object has fields!";
                    instanceFields = myFields;
                }
            }
        }
        if (!includeSuperclasses) {
            int myFieldsStart = 0;
            while (myFieldsStart < instanceFields.length && instanceFields[myFieldsStart].getDeclaringClass() != this) {
                myFieldsStart++;
            }
            if (myFieldsStart == 0) {
                return instanceFields;
            }
            if (myFieldsStart == instanceFields.length) {
                return new HotSpotResolvedJavaField[0];
            }
            return Arrays.copyOfRange(instanceFields, myFieldsStart, instanceFields.length);
        }
        return instanceFields;
    }

    @Override
    public Class<?> mirror() {
        return javaClass;
    }

    @Override
    public String getSourceFileName() {
        HotSpotVMConfig config = runtime().getConfig();
        final int sourceFileNameIndex = unsafe.getChar(metaspaceKlass() + config.klassSourceFileNameIndexOffset);
        if (sourceFileNameIndex == 0) {
            return null;
        }
        return constantPool().lookupUtf8(sourceFileNameIndex);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return javaClass.getAnnotation(annotationClass);
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    /**
     * Gets the address of the C++ Klass object for this type.
     */
    public Constant klass() {
        return Constant.forIntegerKind(runtime().getTarget().wordKind, metaspaceKlass(), this);
    }

    public boolean isPrimaryType() {
        return runtime().getConfig().secondarySuperCacheOffset != superCheckOffset();
    }

    public int superCheckOffset() {
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getInt(metaspaceKlass() + config.superCheckOffsetOffset);
    }

    public long prototypeMarkWord() {
        HotSpotVMConfig config = runtime().getConfig();
        if (isArray()) {
            return config.arrayPrototypeMarkWord();
        } else {
            return unsafeReadWord(metaspaceKlass() + config.prototypeMarkWordOffset);
        }
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset) {
        ResolvedJavaField[] declaredFields = getInstanceFields(true);
        for (ResolvedJavaField field : declaredFields) {
            if (((HotSpotResolvedJavaField) field).offset() == offset) {
                return field;
            }
        }
        return null;
    }

    @Override
    public URL getClassFilePath() {
        Class<?> cls = mirror();
        return cls.getResource(MetaUtil.getSimpleName(cls, true).replace('.', '$') + ".class");
    }

    @Override
    public boolean isLocal() {
        return mirror().isLocalClass();
    }

    @Override
    public boolean isMember() {
        return mirror().isMemberClass();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        final Class<?> encl = mirror().getEnclosingClass();
        return encl == null ? null : fromClass(encl);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        Constructor[] constructors = javaClass.getDeclaredConstructors();
        ResolvedJavaMethod[] result = new ResolvedJavaMethod[constructors.length];
        for (int i = 0; i < constructors.length; i++) {
            result[i] = runtime().getHostProviders().getMetaAccess().lookupJavaConstructor(constructors[i]);
            assert result[i].isConstructor();
        }
        return result;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        Method[] methods = javaClass.getDeclaredMethods();
        ResolvedJavaMethod[] result = new ResolvedJavaMethod[methods.length];
        for (int i = 0; i < methods.length; i++) {
            result[i] = runtime().getHostProviders().getMetaAccess().lookupJavaMethod(methods[i]);
            assert !result[i].isConstructor();
        }
        return result;
    }

    public ResolvedJavaMethod getClassInitializer() {
        long metaspaceMethod = runtime().getCompilerToVM().getClassInitializer(this);
        if (metaspaceMethod != 0L) {
            return createMethod(metaspaceMethod);
        }
        return null;
    }

    @Override
    public Constant newArray(int length) {
        return Constant.forObject(Array.newInstance(javaClass, length));
    }

    /**
     * @return the {@link NodeClass} value (which may be {@code null}) associated with this type
     */
    public NodeClass getNodeClass() {
        return nodeClass;
    }

    /**
     * Sets the {@link NodeClass} value associated with this type.
     */
    public void setNodeClass(NodeClass nodeClass) {
        this.nodeClass = nodeClass;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HotSpotResolvedObjectType)) {
            return false;
        }
        HotSpotResolvedObjectType that = (HotSpotResolvedObjectType) obj;
        return this.mirror() == that.mirror();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        String simpleName;
        if (isArray() || isInterface()) {
            simpleName = getName();
        } else {
            simpleName = getName().substring(1, getName().length() - 1);
        }
        return "HotSpotType<" + simpleName + ", resolved>";
    }
}
