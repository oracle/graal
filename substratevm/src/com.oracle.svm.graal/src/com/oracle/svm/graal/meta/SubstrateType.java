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
package com.oracle.svm.graal.meta;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.Replaced;
import com.oracle.svm.core.util.VMError;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.nodes.NodeCloneable;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateType extends NodeClass implements SharedType, Replaced {

    protected static final SubstrateType[] EMPTY_ARRAY = new SubstrateType[0];

    private final JavaKind kind;
    private final DynamicHub hub;

    /**
     * All instance fields (including the fields of superclasses) for this type.
     *
     * If the type has more than one instance field, it is a {@link SubstrateField}[] array. If the
     * type has exactly one instance field, it is the {@link SubstrateField} instance to avoid
     * having an array in the image heap. If the type has no instance field, it is a
     * {@link SubstrateField}[] array of length 0. If it is not known if the type has an instance
     * field (because the type metadata was created at image runtime), it is null.
     */
    @UnknownObjectField(types = {SubstrateField[].class, SubstrateField.class}, canBeNull = true)//
    Object rawAllInstanceFields;

    @UnknownPrimitiveField private int instanceOfFromTypeID;
    @UnknownPrimitiveField private int instanceOfNumTypeIDs;
    @UnknownObjectField(types = {DynamicHub.class}) protected DynamicHub uniqueConcreteImplementation;

    public SubstrateType(JavaKind kind, DynamicHub hub) {
        /* The constructor does not use the parameter, so we can pass whatever we want. */
        super(Node.class);

        this.kind = kind;
        this.hub = hub;

        /* Marker value that we do not have information for instanceOf checks. */
        this.instanceOfFromTypeID = -1;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setRawAllInstanceFields(SubstrateField[] allInstanceFields) {
        if (allInstanceFields.length == 0) {
            /*
             * We cannot use null as the marker value, because null means
             * "no field information available" for instances created at run time.
             */
            this.rawAllInstanceFields = SubstrateField.EMPTY_ARRAY;
        } else if (allInstanceFields.length == 1) {
            this.rawAllInstanceFields = allInstanceFields[0];
        } else {
            this.rawAllInstanceFields = allInstanceFields;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getRawAllInstanceFields() {
        return rawAllInstanceFields;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setTypeCheckData(int instanceOfFromTypeID, int instanceOfNumTypeIDs, DynamicHub uniqueConcreteImplementation) {
        this.instanceOfFromTypeID = instanceOfFromTypeID;
        this.instanceOfNumTypeIDs = instanceOfNumTypeIDs;
        this.uniqueConcreteImplementation = uniqueConcreteImplementation;
    }

    /**
     * The kind of the field in memory (in contrast to {@link #getJavaKind()}, which is the kind of
     * the field on the Java type system level). For example {@link WordBase word types} have a
     * {@link #getJavaKind} of {@link JavaKind#Object}, but a primitive {@link #getStorageKind}.
     */
    @Override
    public final JavaKind getStorageKind() {
        if (WordBase.class.isAssignableFrom(hub.asClass())) {
            return FrameAccess.getWordKind();
        } else {
            return getJavaKind();
        }
    }

    @Override
    public DynamicHub getHub() {
        return hub;
    }

    @Override
    public int getInstanceOfFromTypeID() {
        return instanceOfFromTypeID;
    }

    @Override
    public int getInstanceOfNumTypeIDs() {
        return instanceOfNumTypeIDs;
    }

    @Override
    public String getName() {
        return MetaUtil.toInternalName(hub.getName());
    }

    @Override
    public JavaKind getJavaKind() {
        return kind;
        // return Kind.fromJavaClass(hub.asClass());
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return new AssumptionResult<>(false);
    }

    @Override
    public boolean isInterface() {
        return hub.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        return hub.isInstanceClass();
    }

    @Override
    public boolean isArray() {
        return hub.isArray();
    }

    @Override
    public boolean isPrimitive() {
        return hub.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        throw new InternalError("isEnum for " + hub.getName() + " unimplemented");
    }

    @Override
    public int getModifiers() {
        return hub.getModifiers();
    }

    @Override
    public boolean isInitialized() {
        return hub.isInitialized();
    }

    @Override
    public void initialize() {
        hub.ensureInitialized();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return hub.isAssignableFromHub(((SubstrateType) other).hub);
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        if (obj.getJavaKind() == JavaKind.Object && !obj.isNull()) {
            DynamicHub objHub = KnownIntrinsics.readHub(SubstrateObjectConstant.asObject(obj));
            return hub.isAssignableFromHub(objHub);
        }
        return false;
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        ResolvedJavaType result = getSingleImplementor();
        if (result == null) {
            return null;
        } else {
            return new AssumptionResult<>(result);
        }
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        if (uniqueConcreteImplementation == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaType(uniqueConcreteImplementation.asClass());
    }

    @Override
    public SubstrateType getSuperclass() {
        DynamicHub superHub = hub.getSuperHub();
        if (superHub == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(superHub);
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        DynamicHub[] hubs = hub.getInterfaces();
        SubstrateType[] result = new SubstrateType[hubs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(hubs[i]);
        }
        return result;
    }

    private SubstrateType getSuperType() {
        if (isArray() || isInterface()) {
            return SubstrateMetaAccess.singleton().lookupJavaType(Object.class);
        } else {
            return getSuperclass();
        }
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (this.isPrimitive() || otherType.isPrimitive()) {
            return null;
        } else {
            SubstrateType t1 = this;
            SubstrateType t2 = (SubstrateType) otherType;
            while (true) {
                if (t1.isAssignableFrom(t2)) {
                    return t1;
                }
                if (t2.isAssignableFrom(t1)) {
                    return t2;
                }
                t1 = t1.getSuperType();
                t2 = t2.getSuperType();
            }
        }
    }

    @Override
    public boolean isJavaLangObject() {
        return hub.asClass() == Object.class;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (hub.getComponentHub() == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(hub.getComponentHub());
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        if (hub.getArrayHub() == null) {
            throw VMError.shouldNotReachHere("no array class for " + hub.getName() + " available");
        }
        return SubstrateMetaAccess.singleton().lookupJavaTypeFromHub(hub.getArrayHub());
    }

    @Override
    public SubstrateField[] getInstanceFields(boolean includeSuperclasses) {
        if (rawAllInstanceFields == null) {
            /*
             * The type was created at run time from the Class, so we do not have field information.
             * If we need the fields for a type, the type has to be created during image generation.
             */
            throw VMError.shouldNotReachHere("no instance fields for " + hub.getName() + " available");
        }

        SubstrateType superclass = getSuperclass();
        if (includeSuperclasses || superclass == null) {
            if (rawAllInstanceFields instanceof SubstrateField) {
                return new SubstrateField[]{(SubstrateField) rawAllInstanceFields};
            } else {
                return (SubstrateField[]) rawAllInstanceFields;
            }

        } else {
            int totalCount = getInstanceFieldCount();
            int superCount = superclass.getInstanceFieldCount();
            assert totalCount >= superCount;

            if (totalCount == superCount) {
                return SubstrateField.EMPTY_ARRAY;
            } else if (rawAllInstanceFields instanceof SubstrateField) {
                assert superCount == 0 && totalCount == 1;
                return new SubstrateField[]{(SubstrateField) rawAllInstanceFields};
            } else if (superCount == 0) {
                return (SubstrateField[]) rawAllInstanceFields;
            } else {
                assert Arrays.equals(superclass.getInstanceFields(true),
                                Arrays.copyOf((SubstrateField[]) rawAllInstanceFields, superCount)) : "Superclass fields must be the first elements of the fields defined in this class";
                return Arrays.copyOfRange((SubstrateField[]) rawAllInstanceFields, superCount, totalCount);
            }
        }
    }

    public int getInstanceFieldCount() {
        if (rawAllInstanceFields instanceof SubstrateField) {
            return 1;
        } else {
            return ((SubstrateField[]) rawAllInstanceFields).length;
        }
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        throw VMError.unimplemented();
    }

    @Override
    public Annotation[] getAnnotations() {
        return getHub().asClass().getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getHub().asClass().getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getHub().asClass().getAnnotation(annotationClass);
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        assert offset >= 0;

        if (rawAllInstanceFields == null) {
            /*
             * The type was created at run time from the Class, so we do not have field information.
             * The type's superclass however might not be created at run time thus having fields we
             * need to look into.
             */
            if (getSuperclass() != null) {
                return getSuperclass().findInstanceFieldWithOffset(offset, expectedKind);
            }

        } else {
            if (rawAllInstanceFields instanceof SubstrateField) {
                SubstrateField field = (SubstrateField) rawAllInstanceFields;
                if (fieldMatches(field, offset)) {
                    return field;
                }
            } else {
                for (SubstrateField field : (SubstrateField[]) rawAllInstanceFields) {
                    if (fieldMatches(field, offset)) {
                        return field;
                    }
                }
            }
        }

        /* No match found. */
        return null;
    }

    /**
     * Determines whether a field of this type matches the expected offset.
     *
     * NOTE: We do not check anything except the offset as Truffle stores type void into types int
     * and object as well as type long into integers.
     */
    private static boolean fieldMatches(SubstrateField field, long offset) {
        return field.getLocation() == offset;
    }

    @Override
    public String getSourceFileName() {
        return hub.getSourceFileName();
    }

    @Override
    public boolean isLocal() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean isMember() {
        throw VMError.unimplemented();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        Class<?> enclosingClass = hub.asClass().getEnclosingClass();
        if (enclosingClass == null) {
            return null;
        }
        return SubstrateMetaAccess.singleton().lookupJavaType(enclosingClass);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        throw VMError.unimplemented();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        throw VMError.unimplemented();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean isLinked() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return SubstrateMetaAccess.singleton().lookupJavaType(Cloneable.class).isAssignableFrom(this);
    }

    @Override
    public ResolvedJavaType getHostClass() {
        throw VMError.unimplemented();
    }

    @Override
    public int hashCode() {
        return hub.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof SubstrateType && ((SubstrateType) obj).hub == hub);
    }

    @Override
    public String toString() {
        return "SubstrateType<" + toJavaName(true) + ">";
    }

    /*
     * Implementation of Truffle NodeClass interface
     */

    @SuppressWarnings("deprecation")
    @Override
    public com.oracle.truffle.api.nodes.NodeFieldAccessor getNodeClassField() {
        return SubstrateNodeFieldAccessor.fromSubstrateField(
                        getNodeFields(field -> field.getDeclaringClass().getHub().asClass() == Node.class && field.getName().equals("nodeClass")).iterator().next());
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.oracle.truffle.api.nodes.NodeFieldAccessor[] getCloneableFields() {
        return nodeFieldIterableToArray(getNodeFields(field -> !SubstrateNodeFieldAccessor.isChildField(field) && !SubstrateNodeFieldAccessor.isChildrenField(field) &&
                        NodeCloneable.class.isAssignableFrom(field.getType().getHub().asClass())));
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.oracle.truffle.api.nodes.NodeFieldAccessor[] getFields() {
        return nodeFieldIterableToArray(getNodeFields(null));
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.oracle.truffle.api.nodes.NodeFieldAccessor getParentField() {
        return SubstrateNodeFieldAccessor.fromSubstrateField(getNodeFields(field -> field.getDeclaringClass().getHub().asClass() == Node.class && field.getName().equals("parent")).iterator().next());
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.oracle.truffle.api.nodes.NodeFieldAccessor[] getChildFields() {
        return nodeFieldIterableToArray(getNodeFields(field -> SubstrateNodeFieldAccessor.isChildField(field)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.oracle.truffle.api.nodes.NodeFieldAccessor[] getChildrenFields() {
        return nodeFieldIterableToArray(getNodeFields(field -> SubstrateNodeFieldAccessor.isChildrenField(field)));
    }

    @SuppressWarnings("deprecation")
    private static com.oracle.truffle.api.nodes.NodeFieldAccessor[] nodeFieldIterableToArray(Iterable<SubstrateField> fields) {
        ArrayList<com.oracle.truffle.api.nodes.NodeFieldAccessor> fieldList = new ArrayList<>();
        for (SubstrateField field : fields) {
            fieldList.add(SubstrateNodeFieldAccessor.fromSubstrateField(field));
        }
        return fieldList.toArray(new com.oracle.truffle.api.nodes.NodeFieldAccessor[0]);
    }

    @Override
    public Iterator<Node> makeIterator(Node node) {
        return new SubstrateNodeIterator(node, this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Node> getType() {
        assert Node.class.isAssignableFrom(getHub().asClass());
        return (Class<? extends Node>) getHub().asClass();
    }

    @Override
    protected Iterable<SubstrateField> getNodeFields() {
        return getNodeFields(null);
    }

    private Iterable<SubstrateField> getNodeFields(Predicate<SubstrateField> filter) {
        return () -> new SubstrateNodeFieldIterator(this, filter);
    }

    @Override
    public void putFieldObject(Object field, Node receiver, Object value) {
        assert !getFieldType(field).isPrimitive();
        assert value == null || getFieldType(field).isInstance(value);
        long offset = SubstrateNodeFieldAccessor.makeOffset((SubstrateField) field);
        UnsafeAccess.UNSAFE.putObject(receiver, offset, value);
    }

    @Override
    public Object getFieldObject(Object field, Node receiver) {
        assert !getFieldType(field).isPrimitive();
        long offset = SubstrateNodeFieldAccessor.makeOffset((SubstrateField) field);
        return UnsafeAccess.UNSAFE.getObject(receiver, offset);
    }

    @Override
    public Object getFieldValue(Object field, Node node) {
        Class<?> fieldType = getFieldType(field);
        long offset = SubstrateNodeFieldAccessor.makeOffset((SubstrateField) field);
        if (fieldType == boolean.class) {
            return UnsafeAccess.UNSAFE.getBoolean(node, offset);
        } else if (fieldType == byte.class) {
            return UnsafeAccess.UNSAFE.getByte(node, offset);
        } else if (fieldType == short.class) {
            return UnsafeAccess.UNSAFE.getShort(node, offset);
        } else if (fieldType == char.class) {
            return UnsafeAccess.UNSAFE.getChar(node, offset);
        } else if (fieldType == int.class) {
            return UnsafeAccess.UNSAFE.getInt(node, offset);
        } else if (fieldType == long.class) {
            return UnsafeAccess.UNSAFE.getLong(node, offset);
        } else if (fieldType == float.class) {
            return UnsafeAccess.UNSAFE.getFloat(node, offset);
        } else if (fieldType == double.class) {
            return UnsafeAccess.UNSAFE.getDouble(node, offset);
        } else {
            return UnsafeAccess.UNSAFE.getObject(node, offset);
        }
    }

    @Override
    public boolean isChildField(Object field) {
        return SubstrateNodeFieldAccessor.isChildField((SubstrateField) field);
    }

    @Override
    public boolean isChildrenField(Object field) {
        return SubstrateNodeFieldAccessor.isChildrenField((SubstrateField) field);
    }

    @Override
    public boolean isCloneableField(Object field) {
        return ((SubstrateField) field).truffleCloneableField;
    }

    @Override
    public Class<?> getFieldType(Object field) {
        return SubstrateNodeFieldAccessor.makeType((SubstrateField) field);
    }

    @Override
    public String getFieldName(Object field) {
        return ((SubstrateField) field).getName();
    }
}

@SuppressWarnings("deprecation")
class SubstrateNodeFieldAccessor extends com.oracle.truffle.api.nodes.NodeFieldAccessor.AbstractUnsafeNodeFieldAccessor {

    private final long offset;

    protected SubstrateNodeFieldAccessor(SubstrateField field, NodeFieldKind nodeFieldKind) {
        super(nodeFieldKind, makeDeclaringClass(field), field.getName(), makeType(field));
        this.offset = makeOffset(field);
    }

    protected static boolean isChildrenField(SubstrateField field) {
        return field.truffleChildrenField;
    }

    protected static boolean isChildField(SubstrateField field) {
        return field.truffleChildField;
    }

    static Class<?> makeType(SubstrateField field) {
        if (field.getType().getStorageKind().isPrimitive()) {
            /* For fields with a Word type, we have to return the primitive class. */
            return field.getType().getStorageKind().toJavaClass();
        } else {
            return field.getType().getHub().asClass();
        }
    }

    static Class<?> makeDeclaringClass(SubstrateField field) {
        return field.getDeclaringClass().getHub().asClass();
    }

    static long makeOffset(SubstrateField field) {
        assert field.getLocation() >= 0;
        return field.getLocation();
    }

    @Override
    public long getOffset() {
        return offset;
    }

    static SubstrateNodeFieldAccessor fromSubstrateField(SubstrateField field) {
        com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind nodeFieldKind;
        if (field.getDeclaringClass().getHub().asClass() == Node.class && field.getName().equals("parent")) {
            nodeFieldKind = com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.PARENT;
        } else if (field.getDeclaringClass().getHub().asClass() == Node.class && field.getName().equals("nodeClass")) {
            nodeFieldKind = com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.NODE_CLASS;
        } else if (SubstrateNodeFieldAccessor.isChildField(field)) {
            nodeFieldKind = com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILD;
        } else if (SubstrateNodeFieldAccessor.isChildrenField(field)) {
            nodeFieldKind = com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILDREN;
        } else {
            nodeFieldKind = com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.DATA;
        }
        return new SubstrateNodeFieldAccessor(field, nodeFieldKind);
    }
}

class SubstrateNodeFieldIterator implements Iterator<SubstrateField> {
    private final SubstrateType type;
    private final Predicate<SubstrateField> filter;
    private int nextFieldInType = 0;
    private SubstrateField nextField;

    SubstrateNodeFieldIterator(SubstrateType type, Predicate<SubstrateField> filter) {
        this.type = type;
        this.filter = filter;
        computeNext();
    }

    private void computeNext() {
        Object rawAllInstanceFields = type.rawAllInstanceFields;
        if (rawAllInstanceFields == null) {
            /*
             * The type was created at run time from the Class, so we do not have field information.
             * If we need the fields for a type, the type has to be created during image generation.
             */
            throw noFieldsError(type);

        } else if (rawAllInstanceFields instanceof SubstrateField) {
            if (nextFieldInType == 0) {
                SubstrateField field = (SubstrateField) rawAllInstanceFields;
                nextFieldInType++;
                if (filter == null || filter.test(field)) {
                    nextField = field;
                    return;
                }
            }
        } else {
            SubstrateField[] fields = (SubstrateField[]) rawAllInstanceFields;
            while (nextFieldInType < fields.length) {
                SubstrateField field = fields[nextFieldInType];
                nextFieldInType++;
                if (filter == null || filter.test(field)) {
                    nextField = field;
                    return;
                }
            }
        }

        nextField = null;
    }

    @Override
    public boolean hasNext() {
        return nextField != null;
    }

    @Override
    public SubstrateField next() {
        SubstrateField result = nextField;
        if (result == null) {
            throw new NoSuchElementException();
        }
        computeNext();
        return result;
    }

    static RuntimeException noFieldsError(SubstrateType type) {
        throw VMError.shouldNotReachHere("no instance fields for " + type.getHub().getName() + " available");
    }
}

class SubstrateNodeIterator implements Iterator<Node> {

    private final Node node;

    private final SubstrateType type;
    private int nextFieldInType;

    private Object[] children;
    private int nextChildInChildren;

    private Node next;

    protected SubstrateNodeIterator(Node node, SubstrateType type) {
        this.node = node;
        this.type = type;
        computeNext();
    }

    private void computeNext() {
        if (computeNextFromChildren()) {
            /* We have another array element from the last @Children field. */
            return;
        }

        Object rawAllInstanceFields = type.rawAllInstanceFields;
        if (rawAllInstanceFields == null) {
            /*
             * The type was created at run time from the Class, so we do not have field information.
             * If we need the fields for a type, the type has to be created during image generation.
             */
            throw SubstrateNodeFieldIterator.noFieldsError(type);

        } else if (rawAllInstanceFields instanceof SubstrateField) {
            if (nextFieldInType == 0) {
                SubstrateField field = (SubstrateField) rawAllInstanceFields;
                nextFieldInType++;
                if (computeNextFromField(field)) {
                    return;
                }
            }

        } else {
            SubstrateField[] fields = (SubstrateField[]) rawAllInstanceFields;
            while (nextFieldInType < fields.length) {
                SubstrateField field = fields[nextFieldInType];
                nextFieldInType++;
                if (computeNextFromField(field)) {
                    return;
                }
            }
        }

        next = null;
    }

    private boolean computeNextFromField(SubstrateField field) {
        if (SubstrateNodeFieldAccessor.isChildField(field)) {
            long offset = field.getLocation();
            next = (Node) UnsafeAccess.UNSAFE.getObject(node, offset);
            if (next != null) {
                return true;
            }
        } else if (SubstrateNodeFieldAccessor.isChildrenField(field)) {
            long offset = field.getLocation();
            children = (Object[]) UnsafeAccess.UNSAFE.getObject(node, offset);
            nextChildInChildren = 0;
            return computeNextFromChildren();
        }
        return false;
    }

    private boolean computeNextFromChildren() {
        if (children == null) {
            return false;
        }

        while (nextChildInChildren < children.length) {
            next = (Node) children[nextChildInChildren];
            nextChildInChildren++;
            if (next != null) {
                return true;
            }
        }

        children = null;
        nextChildInChildren = 0;
        return false;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Node next() {
        Node result = next;
        if (result == null) {
            throw new NoSuchElementException();
        }
        computeNext();
        return result;
    }
}
