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
import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
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

    private HashMap<Long, HotSpotResolvedJavaField> fieldCache;
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
        Class<?> javaClass = runtime().getCompilerToVM().getJavaMirror(metaspaceKlass);
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
     * Gets the metaspace Klass for this type.
     */
    private long metaspaceKlass() {
        return HotSpotGraalRuntime.unsafeReadWord(javaClass, runtime().getConfig().klassOffset);
    }

    @Override
    public int getModifiers() {
        return mirror().getModifiers();
    }

    public int getAccessFlags() {
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getInt(metaspaceKlass() + config.klassAccessFlagsOffset);
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        if (arrayOfType == null) {
            arrayOfType = fromClass(Array.newInstance(mirror(), 0).getClass());
        }
        return arrayOfType;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        Class<?> javaComponentType = mirror().getComponentType();
        return javaComponentType == null ? null : fromClass(javaComponentType);
    }

    @Override
    public ResolvedJavaType findUniqueConcreteSubtype() {
        HotSpotVMConfig config = runtime().getConfig();
        if (isArray()) {
            return getElementalType(this).isFinal() ? this : null;
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
            if (type.isAbstract() || type.isInterface() || !type.isLeafClass()) {
                return null;
            }
            return type;
        } else {
            HotSpotResolvedObjectType type = this;
            while (type.isAbstract()) {
                long subklass = type.getSubklass();
                if (subklass == 0 || unsafeReadWord(subklass + config.nextSiblingOffset) != 0) {
                    return null;
                }
                type = (HotSpotResolvedObjectType) fromMetaspaceKlass(subklass);
            }
            if (type.isAbstract() || type.isInterface() || !type.isLeafClass()) {
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
        Class<?> javaSuperclass = mirror().getSuperclass();
        return javaSuperclass == null ? null : (HotSpotResolvedObjectType) fromClass(javaSuperclass);
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        if (interfaces == null) {
            Class<?>[] javaInterfaces = mirror().getInterfaces();
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
            if (mirror() == Object[].class || componentType.isPrimitive()) {
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
        return isFinal() ? this : null;
    }

    @Override
    public Constant getEncoding(Representation r) {
        switch (r) {
            case JavaClass:
                return HotSpotObjectConstant.forObject(mirror());
            case ObjectHub:
                return klass();
            default:
                throw GraalInternalError.shouldNotReachHere("unexpected representation " + r);
        }
    }

    @Override
    public boolean hasFinalizableSubclass() {
        assert !isArray();
        return runtime().getCompilerToVM().hasFinalizableSubclass(metaspaceKlass());
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
        return mirror().isArray();
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
            unsafe.ensureClassInitialized(mirror());
            assert isInitialized();
        }
    }

    @Override
    public boolean isInstance(Constant obj) {
        if (obj.getKind() == Kind.Object && !obj.isNull()) {
            return mirror().isInstance(HotSpotObjectConstant.asObject(obj));
        }
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return !isArray() && !isInterface();
    }

    @Override
    public boolean isInterface() {
        return mirror().isInterface();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        assert other != null;
        if (other instanceof HotSpotResolvedObjectType) {
            HotSpotResolvedObjectType otherType = (HotSpotResolvedObjectType) other;
            return mirror().isAssignableFrom(otherType.mirror());
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
        if (!method.isAbstract() && method.getDeclaringClass().equals(this)) {
            return method;
        }

        final long resolvedMetaspaceMethod = runtime().getCompilerToVM().resolveMethod(metaspaceKlass(), method.getName(), ((HotSpotSignature) method.getSignature()).getMethodDescriptor());
        if (resolvedMetaspaceMethod == 0) {
            return null;
        }
        HotSpotResolvedJavaMethod resolvedMethod = HotSpotResolvedJavaMethod.fromMetaspace(resolvedMetaspaceMethod);
        if (resolvedMethod.isAbstract()) {
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

    public synchronized HotSpotResolvedJavaField createField(String fieldName, JavaType type, long offset, int rawFlags) {
        HotSpotResolvedJavaField result = null;

        final int flags = rawFlags & getReflectionFieldModifiers();

        final long id = offset + ((long) flags << 32);

        // (thomaswue) Must cache the fields, because the local load elimination only works if the
        // objects from two field lookups are identical.
        if (fieldCache == null) {
            fieldCache = new HashMap<>(8);
        } else {
            result = fieldCache.get(id);
        }

        if (result == null) {
            result = new HotSpotResolvedJavaField(this, fieldName, type, offset, rawFlags);
            fieldCache.put(id, result);
        } else {
            assert result.getName().equals(fieldName);
            // assert result.getType().equals(type);
            assert result.offset() == offset;
            assert result.getModifiers() == flags;
        }

        return result;
    }

    @Override
    public ResolvedJavaMethod findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return ((HotSpotResolvedJavaMethod) method).uniqueConcreteMethod();
    }

    /**
     * This class represents the field information for one field contained in the fields array of an
     * {@code InstanceKlass}. The implementation is similar to the native {@code FieldInfo} class.
     */
    private class FieldInfo {
        /**
         * Native pointer into the array of Java shorts.
         */
        private final long metaspaceData;

        /**
         * Creates a field info for the field in the fields array at index {@code index}.
         *
         * @param index index to the fields array
         */
        public FieldInfo(int index) {
            HotSpotVMConfig config = runtime().getConfig();
            // Get Klass::_fields
            final long metaspaceFields = unsafe.getAddress(metaspaceKlass() + config.instanceKlassFieldsOffset);
            assert config.fieldInfoFieldSlots == 6 : "revisit the field parsing code";
            metaspaceData = metaspaceFields + config.arrayU2DataOffset + config.fieldInfoFieldSlots * Short.BYTES * index;
        }

        private int getAccessFlags() {
            return readFieldSlot(runtime().getConfig().fieldInfoAccessFlagsOffset);
        }

        private int getNameIndex() {
            return readFieldSlot(runtime().getConfig().fieldInfoNameIndexOffset);
        }

        private int getSignatureIndex() {
            return readFieldSlot(runtime().getConfig().fieldInfoSignatureIndexOffset);
        }

        public int getOffset() {
            HotSpotVMConfig config = runtime().getConfig();
            final int lowPacked = readFieldSlot(config.fieldInfoLowPackedOffset);
            final int highPacked = readFieldSlot(config.fieldInfoHighPackedOffset);
            final int offset = ((highPacked << Short.SIZE) | lowPacked) >> config.fieldInfoTagSize;
            return offset;
        }

        /**
         * Helper method to read an entry (slot) from the field array. Currently field info is laid
         * on top an array of Java shorts.
         */
        private int readFieldSlot(int index) {
            return unsafe.getChar(metaspaceData + Short.BYTES * index);
        }

        /**
         * Returns the name of this field as a {@link String}. If the field is an internal field the
         * name index is pointing into the vmSymbols table.
         */
        public String getName() {
            final int nameIndex = getNameIndex();
            return isInternal() ? HotSpotVmSymbols.symbolAt(nameIndex) : constantPool().lookupUtf8(nameIndex);
        }

        /**
         * Returns the signature of this field as {@link String}. If the field is an internal field
         * the signature index is pointing into the vmSymbols table.
         */
        public String getSignature() {
            final int signatureIndex = getSignatureIndex();
            return isInternal() ? HotSpotVmSymbols.symbolAt(signatureIndex) : constantPool().lookupUtf8(signatureIndex);
        }

        public JavaType getType() {
            String signature = getSignature();
            return runtime().lookupType(signature, HotSpotResolvedObjectType.this, false);
        }

        private boolean isInternal() {
            return (getAccessFlags() & runtime().getConfig().jvmAccFieldInternal) != 0;
        }

        public boolean isStatic() {
            return Modifier.isStatic(getAccessFlags());
        }

        public boolean hasGenericSignature() {
            return (getAccessFlags() & runtime().getConfig().jvmAccFieldHasGenericSignature) != 0;
        }
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
                final int fieldCount = getFieldCount();
                ArrayList<HotSpotResolvedJavaField> fieldsArray = new ArrayList<>(fieldCount);

                for (int i = 0; i < fieldCount; i++) {
                    FieldInfo field = new FieldInfo(i);

                    // We are only interested in instance fields.
                    if (!field.isStatic()) {
                        HotSpotResolvedJavaField resolvedJavaField = createField(field.getName(), field.getType(), field.getOffset(), field.getAccessFlags());
                        fieldsArray.add(resolvedJavaField);
                    }
                }

                fieldsArray.sort(new OffsetComparator());

                HotSpotResolvedJavaField[] myFields = fieldsArray.toArray(new HotSpotResolvedJavaField[0]);

                if (mirror() != Object.class) {
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
            while (myFieldsStart < instanceFields.length && !instanceFields[myFieldsStart].getDeclaringClass().equals(this)) {
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

    /**
     * Returns the actual field count of this class's internal {@code InstanceKlass::_fields} array
     * by walking the array and discounting the generic signature slots at the end of the array.
     *
     * <p>
     * See {@code FieldStreamBase::init_generic_signature_start_slot}
     */
    private int getFieldCount() {
        HotSpotVMConfig config = runtime().getConfig();
        final long metaspaceFields = unsafe.getAddress(metaspaceKlass() + config.instanceKlassFieldsOffset);
        int metaspaceFieldsLength = unsafe.getInt(metaspaceFields + config.arrayU1LengthOffset);
        int fieldCount = 0;

        for (int i = 0, index = 0; i < metaspaceFieldsLength; i += config.fieldInfoFieldSlots, index++) {
            FieldInfo field = new FieldInfo(index);
            if (field.hasGenericSignature()) {
                metaspaceFieldsLength--;
            }
            fieldCount++;
        }
        return fieldCount;
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
        return mirror().getAnnotation(annotationClass);
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    /**
     * Gets the metaspace Klass boxed in a {@link Constant}.
     */
    public Constant klass() {
        return HotSpotMetaspaceConstant.forMetaspaceObject(runtime().getTarget().wordKind, metaspaceKlass(), this);
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
        Constructor<?>[] constructors = mirror().getDeclaredConstructors();
        ResolvedJavaMethod[] result = new ResolvedJavaMethod[constructors.length];
        for (int i = 0; i < constructors.length; i++) {
            result[i] = runtime().getHostProviders().getMetaAccess().lookupJavaConstructor(constructors[i]);
            assert result[i].isConstructor();
        }
        return result;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        Method[] methods = mirror().getDeclaredMethods();
        ResolvedJavaMethod[] result = new ResolvedJavaMethod[methods.length];
        for (int i = 0; i < methods.length; i++) {
            result[i] = runtime().getHostProviders().getMetaAccess().lookupJavaMethod(methods[i]);
            assert !result[i].isConstructor();
        }
        return result;
    }

    public ResolvedJavaMethod getClassInitializer() {
        final long metaspaceMethod = runtime().getCompilerToVM().getClassInitializer(metaspaceKlass());
        if (metaspaceMethod != 0L) {
            return createMethod(metaspaceMethod);
        }
        return null;
    }

    @Override
    public Constant newArray(int length) {
        return HotSpotObjectConstant.forObject(Array.newInstance(mirror(), length));
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
