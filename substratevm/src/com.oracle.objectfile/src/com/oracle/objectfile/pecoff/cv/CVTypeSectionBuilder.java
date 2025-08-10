/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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
package com.oracle.objectfile.pecoff.cv;

import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.ADDRESS_BITS;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.CV_CALL_NEAR_C;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.FUNC_IS_CONSTRUCTOR;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.JAVA_LANG_OBJECT_NAME;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_CLASS;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_IVIRTUAL;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PRIVATE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PROTECTED;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PUBLIC;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PURE_IVIRTUAL;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PURE_VIRTUAL;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_STATIC;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_VANILLA;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_VIRTUAL;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.OBJ_HEADER_NAME;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PBOOL08;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PINT1;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PINT2;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PINT4;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PINT8;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PREAL32;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PREAL64;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PVOID;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_64PWCHAR;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_BOOL08;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_INT1;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_INT2;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_INT4;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_INT8;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_NOTYPE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_REAL32;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_REAL64;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_UINT4;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_VOID;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_WCHAR;
import static com.oracle.objectfile.pecoff.cv.CVTypeRecord.CV_TYPE_RECORD_MAX_SIZE;
import static com.oracle.objectfile.pecoff.cv.CVTypeRecord.CVClassRecord.ATTR_FORWARD_REF;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.MemberEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;

import jdk.graal.compiler.debug.GraalError;

class CVTypeSectionBuilder {

    private final int objectHeaderRecordIndex;

    private final CVTypeSectionImpl typeSection;

    private final TypeTable types;

    CVTypeSectionBuilder(CVTypeSectionImpl typeSection) {
        this.typeSection = typeSection;
        this.types = new TypeTable(typeSection);
        objectHeaderRecordIndex = types.getIndexForForwardRef(OBJ_HEADER_NAME);
    }

    void verifyAllClassesDefined() {
        /* When this is called, all types should be seen already. */
        /* Just in case, check to see if we have some unresolved forward refs. */
        types.testForUndefinedClasses();
    }

    CVTypeRecord buildType(TypeEntry typeEntry) {

        CVTypeRecord typeRecord = types.getExistingType(typeEntry);
        /*
         * If we've never seen the class or only defined it as a forward reference, define it now.
         */
        if (typeRecord != null && typeRecord.type == LF_CLASS && !((CVTypeRecord.CVClassRecord) typeRecord).isForwardRef()) {
            log("buildType() type %s(%s) is known %s", typeEntry.getTypeName(), "typeEntry.typeKind().name()", typeRecord);
        } else {
            log("buildType() %s %s size=%d - begin", "typeEntry.typeKind().name()", typeEntry.getTypeName(), typeEntry.getSize());
            switch (typeEntry) {
                case PrimitiveTypeEntry primitiveTypeEntry -> typeRecord = getPrimitiveTypeEntry(primitiveTypeEntry);
                case PointerToTypeEntry pointerToTypeEntry -> typeRecord = buildPointerToTypeEntry(pointerToTypeEntry);
                case HeaderTypeEntry headerTypeEntry -> {
                    /*
                     * The bits at the beginning of an Object: contains pointer to DynamicHub.
                     */
                    assert typeEntry.getTypeName().equals(OBJ_HEADER_NAME);
                    typeRecord = buildStructureTypeEntry(headerTypeEntry);
                }
                case StructureTypeEntry structureTypeEntry ->
                    // typeEntry is either ArrayTypeEntry, ClassEntry, or ForeignStructTypeEntry
                    // TODO continue treat foreign types as interfaces/classes but fix this later
                    typeRecord = buildStructureTypeEntry(structureTypeEntry);
            }
        }
        assert typeRecord != null;
        log("buildType end: %s", typeRecord);
        return typeRecord;
    }

    /**
     * Add type records for function. In the future add local types when they become available.
     *
     * @param entry primaryEntry containing entities whose type records must be added
     * @return type record for this function (may return existing matching record)
     */
    CVTypeRecord buildFunction(CompiledMethodEntry entry) {
        ClassEntry ownerType = entry.ownerType();
        assert ownerType != null;
        return buildMemberFunction(ownerType, entry.primary().getMethodEntry());
    }

    static class FieldListBuilder {

        static final int CV_INDEX_RECORD_SIZE = CVUtil.align4(new CVTypeRecord.CVIndexRecord(0).computeSize());
        final List<CVTypeRecord.FieldRecord> fields = new ArrayList<>();

        FieldListBuilder() {
        }

        void addField(CVTypeRecord.FieldRecord field) {
            fields.add(field);
        }

        int getFieldCount() {
            return fields.size();
        }

        CVTypeRecord.CVFieldListRecord buildFieldListRecords(CVTypeSectionBuilder builder) {

            /*
             * The last FieldList must refer back to the one before it, and must contain the first
             * fields in the class.
             */

            CVTypeRecord.CVFieldListRecord currentFieldList = new CVTypeRecord.CVFieldListRecord();
            Deque<CVTypeRecord.CVFieldListRecord> fl = new LinkedList<>();
            fl.add(currentFieldList);

            /* Build all Field List records in field order (FIFO). */
            for (CVTypeRecord.FieldRecord fieldRecord : fields) {
                /*
                 * Calculate the potential size of the fieldList if the current fieldRecord and a
                 * (potential) index record are added to it.
                 */
                int sizeOfExpandedFieldList = currentFieldList.getEstimatedSize() + CVUtil.align4(fieldRecord.computeSize()) + CV_INDEX_RECORD_SIZE;
                /* If there isn't enough room for the new fieldRecord, start a new CVFieldList. */
                if (sizeOfExpandedFieldList >= CV_TYPE_RECORD_MAX_SIZE) {
                    currentFieldList = new CVTypeRecord.CVFieldListRecord();
                    fl.add(currentFieldList);
                }
                currentFieldList.add(fieldRecord);
            }

            /*
             * Emit all Field List records in reverse order (LIFO), adding Index records to all but
             * the first emitted.
             */
            CVTypeRecord.CVFieldListRecord fieldListRecord = null;
            int idx = 0;
            while (!fl.isEmpty()) {
                fieldListRecord = fl.removeLast();
                fieldListRecord = builder.addTypeRecord(fieldListRecord);
                /* For all fieldlist but the first, link to the previous record. */
                if (idx != 0) {
                    fieldListRecord.add(new CVTypeRecord.CVIndexRecord(idx));
                }
                idx = fieldListRecord.getSequenceNumber();
            }
            return fieldListRecord;
        }
    }

    private CVTypeRecord getPrimitiveTypeEntry(final PrimitiveTypeEntry typeEntry) {
        // Check if we have already seen this primitive type
        CVTypeRecord primitiveType = types.getExistingType(typeEntry);
        if (primitiveType != null) {
            return primitiveType;
        }

        /*
         * Primitive types are pre-defined and do not get written out to the typeInfo section. We
         * may need to fetch the correct sequence numbers for foreign primitives
         */
        short typeId;
        short pointerTypeId;
        int size = typeEntry.getSize();
        if (typeEntry.isNumericFloat()) {
            assert size == 4 || size == 8;
            if (size == 4) {
                typeId = T_REAL32;
                pointerTypeId = T_64PREAL32;
            } else {
                typeId = T_REAL64;
                pointerTypeId = T_64PREAL64;
            }
        } else {
            assert typeEntry.isNumericInteger();
            assert size == 1 || size == 2 || size == 4 || size == 8;
            if (size == 1) {
                typeId = T_INT1;
                pointerTypeId = T_64PINT1;
            } else if (size == 2) {
                typeId = T_INT2;
                pointerTypeId = T_64PINT2;
            } else if (size == 4) {
                typeId = T_INT4;
                pointerTypeId = T_64PINT4;
            } else {
                typeId = T_INT8;
                pointerTypeId = T_64PINT8;
            }

            if (typeEntry.isUnsigned()) {
                // signed/unsigned differs by the LSB for 'Real int' types
                typeId++;
                pointerTypeId++;
            }
        }

        types.definePrimitiveType(typeEntry.getTypeName(), typeId, size, pointerTypeId);
        return types.getExistingType(typeEntry);
    }

    private CVTypeRecord buildPointerToTypeEntry(final PointerToTypeEntry typeEntry) {
        CVTypeRecord pointedToType = buildType(typeEntry.getPointerTo());
        CVTypeRecord pointerType = addTypeRecord(new CVTypeRecord.CVTypePointerRecord(pointedToType.getSequenceNumber(), CVTypeRecord.CVTypePointerRecord.NORMAL_64));

        types.typeNameMap.put(typeEntry.getTypeName(), pointerType);
        return pointerType;
    }

    private CVTypeRecord buildStructureTypeEntry(final StructureTypeEntry typeEntry) {

        log("buildStructureTypeEntry size=%d kind=%s %s", typeEntry.getSize(), "typeEntry.typeKind().name()", typeEntry.getTypeName());

        StructureTypeEntry superType = null;
        if (typeEntry instanceof ClassEntry classEntry) {
            superType = classEntry.getSuperClass();
        } else if (typeEntry instanceof ForeignStructTypeEntry foreignStructTypeEntry) {
            superType = foreignStructTypeEntry.getParent();
        }
        int superTypeIndex = superType != null ? types.getIndexForForwardRef(superType) : 0;

        /* Arrays are implemented as classes, but the inheritance from Object() is implicit. */
        if (superTypeIndex == 0 && typeEntry instanceof ArrayTypeEntry) {
            superTypeIndex = types.getIndexForForwardRef(JAVA_LANG_OBJECT_NAME);
        }

        /* Both java.lang.Object and __objhdr have null superclass. */
        /* Force java.lang.Object to have __objhdr as a superclass. */
        /* Force interfaces to have __objhdr as a superclass. */
        if (superTypeIndex == 0 && (typeEntry.getTypeName().equals(JAVA_LANG_OBJECT_NAME) || typeEntry instanceof InterfaceClassEntry)) {
            superTypeIndex = objectHeaderRecordIndex;
        }

        final List<MethodEntry> methods = typeEntry instanceof ClassEntry classEntry ? classEntry.getMethods() : List.of();

        /* Build fieldlist record */
        FieldListBuilder fieldListBuilder = new FieldListBuilder();
        log("building field list");

        if (superTypeIndex != 0) {
            CVTypeRecord.CVBaseMemberRecord btype = new CVTypeRecord.CVBaseMemberRecord(MPROP_PUBLIC, superTypeIndex, 0);
            log("basetype %s", btype);
            fieldListBuilder.addField(btype);
        }

        /* Only define manifested fields. */
        typeEntry.getFields().stream().filter(CVTypeSectionBuilder::isManifestedField).forEach(f -> {
            log("field %s attr=(%s) offset=%d size=%d valuetype=%s", f.fieldName(), f.getModifiersString(), f.getOffset(), f.getSize(), f.getValueType().getTypeName());
            CVTypeRecord.FieldRecord fieldRecord = buildField(f);
            log("field %s", fieldRecord);
            fieldListBuilder.addField(fieldRecord);
        });

        if (typeEntry instanceof ArrayTypeEntry arrayEntry) {
            /*
             * Model an array as a struct with a pointer, length and then array of length 0.
             * String[] becomes struct String[] : Object { int length; String*[0]; }
             */

            /* Build 0 length array - this index could be cached. */
            final TypeEntry elementType = arrayEntry.getElementType();
            int elementTypeIndex = types.getIndexForPointerOrPrimitive(elementType);
            CVTypeRecord array0record = addTypeRecord(new CVTypeRecord.CVTypeArrayRecord(elementTypeIndex, T_UINT4, 0));

            /* Build a field for the 0 length array. */
            CVTypeRecord.CVMemberRecord dm = new CVTypeRecord.CVMemberRecord(MPROP_PUBLIC, array0record.getSequenceNumber(), typeEntry.getSize(), "data");
            log("field %s", dm);
            fieldListBuilder.addField(dm);
        }

        /*
         * Functions go into the main fieldList if they are not overloaded. Overloaded functions get
         * a M_FUNCTION entry in the field list, and a LF_METHODLIST record pointing to M_MFUNCTION
         * records for each overload.
         */
        if (!methods.isEmpty()) {

            log("building methods");

            /* first build a list of all overloaded functions */
            HashSet<String> overloaded = new HashSet<>(methods.size());
            HashSet<String> allFunctions = new HashSet<>(methods.size());
            methods.forEach(m -> {
                if (allFunctions.contains(m.getMethodName())) {
                    overloaded.add(m.getMethodName());
                } else {
                    allFunctions.add(m.getMethodName());
                }
            });

            /* TODO: if methodlist is too big, split it up using LF_INDEX records. */
            overloaded.forEach(mname -> {

                /* LF_METHODLIST */
                CVTypeRecord.CVTypeMethodListRecord mlist = new CVTypeRecord.CVTypeMethodListRecord();

                /* LF_MFUNCTION records */
                methods.stream().filter(methodEntry -> methodEntry.getMethodName().equals(mname)).forEach(m -> {
                    log("overloaded method %s attr=(%s) valuetype=%s", m.getMethodName(), m.getModifiersString(), m.getValueType().getTypeName());
                    CVTypeRecord.CVTypeMFunctionRecord mFunctionRecord = buildMemberFunction((ClassEntry) typeEntry, m);
                    short attr = modifiersToAttr(m);
                    log("    overloaded method %s", mFunctionRecord);
                    mlist.add(attr, mFunctionRecord.getSequenceNumber(), m.getVtableOffset(), m.getMethodName());
                });

                CVTypeRecord.CVTypeMethodListRecord nmlist = addTypeRecord(mlist);

                /* LF_METHOD record */
                CVTypeRecord.CVOverloadedMethodRecord methodRecord = new CVTypeRecord.CVOverloadedMethodRecord((short) nmlist.count(), nmlist.getSequenceNumber(), mname);
                fieldListBuilder.addField(methodRecord);
            });

            methods.stream().filter(methodEntry -> !overloaded.contains(methodEntry.getMethodName())).forEach(m -> {
                log("`unique method %s %s(...)", m.getMethodName(), m.getModifiersString(), m.getValueType().getTypeName(), m.getMethodName());
                CVTypeRecord.CVOneMethodRecord method = buildMethod((ClassEntry) typeEntry, m);
                log("    unique method %s", method);
                fieldListBuilder.addField(method);
            });
        }
        /* Build fieldlist record from manifested fields. */
        CVTypeRecord.CVFieldListRecord fieldListRecord = fieldListBuilder.buildFieldListRecords(this);
        int fieldListIdx = fieldListRecord.getSequenceNumber();
        int fieldCount = fieldListBuilder.getFieldCount();
        log("finished building fieldlist %s", fieldListRecord);

        /* Build final class record. */
        short attrs = 0; /* property attribute field (prop_t) */
        CVTypeRecord typeRecord = new CVTypeRecord.CVClassRecord(LF_CLASS, (short) fieldCount, attrs, fieldListIdx, 0, 0, typeEntry.getSize(), typeEntry.getTypeName(), null);
        typeRecord = addTypeRecord(typeRecord);

        if (typeEntry instanceof ClassEntry classEntry) {
            /* Add a UDT record (if we have the information) */
            /*
             * Try to find a line number for the first function - if none, don't bother to create
             * the record.
             */
            int line = methods.stream().mapToInt(MethodEntry::getLine).min().orElse(0);
            if (line > 0) {
                int idIdx = typeSection.getStringId(classEntry.getFullFileName()).getSequenceNumber();
                CVTypeRecord.CVUdtTypeLineRecord udt = new CVTypeRecord.CVUdtTypeLineRecord(typeRecord.getSequenceNumber(), idIdx, line);
                addTypeRecord(udt);
            }
        }

        types.typeNameMap.put(typeEntry.getTypeName(), typeRecord);

        /* CVSymbolSubsectionBuilder will add associated S_UDT record to symbol table. */
        log("  finished class %s", typeRecord);

        return typeRecord;
    }

    private static boolean isManifestedField(FieldEntry fieldEntry) {
        return fieldEntry.getOffset() >= 0;
    }

    private CVTypeRecord.FieldRecord buildField(FieldEntry fieldEntry) {
        TypeEntry valueType = fieldEntry.getValueType();
        int valueTypeIndex = types.getIndexForPointerOrPrimitive(valueType);
        short attr = modifiersToAttr(fieldEntry);
        if (Modifier.isStatic(fieldEntry.getModifiers())) {
            return new CVTypeRecord.CVStaticMemberRecord(attr, valueTypeIndex, fieldEntry.fieldName());
        } else {
            return new CVTypeRecord.CVMemberRecord(attr, valueTypeIndex, fieldEntry.getOffset(), fieldEntry.fieldName());
        }
    }

    private static short modifiersToAttr(MethodEntry member) {

        short attr = accessToAttr(member);
        boolean isStatic = Modifier.isStatic(member.getModifiers());
        if (isStatic) {
            attr += MPROP_STATIC;
        } else if (!member.isVirtual()) {
            // noinspection ConstantConditions
            attr += MPROP_VANILLA;
        } else if (Modifier.isAbstract(member.getModifiers())) {
            attr += member.isOverride() ? MPROP_PURE_VIRTUAL : MPROP_PURE_IVIRTUAL;
        } else {
            attr += member.isOverride() ? MPROP_VIRTUAL : MPROP_IVIRTUAL;
        }
        return attr;
    }

    private static short modifiersToAttr(FieldEntry member) {
        return accessToAttr(member);
    }

    private CVTypeRecord.CVOneMethodRecord buildMethod(ClassEntry classEntry, MethodEntry methodEntry) {
        CVTypeRecord.CVTypeMFunctionRecord funcRecord = buildMemberFunction(classEntry, methodEntry);
        short attr = modifiersToAttr(methodEntry);
        return new CVTypeRecord.CVOneMethodRecord(attr, funcRecord.getSequenceNumber(), methodEntry.getVtableOffset(), methodEntry.getMethodName());
    }

    private static short accessToAttr(MemberEntry member) {
        int modifiers = member.getModifiers();
        final short attr;
        if (Modifier.isPublic(modifiers)) {
            attr = MPROP_PUBLIC;
        } else if (Modifier.isPrivate(modifiers)) {
            attr = MPROP_PRIVATE;
        } else if (Modifier.isProtected(modifiers)) {
            attr = MPROP_PROTECTED;
        } else {
            attr = MPROP_VANILLA;
        }
        return attr;
    }

    CVTypeRecord.CVTypeMFunctionRecord buildMemberFunction(ClassEntry classEntry, MethodEntry methodEntry) {
        CVTypeRecord.CVTypeMFunctionRecord mFunctionRecord = new CVTypeRecord.CVTypeMFunctionRecord();
        mFunctionRecord.setClassType(types.getIndexForForwardRef(classEntry));
        mFunctionRecord.setCallType((byte) (CV_CALL_NEAR_C));
        mFunctionRecord.setThisType(Modifier.isStatic(methodEntry.getModifiers()) ? T_NOTYPE : types.getIndexForPointerOrPrimitive(classEntry));
        /* 'attr' is CV_funcattr_t and if set to 2 indicates a constructor function. */
        byte attr = methodEntry.isConstructor() ? (byte) FUNC_IS_CONSTRUCTOR : 0;
        mFunctionRecord.setFuncAttr(attr);
        mFunctionRecord.setReturnType(types.getIndexForPointerOrPrimitive(methodEntry.getValueType()));
        CVTypeRecord.CVTypeArglistRecord argListType = new CVTypeRecord.CVTypeArglistRecord();
        for (TypeEntry paramType : methodEntry.getParamTypes()) {
            argListType.add(types.getIndexForPointerOrPrimitive(paramType));
        }
        argListType = addTypeRecord(argListType);
        mFunctionRecord.setArgList(argListType);
        return addTypeRecord(mFunctionRecord);
    }

    private <T extends CVTypeRecord> T addTypeRecord(T record) {
        return types.addTypeRecord(record);
    }

    int getIndexForPointerOrPrimitive(TypeEntry entry) {
        return types.getIndexForPointerOrPrimitive(entry);
    }

    private void log(String fmt, Object... args) {
        typeSection.log(fmt, args);
    }

    static class TypeTable {

        private static final int CV_TYPENAME_INITIAL_CAPACITY = 20000;

        /* A map of typename to type records, */
        private final Map<String, CVTypeRecord> typeNameMap = new HashMap<>(CV_TYPENAME_INITIAL_CAPACITY);

        /* For convenience, quick lookup of pointer type indices given class type index */
        /* Could have saved this in typeNameMap. */
        /* maps type index to pointer to forward ref record */
        private final Map<Integer, CVTypeRecord> typePointerMap = new HashMap<>(CV_TYPENAME_INITIAL_CAPACITY);

        /*
         * A map of type names to type records. Only forward references are stored here, and only
         * until they are defined.
         */
        private final Map<String, CVTypeRecord> forwardRefMap = new HashMap<>(CV_TYPENAME_INITIAL_CAPACITY);

        private final CVTypeSectionImpl typeSection;

        TypeTable(CVTypeSectionImpl typeSection) {
            this.typeSection = typeSection;
            addPrimitiveTypes();
        }

        void testForUndefinedClasses() {
            for (CVTypeRecord record : forwardRefMap.values()) {
                CVTypeRecord.CVClassRecord classRecord = (CVTypeRecord.CVClassRecord) record;
                if (!typeNameMap.containsKey(classRecord.getClassName())) {
                    GraalError.shouldNotReachHere("no typeentry for " + classRecord.getClassName() + "; type remains incomplete");
                }
            }
        }

        private <T extends CVTypeRecord> T addTypeRecord(T record) {
            return typeSection.addOrReference(record);
        }

        /**
         * Return a CV type index for a pointer to a java type, or the type itself if a primitive or
         * pointer type.
         *
         * @param entry The java type to return a typeindex for. If the type has not been seen, a
         *            forward reference is generated.
         * @return The index for the typeentry for a pointer to the type. If the type is a primitive
         *         type, the index returned is for the type, not a pointer to the type.
         */
        int getIndexForPointerOrPrimitive(TypeEntry entry) {
            if (entry instanceof PrimitiveTypeEntry || entry instanceof PointerToTypeEntry) {
                CVTypeRecord record = typeSection.addTypeRecords(entry);
                assert record != null;
                return record.getSequenceNumber();
            }
            CVTypeRecord forwardRefRecord = getExistingForwardReference(entry);
            if (forwardRefRecord == null) {
                forwardRefRecord = addTypeRecord(new CVTypeRecord.CVClassRecord((short) ATTR_FORWARD_REF, entry.getTypeName(), null));
                forwardRefMap.put(entry.getTypeName(), forwardRefRecord);
            }
            /* We now have a class record but must create a pointer record. */
            CVTypeRecord ptrRecord = typePointerMap.get(forwardRefRecord.getSequenceNumber());
            if (ptrRecord == null) {
                ptrRecord = addTypeRecord(new CVTypeRecord.CVTypePointerRecord(forwardRefRecord.getSequenceNumber(), CVTypeRecord.CVTypePointerRecord.NORMAL_64));
                typePointerMap.put(forwardRefRecord.getSequenceNumber(), ptrRecord);
            }
            return ptrRecord.getSequenceNumber();
        }

        private int getIndexForForwardRef(StructureTypeEntry entry) {
            return getIndexForForwardRef(entry.getTypeName());
        }

        int getIndexForForwardRef(String className) {
            CVTypeRecord clsRecord = forwardRefMap.get(className);
            if (clsRecord == null) {
                clsRecord = addTypeRecord(new CVTypeRecord.CVClassRecord((short) ATTR_FORWARD_REF, className, null));
                forwardRefMap.put(className, clsRecord);
            }
            return clsRecord.getSequenceNumber();
        }

        CVTypeRecord getExistingType(TypeEntry typeEntry) {
            return typeNameMap.get(typeEntry.getTypeName());
        }

        CVTypeRecord getExistingForwardReference(TypeEntry typeEntry) {
            return forwardRefMap.get(typeEntry.getTypeName());
        }

        void definePrimitiveType(String typename, short typeId, int length, short pointerTypeId) {
            CVTypeRecord record = new CVTypeRecord.CVTypePrimitive(typeId, length);
            typeNameMap.put(typename, record);
            if (pointerTypeId != 0) {
                CVTypeRecord pointerRecord = new CVTypeRecord.CVTypePrimitive(pointerTypeId, ADDRESS_BITS);
                typePointerMap.put((int) typeId, pointerRecord);
            }
        }

        private void addPrimitiveTypes() {
            /*
             * Primitive types are pre-defined and do not get written out to the typeInfo section.
             */
            definePrimitiveType("void", T_VOID, 0, T_64PVOID);
            definePrimitiveType("byte", T_INT1, Byte.BYTES, T_64PINT1);
            definePrimitiveType("boolean", T_BOOL08, 1, T_64PBOOL08);
            definePrimitiveType("char", T_WCHAR, Character.BYTES, T_64PWCHAR);
            definePrimitiveType("short", T_INT2, Short.BYTES, T_64PINT2);
            definePrimitiveType("int", T_INT4, Integer.BYTES, T_64PINT4);
            definePrimitiveType("long", T_INT8, Long.BYTES, T_64PINT8);
            definePrimitiveType("float", T_REAL32, Float.BYTES, T_64PREAL32);
            definePrimitiveType("double", T_REAL64, Double.BYTES, T_64PREAL64);
        }
    }
}
