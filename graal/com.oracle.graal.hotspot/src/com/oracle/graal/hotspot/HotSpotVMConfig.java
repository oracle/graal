/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspotvmconfig.*;

/**
 * Used to access native configuration details.
 *
 * All non-static, public fields in this class are so that they can be compiled as constants.
 */
public class HotSpotVMConfig extends CompilerObject {

    private static final long serialVersionUID = -4744897993263044184L;

    /**
     * Determines if the current architecture is included in a given architecture set specification.
     *
     * @param currentArch
     * @param archsSpecification specifies a set of architectures. A zero length value implies all
     *            architectures.
     */
    private static boolean isRequired(String currentArch, String[] archsSpecification) {
        if (archsSpecification.length == 0) {
            return true;
        }
        for (String arch : archsSpecification) {
            if (arch.equals(currentArch)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maximum allowed size of allocated area for a frame.
     */
    public final int maxFrameSize = 16 * 1024;

    HotSpotVMConfig(CompilerToVM compilerToVm) {
        compilerToVm.initializeConfiguration(this);
        assert verifyInitialization();

        oopEncoding = new CompressEncoding(narrowOopBase, narrowOopShift, logMinObjAlignment());
        klassEncoding = new CompressEncoding(narrowKlassBase, narrowKlassShift, logKlassAlignment);

        assert check();
    }

    /**
     * Check that the initialization produces the same result as the values captured through
     * vmStructs.
     */
    private boolean verifyInitialization() {
        /** These fields are set in {@link CompilerToVM#initializeConfiguration}. */
        assert gHotSpotVMStructs != 0;
        assert gHotSpotVMTypes != 0;
        assert gHotSpotVMIntConstants != 0;
        assert gHotSpotVMLongConstants != 0;

        // Fill the VM fields hash map.
        HashMap<String, VMFields.Field> vmFields = new HashMap<>();
        for (VMFields.Field e : new VMFields(gHotSpotVMStructs)) {
            vmFields.put(e.getName(), e);
        }

        // Fill the VM types hash map.
        HashMap<String, VMTypes.Type> vmTypes = new HashMap<>();
        for (VMTypes.Type e : new VMTypes(gHotSpotVMTypes)) {
            vmTypes.put(e.getTypeName(), e);
        }

        // Fill the VM constants hash map.
        HashMap<String, AbstractConstant> vmConstants = new HashMap<>();
        for (AbstractConstant e : new VMIntConstants(gHotSpotVMIntConstants)) {
            vmConstants.put(e.getName(), e);
        }
        for (AbstractConstant e : new VMLongConstants(gHotSpotVMLongConstants)) {
            vmConstants.put(e.getName(), e);
        }

        // Fill the flags hash map.
        HashMap<String, Flags.Flag> flags = new HashMap<>();
        for (Flags.Flag e : new Flags(vmFields, vmTypes)) {
            flags.put(e.getName(), e);
        }

        String currentArch = getHostArchitectureName();

        for (Field f : HotSpotVMConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(HotSpotVMField.class)) {
                HotSpotVMField annotation = f.getAnnotation(HotSpotVMField.class);
                String name = annotation.name();
                String type = annotation.type();
                VMFields.Field entry = vmFields.get(name);
                if (entry == null) {
                    if (!isRequired(currentArch, annotation.archs())) {
                        continue;
                    }
                    throw new IllegalArgumentException("field not found: " + name);
                }

                // Make sure the native type is still the type we expect.
                if (!type.equals("")) {
                    if (!type.equals(entry.getTypeString())) {
                        throw new IllegalArgumentException("compiler expects type " + type + " but field " + name + " is of type " + entry.getTypeString());
                    }
                }

                switch (annotation.get()) {
                    case OFFSET:
                        checkField(f, entry.getOffset());
                        break;
                    case ADDRESS:
                        checkField(f, entry.getAddress());
                        break;
                    case VALUE:
                        checkField(f, entry.getValue());
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere("unknown kind " + annotation.get());
                }
            } else if (f.isAnnotationPresent(HotSpotVMType.class)) {
                HotSpotVMType annotation = f.getAnnotation(HotSpotVMType.class);
                String name = annotation.name();
                VMTypes.Type entry = vmTypes.get(name);
                if (entry == null) {
                    throw new IllegalArgumentException("type not found: " + name);
                }
                switch (annotation.get()) {
                    case SIZE:
                        checkField(f, entry.getSize());
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere("unknown kind " + annotation.get());
                }
            } else if (f.isAnnotationPresent(HotSpotVMConstant.class)) {
                HotSpotVMConstant annotation = f.getAnnotation(HotSpotVMConstant.class);
                String name = annotation.name();
                AbstractConstant entry = vmConstants.get(name);
                if (entry == null) {
                    if (!isRequired(currentArch, annotation.archs())) {
                        continue;
                    }
                    throw new IllegalArgumentException("constant not found: " + name);
                }
                checkField(f, entry.getValue());
            } else if (f.isAnnotationPresent(HotSpotVMFlag.class)) {
                HotSpotVMFlag annotation = f.getAnnotation(HotSpotVMFlag.class);
                String name = annotation.name();
                Flags.Flag entry = flags.get(name);
                if (entry == null) {
                    if (annotation.optional() || !isRequired(currentArch, annotation.archs())) {
                        continue;
                    }
                    throw new IllegalArgumentException("flag not found: " + name);

                }
                checkField(f, entry.getValue());
            }
        }
        return true;
    }

    private final CompressEncoding oopEncoding;
    private final CompressEncoding klassEncoding;

    public CompressEncoding getOopEncoding() {
        return oopEncoding;
    }

    public CompressEncoding getKlassEncoding() {
        return klassEncoding;
    }

    private void checkField(Field field, Object value) {
        try {
            Class<?> fieldType = field.getType();
            if (fieldType == boolean.class) {
                if (value instanceof String) {
                    assert field.getBoolean(this) == Boolean.valueOf((String) value) : field + " " + value + " " + field.getBoolean(this);
                } else if (value instanceof Boolean) {
                    assert field.getBoolean(this) == (boolean) value : field + " " + value + " " + field.getBoolean(this);
                } else if (value instanceof Long) {
                    assert field.getBoolean(this) == (((long) value) != 0) : field + " " + value + " " + field.getBoolean(this);
                } else {
                    GraalInternalError.shouldNotReachHere(value.getClass().getSimpleName());
                }
            } else if (fieldType == int.class) {
                if (value instanceof Integer) {
                    assert field.getInt(this) == (int) value : field + " " + value + " " + field.getInt(this);
                } else if (value instanceof Long) {
                    assert field.getInt(this) == (int) (long) value : field + " " + value + " " + field.getInt(this);
                } else {
                    GraalInternalError.shouldNotReachHere(value.getClass().getSimpleName());
                }
            } else if (fieldType == long.class) {
                assert field.getLong(this) == (long) value : field + " " + value + " " + field.getLong(this);
            } else {
                GraalInternalError.shouldNotReachHere(field.toString());
            }
        } catch (IllegalAccessException e) {
            throw GraalInternalError.shouldNotReachHere(field.toString() + ": " + e);
        }
    }

    /**
     * Gets the host architecture name for the purpose of finding the corresponding
     * {@linkplain HotSpotBackendFactory backend}.
     */
    public String getHostArchitectureName() {
        String arch = System.getProperty("os.arch");
        switch (arch) {
            case "x86_64":
                arch = "amd64";
                break;
            case "sparcv9":
                arch = "sparc";
                break;
        }
        return arch;
    }

    /**
     * VMStructEntry (see vmStructs.hpp).
     */
    @HotSpotVMValue(expression = "gHotSpotVMStructs", get = HotSpotVMValue.Type.ADDRESS) @Stable private long gHotSpotVMStructs;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryTypeNameOffset") @Stable private long gHotSpotVMStructEntryTypeNameOffset;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryFieldNameOffset") @Stable private long gHotSpotVMStructEntryFieldNameOffset;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryTypeStringOffset") @Stable private long gHotSpotVMStructEntryTypeStringOffset;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryIsStaticOffset") @Stable private long gHotSpotVMStructEntryIsStaticOffset;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryOffsetOffset") @Stable private long gHotSpotVMStructEntryOffsetOffset;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryAddressOffset") @Stable private long gHotSpotVMStructEntryAddressOffset;
    @HotSpotVMValue(expression = "gHotSpotVMStructEntryArrayStride") @Stable private long gHotSpotVMStructEntryArrayStride;

    class VMFields implements Iterable<VMFields.Field> {

        private long address;

        public VMFields(long address) {
            this.address = address;
        }

        public Iterator<VMFields.Field> iterator() {
            return new Iterator<VMFields.Field>() {

                private int index = 0;

                private Field current() {
                    return new Field(address + gHotSpotVMStructEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL fieldName.
                 */
                public boolean hasNext() {
                    Field entry = current();
                    return entry.getFieldName() != null;
                }

                public Field next() {
                    Field entry = current();
                    index++;
                    return entry;
                }

                @Override
                public void remove() {
                    throw GraalInternalError.unimplemented();
                }
            };
        }

        class Field {

            private long entryAddress;

            Field(long address) {
                this.entryAddress = address;
            }

            public String getTypeName() {
                long typeNameAddress = unsafe.getAddress(entryAddress + gHotSpotVMStructEntryTypeNameOffset);
                return readCString(typeNameAddress);
            }

            public String getFieldName() {
                long fieldNameAddress = unsafe.getAddress(entryAddress + gHotSpotVMStructEntryFieldNameOffset);
                return readCString(fieldNameAddress);
            }

            public String getTypeString() {
                long typeStringAddress = unsafe.getAddress(entryAddress + gHotSpotVMStructEntryTypeStringOffset);
                return readCString(typeStringAddress);
            }

            public boolean isStatic() {
                return unsafe.getInt(entryAddress + gHotSpotVMStructEntryIsStaticOffset) != 0;
            }

            public long getOffset() {
                return unsafe.getLong(entryAddress + gHotSpotVMStructEntryOffsetOffset);
            }

            public long getAddress() {
                return unsafe.getAddress(entryAddress + gHotSpotVMStructEntryAddressOffset);
            }

            public String getName() {
                String typeName = getTypeName();
                String fieldName = getFieldName();
                return typeName + "::" + fieldName;
            }

            public long getValue() {
                String type = getTypeString();
                switch (type) {
                    case "int":
                        return unsafe.getInt(getAddress());
                    case "address":
                    case "intptr_t":
                        return unsafe.getAddress(getAddress());
                    default:
                        // All foo* types are addresses.
                        if (type.endsWith("*")) {
                            return unsafe.getAddress(getAddress());
                        }
                        throw GraalInternalError.shouldNotReachHere(type);
                }
            }

            @Override
            public String toString() {
                return String.format("Field[typeName=%s, fieldName=%s, typeString=%s, isStatic=%b, offset=%d, address=0x%x]", getTypeName(), getFieldName(), getTypeString(), isStatic(), getOffset(),
                                getAddress());
            }
        }
    }

    /**
     * VMTypeEntry (see vmStructs.hpp).
     */
    @HotSpotVMValue(expression = "gHotSpotVMTypes", get = HotSpotVMValue.Type.ADDRESS) @Stable private long gHotSpotVMTypes;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntryTypeNameOffset") @Stable private long gHotSpotVMTypeEntryTypeNameOffset;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntrySuperclassNameOffset") @Stable private long gHotSpotVMTypeEntrySuperclassNameOffset;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntryIsOopTypeOffset") @Stable private long gHotSpotVMTypeEntryIsOopTypeOffset;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntryIsIntegerTypeOffset") @Stable private long gHotSpotVMTypeEntryIsIntegerTypeOffset;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntryIsUnsignedOffset") @Stable private long gHotSpotVMTypeEntryIsUnsignedOffset;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntrySizeOffset") @Stable private long gHotSpotVMTypeEntrySizeOffset;
    @HotSpotVMValue(expression = "gHotSpotVMTypeEntryArrayStride") @Stable private long gHotSpotVMTypeEntryArrayStride;

    class VMTypes implements Iterable<VMTypes.Type> {

        private long address;

        public VMTypes(long address) {
            this.address = address;
        }

        public Iterator<VMTypes.Type> iterator() {
            return new Iterator<VMTypes.Type>() {

                private int index = 0;

                private Type current() {
                    return new Type(address + gHotSpotVMTypeEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL type name.
                 */
                public boolean hasNext() {
                    Type entry = current();
                    return entry.getTypeName() != null;
                }

                public Type next() {
                    Type entry = current();
                    index++;
                    return entry;
                }

                @Override
                public void remove() {
                    throw GraalInternalError.unimplemented();
                }
            };
        }

        class Type {

            private long entryAddress;

            Type(long address) {
                this.entryAddress = address;
            }

            public String getTypeName() {
                long typeNameAddress = unsafe.getAddress(entryAddress + gHotSpotVMTypeEntryTypeNameOffset);
                return readCString(typeNameAddress);
            }

            public String getSuperclassName() {
                long superclassNameAddress = unsafe.getAddress(entryAddress + gHotSpotVMTypeEntrySuperclassNameOffset);
                return readCString(superclassNameAddress);
            }

            public boolean isOopType() {
                return unsafe.getInt(entryAddress + gHotSpotVMTypeEntryIsOopTypeOffset) != 0;
            }

            public boolean isIntegerType() {
                return unsafe.getInt(entryAddress + gHotSpotVMTypeEntryIsIntegerTypeOffset) != 0;
            }

            public boolean isUnsigned() {
                return unsafe.getInt(entryAddress + gHotSpotVMTypeEntryIsUnsignedOffset) != 0;
            }

            public long getSize() {
                return unsafe.getLong(entryAddress + gHotSpotVMTypeEntrySizeOffset);
            }

            @Override
            public String toString() {
                return String.format("Type[typeName=%s, superclassName=%s, isOopType=%b, isIntegerType=%b, isUnsigned=%b, size=%d]", getTypeName(), getSuperclassName(), isOopType(), isIntegerType(),
                                isUnsigned(), getSize());
            }
        }
    }

    public abstract class AbstractConstant {

        protected long address;
        protected long nameOffset;
        protected long valueOffset;

        AbstractConstant(long address, long nameOffset, long valueOffset) {
            this.address = address;
            this.nameOffset = nameOffset;
            this.valueOffset = valueOffset;
        }

        public String getName() {
            long nameAddress = unsafe.getAddress(address + nameOffset);
            return readCString(nameAddress);
        }

        public abstract long getValue();
    }

    /**
     * VMIntConstantEntry (see vmStructs.hpp).
     */
    @HotSpotVMValue(expression = "gHotSpotVMIntConstants", get = HotSpotVMValue.Type.ADDRESS) @Stable private long gHotSpotVMIntConstants;
    @HotSpotVMValue(expression = "gHotSpotVMIntConstantEntryNameOffset") @Stable private long gHotSpotVMIntConstantEntryNameOffset;
    @HotSpotVMValue(expression = "gHotSpotVMIntConstantEntryValueOffset") @Stable private long gHotSpotVMIntConstantEntryValueOffset;
    @HotSpotVMValue(expression = "gHotSpotVMIntConstantEntryArrayStride") @Stable private long gHotSpotVMIntConstantEntryArrayStride;

    class VMIntConstants implements Iterable<VMIntConstants.Constant> {

        private long address;

        public VMIntConstants(long address) {
            this.address = address;
        }

        public Iterator<VMIntConstants.Constant> iterator() {
            return new Iterator<VMIntConstants.Constant>() {

                private int index = 0;

                private Constant current() {
                    return new Constant(address + gHotSpotVMIntConstantEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Constant entry = current();
                    return entry.getName() != null;
                }

                public Constant next() {
                    Constant entry = current();
                    index++;
                    return entry;
                }

                @Override
                public void remove() {
                    throw GraalInternalError.unimplemented();
                }
            };
        }

        class Constant extends AbstractConstant {

            Constant(long address) {
                super(address, gHotSpotVMIntConstantEntryNameOffset, gHotSpotVMIntConstantEntryValueOffset);
            }

            @Override
            public long getValue() {
                return unsafe.getInt(address + valueOffset);
            }

            @Override
            public String toString() {
                return String.format("IntConstant[name=%s, value=%d (0x%x)]", getName(), getValue(), getValue());
            }
        }
    }

    /**
     * VMLongConstantEntry (see vmStructs.hpp).
     */
    @HotSpotVMValue(expression = "gHotSpotVMLongConstants", get = HotSpotVMValue.Type.ADDRESS) @Stable private long gHotSpotVMLongConstants;
    @HotSpotVMValue(expression = "gHotSpotVMLongConstantEntryNameOffset") @Stable private long gHotSpotVMLongConstantEntryNameOffset;
    @HotSpotVMValue(expression = "gHotSpotVMLongConstantEntryValueOffset") @Stable private long gHotSpotVMLongConstantEntryValueOffset;
    @HotSpotVMValue(expression = "gHotSpotVMLongConstantEntryArrayStride") @Stable private long gHotSpotVMLongConstantEntryArrayStride;

    class VMLongConstants implements Iterable<VMLongConstants.Constant> {

        private long address;

        public VMLongConstants(long address) {
            this.address = address;
        }

        public Iterator<VMLongConstants.Constant> iterator() {
            return new Iterator<VMLongConstants.Constant>() {

                private int index = 0;

                private Constant currentEntry() {
                    return new Constant(address + gHotSpotVMLongConstantEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Constant entry = currentEntry();
                    return entry.getName() != null;
                }

                public Constant next() {
                    Constant entry = currentEntry();
                    index++;
                    return entry;
                }

                @Override
                public void remove() {
                    throw GraalInternalError.unimplemented();
                }
            };
        }

        class Constant extends AbstractConstant {

            Constant(long address) {
                super(address, gHotSpotVMLongConstantEntryNameOffset, gHotSpotVMLongConstantEntryValueOffset);
            }

            @Override
            public long getValue() {
                return unsafe.getLong(address + valueOffset);
            }

            @Override
            public String toString() {
                return String.format("LongConstant[name=%s, value=%d (0x%x)]", getName(), getValue(), getValue());
            }
        }
    }

    class Flags implements Iterable<Flags.Flag> {

        private long address;
        private long entrySize;
        private long typeOffset;
        private long nameOffset;
        private long addrOffset;

        public Flags(HashMap<String, VMFields.Field> vmStructs, HashMap<String, VMTypes.Type> vmTypes) {
            address = vmStructs.get("Flag::flags").getValue();
            entrySize = vmTypes.get("Flag").getSize();
            typeOffset = vmStructs.get("Flag::_type").getOffset();
            nameOffset = vmStructs.get("Flag::_name").getOffset();
            addrOffset = vmStructs.get("Flag::_addr").getOffset();

            assert vmTypes.get("bool").getSize() == Byte.BYTES;
            assert vmTypes.get("intx").getSize() == Long.BYTES;
            assert vmTypes.get("uintx").getSize() == Long.BYTES;
        }

        public Iterator<Flags.Flag> iterator() {
            return new Iterator<Flags.Flag>() {

                private int index = 0;

                private Flag current() {
                    return new Flag(address + entrySize * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Flag entry = current();
                    return entry.getName() != null;
                }

                public Flag next() {
                    Flag entry = current();
                    index++;
                    return entry;
                }

                @Override
                public void remove() {
                    throw GraalInternalError.unimplemented();
                }
            };
        }

        class Flag {

            private long entryAddress;

            Flag(long address) {
                this.entryAddress = address;
            }

            public String getType() {
                long typeAddress = unsafe.getAddress(entryAddress + typeOffset);
                return readCString(typeAddress);
            }

            public String getName() {
                long nameAddress = unsafe.getAddress(entryAddress + nameOffset);
                return readCString(nameAddress);
            }

            public long getAddr() {
                return unsafe.getAddress(entryAddress + addrOffset);
            }

            public Object getValue() {
                switch (getType()) {
                    case "bool":
                        return Boolean.valueOf(unsafe.getByte(getAddr()) != 0);
                    case "intx":
                    case "uintx":
                    case "uint64_t":
                        return Long.valueOf(unsafe.getLong(getAddr()));
                    case "double":
                        return Double.valueOf(unsafe.getDouble(getAddr()));
                    case "ccstr":
                    case "ccstrlist":
                        return readCString(getAddr());
                    default:
                        throw GraalInternalError.shouldNotReachHere(getType());
                }
            }

            @Override
            public String toString() {
                return String.format("Flag[type=%s, name=%s, value=%s]", getType(), getName(), getValue());
            }
        }
    }

    // os information, register layout, code generation, ...
    @HotSpotVMValue(expression = "DEBUG_ONLY(1) NOT_DEBUG(0)") @Stable public boolean cAssertions;
    public final boolean windowsOs = System.getProperty("os.name", "").startsWith("Windows");

    @HotSpotVMFlag(name = "CodeEntryAlignment") @Stable public int codeEntryAlignment;
    @HotSpotVMFlag(name = "VerifyOops") @Stable public boolean verifyOops;
    @HotSpotVMFlag(name = "CITime") @Stable public boolean ciTime;
    @HotSpotVMFlag(name = "CITimeEach") @Stable public boolean ciTimeEach;
    @HotSpotVMFlag(name = "CompileThreshold") @Stable public long compileThreshold;
    @HotSpotVMFlag(name = "CompileTheWorldStartAt", optional = true) @Stable public int compileTheWorldStartAt;
    @HotSpotVMFlag(name = "CompileTheWorldStopAt", optional = true) @Stable public int compileTheWorldStopAt;
    @HotSpotVMFlag(name = "DontCompileHugeMethods") @Stable public boolean dontCompileHugeMethods;
    @HotSpotVMFlag(name = "HugeMethodLimit") @Stable public int hugeMethodLimit;
    @HotSpotVMFlag(name = "PrintInlining") @Stable public boolean printInlining;
    @HotSpotVMFlag(name = "GraalUseFastLocking") @Stable public boolean useFastLocking;
    @HotSpotVMFlag(name = "ForceUnreachable") @Stable public boolean forceUnreachable;
    @HotSpotVMFlag(name = "GPUOffload") @Stable public boolean gpuOffload;

    @HotSpotVMFlag(name = "UseTLAB") @Stable public boolean useTLAB;
    @HotSpotVMFlag(name = "UseBiasedLocking") @Stable public boolean useBiasedLocking;
    @HotSpotVMFlag(name = "UsePopCountInstruction") @Stable public boolean usePopCountInstruction;
    @HotSpotVMFlag(name = "UseCountLeadingZerosInstruction", archs = {"amd64"}) @Stable public boolean useCountLeadingZerosInstruction;
    @HotSpotVMFlag(name = "UseAESIntrinsics") @Stable public boolean useAESIntrinsics;
    @HotSpotVMFlag(name = "UseCRC32Intrinsics") @Stable public boolean useCRC32Intrinsics;
    @HotSpotVMFlag(name = "UseG1GC") @Stable public boolean useG1GC;
    @HotSpotVMFlag(name = "UseConcMarkSweepGC") @Stable public boolean useCMSGC;

    @HotSpotVMFlag(name = "AllocatePrefetchStyle") @Stable public int allocatePrefetchStyle;
    @HotSpotVMFlag(name = "AllocatePrefetchInstr") @Stable public int allocatePrefetchInstr;
    @HotSpotVMFlag(name = "AllocatePrefetchLines") @Stable public int allocatePrefetchLines;
    @HotSpotVMFlag(name = "AllocateInstancePrefetchLines") @Stable public int allocateInstancePrefetchLines;
    @HotSpotVMFlag(name = "AllocatePrefetchStepSize") @Stable public int allocatePrefetchStepSize;
    @HotSpotVMFlag(name = "AllocatePrefetchDistance") @Stable public int allocatePrefetchDistance;

    @HotSpotVMFlag(name = "FlightRecorder", optional = true) @Stable public boolean flightRecorder;

    @HotSpotVMField(name = "Universe::_collectedHeap", type = "CollectedHeap*", get = HotSpotVMField.Type.VALUE) @Stable private long universeCollectedHeap;
    @HotSpotVMField(name = "CollectedHeap::_total_collections", type = "unsigned int", get = HotSpotVMField.Type.OFFSET) @Stable private int collectedHeapTotalCollectionsOffset;

    public long gcTotalCollectionsAddress() {
        return universeCollectedHeap + collectedHeapTotalCollectionsOffset;
    }

    @HotSpotVMFlag(name = "GraalDeferredInitBarriers") @Stable public boolean useDeferredInitBarriers;
    @HotSpotVMFlag(name = "GraalHProfEnabled") @Stable public boolean useHeapProfiler;

    // Compressed Oops related values.
    @HotSpotVMFlag(name = "UseCompressedOops") @Stable public boolean useCompressedOops;
    @HotSpotVMFlag(name = "UseCompressedClassPointers") @Stable public boolean useCompressedClassPointers;

    @HotSpotVMField(name = "Universe::_narrow_oop._base", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long narrowOopBase;
    @HotSpotVMField(name = "Universe::_narrow_oop._shift", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int narrowOopShift;
    @HotSpotVMFlag(name = "ObjectAlignmentInBytes") @Stable public int objectAlignment;

    public int logMinObjAlignment() {
        return (int) (Math.log(objectAlignment) / Math.log(2));
    }

    @HotSpotVMField(name = "Universe::_narrow_klass._base", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long narrowKlassBase;
    @HotSpotVMField(name = "Universe::_narrow_klass._shift", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int narrowKlassShift;
    @HotSpotVMConstant(name = "LogKlassAlignmentInBytes") @Stable public int logKlassAlignment;

    // CPU capabilities
    @HotSpotVMFlag(name = "UseSSE") @Stable public int useSSE;
    @HotSpotVMFlag(name = "UseAVX", archs = {"amd64"}) @Stable public int useAVX;

    // X86 specific values
    @HotSpotVMField(name = "VM_Version::_cpuFeatures", type = "int", get = HotSpotVMField.Type.VALUE, archs = {"amd64"}) @Stable public int x86CPUFeatures;
    @HotSpotVMConstant(name = "VM_Version::CPU_CX8", archs = {"amd64"}) @Stable public int cpuCX8;
    @HotSpotVMConstant(name = "VM_Version::CPU_CMOV", archs = {"amd64"}) @Stable public int cpuCMOV;
    @HotSpotVMConstant(name = "VM_Version::CPU_FXSR", archs = {"amd64"}) @Stable public int cpuFXSR;
    @HotSpotVMConstant(name = "VM_Version::CPU_HT", archs = {"amd64"}) @Stable public int cpuHT;
    @HotSpotVMConstant(name = "VM_Version::CPU_MMX", archs = {"amd64"}) @Stable public int cpuMMX;
    @HotSpotVMConstant(name = "VM_Version::CPU_3DNOW_PREFETCH", archs = {"amd64"}) @Stable public int cpu3DNOWPREFETCH;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE", archs = {"amd64"}) @Stable public int cpuSSE;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE2", archs = {"amd64"}) @Stable public int cpuSSE2;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE3", archs = {"amd64"}) @Stable public int cpuSSE3;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSSE3", archs = {"amd64"}) @Stable public int cpuSSSE3;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE4A", archs = {"amd64"}) @Stable public int cpuSSE4A;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE4_1", archs = {"amd64"}) @Stable public int cpuSSE41;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE4_2", archs = {"amd64"}) @Stable public int cpuSSE42;
    @HotSpotVMConstant(name = "VM_Version::CPU_POPCNT", archs = {"amd64"}) @Stable public int cpuPOPCNT;
    @HotSpotVMConstant(name = "VM_Version::CPU_LZCNT", archs = {"amd64"}) @Stable public int cpuLZCNT;
    @HotSpotVMConstant(name = "VM_Version::CPU_TSC", archs = {"amd64"}) @Stable public int cpuTSC;
    @HotSpotVMConstant(name = "VM_Version::CPU_TSCINV", archs = {"amd64"}) @Stable public int cpuTSCINV;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX", archs = {"amd64"}) @Stable public int cpuAVX;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX2", archs = {"amd64"}) @Stable public int cpuAVX2;
    @HotSpotVMConstant(name = "VM_Version::CPU_AES", archs = {"amd64"}) @Stable public int cpuAES;
    @HotSpotVMConstant(name = "VM_Version::CPU_ERMS", archs = {"amd64"}) @Stable public int cpuERMS;
    @HotSpotVMConstant(name = "VM_Version::CPU_CLMUL", archs = {"amd64"}) @Stable public int cpuCLMUL;

    // offsets, ...
    @HotSpotVMFlag(name = "StackShadowPages") @Stable public int stackShadowPages;
    @HotSpotVMFlag(name = "UseStackBanging") @Stable public boolean useStackBanging;
    @HotSpotVMConstant(name = "STACK_BIAS") @Stable public int stackBias;

    @HotSpotVMField(name = "oopDesc::_mark", type = "markOop", get = HotSpotVMField.Type.OFFSET) @Stable public int markOffset;
    @HotSpotVMField(name = "oopDesc::_metadata._klass", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int hubOffset;

    @HotSpotVMField(name = "Klass::_prototype_header", type = "markOop", get = HotSpotVMField.Type.OFFSET) @Stable public int prototypeMarkWordOffset;
    @HotSpotVMField(name = "Klass::_subklass", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int subklassOffset;
    @HotSpotVMField(name = "Klass::_next_sibling", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int nextSiblingOffset;
    @HotSpotVMField(name = "Klass::_super_check_offset", type = "juint", get = HotSpotVMField.Type.OFFSET) @Stable public int superCheckOffsetOffset;
    @HotSpotVMField(name = "Klass::_secondary_super_cache", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int secondarySuperCacheOffset;
    @HotSpotVMField(name = "Klass::_secondary_supers", type = "Array<Klass*>*", get = HotSpotVMField.Type.OFFSET) @Stable public int secondarySupersOffset;

    /**
     * The offset of the _java_mirror field (of type {@link Class}) in a Klass.
     */
    @HotSpotVMField(name = "Klass::_java_mirror", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int classMirrorOffset;

    @HotSpotVMField(name = "Klass::_super", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int klassSuperKlassOffset;
    @HotSpotVMField(name = "Klass::_modifier_flags", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int klassModifierFlagsOffset;
    @HotSpotVMField(name = "Klass::_access_flags", type = "AccessFlags", get = HotSpotVMField.Type.OFFSET) @Stable public int klassAccessFlagsOffset;
    @HotSpotVMField(name = "Klass::_layout_helper", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int klassLayoutHelperOffset;
    @HotSpotVMField(name = "Klass::_layout_helper", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int klassInstanceSizeOffset;

    @HotSpotVMConstant(name = "Klass::_lh_neutral_value") @Stable public int klassLayoutHelperNeutralValue;
    @HotSpotVMConstant(name = "Klass::_lh_instance_slow_path_bit") @Stable public int klassLayoutHelperInstanceSlowPathBit;
    @HotSpotVMConstant(name = "Klass::_lh_log2_element_size_shift") @Stable public int layoutHelperLog2ElementSizeShift;
    @HotSpotVMConstant(name = "Klass::_lh_log2_element_size_mask") @Stable public int layoutHelperLog2ElementSizeMask;
    @HotSpotVMConstant(name = "Klass::_lh_element_type_shift") @Stable public int layoutHelperElementTypeShift;
    @HotSpotVMConstant(name = "Klass::_lh_element_type_mask") @Stable public int layoutHelperElementTypeMask;
    @HotSpotVMConstant(name = "Klass::_lh_header_size_shift") @Stable public int layoutHelperHeaderSizeShift;
    @HotSpotVMConstant(name = "Klass::_lh_header_size_mask") @Stable public int layoutHelperHeaderSizeMask;
    @HotSpotVMConstant(name = "Klass::_lh_array_tag_shift") @Stable public int layoutHelperArrayTagShift;
    @HotSpotVMConstant(name = "Klass::_lh_array_tag_type_value") @Stable public int layoutHelperArrayTagTypeValue;
    @HotSpotVMConstant(name = "Klass::_lh_array_tag_obj_value") @Stable public int layoutHelperArrayTagObjectValue;

    /**
     * This filters out the bit that differentiates a type array from an object array.
     */
    public int layoutHelperElementTypePrimitiveInPlace() {
        return (layoutHelperArrayTagTypeValue & ~layoutHelperArrayTagObjectValue) << layoutHelperArrayTagShift;
    }

    /**
     * Bit pattern in the klass layout helper that can be used to identify arrays.
     */
    public final int arrayKlassLayoutHelperIdentifier = 0x80000000;

    @HotSpotVMField(name = "ArrayKlass::_component_mirror", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayKlassComponentMirrorOffset;

    @HotSpotVMType(name = "vtableEntry", get = HotSpotVMType.Type.SIZE) @Stable public int vtableEntrySize;
    @HotSpotVMField(name = "vtableEntry::_method", type = "Method*", get = HotSpotVMField.Type.OFFSET) @Stable public int vtableEntryMethodOffset;
    @HotSpotVMValue(expression = "InstanceKlass::vtable_start_offset() * HeapWordSize") @Stable public int instanceKlassVtableStartOffset;

    /**
     * The offset of the array length word in an array object's header.
     */
    @HotSpotVMValue(expression = "arrayOopDesc::length_offset_in_bytes()") @Stable public int arrayLengthOffset;

    @HotSpotVMField(name = "Array<int>::_length", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayU1LengthOffset;
    @HotSpotVMField(name = "Array<u1>::_data", type = "", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayU1DataOffset;
    @HotSpotVMField(name = "Array<u2>::_data", type = "", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayU2DataOffset;
    @HotSpotVMField(name = "Array<Klass*>::_length", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int metaspaceArrayLengthOffset;
    @HotSpotVMField(name = "Array<Klass*>::_data[0]", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int metaspaceArrayBaseOffset;

    @HotSpotVMField(name = "InstanceKlass::_graal_node_class", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassNodeClassOffset;
    @HotSpotVMField(name = "InstanceKlass::_source_file_name_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassSourceFileNameIndexOffset;
    @HotSpotVMField(name = "InstanceKlass::_init_state", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassInitStateOffset;
    @HotSpotVMField(name = "InstanceKlass::_constants", type = "ConstantPool*", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassConstantsOffset;
    @HotSpotVMField(name = "InstanceKlass::_fields", type = "Array<u2>*", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassFieldsOffset;

    @HotSpotVMConstant(name = "InstanceKlass::linked") @Stable public int instanceKlassStateLinked;
    @HotSpotVMConstant(name = "InstanceKlass::fully_initialized") @Stable public int instanceKlassStateFullyInitialized;

    @HotSpotVMField(name = "ObjArrayKlass::_element_klass", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayClassElementOffset;

    @HotSpotVMConstant(name = "FieldInfo::access_flags_offset") @Stable public int fieldInfoAccessFlagsOffset;
    @HotSpotVMConstant(name = "FieldInfo::name_index_offset") @Stable public int fieldInfoNameIndexOffset;
    @HotSpotVMConstant(name = "FieldInfo::signature_index_offset") @Stable public int fieldInfoSignatureIndexOffset;
    @HotSpotVMConstant(name = "FieldInfo::initval_index_offset") @Stable public int fieldInfoInitvalIndexOffset;
    @HotSpotVMConstant(name = "FieldInfo::low_packed_offset") @Stable public int fieldInfoLowPackedOffset;
    @HotSpotVMConstant(name = "FieldInfo::high_packed_offset") @Stable public int fieldInfoHighPackedOffset;
    @HotSpotVMConstant(name = "FieldInfo::field_slots") @Stable public int fieldInfoFieldSlots;

    @HotSpotVMConstant(name = "FIELDINFO_TAG_SIZE") @Stable public int fieldInfoTagSize;

    @HotSpotVMConstant(name = "JVM_ACC_FIELD_INTERNAL") @Stable public int jvmAccFieldInternal;
    @HotSpotVMConstant(name = "JVM_ACC_FIELD_STABLE") @Stable public int jvmAccFieldStable;
    @HotSpotVMConstant(name = "JVM_ACC_FIELD_HAS_GENERIC_SIGNATURE") @Stable public int jvmAccFieldHasGenericSignature;

    @HotSpotVMField(name = "Thread::_tlab", type = "ThreadLocalAllocBuffer", get = HotSpotVMField.Type.OFFSET) @Stable public int threadTlabOffset;

    @HotSpotVMField(name = "JavaThread::_anchor", type = "JavaFrameAnchor", get = HotSpotVMField.Type.OFFSET) @Stable public int javaThreadAnchorOffset;
    @HotSpotVMField(name = "JavaThread::_threadObj", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int threadObjectOffset;
    @HotSpotVMField(name = "JavaThread::_osthread", type = "OSThread*", get = HotSpotVMField.Type.OFFSET) @Stable public int osThreadOffset;
    @HotSpotVMField(name = "JavaThread::_dirty_card_queue", type = "DirtyCardQueue", get = HotSpotVMField.Type.OFFSET) @Stable public int javaThreadDirtyCardQueueOffset;
    @HotSpotVMField(name = "JavaThread::_is_method_handle_return", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int threadIsMethodHandleReturnOffset;
    @HotSpotVMField(name = "JavaThread::_satb_mark_queue", type = "ObjPtrQueue", get = HotSpotVMField.Type.OFFSET) @Stable public int javaThreadSatbMarkQueueOffset;
    @HotSpotVMField(name = "JavaThread::_vm_result", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int threadObjectResultOffset;
    @HotSpotVMValue(expression = "in_bytes(JavaThread::graal_counters_offset())") @Stable public int graalCountersThreadOffset;

    /**
     * An invalid value for {@link #rtldDefault}.
     */
    public static final long INVALID_RTLD_DEFAULT_HANDLE = 0xDEADFACE;

    /**
     * Address of the library lookup routine. The C signature of this routine is:
     *
     * <pre>
     *     void* (const char *filename, char *ebuf, int ebuflen)
     * </pre>
     */
    @HotSpotVMValue(expression = "os::dll_load", get = HotSpotVMValue.Type.ADDRESS) @Stable public long dllLoad;

    /**
     * Address of the library lookup routine. The C signature of this routine is:
     *
     * <pre>
     *     void* (void* handle, const char* name)
     * </pre>
     */
    @HotSpotVMValue(expression = "os::dll_lookup", get = HotSpotVMValue.Type.ADDRESS) @Stable public long dllLookup;

    /**
     * A pseudo-handle which when used as the first argument to {@link #dllLookup} means lookup will
     * return the first occurrence of the desired symbol using the default library search order. If
     * this field is {@value #INVALID_RTLD_DEFAULT_HANDLE}, then this capability is not supported on
     * the current platform.
     */
    @HotSpotVMValue(expression = "RTLD_DEFAULT", defines = {"TARGET_OS_FAMILY_bsd", "TARGET_OS_FAMILY_linux"}, get = HotSpotVMValue.Type.ADDRESS) @Stable public long rtldDefault = INVALID_RTLD_DEFAULT_HANDLE;

    /**
     * This field is used to pass exception objects into and out of the runtime system during
     * exception handling for compiled code.
     * <p>
     * <b>NOTE: This is not the same as {@link #pendingExceptionOffset}.</b>
     */
    @HotSpotVMField(name = "JavaThread::_exception_oop", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int threadExceptionOopOffset;
    @HotSpotVMField(name = "JavaThread::_exception_pc", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable public int threadExceptionPcOffset;

    @HotSpotVMField(name = "JavaFrameAnchor::_last_Java_sp", type = "intptr_t*", get = HotSpotVMField.Type.OFFSET) @Stable private int javaFrameAnchorLastJavaSpOffset;
    @HotSpotVMField(name = "JavaFrameAnchor::_last_Java_pc", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable private int javaFrameAnchorLastJavaPcOffset;
    @HotSpotVMField(name = "JavaFrameAnchor::_last_Java_fp", type = "intptr_t*", get = HotSpotVMField.Type.OFFSET, archs = {"amd64"}) @Stable private int javaFrameAnchorLastJavaFpOffset;
    @HotSpotVMField(name = "JavaFrameAnchor::_flags", type = "int", get = HotSpotVMField.Type.OFFSET, archs = {"sparc"}) @Stable private int javaFrameAnchorFlagsOffset;

    public int threadLastJavaSpOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaSpOffset;
    }

    public int threadLastJavaPcOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaPcOffset;
    }

    /**
     * This value is only valid on AMD64.
     */
    public int threadLastJavaFpOffset() {
        // TODO add an assert for AMD64
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaFpOffset;
    }

    /**
     * This value is only valid on SPARC.
     */
    public int threadJavaFrameAnchorFlagsOffset() {
        // TODO add an assert for SPARC
        return javaThreadAnchorOffset + javaFrameAnchorFlagsOffset;
    }

    // These are only valid on AMD64.
    @HotSpotVMConstant(name = "frame::arg_reg_save_area_bytes", archs = {"amd64"}) @Stable public int runtimeCallStackSize;
    @HotSpotVMConstant(name = "frame::interpreter_frame_sender_sp_offset", archs = {"amd64"}) @Stable public int frameInterpreterFrameSenderSpOffset;
    @HotSpotVMConstant(name = "frame::interpreter_frame_last_sp_offset", archs = {"amd64"}) @Stable public int frameInterpreterFrameLastSpOffset;

    @HotSpotVMField(name = "PtrQueue::_active", type = "bool", get = HotSpotVMField.Type.OFFSET) @Stable public int ptrQueueActiveOffset;
    @HotSpotVMField(name = "PtrQueue::_buf", type = "void**", get = HotSpotVMField.Type.OFFSET) @Stable public int ptrQueueBufferOffset;
    @HotSpotVMField(name = "PtrQueue::_index", type = "size_t", get = HotSpotVMField.Type.OFFSET) @Stable public int ptrQueueIndexOffset;

    @HotSpotVMField(name = "OSThread::_interrupted", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int osThreadInterruptedOffset;

    @HotSpotVMConstant(name = "markOopDesc::unlocked_value") @Stable public int unlockedMask;
    @HotSpotVMConstant(name = "markOopDesc::biased_lock_mask_in_place") @Stable public int biasedLockMaskInPlace;
    @HotSpotVMConstant(name = "markOopDesc::age_mask_in_place") @Stable public int ageMaskInPlace;
    @HotSpotVMConstant(name = "markOopDesc::epoch_mask_in_place") @Stable public int epochMaskInPlace;

    @HotSpotVMConstant(name = "markOopDesc::hash_shift") @Stable public long markOopDescHashShift;
    @HotSpotVMConstant(name = "markOopDesc::hash_mask") @Stable public long markOopDescHashMask;
    @HotSpotVMConstant(name = "markOopDesc::hash_mask_in_place") @Stable public long markOopDescHashMaskInPlace;

    @HotSpotVMConstant(name = "markOopDesc::biased_lock_pattern") @Stable public int biasedLockPattern;
    @HotSpotVMConstant(name = "markOopDesc::no_hash_in_place") @Stable public int markWordNoHashInPlace;
    @HotSpotVMConstant(name = "markOopDesc::no_lock_in_place") @Stable public int markWordNoLockInPlace;

    /**
     * See markOopDesc::prototype().
     */
    public long arrayPrototypeMarkWord() {
        return markWordNoHashInPlace | markWordNoLockInPlace;
    }

    /**
     * See markOopDesc::copy_set_hash().
     */
    public long tlabIntArrayMarkWord() {
        long tmp = arrayPrototypeMarkWord() & (~markOopDescHashMaskInPlace);
        tmp |= ((0x2 & markOopDescHashMask) << markOopDescHashShift);
        return tmp;
    }

    /**
     * Offset of the _pending_exception field in ThreadShadow (defined in exceptions.hpp). This
     * field is used to propagate exceptions through C/C++ calls.
     * <p>
     * <b>NOTE: This is not the same as {@link #threadExceptionOopOffset}.</b>
     */
    @HotSpotVMField(name = "ThreadShadow::_pending_exception", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingExceptionOffset;
    @HotSpotVMField(name = "ThreadShadow::_pending_deoptimization", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingDeoptimizationOffset;
    @HotSpotVMField(name = "ThreadShadow::_pending_failed_speculation", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingFailedSpeculationOffset;
    @HotSpotVMField(name = "ThreadShadow::_pending_transfer_to_interpreter", type = "bool", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingTransferToInterpreterOffset;

    @HotSpotVMFlag(name = "UseHSAILDeoptimization") @Stable public boolean useHSAILDeoptimization;
    @HotSpotVMFlag(name = "UseHSAILSafepoints") @Stable public boolean useHSAILSafepoints;

    /**
     * Offsets of Hsail deoptimization fields (defined in gpu_hsail.hpp). Used to propagate
     * exceptions from Hsail back to C++ runtime.
     */
    @HotSpotVMField(name = "Hsail::HSAILDeoptimizationInfo::_notice_safepoints", type = "jint*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailNoticeSafepointsOffset;
    @HotSpotVMField(name = "Hsail::HSAILDeoptimizationInfo::_deopt_occurred", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailDeoptOccurredOffset;
    @HotSpotVMField(name = "Hsail::HSAILDeoptimizationInfo::_never_ran_array", type = "jboolean*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailNeverRanArrayOffset;
    @HotSpotVMField(name = "Hsail::HSAILDeoptimizationInfo::_deopt_next_index", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailDeoptNextIndexOffset;
    @HotSpotVMField(name = "Hsail::HSAILDeoptimizationInfo::_alloc_info", type = "HSAILAllocationInfo*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailAllocInfoOffset;
    @HotSpotVMField(name = "Hsail::HSAILDeoptimizationInfo::_cur_tlab_info", type = "HSAILTlabInfo**", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailCurTlabInfoOffset;

    @HotSpotVMField(name = "Hsail::HSAILKernelDeoptimization::_workitemid", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailDeoptimizationWorkItem;
    @HotSpotVMField(name = "Hsail::HSAILKernelDeoptimization::_actionAndReason", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailDeoptimizationReason;

    @HotSpotVMField(name = "HSAILFrame::_pc_offset", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailFramePcOffset;
    @HotSpotVMField(name = "HSAILFrame::_num_s_regs", type = "jbyte", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailFrameNumSRegOffset;
    @HotSpotVMField(name = "HSAILFrame::_num_d_regs", type = "jbyte", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailFrameNumDRegOffset;
    @HotSpotVMType(name = "HSAILFrame", get = HotSpotVMType.Type.SIZE) @Stable public int hsailFrameHeaderSize;
    @HotSpotVMType(name = "Hsail::HSAILKernelDeoptimization", get = HotSpotVMType.Type.SIZE) @Stable public int hsailKernelDeoptimizationHeaderSize;
    @HotSpotVMType(name = "Hsail::HSAILDeoptimizationInfo", get = HotSpotVMType.Type.SIZE) @Stable public int hsailDeoptimizationInfoHeaderSize;

    @HotSpotVMField(name = "HSAILAllocationInfo::_tlab_infos_pool_start", type = "HSAILTlabInfo*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailAllocInfoTlabInfosPoolStartOffset;
    @HotSpotVMField(name = "HSAILAllocationInfo::_tlab_infos_pool_next", type = "HSAILTlabInfo*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailAllocInfoTlabInfosPoolNextOffset;
    @HotSpotVMField(name = "HSAILAllocationInfo::_tlab_infos_pool_end", type = "HSAILTlabInfo*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailAllocInfoTlabInfosPoolEndOffset;
    @HotSpotVMField(name = "HSAILAllocationInfo::_tlab_align_reserve_bytes", type = "size_t", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailAllocInfoTlabAlignReserveBytesOffset;

    @HotSpotVMField(name = "HSAILTlabInfo::_start", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoStartOffset;
    @HotSpotVMField(name = "HSAILTlabInfo::_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoTopOffset;
    @HotSpotVMField(name = "HSAILTlabInfo::_end", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoEndOffset;
    @HotSpotVMField(name = "HSAILTlabInfo::_last_good_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoLastGoodTopOffset;
    @HotSpotVMField(name = "HSAILTlabInfo::_original_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoOriginalTopOffset;
    @HotSpotVMField(name = "HSAILTlabInfo::_donor_thread", type = "JavaThread*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoDonorThreadOffset;
    @HotSpotVMField(name = "HSAILTlabInfo::_alloc_info", type = "HSAILAllocationInfo*", get = HotSpotVMField.Type.OFFSET) @Stable public int hsailTlabInfoAllocInfoOffset;
    @HotSpotVMType(name = "HSAILTlabInfo", get = HotSpotVMType.Type.SIZE) @Stable public int hsailTlabInfoSize;

    /**
     * Mark word right shift to get identity hash code.
     */
    @HotSpotVMConstant(name = "markOopDesc::hash_shift") @Stable public int identityHashCodeShift;

    /**
     * Identity hash code value when uninitialized.
     */
    @HotSpotVMConstant(name = "markOopDesc::no_hash") @Stable public int uninitializedIdentityHashCodeValue;

    @HotSpotVMField(name = "Method::_access_flags", type = "AccessFlags", get = HotSpotVMField.Type.OFFSET) @Stable public int methodAccessFlagsOffset;
    @HotSpotVMField(name = "Method::_constMethod", type = "ConstMethod*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodConstMethodOffset;
    @HotSpotVMField(name = "Method::_intrinsic_id", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int methodIntrinsicIdOffset;
    @HotSpotVMField(name = "Method::_flags", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int methodFlagsOffset;
    @HotSpotVMField(name = "Method::_vtable_index", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodVtableIndexOffset;

    @HotSpotVMConstant(name = "Method::_jfr_towrite") @Stable public int methodFlagsJfrTowrite;
    @HotSpotVMConstant(name = "Method::_caller_sensitive") @Stable public int methodFlagsCallerSensitive;
    @HotSpotVMConstant(name = "Method::_force_inline") @Stable public int methodFlagsForceInline;
    @HotSpotVMConstant(name = "Method::_dont_inline") @Stable public int methodFlagsDontInline;
    @HotSpotVMConstant(name = "Method::_hidden") @Stable public int methodFlagsHidden;
    @HotSpotVMConstant(name = "Method::nonvirtual_vtable_index") @Stable public int nonvirtualVtableIndex;
    @HotSpotVMConstant(name = "Method::invalid_vtable_index") @Stable public int invalidVtableIndex;

    @HotSpotVMConstant(name = "JVM_ACC_MONITOR_MATCH") @Stable public int jvmAccMonitorMatch;
    @HotSpotVMConstant(name = "JVM_ACC_HAS_MONITOR_BYTECODES") @Stable public int jvmAccHasMonitorBytecodes;

    @HotSpotVMField(name = "CompileTask::_num_inlined_bytecodes", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int compileTaskNumInlinedBytecodesOffset;

    /**
     * Value of Method::extra_stack_entries().
     */
    @HotSpotVMValue(expression = "Method::extra_stack_entries()") @Stable public int extraStackEntries;

    @HotSpotVMField(name = "ConstMethod::_constants", type = "ConstantPool*", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodConstantsOffset;
    @HotSpotVMField(name = "ConstMethod::_flags", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodFlagsOffset;
    @HotSpotVMField(name = "ConstMethod::_code_size", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodCodeSizeOffset;
    @HotSpotVMField(name = "ConstMethod::_name_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodNameIndexOffset;
    @HotSpotVMField(name = "ConstMethod::_signature_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodSignatureIndexOffset;
    @HotSpotVMField(name = "ConstMethod::_max_stack", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodMaxStackOffset;
    @HotSpotVMField(name = "ConstMethod::_max_locals", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int methodMaxLocalsOffset;

    @HotSpotVMConstant(name = "ConstMethod::_has_linenumber_table") @Stable public int constMethodHasLineNumberTable;
    @HotSpotVMConstant(name = "ConstMethod::_has_localvariable_table") @Stable public int constMethodHasLocalVariableTable;
    @HotSpotVMConstant(name = "ConstMethod::_has_exception_table") @Stable public int constMethodHasExceptionTable;

    @HotSpotVMType(name = "ExceptionTableElement", get = HotSpotVMType.Type.SIZE) @Stable public int exceptionTableElementSize;
    @HotSpotVMField(name = "ExceptionTableElement::start_pc", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementStartPcOffset;
    @HotSpotVMField(name = "ExceptionTableElement::end_pc", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementEndPcOffset;
    @HotSpotVMField(name = "ExceptionTableElement::handler_pc", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementHandlerPcOffset;
    @HotSpotVMField(name = "ExceptionTableElement::catch_type_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementCatchTypeIndexOffset;

    @HotSpotVMType(name = "LocalVariableTableElement", get = HotSpotVMType.Type.SIZE) @Stable public int localVariableTableElementSize;
    @HotSpotVMField(name = "LocalVariableTableElement::start_bci", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementStartBciOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::length", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementLengthOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::name_cp_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementNameCpIndexOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::descriptor_cp_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementDescriptorCpIndexOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::signature_cp_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementSignatureCpIndexOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::slot", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementSlotOffset;

    @HotSpotVMType(name = "ConstantPool", get = HotSpotVMType.Type.SIZE) @Stable public int constantPoolSize;
    @HotSpotVMField(name = "ConstantPool::_tags", type = "Array<u1>*", get = HotSpotVMField.Type.OFFSET) @Stable public int constantPoolTagsOffset;
    @HotSpotVMField(name = "ConstantPool::_pool_holder", type = "InstanceKlass*", get = HotSpotVMField.Type.OFFSET) @Stable public int constantPoolHolderOffset;
    @HotSpotVMField(name = "ConstantPool::_length", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int constantPoolLengthOffset;

    @HotSpotVMConstant(name = "ConstantPool::CPCACHE_INDEX_TAG") @Stable public int constantPoolCpCacheIndexTag;

    @HotSpotVMConstant(name = "JVM_CONSTANT_Utf8") @Stable public int jvmConstantUtf8;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Integer") @Stable public int jvmConstantInteger;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Long") @Stable public int jvmConstantLong;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Float") @Stable public int jvmConstantFloat;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Double") @Stable public int jvmConstantDouble;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Class") @Stable public int jvmConstantClass;
    @HotSpotVMConstant(name = "JVM_CONSTANT_UnresolvedClass") @Stable public int jvmConstantUnresolvedClass;
    @HotSpotVMConstant(name = "JVM_CONSTANT_UnresolvedClassInError") @Stable public int jvmConstantUnresolvedClassInError;
    @HotSpotVMConstant(name = "JVM_CONSTANT_String") @Stable public int jvmConstantString;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Fieldref") @Stable public int jvmConstantFieldref;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Methodref") @Stable public int jvmConstantMethodref;
    @HotSpotVMConstant(name = "JVM_CONSTANT_InterfaceMethodref") @Stable public int jvmConstantInterfaceMethodref;
    @HotSpotVMConstant(name = "JVM_CONSTANT_NameAndType") @Stable public int jvmConstantNameAndType;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodHandle") @Stable public int jvmConstantMethodHandle;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodHandleInError") @Stable public int jvmConstantMethodHandleInError;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodType") @Stable public int jvmConstantMethodType;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodTypeInError") @Stable public int jvmConstantMethodTypeInError;
    @HotSpotVMConstant(name = "JVM_CONSTANT_InvokeDynamic") @Stable public int jvmConstantInvokeDynamic;

    @HotSpotVMConstant(name = "HeapWordSize") @Stable public int heapWordSize;

    @HotSpotVMType(name = "Symbol*", get = HotSpotVMType.Type.SIZE) @Stable public int symbolPointerSize;
    @HotSpotVMField(name = "Symbol::_length", type = "unsigned short", get = HotSpotVMField.Type.OFFSET) @Stable public int symbolLengthOffset;
    @HotSpotVMField(name = "Symbol::_body[0]", type = "jbyte", get = HotSpotVMField.Type.OFFSET) @Stable public int symbolBodyOffset;

    @HotSpotVMField(name = "vmSymbols::_symbols[0]", type = "Symbol*", get = HotSpotVMField.Type.ADDRESS) @Stable public long vmSymbolsSymbols;
    @HotSpotVMConstant(name = "vmSymbols::FIRST_SID") @Stable public int vmSymbolsFirstSID;
    @HotSpotVMConstant(name = "vmSymbols::SID_LIMIT") @Stable public int vmSymbolsSIDLimit;

    @HotSpotVMConstant(name = "JVM_ACC_HAS_FINALIZER") @Stable public int klassHasFinalizerFlag;

    // Modifier.SYNTHETIC is not public so we get it via vmStructs.
    @HotSpotVMConstant(name = "JVM_ACC_SYNTHETIC") @Stable public int syntheticFlag;

    /**
     * @see HotSpotResolvedObjectType#createField
     */
    @HotSpotVMConstant(name = "JVM_RECOGNIZED_FIELD_MODIFIERS") @Stable public int recognizedFieldModifiers;

    /**
     * Bit pattern that represents a non-oop. Neither the high bits nor the low bits of this value
     * are allowed to look like (respectively) the high or low bits of a real oop.
     */
    @HotSpotVMField(name = "Universe::_non_oop_bits", type = "intptr_t", get = HotSpotVMField.Type.VALUE) @Stable public long nonOopBits;

    @HotSpotVMField(name = "StubRoutines::_verify_oop_count", type = "jint", get = HotSpotVMField.Type.ADDRESS) @Stable public long verifyOopCounterAddress;
    @HotSpotVMValue(expression = "Universe::verify_oop_mask()") @Stable public long verifyOopMask;
    @HotSpotVMValue(expression = "Universe::verify_oop_bits()") @Stable public long verifyOopBits;

    @HotSpotVMField(name = "CollectedHeap::_barrier_set", type = "BarrierSet*", get = HotSpotVMField.Type.OFFSET) @Stable public int collectedHeapBarrierSetOffset;

    @HotSpotVMField(name = "HeapRegion::LogOfHRGrainBytes", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int logOfHRGrainBytes;

    @HotSpotVMField(name = "BarrierSet::_kind", type = "BarrierSet::Name", get = HotSpotVMField.Type.OFFSET) @Stable private int barrierSetKindOffset;
    @HotSpotVMConstant(name = "BarrierSet::CardTableModRef") @Stable public int barrierSetCardTableModRef;
    @HotSpotVMConstant(name = "BarrierSet::CardTableExtension") @Stable public int barrierSetCardTableExtension;
    @HotSpotVMConstant(name = "BarrierSet::G1SATBCT") @Stable public int barrierSetG1SATBCT;
    @HotSpotVMConstant(name = "BarrierSet::G1SATBCTLogging") @Stable public int barrierSetG1SATBCTLogging;
    @HotSpotVMConstant(name = "BarrierSet::ModRef") @Stable public int barrierSetModRef;
    @HotSpotVMConstant(name = "BarrierSet::Other") @Stable public int barrierSetOther;

    @HotSpotVMField(name = "CardTableModRefBS::byte_map_base", type = "jbyte*", get = HotSpotVMField.Type.OFFSET) @Stable private int cardTableModRefBSByteMapBaseOffset;
    @HotSpotVMConstant(name = "CardTableModRefBS::card_shift") @Stable public int cardTableModRefBSCardShift;

    @HotSpotVMValue(expression = "(jbyte)CardTableModRefBS::dirty_card_val()") @Stable public byte dirtyCardValue;
    @HotSpotVMValue(expression = "(jbyte)G1SATBCardTableModRefBS::g1_young_card_val()") @Stable public byte g1YoungCardValue;

    public long cardtableStartAddress() {
        final long barrierSetAddress = unsafe.getAddress(universeCollectedHeap + collectedHeapBarrierSetOffset);
        final int kind = unsafe.getInt(barrierSetAddress + barrierSetKindOffset);
        if ((kind == barrierSetCardTableModRef) || (kind == barrierSetCardTableExtension) || (kind == barrierSetG1SATBCT) || (kind == barrierSetG1SATBCTLogging)) {
            final long base = unsafe.getAddress(barrierSetAddress + cardTableModRefBSByteMapBaseOffset);
            assert base != 0 : "unexpected byte_map_base: " + base;
            return base;
        }
        if ((kind == barrierSetModRef) || (kind == barrierSetOther)) {
            // No post barriers
            return 0;
        }
        throw GraalInternalError.shouldNotReachHere("kind: " + kind);
    }

    public int cardtableShift() {
        final long barrierSetAddress = unsafe.getAddress(universeCollectedHeap + collectedHeapBarrierSetOffset);
        final int kind = unsafe.getInt(barrierSetAddress + barrierSetKindOffset);
        if ((kind == barrierSetCardTableModRef) || (kind == barrierSetCardTableExtension) || (kind == barrierSetG1SATBCT) || (kind == barrierSetG1SATBCTLogging)) {
            return cardTableModRefBSCardShift;
        }
        if ((kind == barrierSetModRef) || (kind == barrierSetOther)) {
            // No post barriers
            return 0;
        }
        throw GraalInternalError.shouldNotReachHere("kind: " + kind);
    }

    @HotSpotVMField(name = "os::_polling_page", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long safepointPollingAddress;

    // G1 Collector Related Values.

    public int g1CardQueueIndexOffset() {
        return javaThreadDirtyCardQueueOffset + ptrQueueIndexOffset;
    }

    public int g1CardQueueBufferOffset() {
        return javaThreadDirtyCardQueueOffset + ptrQueueBufferOffset;
    }

    public int g1SATBQueueMarkingOffset() {
        return javaThreadSatbMarkQueueOffset + ptrQueueActiveOffset;
    }

    public int g1SATBQueueIndexOffset() {
        return javaThreadSatbMarkQueueOffset + ptrQueueIndexOffset;
    }

    public int g1SATBQueueBufferOffset() {
        return javaThreadSatbMarkQueueOffset + ptrQueueBufferOffset;
    }

    @HotSpotVMField(name = "java_lang_Class::_klass_offset", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int klassOffset;
    @HotSpotVMField(name = "java_lang_Class::_array_klass_offset", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int arrayKlassOffset;

    @HotSpotVMField(name = "Method::_method_data", type = "MethodData*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataOffset;
    @HotSpotVMField(name = "Method::_from_compiled_entry", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable public int methodCompiledEntryOffset;
    @HotSpotVMField(name = "Method::_code", type = "nmethod*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodCodeOffset;

    @HotSpotVMField(name = "MethodData::_size", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataSize;
    @HotSpotVMField(name = "MethodData::_data_size", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataDataSize;
    @HotSpotVMField(name = "MethodData::_data[0]", type = "intptr_t", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataOopDataOffset;
    @HotSpotVMField(name = "MethodData::_trap_hist._array[0]", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataOopTrapHistoryOffset;
    @HotSpotVMField(name = "MethodData::_graal_node_count", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataGraalNodeCountOffset;

    @HotSpotVMField(name = "nmethod::_verified_entry_point", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable public int nmethodEntryOffset;
    @HotSpotVMField(name = "nmethod::_comp_level", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int nmethodCompLevelOffset;

    @HotSpotVMConstant(name = "CompLevel_full_optimization") @Stable public int compilationLevelFullOptimization;

    @HotSpotVMType(name = "BasicLock", get = HotSpotVMType.Type.SIZE) @Stable public int basicLockSize;
    @HotSpotVMField(name = "BasicLock::_displaced_header", type = "markOop", get = HotSpotVMField.Type.OFFSET) @Stable public int basicLockDisplacedHeaderOffset;

    @HotSpotVMValue(expression = "Universe::heap()->end_addr()", get = HotSpotVMValue.Type.ADDRESS) @Stable public long heapEndAddress;
    @HotSpotVMValue(expression = "Universe::heap()->top_addr()", get = HotSpotVMValue.Type.ADDRESS) @Stable public long heapTopAddress;

    @HotSpotVMField(name = "Thread::_allocated_bytes", type = "jlong", get = HotSpotVMField.Type.OFFSET) @Stable public int threadAllocatedBytesOffset;

    @HotSpotVMFlag(name = "TLABWasteIncrement") @Stable public int tlabRefillWasteIncrement;
    @HotSpotVMValue(expression = "ThreadLocalAllocBuffer::alignment_reserve()") @Stable public int tlabAlignmentReserve;

    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_start", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferStartOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_end", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferEndOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferTopOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_pf_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferPfTopOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_slow_allocations", type = "unsigned", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferSlowAllocationsOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_fast_refill_waste", type = "unsigned", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferFastRefillWasteOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_number_of_refills", type = "unsigned", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferNumberOfRefillsOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_refill_waste_limit", type = "size_t", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferRefillWasteLimitOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_desired_size", type = "size_t", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferDesiredSizeOffset;

    public int tlabSlowAllocationsOffset() {
        return threadTlabOffset + threadLocalAllocBufferSlowAllocationsOffset;
    }

    public int tlabFastRefillWasteOffset() {
        return threadTlabOffset + threadLocalAllocBufferFastRefillWasteOffset;
    }

    public int tlabNumberOfRefillsOffset() {
        return threadTlabOffset + threadLocalAllocBufferNumberOfRefillsOffset;
    }

    public int tlabRefillWasteLimitOffset() {
        return threadTlabOffset + threadLocalAllocBufferRefillWasteLimitOffset;
    }

    public int threadTlabSizeOffset() {
        return threadTlabOffset + threadLocalAllocBufferDesiredSizeOffset;
    }

    public int threadTlabStartOffset() {
        return threadTlabOffset + threadLocalAllocBufferStartOffset;
    }

    public int threadTlabEndOffset() {
        return threadTlabOffset + threadLocalAllocBufferEndOffset;
    }

    public int threadTlabTopOffset() {
        return threadTlabOffset + threadLocalAllocBufferTopOffset;
    }

    public int threadTlabPfTopOffset() {
        return threadTlabOffset + threadLocalAllocBufferPfTopOffset;
    }

    @HotSpotVMFlag(name = "TLABStats") @Stable public boolean tlabStats;
    @HotSpotVMValue(expression = " !CMSIncrementalMode && Universe::heap()->supports_inline_contig_alloc()") @Stable public boolean inlineContiguousAllocationSupported;

    /**
     * The DataLayout header size is the same as the cell size.
     */
    @HotSpotVMConstant(name = "DataLayout::cell_size") @Stable public int dataLayoutHeaderSize;
    @HotSpotVMField(name = "DataLayout::_header._struct._tag", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutTagOffset;
    @HotSpotVMField(name = "DataLayout::_header._struct._flags", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutFlagsOffset;
    @HotSpotVMField(name = "DataLayout::_header._struct._bci", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutBCIOffset;
    @HotSpotVMField(name = "DataLayout::_cells[0]", type = "intptr_t", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutCellsOffset;
    @HotSpotVMConstant(name = "DataLayout::cell_size") @Stable public int dataLayoutCellSize;

    @HotSpotVMConstant(name = "DataLayout::no_tag") @Stable public int dataLayoutNoTag;
    @HotSpotVMConstant(name = "DataLayout::bit_data_tag") @Stable public int dataLayoutBitDataTag;
    @HotSpotVMConstant(name = "DataLayout::counter_data_tag") @Stable public int dataLayoutCounterDataTag;
    @HotSpotVMConstant(name = "DataLayout::jump_data_tag") @Stable public int dataLayoutJumpDataTag;
    @HotSpotVMConstant(name = "DataLayout::receiver_type_data_tag") @Stable public int dataLayoutReceiverTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::virtual_call_data_tag") @Stable public int dataLayoutVirtualCallDataTag;
    @HotSpotVMConstant(name = "DataLayout::ret_data_tag") @Stable public int dataLayoutRetDataTag;
    @HotSpotVMConstant(name = "DataLayout::branch_data_tag") @Stable public int dataLayoutBranchDataTag;
    @HotSpotVMConstant(name = "DataLayout::multi_branch_data_tag") @Stable public int dataLayoutMultiBranchDataTag;
    @HotSpotVMConstant(name = "DataLayout::arg_info_data_tag") @Stable public int dataLayoutArgInfoDataTag;
    @HotSpotVMConstant(name = "DataLayout::call_type_data_tag") @Stable public int dataLayoutCallTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::virtual_call_type_data_tag") @Stable public int dataLayoutVirtualCallTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::parameters_type_data_tag") @Stable public int dataLayoutParametersTypeDataTag;

    @HotSpotVMFlag(name = "BciProfileWidth") @Stable public int bciProfileWidth;
    @HotSpotVMFlag(name = "TypeProfileWidth") @Stable public int typeProfileWidth;
    @HotSpotVMFlag(name = "MethodProfileWidth") @Stable public int methodProfileWidth;

    @HotSpotVMField(name = "CodeBlob::_code_offset", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable private int codeBlobCodeOffsetOffset;
    @HotSpotVMField(name = "SharedRuntime::_ic_miss_blob", type = "RuntimeStub*", get = HotSpotVMField.Type.VALUE) @Stable private long inlineCacheMissBlob;

    public long inlineCacheMissStub() {
        return inlineCacheMissBlob + unsafe.getInt(inlineCacheMissBlob + codeBlobCodeOffsetOffset);
    }

    @HotSpotVMField(name = "CodeCache::_heap", type = "CodeHeap*", get = HotSpotVMField.Type.VALUE) @Stable private long codeCacheHeap;
    @HotSpotVMField(name = "CodeHeap::_memory", type = "VirtualSpace", get = HotSpotVMField.Type.OFFSET) @Stable private int codeHeapMemoryOffset;
    @HotSpotVMField(name = "VirtualSpace::_low_boundary", type = "char*", get = HotSpotVMField.Type.OFFSET) @Stable private int virtualSpaceLowBoundaryOffset;
    @HotSpotVMField(name = "VirtualSpace::_high_boundary", type = "char*", get = HotSpotVMField.Type.OFFSET) @Stable private int virtualSpaceHighBoundaryOffset;

    /**
     * @return CodeCache::_heap-&gt;_memory._low_boundary
     */
    public long codeCacheLowBoundary() {
        return unsafe.getAddress(codeCacheHeap + codeHeapMemoryOffset + virtualSpaceLowBoundaryOffset);
    }

    /**
     * @return CodeCache::_heap-&gt;_memory._high_boundary
     */
    public long codeCacheHighBoundary() {
        return unsafe.getAddress(codeCacheHeap + codeHeapMemoryOffset + virtualSpaceHighBoundaryOffset);
    }

    @HotSpotVMField(name = "StubRoutines::_aescrypt_encryptBlock", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long aescryptEncryptBlockStub;
    @HotSpotVMField(name = "StubRoutines::_aescrypt_decryptBlock", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long aescryptDecryptBlockStub;
    @HotSpotVMField(name = "StubRoutines::_cipherBlockChaining_encryptAESCrypt", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long cipherBlockChainingEncryptAESCryptStub;
    @HotSpotVMField(name = "StubRoutines::_cipherBlockChaining_decryptAESCrypt", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long cipherBlockChainingDecryptAESCryptStub;
    @HotSpotVMField(name = "StubRoutines::_updateBytesCRC32", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long updateBytesCRC32Stub;
    @HotSpotVMField(name = "StubRoutines::_crc_table_adr", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long crcTableAddress;

    @HotSpotVMField(name = "StubRoutines::_jbyte_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jshort_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jlong_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_jbyte_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jshort_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jint_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jlong_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_disjoint_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopDisjointArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jbyte_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jshort_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jlong_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jbyte_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jshort_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jint_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jlong_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedDisjointArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_checkcast_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long checkcastArraycopy;
    @HotSpotVMField(name = "StubRoutines::_checkcast_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long checkcastArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_unsafe_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long unsafeArraycopy;
    @HotSpotVMField(name = "StubRoutines::_generic_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long genericArraycopy;

    @HotSpotVMValue(expression = "GraalRuntime::new_instance", get = HotSpotVMValue.Type.ADDRESS) @Stable public long newInstanceAddress;
    @HotSpotVMValue(expression = "GraalRuntime::new_array", get = HotSpotVMValue.Type.ADDRESS) @Stable public long newArrayAddress;
    @HotSpotVMValue(expression = "GraalRuntime::new_multi_array", get = HotSpotVMValue.Type.ADDRESS) @Stable public long newMultiArrayAddress;
    @HotSpotVMValue(expression = "GraalRuntime::dynamic_new_array", get = HotSpotVMValue.Type.ADDRESS) @Stable public long dynamicNewArrayAddress;
    @HotSpotVMValue(expression = "GraalRuntime::dynamic_new_instance", get = HotSpotVMValue.Type.ADDRESS) @Stable public long dynamicNewInstanceAddress;
    @HotSpotVMValue(expression = "GraalRuntime::thread_is_interrupted", get = HotSpotVMValue.Type.ADDRESS) @Stable public long threadIsInterruptedAddress;
    @HotSpotVMValue(expression = "GraalRuntime::vm_message", signature = "(unsigned char, long, long, long, long)", get = HotSpotVMValue.Type.ADDRESS) @Stable public long vmMessageAddress;
    @HotSpotVMValue(expression = "GraalRuntime::identity_hash_code", get = HotSpotVMValue.Type.ADDRESS) @Stable public long identityHashCodeAddress;
    @HotSpotVMValue(expression = "GraalRuntime::exception_handler_for_pc", signature = "(JavaThread*)", get = HotSpotVMValue.Type.ADDRESS) @Stable public long exceptionHandlerForPcAddress;
    @HotSpotVMValue(expression = "GraalRuntime::monitorenter", get = HotSpotVMValue.Type.ADDRESS) @Stable public long monitorenterAddress;
    @HotSpotVMValue(expression = "GraalRuntime::monitorexit", get = HotSpotVMValue.Type.ADDRESS) @Stable public long monitorexitAddress;
    @HotSpotVMValue(expression = "GraalRuntime::create_null_exception", get = HotSpotVMValue.Type.ADDRESS) @Stable public long createNullPointerExceptionAddress;
    @HotSpotVMValue(expression = "GraalRuntime::create_out_of_bounds_exception", get = HotSpotVMValue.Type.ADDRESS) @Stable public long createOutOfBoundsExceptionAddress;
    @HotSpotVMValue(expression = "GraalRuntime::log_primitive", get = HotSpotVMValue.Type.ADDRESS) @Stable public long logPrimitiveAddress;
    @HotSpotVMValue(expression = "GraalRuntime::log_object", get = HotSpotVMValue.Type.ADDRESS) @Stable public long logObjectAddress;
    @HotSpotVMValue(expression = "GraalRuntime::log_printf", get = HotSpotVMValue.Type.ADDRESS) @Stable public long logPrintfAddress;
    @HotSpotVMValue(expression = "GraalRuntime::vm_error", get = HotSpotVMValue.Type.ADDRESS) @Stable public long vmErrorAddress;
    @HotSpotVMValue(expression = "GraalRuntime::load_and_clear_exception", get = HotSpotVMValue.Type.ADDRESS) @Stable public long loadAndClearExceptionAddress;
    @HotSpotVMValue(expression = "GraalRuntime::write_barrier_pre", get = HotSpotVMValue.Type.ADDRESS) @Stable public long writeBarrierPreAddress;
    @HotSpotVMValue(expression = "GraalRuntime::write_barrier_post", get = HotSpotVMValue.Type.ADDRESS) @Stable public long writeBarrierPostAddress;
    @HotSpotVMValue(expression = "GraalRuntime::validate_object", get = HotSpotVMValue.Type.ADDRESS) @Stable public long validateObject;

    @HotSpotVMValue(expression = "SharedRuntime::register_finalizer", get = HotSpotVMValue.Type.ADDRESS) @Stable public long registerFinalizerAddress;
    @HotSpotVMValue(expression = "SharedRuntime::exception_handler_for_return_address", get = HotSpotVMValue.Type.ADDRESS) @Stable public long exceptionHandlerForReturnAddressAddress;
    @HotSpotVMValue(expression = "SharedRuntime::OSR_migration_end", get = HotSpotVMValue.Type.ADDRESS) @Stable public long osrMigrationEndAddress;

    @HotSpotVMValue(expression = "os::javaTimeMillis", get = HotSpotVMValue.Type.ADDRESS) @Stable public long javaTimeMillisAddress;
    @HotSpotVMValue(expression = "os::javaTimeNanos", get = HotSpotVMValue.Type.ADDRESS) @Stable public long javaTimeNanosAddress;
    @HotSpotVMValue(expression = "SharedRuntime::dsin", get = HotSpotVMValue.Type.ADDRESS) @Stable public long arithmeticSinAddress;
    @HotSpotVMValue(expression = "SharedRuntime::dcos", get = HotSpotVMValue.Type.ADDRESS) @Stable public long arithmeticCosAddress;
    @HotSpotVMValue(expression = "SharedRuntime::dtan", get = HotSpotVMValue.Type.ADDRESS) @Stable public long arithmeticTanAddress;

    @HotSpotVMValue(expression = "(jint) GraalCounterSize") @Stable public int graalCountersSize;

    @HotSpotVMValue(expression = "Deoptimization::fetch_unroll_info", signature = "(JavaThread*)", get = HotSpotVMValue.Type.ADDRESS) @Stable public long deoptimizationFetchUnrollInfo;
    @HotSpotVMValue(expression = "Deoptimization::uncommon_trap", get = HotSpotVMValue.Type.ADDRESS) @Stable public long deoptimizationUncommonTrap;
    @HotSpotVMValue(expression = "Deoptimization::unpack_frames", signature = "(JavaThread*, int)", get = HotSpotVMValue.Type.ADDRESS) @Stable public long deoptimizationUnpackFrames;

    @HotSpotVMConstant(name = "Deoptimization::Reason_none") @Stable public int deoptReasonNone;
    @HotSpotVMConstant(name = "Deoptimization::Reason_null_check") @Stable public int deoptReasonNullCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_range_check") @Stable public int deoptReasonRangeCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_class_check") @Stable public int deoptReasonClassCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_array_check") @Stable public int deoptReasonArrayCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_null_assert") @Stable public int deoptReasonUnreached0;
    @HotSpotVMConstant(name = "Deoptimization::Reason_intrinsic") @Stable public int deoptReasonTypeCheckInlining;
    @HotSpotVMConstant(name = "Deoptimization::Reason_bimorphic") @Stable public int deoptReasonOptimizedTypeCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_unhandled") @Stable public int deoptReasonNotCompiledExceptionHandler;
    @HotSpotVMConstant(name = "Deoptimization::Reason_uninitialized") @Stable public int deoptReasonUnresolved;
    @HotSpotVMConstant(name = "Deoptimization::Reason_age") @Stable public int deoptReasonJsrMismatch;
    @HotSpotVMConstant(name = "Deoptimization::Reason_div0_check") @Stable public int deoptReasonDiv0Check;
    @HotSpotVMConstant(name = "Deoptimization::Reason_constraint") @Stable public int deoptReasonConstraint;
    @HotSpotVMConstant(name = "Deoptimization::Reason_loop_limit_check") @Stable public int deoptReasonLoopLimitCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_aliasing") @Stable public int deoptReasonAliasing;
    @HotSpotVMConstant(name = "Deoptimization::Reason_transfer_to_interpreter") @Stable public int deoptReasonTransferToInterpreter;
    @HotSpotVMConstant(name = "Deoptimization::Reason_LIMIT") @Stable public int deoptReasonOSROffset;

    @HotSpotVMConstant(name = "Deoptimization::Action_none") @Stable public int deoptActionNone;
    @HotSpotVMConstant(name = "Deoptimization::Action_maybe_recompile") @Stable public int deoptActionMaybeRecompile;
    @HotSpotVMConstant(name = "Deoptimization::Action_reinterpret") @Stable public int deoptActionReinterpret;
    @HotSpotVMConstant(name = "Deoptimization::Action_make_not_entrant") @Stable public int deoptActionMakeNotEntrant;
    @HotSpotVMConstant(name = "Deoptimization::Action_make_not_compilable") @Stable public int deoptActionMakeNotCompilable;

    @HotSpotVMConstant(name = "Deoptimization::_action_bits") @Stable public int deoptimizationActionBits;
    @HotSpotVMConstant(name = "Deoptimization::_reason_bits") @Stable public int deoptimizationReasonBits;
    @HotSpotVMConstant(name = "Deoptimization::_debug_id_bits") @Stable public int deoptimizationDebugIdBits;
    @HotSpotVMConstant(name = "Deoptimization::_action_shift") @Stable public int deoptimizationActionShift;
    @HotSpotVMConstant(name = "Deoptimization::_reason_shift") @Stable public int deoptimizationReasonShift;
    @HotSpotVMConstant(name = "Deoptimization::_debug_id_shift") @Stable public int deoptimizationDebugIdShift;

    @HotSpotVMConstant(name = "Deoptimization::Unpack_deopt") @Stable public int deoptimizationUnpackDeopt;
    @HotSpotVMConstant(name = "Deoptimization::Unpack_exception") @Stable public int deoptimizationUnpackException;
    @HotSpotVMConstant(name = "Deoptimization::Unpack_uncommon_trap") @Stable public int deoptimizationUnpackUncommonTrap;
    @HotSpotVMConstant(name = "Deoptimization::Unpack_reexecute") @Stable public int deoptimizationUnpackReexecute;

    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_size_of_deoptimized_frame", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_caller_adjustment", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockCallerAdjustmentOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_number_of_frames", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockNumberOfFramesOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_total_frame_sizes", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockTotalFrameSizesOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_frame_sizes", type = "intptr_t*", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockFrameSizesOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_frame_pcs", type = "address*", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockFramePcsOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_initial_info", type = "intptr_t", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockInitialInfoOffset;

    @HotSpotVMConstant(name = "vmIntrinsics::_invokeBasic") @Stable public int vmIntrinsicInvokeBasic;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToVirtual") @Stable public int vmIntrinsicLinkToVirtual;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToStatic") @Stable public int vmIntrinsicLinkToStatic;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToSpecial") @Stable public int vmIntrinsicLinkToSpecial;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToInterface") @Stable public int vmIntrinsicLinkToInterface;

    @HotSpotVMConstant(name = "GraalEnv::ok") @Stable public int codeInstallResultOk;
    @HotSpotVMConstant(name = "GraalEnv::dependencies_failed") @Stable public int codeInstallResultDependenciesFailed;
    @HotSpotVMConstant(name = "GraalEnv::cache_full") @Stable public int codeInstallResultCacheFull;
    @HotSpotVMConstant(name = "GraalEnv::code_too_large") @Stable public int codeInstallResultCodeTooLarge;

    @HotSpotVMConstant(name = "CompilerToVM::KLASS_TAG") @Stable public int compilerToVMKlassTag;
    @HotSpotVMConstant(name = "CompilerToVM::SYMBOL_TAG") @Stable public int compilerToVMSymbolTag;

    @HotSpotVMConstant(name = "CodeInstaller::VERIFIED_ENTRY") @Stable public int codeInstallerMarkIdVerifiedEntry;
    @HotSpotVMConstant(name = "CodeInstaller::UNVERIFIED_ENTRY") @Stable public int codeInstallerMarkIdUnverifiedEntry;
    @HotSpotVMConstant(name = "CodeInstaller::OSR_ENTRY") @Stable public int codeInstallerMarkIdOsrEntry;
    @HotSpotVMConstant(name = "CodeInstaller::EXCEPTION_HANDLER_ENTRY") @Stable public int codeInstallerMarkIdExceptionHandlerEntry;
    @HotSpotVMConstant(name = "CodeInstaller::DEOPT_HANDLER_ENTRY") @Stable public int codeInstallerMarkIdDeoptHandlerEntry;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKEINTERFACE") @Stable public int codeInstallerMarkIdInvokeinterface;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKEVIRTUAL") @Stable public int codeInstallerMarkIdInvokevirtual;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKESTATIC") @Stable public int codeInstallerMarkIdInvokestatic;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKESPECIAL") @Stable public int codeInstallerMarkIdInvokespecial;
    @HotSpotVMConstant(name = "CodeInstaller::INLINE_INVOKE") @Stable public int codeInstallerMarkIdInlineInvoke;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_NEAR") @Stable public int codeInstallerMarkIdPollNear;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_RETURN_NEAR") @Stable public int codeInstallerMarkIdPollReturnNear;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_FAR") @Stable public int codeInstallerMarkIdPollFar;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_RETURN_FAR") @Stable public int codeInstallerMarkIdPollReturnFar;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKE_INVALID") @Stable public int codeInstallerMarkIdInvokeInvalid;

    public boolean check() {
        for (Field f : getClass().getDeclaredFields()) {
            int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                assert Modifier.isFinal(modifiers) || f.getAnnotation(Stable.class) != null : "field should either be final or @Stable: " + f;
            }
        }

        assert codeEntryAlignment > 0 : codeEntryAlignment;
        assert (layoutHelperArrayTagObjectValue & (1 << (Integer.SIZE - 1))) != 0 : "object array must have first bit set";
        assert (layoutHelperArrayTagTypeValue & (1 << (Integer.SIZE - 1))) != 0 : "type array must have first bit set";

        return true;
    }

    /**
     * A compact representation of the different encoding strategies for Objects and metadata.
     */
    public static class CompressEncoding {
        public final long base;
        public final int shift;
        public final int alignment;

        CompressEncoding(long base, int shift, int alignment) {
            this.base = base;
            this.shift = shift;
            this.alignment = alignment;
        }

        public int compress(long ptr) {
            if (ptr == 0L) {
                return 0;
            } else {
                return (int) ((ptr - base) >>> shift);
            }
        }

        public long uncompress(int ptr) {
            if (ptr == 0) {
                return 0L;
            } else {
                return ((ptr & 0xFFFFFFFFL) << shift) + base;
            }
        }

        @Override
        public String toString() {
            return "base: " + base + " shift: " + shift + " alignment: " + alignment;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + alignment;
            result = prime * result + (int) (base ^ (base >>> 32));
            result = prime * result + shift;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CompressEncoding) {
                CompressEncoding other = (CompressEncoding) obj;
                return alignment == other.alignment && base == other.base && shift == other.shift;
            } else {
                return false;
            }
        }
    }

    /**
     * Returns the name of the C/C++ function that is associated (via HotSpotVMValue annotation)
     * with the HotSpotVMConfig object's field containing {@code foreignCalltargetAddress}; returns
     * null if no field holds the provided address.
     *
     * @param foreignCallTargetAddress address of foreign call target
     * @return C/C++ symbol name or null
     */
    public String getCSymbol(long foreignCallTargetAddress) {
        for (Field f : HotSpotVMConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(HotSpotVMValue.class)) {
                HotSpotVMValue annotation = f.getAnnotation(HotSpotVMValue.class);

                if (annotation.get() == HotSpotVMValue.Type.ADDRESS) {
                    try {
                        if (foreignCallTargetAddress == f.getLong(this)) {
                            return (annotation.expression() + annotation.signature());
                        }
                    } catch (IllegalArgumentException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    } catch (IllegalAccessException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
            }
        }
        return null;
    }
}
