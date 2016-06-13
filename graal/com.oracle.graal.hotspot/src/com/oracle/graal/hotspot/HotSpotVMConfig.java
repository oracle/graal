/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import jdk.internal.vm.annotation.Stable;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * Used to access native configuration details.
 *
 * All non-static, public fields in this class are so that they can be compiled as constants.
 */
public class HotSpotVMConfig extends HotSpotVMConfigAccess {

    private final String osName = getHostOSName();
    private final String osArch = getHostArchitectureName();

    public final boolean windowsOs = System.getProperty("os.name", "").startsWith("Windows");
    public final boolean linuxOs = System.getProperty("os.name", "").startsWith("Linux");

    @SuppressWarnings("try")
    HotSpotVMConfig(HotSpotVMConfigStore store) {
        super(store);

        oopEncoding = new CompressEncoding(narrowOopBase, narrowOopShift, logMinObjAlignment());
        klassEncoding = new CompressEncoding(narrowKlassBase, narrowKlassShift, logKlassAlignment);

        assert check();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private final CompressEncoding oopEncoding;
    private final CompressEncoding klassEncoding;

    public CompressEncoding getOopEncoding() {
        return oopEncoding;
    }

    public CompressEncoding getKlassEncoding() {
        return klassEncoding;
    }

    /**
     * Gets the host operating system name.
     */
    private static String getHostOSName() {
        String osName = System.getProperty("os.name");
        switch (osName) {
            case "Linux":
                osName = "linux";
                break;
            case "SunOS":
                osName = "solaris";
                break;
            case "Mac OS X":
                osName = "bsd";
                break;
            default:
                // Of course Windows is different...
                if (osName.startsWith("Windows")) {
                    osName = "windows";
                } else {
                    throw new JVMCIError("Unexpected OS name: " + osName);
                }
        }
        return osName;
    }

    /**
     * Gets the host architecture name for the purpose of finding the corresponding
     * {@linkplain HotSpotJVMCIBackendFactory backend}.
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

    private final Integer amd64RequiredInt = osArch.equals("amd64") ? null : 0;
    private final Integer sparcRequiredInt = osArch.equals("sparc") ? null : 0;

    public final boolean cAssertions = getConstant("ASSERT", Boolean.class);

    public final int codeEntryAlignment = getFlag("CodeEntryAlignment", Integer.class);
    public final boolean verifyOops = getFlag("VerifyOops", Boolean.class);
    public final boolean ciTime = getFlag("CITime", Boolean.class);
    public final boolean ciTimeEach = getFlag("CITimeEach", Boolean.class);
    public final int compileTheWorldStartAt = getFlag("CompileTheWorldStartAt", Integer.class, 1);
    public final int compileTheWorldStopAt = getFlag("CompileTheWorldStopAt", Integer.class, Integer.MAX_VALUE);
    public final boolean dontCompileHugeMethods = getFlag("DontCompileHugeMethods", Boolean.class);
    public final int hugeMethodLimit = getFlag("HugeMethodLimit", Integer.class);
    public final boolean printInlining = getFlag("PrintInlining", Boolean.class);
    public final boolean inline = getFlag("Inline", Boolean.class);
    public final boolean useFastLocking = getFlag("JVMCIUseFastLocking", Boolean.class);
    public final boolean forceUnreachable = getFlag("ForceUnreachable", Boolean.class);
    public final int codeSegmentSize = getFlag("CodeCacheSegmentSize", Integer.class);
    public final boolean foldStableValues = getFlag("FoldStableValues", Boolean.class);

    public final boolean useTLAB = getFlag("UseTLAB", Boolean.class);
    public final boolean useBiasedLocking = getFlag("UseBiasedLocking", Boolean.class);
    public final boolean usePopCountInstruction = getFlag("UsePopCountInstruction", Boolean.class);
    public final boolean useAESIntrinsics = getFlag("UseAESIntrinsics", Boolean.class);
    public final boolean useCRC32Intrinsics = getFlag("UseCRC32Intrinsics", Boolean.class);
    public final boolean useG1GC = getFlag("UseG1GC", Boolean.class);
    public final boolean useCMSGC = getFlag("UseConcMarkSweepGC", Boolean.class);

    public final int allocatePrefetchStyle = getFlag("AllocatePrefetchStyle", Integer.class);
    public final int allocatePrefetchInstr = getFlag("AllocatePrefetchInstr", Integer.class);
    public final int allocatePrefetchLines = getFlag("AllocatePrefetchLines", Integer.class);
    public final int allocateInstancePrefetchLines = getFlag("AllocateInstancePrefetchLines", Integer.class);
    public final int allocatePrefetchStepSize = getFlag("AllocatePrefetchStepSize", Integer.class);
    public final int allocatePrefetchDistance = getFlag("AllocatePrefetchDistance", Integer.class);

    public final boolean flightRecorder = getFlag("FightRecorder", Boolean.class, false);

    private final long universeCollectedHeap = getFieldValue("CompilerToVM::Data::Universe_collectedHeap", Long.class, "CollectedHeap*");
    private final int collectedHeapTotalCollectionsOffset = getFieldOffset("CollectedHeap::_total_collections", Integer.class, "unsigned int");

    public long gcTotalCollectionsAddress() {
        return universeCollectedHeap + collectedHeapTotalCollectionsOffset;
    }

    public final boolean useDeferredInitBarriers = getFlag("ReduceInitialCardMarks", Boolean.class);

    // Compressed Oops related values.
    public final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);
    public final boolean useCompressedClassPointers = getFlag("UseCompressedClassPointers", Boolean.class);

    public final long narrowOopBase = getFieldValue("CompilerToVM::Data::Universe_narrow_oop_base", Long.class, "address");
    public final int narrowOopShift = getFieldValue("CompilerToVM::Data::Universe_narrow_oop_shift", Integer.class, "int");
    public final int objectAlignment = getFlag("ObjectAlignmentInBytes", Integer.class);

    public final int minObjAlignment() {
        return objectAlignment / heapWordSize;
    }

    public final int logMinObjAlignment() {
        return (int) (Math.log(objectAlignment) / Math.log(2));
    }

    public final int narrowKlassSize = getTypeSize("narrowKlass");
    public final long narrowKlassBase = getFieldValue("CompilerToVM::Data::Universe_narrow_klass_base", Long.class, "address");
    public final int narrowKlassShift = getFieldValue("CompilerToVM::Data::Universe_narrow_klass_shift", Integer.class, "int");
    public final int logKlassAlignment = getConstant("LogKlassAlignmentInBytes", Integer.class);

    public final int stackShadowPages = getFlag("StackShadowPages", Integer.class);
    public final int stackReservedPages = getFlag("StackReservedPages", Integer.class);
    public final boolean useStackBanging = getFlag("UseStackBanging", Boolean.class);
    public final int stackBias = getConstant("STACK_BIAS", Integer.class);
    public final int vmPageSize = getFieldValue("CompilerToVM::Data::vm_page_size", Integer.class, "int");

    // offsets, ...
    public final int markOffset = getFieldOffset("oopDesc::_mark", Integer.class, "markOop");
    public final int hubOffset = getFieldOffset("oopDesc::_metadata._klass", Integer.class, "Klass*");

    public final int prototypeMarkWordOffset = getFieldOffset("Klass::_prototype_header", Integer.class, "markOop");
    public final int subklassOffset = getFieldOffset("Klass::_subklass", Integer.class, "Klass*");
    public final int nextSiblingOffset = getFieldOffset("Klass::_next_sibling", Integer.class, "Klass*");
    public final int superCheckOffsetOffset = getFieldOffset("Klass::_super_check_offset", Integer.class, "juint");
    public final int secondarySuperCacheOffset = getFieldOffset("Klass::_secondary_super_cache", Integer.class, "Klass*");
    public final int secondarySupersOffset = getFieldOffset("Klass::_secondary_supers", Integer.class, "Array<Klass*>*");

    /**
     * The offset of the _java_mirror field (of type {@link Class}) in a Klass.
     */
    public final int classMirrorOffset = getFieldOffset("Klass::_java_mirror", Integer.class, "oop");

    public final int klassSuperKlassOffset = getFieldOffset("Klass::_super", Integer.class, "Klass*");
    public final int klassModifierFlagsOffset = getFieldOffset("Klass::_modifier_flags", Integer.class, "jint");
    public final int klassAccessFlagsOffset = getFieldOffset("Klass::_access_flags", Integer.class, "AccessFlags");
    public final int klassLayoutHelperOffset = getFieldOffset("Klass::_layout_helper", Integer.class, "jint");
    public final int klassNameOffset = getFieldOffset("Klass::_name", Integer.class, "Symbol*");

    public final int klassLayoutHelperNeutralValue = getConstant("Klass::_lh_neutral_value", Integer.class);
    public final int klassLayoutHelperInstanceSlowPathBit = getConstant("Klass::_lh_instance_slow_path_bit", Integer.class);
    public final int layoutHelperLog2ElementSizeShift = getConstant("Klass::_lh_log2_element_size_shift", Integer.class);
    public final int layoutHelperLog2ElementSizeMask = getConstant("Klass::_lh_log2_element_size_mask", Integer.class);
    public final int layoutHelperElementTypeShift = getConstant("Klass::_lh_element_type_shift", Integer.class);
    public final int layoutHelperElementTypeMask = getConstant("Klass::_lh_element_type_mask", Integer.class);
    public final int layoutHelperHeaderSizeShift = getConstant("Klass::_lh_header_size_shift", Integer.class);
    public final int layoutHelperHeaderSizeMask = getConstant("Klass::_lh_header_size_mask", Integer.class);
    public final int layoutHelperArrayTagShift = getConstant("Klass::_lh_array_tag_shift", Integer.class);
    public final int layoutHelperArrayTagTypeValue = getConstant("Klass::_lh_array_tag_type_value", Integer.class);
    public final int layoutHelperArrayTagObjectValue = getConstant("Klass::_lh_array_tag_obj_value", Integer.class);

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

    public final int vtableEntrySize = getTypeSize("vtableEntry");
    public final int vtableEntryMethodOffset = getFieldOffset("vtableEntry::_method", Integer.class, "Method*");

    public final int instanceKlassSize = getTypeSize("InstanceKlass");
    public final int instanceKlassSourceFileNameIndexOffset = getFieldOffset("InstanceKlass::_source_file_name_index", Integer.class, "u2");
    public final int instanceKlassInitStateOffset = getFieldOffset("InstanceKlass::_init_state", Integer.class, "u1");
    public final int instanceKlassConstantsOffset = getFieldOffset("InstanceKlass::_constants", Integer.class, "ConstantPool*");
    public final int instanceKlassFieldsOffset = getFieldOffset("InstanceKlass::_fields", Integer.class, "Array<u2>*");
    public final int klassVtableStartOffset = getFieldValue("CompilerToVM::Data::Klass_vtable_start_offset", Integer.class, "int");
    public final int klassVtableLengthOffset = getFieldValue("CompilerToVM::Data::Klass_vtable_length_offset", Integer.class, "int");

    public final int instanceKlassStateLinked = getConstant("InstanceKlass::linked", Integer.class);
    public final int instanceKlassStateFullyInitialized = getConstant("InstanceKlass::fully_initialized", Integer.class);

    public final int arrayOopDescSize = getTypeSize("arrayOopDesc");

    /**
     * The offset of the array length word in an array object's header.
     *
     * See {@code arrayOopDesc::length_offset_in_bytes()}.
     */
    public final int arrayOopDescLengthOffset() {
        return useCompressedClassPointers ? hubOffset + narrowKlassSize : arrayOopDescSize;
    }

    public final int arrayU1LengthOffset = getFieldOffset("Array<int>::_length", Integer.class, "int");
    public final int arrayU1DataOffset = getFieldOffset("Array<u1>::_data", Integer.class);
    public final int arrayU2DataOffset = getFieldOffset("Array<u2>::_data", Integer.class);
    public final int metaspaceArrayLengthOffset = getFieldOffset("Array<Klass*>::_length", Integer.class, "int");
    public final int metaspaceArrayBaseOffset = getFieldOffset("Array<Klass*>::_data[0]", Integer.class, "Klass*");

    public final int arrayClassElementOffset = getFieldOffset("ObjArrayKlass::_element_klass", Integer.class, "Klass*");

    public final int fieldInfoAccessFlagsOffset = getConstant("FieldInfo::access_flags_offset", Integer.class);
    public final int fieldInfoNameIndexOffset = getConstant("FieldInfo::name_index_offset", Integer.class);
    public final int fieldInfoSignatureIndexOffset = getConstant("FieldInfo::signature_index_offset", Integer.class);
    public final int fieldInfoInitvalIndexOffset = getConstant("FieldInfo::initval_index_offset", Integer.class);
    public final int fieldInfoLowPackedOffset = getConstant("FieldInfo::low_packed_offset", Integer.class);
    public final int fieldInfoHighPackedOffset = getConstant("FieldInfo::high_packed_offset", Integer.class);
    public final int fieldInfoFieldSlots = getConstant("FieldInfo::field_slots", Integer.class);

    public final int fieldInfoTagSize = getConstant("FIELDINFO_TAG_SIZE", Integer.class);

    public final int jvmAccMonitorMatch = getConstant("JVM_ACC_MONITOR_MATCH", Integer.class);
    public final int jvmAccHasMonitorBytecodes = getConstant("JVM_ACC_HAS_MONITOR_BYTECODES", Integer.class);
    public final int jvmAccHasFinalizer = getConstant("JVM_ACC_HAS_FINALIZER", Integer.class);
    public final int jvmAccFieldInternal = getConstant("JVM_ACC_FIELD_INTERNAL", Integer.class);
    public final int jvmAccFieldStable = getConstant("JVM_ACC_FIELD_STABLE", Integer.class);
    public final int jvmAccFieldHasGenericSignature = getConstant("JVM_ACC_FIELD_HAS_GENERIC_SIGNATURE", Integer.class);
    public final int jvmAccWrittenFlags = getConstant("JVM_ACC_WRITTEN_FLAGS", Integer.class);
    public final int jvmAccIsCloneableFast = getConstant("JVM_ACC_IS_CLONEABLE_FAST", Integer.class);

    // Modifier.SYNTHETIC is not public so we get it via vmStructs.
    public final int jvmAccSynthetic = getConstant("JVM_ACC_SYNTHETIC", Integer.class);

    /**
     * see HotSpotResolvedObjectTypeImpl#createField
     */
    public final int recognizedFieldModifiers = getConstant("JVM_RECOGNIZED_FIELD_MODIFIERS", Integer.class);

    public final int threadTlabOffset = getFieldOffset("Thread::_tlab", Integer.class, "ThreadLocalAllocBuffer");

    public final int javaThreadAnchorOffset = getFieldOffset("JavaThread::_anchor", Integer.class, "JavaFrameAnchor");
    public final int threadObjectOffset = getFieldOffset("JavaThread::_threadObj", Integer.class, "oop");
    public final int osThreadOffset = getFieldOffset("JavaThread::_osthread", Integer.class, "OSThread*");
    public final int javaThreadDirtyCardQueueOffset = getFieldOffset("JavaThread::_dirty_card_queue", Integer.class, "DirtyCardQueue");
    public final int threadIsMethodHandleReturnOffset = getFieldOffset("JavaThread::_is_method_handle_return", Integer.class, "int");
    public final int javaThreadSatbMarkQueueOffset = getFieldOffset("JavaThread::_satb_mark_queue", Integer.class, "SATBMarkQueue");
    public final int threadObjectResultOffset = getFieldOffset("JavaThread::_vm_result", Integer.class, "oop");
    public final int jvmciCountersThreadOffset = getFieldOffset("JavaThread::_jvmci_counters", Integer.class, "jlong*");
    public final int javaThreadReservedStackActivationOffset = getFieldOffset("JavaThread::_reserved_stack_activation", Integer.class, "address");

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
    public final long dllLoad = getAddress("os::dll_load");

    /**
     * Address of the library lookup routine. The C signature of this routine is:
     *
     * <pre>
     *     void* (void* handle, const char* name)
     * </pre>
     */
    public final long dllLookup = getAddress("os::dll_lookup");

    /**
     * A pseudo-handle which when used as the first argument to {@link #dllLookup} means lookup will
     * return the first occurrence of the desired symbol using the default library search order. If
     * this field is {@value #INVALID_RTLD_DEFAULT_HANDLE}, then this capability is not supported on
     * the current platform.
     */
    public final long rtldDefault = getAddress("RTLD_DEFAULT", osName.equals("bsd") || osName.equals("linux") ? null : INVALID_RTLD_DEFAULT_HANDLE);

    /**
     * This field is used to pass exception objects into and out of the runtime system during
     * exception handling for compiled code.
     */
    public final int threadExceptionOopOffset = getFieldOffset("JavaThread::_exception_oop", Integer.class, "oop");
    public final int threadExceptionPcOffset = getFieldOffset("JavaThread::_exception_pc", Integer.class, "address");
    public final int pendingExceptionOffset = getFieldOffset("ThreadShadow::_pending_exception", Integer.class, "oop");

    public final int pendingDeoptimizationOffset = getFieldOffset("JavaThread::_pending_deoptimization", Integer.class, "int");
    public final int pendingFailedSpeculationOffset = getFieldOffset("JavaThread::_pending_failed_speculation", Integer.class, "oop");
    public final int pendingTransferToInterpreterOffset = getFieldOffset("JavaThread::_pending_transfer_to_interpreter", Integer.class, "bool");

    private final int javaFrameAnchorLastJavaSpOffset = getFieldOffset("JavaFrameAnchor::_last_Java_sp", Integer.class, "intptr_t*");
    private final int javaFrameAnchorLastJavaPcOffset = getFieldOffset("JavaFrameAnchor::_last_Java_pc", Integer.class, "address");
    private final int javaFrameAnchorLastJavaFpOffset = getFieldOffset("JavaFrameAnchor::_last_Java_fp", Integer.class, "intptr_t*",
                    osArch.equals("aarch64") || osArch.equals("amd64") ? null : Integer.MAX_VALUE);
    private final int javaFrameAnchorFlagsOffset = getFieldOffset("JavaFrameAnchor::_flags", Integer.class, "int", sparcRequiredInt);

    public int threadLastJavaSpOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaSpOffset;
    }

    public int threadLastJavaPcOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaPcOffset;
    }

    public int threadLastJavaFpOffset() {
        assert getHostArchitectureName().equals("aarch64") || getHostArchitectureName().equals("amd64");
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
    public final int runtimeCallStackSize = getConstant("frame::arg_reg_save_area_bytes", Integer.class, amd64RequiredInt);
    public final int frameInterpreterFrameSenderSpOffset = getConstant("frame::interpreter_frame_sender_sp_offset", Integer.class, amd64RequiredInt);
    public final int frameInterpreterFrameLastSpOffset = getConstant("frame::interpreter_frame_last_sp_offset", Integer.class, amd64RequiredInt);

    private final int dirtyCardQueueBufferOffset = getConstant("dirtyCardQueueBufferOffset", Integer.class);
    private final int dirtyCardQueueIndexOffset = getConstant("dirtyCardQueueIndexOffset", Integer.class);

    private final int satbMarkQueueBufferOffset = getConstant("satbMarkQueueBufferOffset", Integer.class);
    private final int satbMarkQueueIndexOffset = getConstant("satbMarkQueueIndexOffset", Integer.class);
    private final int satbMarkQueueActiveOffset = getConstant("satbMarkQueueActiveOffset", Integer.class);

    public final int osThreadInterruptedOffset = getFieldOffset("OSThread::_interrupted", Integer.class, "jint");

    public final long markOopDescHashShift = getConstant("markOopDesc::hash_shift", Long.class);

    public final int biasedLockMaskInPlace = getConstant("markOopDesc::biased_lock_mask_in_place", Integer.class);
    public final int ageMaskInPlace = getConstant("markOopDesc::age_mask_in_place", Integer.class);
    public final int epochMaskInPlace = getConstant("markOopDesc::epoch_mask_in_place", Integer.class);
    public final long markOopDescHashMask = getConstant("markOopDesc::hash_mask", Long.class);
    public final long markOopDescHashMaskInPlace = getConstant("markOopDesc::hash_mask_in_place", Long.class);

    public final int unlockedMask = getConstant("markOopDesc::unlocked_value", Integer.class);
    public final int biasedLockPattern = getConstant("markOopDesc::biased_lock_pattern", Integer.class);

    public final int markWordNoHashInPlace = getConstant("markOopDesc::no_hash_in_place", Integer.class);
    public final int markWordNoLockInPlace = getConstant("markOopDesc::no_lock_in_place", Integer.class);

    /**
     * See {@code markOopDesc::prototype()}.
     */
    public long arrayPrototypeMarkWord() {
        return markWordNoHashInPlace | markWordNoLockInPlace;
    }

    /**
     * See {@code markOopDesc::copy_set_hash()}.
     */
    public long tlabIntArrayMarkWord() {
        long tmp = arrayPrototypeMarkWord() & (~markOopDescHashMaskInPlace);
        tmp |= ((0x2 & markOopDescHashMask) << markOopDescHashShift);
        return tmp;
    }

    /**
     * Mark word right shift to get identity hash code.
     */
    public final int identityHashCodeShift = getConstant("markOopDesc::hash_shift", Integer.class);

    /**
     * Identity hash code value when uninitialized.
     */
    public final int uninitializedIdentityHashCodeValue = getConstant("markOopDesc::no_hash", Integer.class);

    public final int methodAccessFlagsOffset = getFieldOffset("Method::_access_flags", Integer.class, "AccessFlags");
    public final int methodConstMethodOffset = getFieldOffset("Method::_constMethod", Integer.class, "ConstMethod*");
    public final int methodIntrinsicIdOffset = getFieldOffset("Method::_intrinsic_id", Integer.class, "u2");
    public final int methodFlagsOffset = getFieldOffset("Method::_flags", Integer.class, "u2");
    public final int methodVtableIndexOffset = getFieldOffset("Method::_vtable_index", Integer.class, "int");

    public final int methodCountersOffset = getFieldOffset("Method::_method_counters", Integer.class, "MethodCounters*");
    public final int methodDataOffset = getFieldOffset("Method::_method_data", Integer.class, "MethodData*");
    public final int methodCompiledEntryOffset = getFieldOffset("Method::_from_compiled_entry", Integer.class, "address");
    public final int methodCodeOffset = getFieldOffset("Method::_code", Integer.class, "CompiledMethod*");

    public final int methodFlagsJfrTowrite = getConstant("Method::_jfr_towrite", Integer.class);
    public final int methodFlagsCallerSensitive = getConstant("Method::_caller_sensitive", Integer.class);
    public final int methodFlagsForceInline = getConstant("Method::_force_inline", Integer.class);
    public final int methodFlagsDontInline = getConstant("Method::_dont_inline", Integer.class);
    public final int methodFlagsHidden = getConstant("Method::_hidden", Integer.class);
    public final int methodFlagsReservedStackAccess = getConstant("Method::_reserved_stack_access", Integer.class);
    public final int nonvirtualVtableIndex = getConstant("Method::nonvirtual_vtable_index", Integer.class);
    public final int invalidVtableIndex = getConstant("Method::invalid_vtable_index", Integer.class);

    public final int invocationCounterOffset = getFieldOffset("MethodCounters::_invocation_counter", Integer.class, "InvocationCounter");
    public final int backedgeCounterOffset = getFieldOffset("MethodCounters::_backedge_counter", Integer.class, "InvocationCounter");
    public final int invocationCounterIncrement = getConstant("InvocationCounter::count_increment", Integer.class);
    public final int invocationCounterShift = getConstant("InvocationCounter::count_shift", Integer.class);

    public final int methodDataSize = getFieldOffset("MethodData::_size", Integer.class, "int");
    public final int methodDataDataSize = getFieldOffset("MethodData::_data_size", Integer.class, "int");
    public final int methodDataOopDataOffset = getFieldOffset("MethodData::_data[0]", Integer.class, "intptr_t");
    public final int methodDataOopTrapHistoryOffset = getFieldOffset("MethodData::_trap_hist._array[0]", Integer.class, "u1");
    public final int methodDataIRSizeOffset = getFieldOffset("MethodData::_jvmci_ir_size", Integer.class, "int");

    public final int nmethodEntryOffset = getFieldOffset("nmethod::_verified_entry_point", Integer.class, "address");
    public final int nmethodCompLevelOffset = getFieldOffset("nmethod::_comp_level", Integer.class, "int");

    public final int compilationLevelNone = getConstant("CompLevel_none", Integer.class);
    public final int compilationLevelSimple = getConstant("CompLevel_simple", Integer.class);
    public final int compilationLevelLimitedProfile = getConstant("CompLevel_limited_profile", Integer.class);
    public final int compilationLevelFullProfile = getConstant("CompLevel_full_profile", Integer.class);
    public final int compilationLevelFullOptimization = getConstant("CompLevel_full_optimization", Integer.class);

    public final int compLevelAdjustmentNone = getConstant("JVMCIRuntime::none", Integer.class);
    public final int compLevelAdjustmentByHolder = getConstant("JVMCIRuntime::by_holder", Integer.class);
    public final int compLevelAdjustmentByFullSignature = getConstant("JVMCIRuntime::by_full_signature", Integer.class);

    public final int invocationEntryBci = getConstant("InvocationEntryBci", Integer.class);

    public final int jvmciEnvTaskOffset = getFieldOffset("JVMCIEnv::_task", Integer.class, "CompileTask*");
    public final int jvmciEnvJvmtiCanHotswapOrPostBreakpointOffset = getFieldOffset("JVMCIEnv::_jvmti_can_hotswap_or_post_breakpoint", Integer.class, "bool");
    public final int compileTaskNumInlinedBytecodesOffset = getFieldOffset("CompileTask::_num_inlined_bytecodes", Integer.class, "int");

    public final int extraStackEntries = getFieldValue("CompilerToVM::Data::Method_extra_stack_entries", Integer.class, "int");

    public final int constMethodConstantsOffset = getFieldOffset("ConstMethod::_constants", Integer.class, "ConstantPool*");
    public final int constMethodFlagsOffset = getFieldOffset("ConstMethod::_flags", Integer.class, "u2");
    public final int constMethodCodeSizeOffset = getFieldOffset("ConstMethod::_code_size", Integer.class, "u2");
    public final int constMethodNameIndexOffset = getFieldOffset("ConstMethod::_name_index", Integer.class, "u2");
    public final int constMethodSignatureIndexOffset = getFieldOffset("ConstMethod::_signature_index", Integer.class, "u2");
    public final int constMethodMaxStackOffset = getFieldOffset("ConstMethod::_max_stack", Integer.class, "u2");
    public final int methodMaxLocalsOffset = getFieldOffset("ConstMethod::_max_locals", Integer.class, "u2");

    public final int constMethodHasLineNumberTable = getConstant("ConstMethod::_has_linenumber_table", Integer.class);
    public final int constMethodHasLocalVariableTable = getConstant("ConstMethod::_has_localvariable_table", Integer.class);
    public final int constMethodHasExceptionTable = getConstant("ConstMethod::_has_exception_table", Integer.class);

    public final int exceptionTableElementSize = getTypeSize("ExceptionTableElement");
    public final int exceptionTableElementStartPcOffset = getFieldOffset("ExceptionTableElement::start_pc", Integer.class, "u2");
    public final int exceptionTableElementEndPcOffset = getFieldOffset("ExceptionTableElement::end_pc", Integer.class, "u2");
    public final int exceptionTableElementHandlerPcOffset = getFieldOffset("ExceptionTableElement::handler_pc", Integer.class, "u2");
    public final int exceptionTableElementCatchTypeIndexOffset = getFieldOffset("ExceptionTableElement::catch_type_index", Integer.class, "u2");

    public final int localVariableTableElementSize = getTypeSize("LocalVariableTableElement");
    public final int localVariableTableElementStartBciOffset = getFieldOffset("LocalVariableTableElement::start_bci", Integer.class, "u2");
    public final int localVariableTableElementLengthOffset = getFieldOffset("LocalVariableTableElement::length", Integer.class, "u2");
    public final int localVariableTableElementNameCpIndexOffset = getFieldOffset("LocalVariableTableElement::name_cp_index", Integer.class, "u2");
    public final int localVariableTableElementDescriptorCpIndexOffset = getFieldOffset("LocalVariableTableElement::descriptor_cp_index", Integer.class, "u2");
    public final int localVariableTableElementSignatureCpIndexOffset = getFieldOffset("LocalVariableTableElement::signature_cp_index", Integer.class, "u2");
    public final int localVariableTableElementSlotOffset = getFieldOffset("LocalVariableTableElement::slot", Integer.class, "u2");

    public final int constantPoolSize = getTypeSize("ConstantPool");
    public final int constantPoolTagsOffset = getFieldOffset("ConstantPool::_tags", Integer.class, "Array<u1>*");
    public final int constantPoolHolderOffset = getFieldOffset("ConstantPool::_pool_holder", Integer.class, "InstanceKlass*");
    public final int constantPoolLengthOffset = getFieldOffset("ConstantPool::_length", Integer.class, "int");

    public final int constantPoolCpCacheIndexTag = getConstant("ConstantPool::CPCACHE_INDEX_TAG", Integer.class);

    public final int jvmConstantUtf8 = getConstant("JVM_CONSTANT_Utf8", Integer.class);
    public final int jvmConstantInteger = getConstant("JVM_CONSTANT_Integer", Integer.class);
    public final int jvmConstantLong = getConstant("JVM_CONSTANT_Long", Integer.class);
    public final int jvmConstantFloat = getConstant("JVM_CONSTANT_Float", Integer.class);
    public final int jvmConstantDouble = getConstant("JVM_CONSTANT_Double", Integer.class);
    public final int jvmConstantClass = getConstant("JVM_CONSTANT_Class", Integer.class);
    public final int jvmConstantUnresolvedClass = getConstant("JVM_CONSTANT_UnresolvedClass", Integer.class);
    public final int jvmConstantUnresolvedClassInError = getConstant("JVM_CONSTANT_UnresolvedClassInError", Integer.class);
    public final int jvmConstantString = getConstant("JVM_CONSTANT_String", Integer.class);
    public final int jvmConstantFieldref = getConstant("JVM_CONSTANT_Fieldref", Integer.class);
    public final int jvmConstantMethodref = getConstant("JVM_CONSTANT_Methodref", Integer.class);
    public final int jvmConstantInterfaceMethodref = getConstant("JVM_CONSTANT_InterfaceMethodref", Integer.class);
    public final int jvmConstantNameAndType = getConstant("JVM_CONSTANT_NameAndType", Integer.class);
    public final int jvmConstantMethodHandle = getConstant("JVM_CONSTANT_MethodHandle", Integer.class);
    public final int jvmConstantMethodHandleInError = getConstant("JVM_CONSTANT_MethodHandleInError", Integer.class);
    public final int jvmConstantMethodType = getConstant("JVM_CONSTANT_MethodType", Integer.class);
    public final int jvmConstantMethodTypeInError = getConstant("JVM_CONSTANT_MethodTypeInError", Integer.class);
    public final int jvmConstantInvokeDynamic = getConstant("JVM_CONSTANT_InvokeDynamic", Integer.class);

    public final int jvmConstantExternalMax = getConstant("JVM_CONSTANT_ExternalMax", Integer.class);
    public final int jvmConstantInternalMin = getConstant("JVM_CONSTANT_InternalMin", Integer.class);
    public final int jvmConstantInternalMax = getConstant("JVM_CONSTANT_InternalMax", Integer.class);

    public final int heapWordSize = getConstant("HeapWordSize", Integer.class);

    public final int symbolPointerSize = getTypeSize("Symbol*");

    public final long vmSymbolsSymbols = getFieldAddress("vmSymbols::_symbols[0]", "Symbol*");
    public final int vmSymbolsFirstSID = getConstant("vmSymbols::FIRST_SID", Integer.class);
    public final int vmSymbolsSIDLimit = getConstant("vmSymbols::SID_LIMIT", Integer.class);

    /**
     * Bit pattern that represents a non-oop. Neither the high bits nor the low bits of this value
     * are allowed to look like (respectively) the high or low bits of a real oop.
     */
    public final long nonOopBits = getFieldValue("CompilerToVM::Data::Universe_non_oop_bits", Long.class, "void*");

    public final long verifyOopCounterAddress = getFieldAddress("StubRoutines::_verify_oop_count", "jint");
    public final long verifyOopMask = getFieldValue("CompilerToVM::Data::Universe_verify_oop_mask", Long.class, "uintptr_t");
    public final long verifyOopBits = getFieldValue("CompilerToVM::Data::Universe_verify_oop_bits", Long.class, "uintptr_t");
    public final int universeBaseVtableSize = getFieldValue("CompilerToVM::Data::Universe_base_vtable_size", Integer.class, "int");

    public final int baseVtableLength() {
        return universeBaseVtableSize / vtableEntrySize;
    }

    public final int logOfHRGrainBytes = getFieldValue("HeapRegion::LogOfHRGrainBytes", Integer.class, "int");

    public final byte dirtyCardValue = getConstant("CardTableModRefBS::dirty_card", Byte.class);
    public final byte g1YoungCardValue = getConstant("G1SATBCardTableModRefBS::g1_young_gen", Byte.class);

    private final long cardtableStartAddress = getFieldValue("CompilerToVM::Data::cardtable_start_address", Long.class, "jbyte*");
    private final int cardtableShift = getFieldValue("CompilerToVM::Data::cardtable_shift", Integer.class, "int");

    public long cardtableStartAddress() {
        return cardtableStartAddress;
    }

    public int cardtableShift() {
        return cardtableShift;
    }

    public final long safepointPollingAddress = getFieldValue("os::_polling_page", Long.class, "address");

    // G1 Collector Related Values.

    public int g1CardQueueIndexOffset() {
        return javaThreadDirtyCardQueueOffset + dirtyCardQueueIndexOffset;
    }

    public int g1CardQueueBufferOffset() {
        return javaThreadDirtyCardQueueOffset + dirtyCardQueueBufferOffset;
    }

    public int g1SATBQueueMarkingOffset() {
        return javaThreadSatbMarkQueueOffset + satbMarkQueueActiveOffset;
    }

    public int g1SATBQueueIndexOffset() {
        return javaThreadSatbMarkQueueOffset + satbMarkQueueIndexOffset;
    }

    public int g1SATBQueueBufferOffset() {
        return javaThreadSatbMarkQueueOffset + satbMarkQueueBufferOffset;
    }

    public final int klassOffset = getFieldValue("java_lang_Class::_klass_offset", Integer.class, "int");
    public final int arrayKlassOffset = getFieldValue("java_lang_Class::_array_klass_offset", Integer.class, "int");

    public final int basicLockSize = getTypeSize("BasicLock");
    public final int basicLockDisplacedHeaderOffset = getFieldOffset("BasicLock::_displaced_header", Integer.class, "markOop");

    public final int threadAllocatedBytesOffset = getFieldOffset("Thread::_allocated_bytes", Integer.class, "jlong");

    public final int tlabRefillWasteIncrement = getFlag("TLABWasteIncrement", Integer.class);

    private final int threadLocalAllocBufferStartOffset = getFieldOffset("ThreadLocalAllocBuffer::_start", Integer.class, "HeapWord*");
    private final int threadLocalAllocBufferEndOffset = getFieldOffset("ThreadLocalAllocBuffer::_end", Integer.class, "HeapWord*");
    private final int threadLocalAllocBufferTopOffset = getFieldOffset("ThreadLocalAllocBuffer::_top", Integer.class, "HeapWord*");
    private final int threadLocalAllocBufferPfTopOffset = getFieldOffset("ThreadLocalAllocBuffer::_pf_top", Integer.class, "HeapWord*");
    private final int threadLocalAllocBufferSlowAllocationsOffset = getFieldOffset("ThreadLocalAllocBuffer::_slow_allocations", Integer.class, "unsigned");
    private final int threadLocalAllocBufferFastRefillWasteOffset = getFieldOffset("ThreadLocalAllocBuffer::_fast_refill_waste", Integer.class, "unsigned");
    private final int threadLocalAllocBufferNumberOfRefillsOffset = getFieldOffset("ThreadLocalAllocBuffer::_number_of_refills", Integer.class, "unsigned");
    private final int threadLocalAllocBufferRefillWasteLimitOffset = getFieldOffset("ThreadLocalAllocBuffer::_refill_waste_limit", Integer.class, "size_t");
    private final int threadLocalAllocBufferDesiredSizeOffset = getFieldOffset("ThreadLocalAllocBuffer::_desired_size", Integer.class, "size_t");

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

    public final int tlabAlignmentReserve = getFieldValue("CompilerToVM::Data::ThreadLocalAllocBuffer_alignment_reserve", Integer.class, "size_t");

    public final boolean tlabStats = getFlag("TLABStats", Boolean.class);

    // FIXME This is only temporary until the GC code is changed.
    public final boolean inlineContiguousAllocationSupported = getFieldValue("CompilerToVM::Data::_supports_inline_contig_alloc", Boolean.class);
    public final long heapEndAddress = getFieldValue("CompilerToVM::Data::_heap_end_addr", Long.class, "HeapWord**");
    public final long heapTopAddress = getFieldValue("CompilerToVM::Data::_heap_top_addr", Long.class, "HeapWord**");

    /**
     * The DataLayout header size is the same as the cell size.
     */
    public final int dataLayoutHeaderSize = getConstant("DataLayout::cell_size", Integer.class);
    public final int dataLayoutTagOffset = getFieldOffset("DataLayout::_header._struct._tag", Integer.class, "u1");
    public final int dataLayoutFlagsOffset = getFieldOffset("DataLayout::_header._struct._flags", Integer.class, "u1");
    public final int dataLayoutBCIOffset = getFieldOffset("DataLayout::_header._struct._bci", Integer.class, "u2");
    public final int dataLayoutCellsOffset = getFieldOffset("DataLayout::_cells[0]", Integer.class, "intptr_t");
    public final int dataLayoutCellSize = getConstant("DataLayout::cell_size", Integer.class);

    public final int dataLayoutNoTag = getConstant("DataLayout::no_tag", Integer.class);
    public final int dataLayoutBitDataTag = getConstant("DataLayout::bit_data_tag", Integer.class);
    public final int dataLayoutCounterDataTag = getConstant("DataLayout::counter_data_tag", Integer.class);
    public final int dataLayoutJumpDataTag = getConstant("DataLayout::jump_data_tag", Integer.class);
    public final int dataLayoutReceiverTypeDataTag = getConstant("DataLayout::receiver_type_data_tag", Integer.class);
    public final int dataLayoutVirtualCallDataTag = getConstant("DataLayout::virtual_call_data_tag", Integer.class);
    public final int dataLayoutRetDataTag = getConstant("DataLayout::ret_data_tag", Integer.class);
    public final int dataLayoutBranchDataTag = getConstant("DataLayout::branch_data_tag", Integer.class);
    public final int dataLayoutMultiBranchDataTag = getConstant("DataLayout::multi_branch_data_tag", Integer.class);
    public final int dataLayoutArgInfoDataTag = getConstant("DataLayout::arg_info_data_tag", Integer.class);
    public final int dataLayoutCallTypeDataTag = getConstant("DataLayout::call_type_data_tag", Integer.class);
    public final int dataLayoutVirtualCallTypeDataTag = getConstant("DataLayout::virtual_call_type_data_tag", Integer.class);
    public final int dataLayoutParametersTypeDataTag = getConstant("DataLayout::parameters_type_data_tag", Integer.class);
    public final int dataLayoutSpeculativeTrapDataTag = getConstant("DataLayout::speculative_trap_data_tag", Integer.class);

    public final int bciProfileWidth = getFlag("BciProfileWidth", Integer.class);
    public final int typeProfileWidth = getFlag("TypeProfileWidth", Integer.class);
    public final int methodProfileWidth = getFlag("MethodProfileWidth", Integer.class);

    public final long inlineCacheMissStub = getFieldValue("CompilerToVM::Data::SharedRuntime_ic_miss_stub", Long.class, "address");
    public final long handleWrongMethodStub = getFieldValue("CompilerToVM::Data::SharedRuntime_handle_wrong_method_stub", Long.class, "address");

    public final long handleDeoptStub = getFieldValue("CompilerToVM::Data::SharedRuntime_deopt_blob_unpack", Long.class, "address");
    public final long uncommonTrapStub = getFieldValue("CompilerToVM::Data::SharedRuntime_deopt_blob_uncommon_trap", Long.class, "address");

    public final long codeCacheLowBound = getFieldValue("CodeCache::_low_bound", Long.class, "address");
    public final long codeCacheHighBound = getFieldValue("CodeCache::_high_bound", Long.class, "address");

    public final long aescryptEncryptBlockStub = getFieldValue("StubRoutines::_aescrypt_encryptBlock", Long.class, "address");
    public final long aescryptDecryptBlockStub = getFieldValue("StubRoutines::_aescrypt_decryptBlock", Long.class, "address");
    public final long cipherBlockChainingEncryptAESCryptStub = getFieldValue("StubRoutines::_cipherBlockChaining_encryptAESCrypt", Long.class, "address");
    public final long cipherBlockChainingDecryptAESCryptStub = getFieldValue("StubRoutines::_cipherBlockChaining_decryptAESCrypt", Long.class, "address");
    public final long updateBytesCRC32Stub = getFieldValue("StubRoutines::_updateBytesCRC32", Long.class, "address");
    public final long crcTableAddress = getFieldValue("StubRoutines::_crc_table_adr", Long.class, "address");

    public final long throwDelayedStackOverflowErrorEntry = getFieldValue("StubRoutines::_throw_delayed_StackOverflowError_entry", Long.class, "address");

    public final long jbyteArraycopy = getFieldValue("StubRoutines::_jbyte_arraycopy", Long.class, "address");
    public final long jshortArraycopy = getFieldValue("StubRoutines::_jshort_arraycopy", Long.class, "address");
    public final long jintArraycopy = getFieldValue("StubRoutines::_jint_arraycopy", Long.class, "address");
    public final long jlongArraycopy = getFieldValue("StubRoutines::_jlong_arraycopy", Long.class, "address");
    public final long oopArraycopy = getFieldValue("StubRoutines::_oop_arraycopy", Long.class, "address");
    public final long oopArraycopyUninit = getFieldValue("StubRoutines::_oop_arraycopy_uninit", Long.class, "address");
    public final long jbyteDisjointArraycopy = getFieldValue("StubRoutines::_jbyte_disjoint_arraycopy", Long.class, "address");
    public final long jshortDisjointArraycopy = getFieldValue("StubRoutines::_jshort_disjoint_arraycopy", Long.class, "address");
    public final long jintDisjointArraycopy = getFieldValue("StubRoutines::_jint_disjoint_arraycopy", Long.class, "address");
    public final long jlongDisjointArraycopy = getFieldValue("StubRoutines::_jlong_disjoint_arraycopy", Long.class, "address");
    public final long oopDisjointArraycopy = getFieldValue("StubRoutines::_oop_disjoint_arraycopy", Long.class, "address");
    public final long oopDisjointArraycopyUninit = getFieldValue("StubRoutines::_oop_disjoint_arraycopy_uninit", Long.class, "address");
    public final long jbyteAlignedArraycopy = getFieldValue("StubRoutines::_arrayof_jbyte_arraycopy", Long.class, "address");
    public final long jshortAlignedArraycopy = getFieldValue("StubRoutines::_arrayof_jshort_arraycopy", Long.class, "address");
    public final long jintAlignedArraycopy = getFieldValue("StubRoutines::_arrayof_jint_arraycopy", Long.class, "address");
    public final long jlongAlignedArraycopy = getFieldValue("StubRoutines::_arrayof_jlong_arraycopy", Long.class, "address");
    public final long oopAlignedArraycopy = getFieldValue("StubRoutines::_arrayof_oop_arraycopy", Long.class, "address");
    public final long oopAlignedArraycopyUninit = getFieldValue("StubRoutines::_arrayof_oop_arraycopy_uninit", Long.class, "address");
    public final long jbyteAlignedDisjointArraycopy = getFieldValue("StubRoutines::_arrayof_jbyte_disjoint_arraycopy", Long.class, "address");
    public final long jshortAlignedDisjointArraycopy = getFieldValue("StubRoutines::_arrayof_jshort_disjoint_arraycopy", Long.class, "address");
    public final long jintAlignedDisjointArraycopy = getFieldValue("StubRoutines::_arrayof_jint_disjoint_arraycopy", Long.class, "address");
    public final long jlongAlignedDisjointArraycopy = getFieldValue("StubRoutines::_arrayof_jlong_disjoint_arraycopy", Long.class, "address");
    public final long oopAlignedDisjointArraycopy = getFieldValue("StubRoutines::_arrayof_oop_disjoint_arraycopy", Long.class, "address");
    public final long oopAlignedDisjointArraycopyUninit = getFieldValue("StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit", Long.class, "address");
    public final long checkcastArraycopy = getFieldValue("StubRoutines::_checkcast_arraycopy", Long.class, "address");
    public final long checkcastArraycopyUninit = getFieldValue("StubRoutines::_checkcast_arraycopy_uninit", Long.class, "address");
    public final long unsafeArraycopy = getFieldValue("StubRoutines::_unsafe_arraycopy", Long.class, "address");
    public final long genericArraycopy = getFieldValue("StubRoutines::_generic_arraycopy", Long.class, "address");

    public final long newInstanceAddress = getAddress("JVMCIRuntime::new_instance");
    public final long newArrayAddress = getAddress("JVMCIRuntime::new_array");
    public final long newMultiArrayAddress = getAddress("JVMCIRuntime::new_multi_array");
    public final long dynamicNewArrayAddress = getAddress("JVMCIRuntime::dynamic_new_array");
    public final long dynamicNewInstanceAddress = getAddress("JVMCIRuntime::dynamic_new_instance");

    public final long threadIsInterruptedAddress = getAddress("JVMCIRuntime::thread_is_interrupted");
    public final long vmMessageAddress = getAddress("JVMCIRuntime::vm_message");
    public final long identityHashCodeAddress = getAddress("JVMCIRuntime::identity_hash_code");
    public final long exceptionHandlerForPcAddress = getAddress("JVMCIRuntime::exception_handler_for_pc");
    public final long monitorenterAddress = getAddress("JVMCIRuntime::monitorenter");
    public final long monitorexitAddress = getAddress("JVMCIRuntime::monitorexit");
    public final long throwAndPostJvmtiExceptionAddress = getAddress("JVMCIRuntime::throw_and_post_jvmti_exception");
    public final long throwKlassExternalNameExceptionAddress = getAddress("JVMCIRuntime::throw_klass_external_name_exception");
    public final long throwClassCastExceptionAddress = getAddress("JVMCIRuntime::throw_class_cast_exception");
    public final long logPrimitiveAddress = getAddress("JVMCIRuntime::log_primitive");
    public final long logObjectAddress = getAddress("JVMCIRuntime::log_object");
    public final long logPrintfAddress = getAddress("JVMCIRuntime::log_printf");
    public final long vmErrorAddress = getAddress("JVMCIRuntime::vm_error");
    public final long loadAndClearExceptionAddress = getAddress("JVMCIRuntime::load_and_clear_exception");
    public final long writeBarrierPreAddress = getAddress("JVMCIRuntime::write_barrier_pre");
    public final long writeBarrierPostAddress = getAddress("JVMCIRuntime::write_barrier_post");
    public final long validateObject = getAddress("JVMCIRuntime::validate_object");

    public final long testDeoptimizeCallInt = getAddress("JVMCIRuntime::test_deoptimize_call_int");

    public final long registerFinalizerAddress = getAddress("SharedRuntime::register_finalizer");
    public final long exceptionHandlerForReturnAddressAddress = getAddress("SharedRuntime::exception_handler_for_return_address");
    public final long osrMigrationEndAddress = getAddress("SharedRuntime::OSR_migration_end");
    public final long enableStackReservedZoneAddress = getAddress("SharedRuntime::enable_stack_reserved_zone");

    public final long javaTimeMillisAddress = getAddress("os::javaTimeMillis");
    public final long javaTimeNanosAddress = getAddress("os::javaTimeNanos");
    public final long arithmeticSinAddress = getFieldValue("CompilerToVM::Data::dsin", Long.class, "address");
    public final long arithmeticCosAddress = getFieldValue("CompilerToVM::Data::dcos", Long.class, "address");
    public final long arithmeticTanAddress = getFieldValue("CompilerToVM::Data::dtan", Long.class, "address");
    public final long arithmeticExpAddress = getFieldValue("CompilerToVM::Data::dexp", Long.class, "address");
    public final long arithmeticLogAddress = getFieldValue("CompilerToVM::Data::dlog", Long.class, "address");
    public final long arithmeticLog10Address = getFieldValue("CompilerToVM::Data::dlog10", Long.class, "address");
    public final long arithmeticPowAddress = getFieldValue("CompilerToVM::Data::dpow", Long.class, "address");

    public final int jvmciCountersSize = getFlag("JVMCICounterSize", Integer.class);

    public final long deoptimizationFetchUnrollInfo = getAddress("Deoptimization::fetch_unroll_info");
    public final long deoptimizationUncommonTrap = getAddress("Deoptimization::uncommon_trap");
    public final long deoptimizationUnpackFrames = getAddress("Deoptimization::unpack_frames");

    public final int deoptReasonNone = getConstant("Deoptimization::Reason_none", Integer.class);
    public final int deoptReasonNullCheck = getConstant("Deoptimization::Reason_null_check", Integer.class);
    public final int deoptReasonRangeCheck = getConstant("Deoptimization::Reason_range_check", Integer.class);
    public final int deoptReasonClassCheck = getConstant("Deoptimization::Reason_class_check", Integer.class);
    public final int deoptReasonArrayCheck = getConstant("Deoptimization::Reason_array_check", Integer.class);
    public final int deoptReasonUnreached0 = getConstant("Deoptimization::Reason_unreached0", Integer.class);
    public final int deoptReasonTypeCheckInlining = getConstant("Deoptimization::Reason_type_checked_inlining", Integer.class);
    public final int deoptReasonOptimizedTypeCheck = getConstant("Deoptimization::Reason_optimized_type_check", Integer.class);
    public final int deoptReasonNotCompiledExceptionHandler = getConstant("Deoptimization::Reason_not_compiled_exception_handler", Integer.class);
    public final int deoptReasonUnresolved = getConstant("Deoptimization::Reason_unresolved", Integer.class);
    public final int deoptReasonJsrMismatch = getConstant("Deoptimization::Reason_jsr_mismatch", Integer.class);
    public final int deoptReasonDiv0Check = getConstant("Deoptimization::Reason_div0_check", Integer.class);
    public final int deoptReasonConstraint = getConstant("Deoptimization::Reason_constraint", Integer.class);
    public final int deoptReasonLoopLimitCheck = getConstant("Deoptimization::Reason_loop_limit_check", Integer.class);
    public final int deoptReasonAliasing = getConstant("Deoptimization::Reason_aliasing", Integer.class);
    public final int deoptReasonTransferToInterpreter = getConstant("Deoptimization::Reason_transfer_to_interpreter", Integer.class);
    public final int deoptReasonOSROffset = getConstant("Deoptimization::Reason_LIMIT", Integer.class);

    public final int deoptActionNone = getConstant("Deoptimization::Action_none", Integer.class);
    public final int deoptActionMaybeRecompile = getConstant("Deoptimization::Action_maybe_recompile", Integer.class);
    public final int deoptActionReinterpret = getConstant("Deoptimization::Action_reinterpret", Integer.class);
    public final int deoptActionMakeNotEntrant = getConstant("Deoptimization::Action_make_not_entrant", Integer.class);
    public final int deoptActionMakeNotCompilable = getConstant("Deoptimization::Action_make_not_compilable", Integer.class);

    public final int deoptimizationActionBits = getConstant("Deoptimization::_action_bits", Integer.class);
    public final int deoptimizationReasonBits = getConstant("Deoptimization::_reason_bits", Integer.class);
    public final int deoptimizationDebugIdBits = getConstant("Deoptimization::_debug_id_bits", Integer.class);
    public final int deoptimizationActionShift = getConstant("Deoptimization::_action_shift", Integer.class);
    public final int deoptimizationReasonShift = getConstant("Deoptimization::_reason_shift", Integer.class);
    public final int deoptimizationDebugIdShift = getConstant("Deoptimization::_debug_id_shift", Integer.class);

    public final int deoptimizationUnpackDeopt = getConstant("Deoptimization::Unpack_deopt", Integer.class);
    public final int deoptimizationUnpackException = getConstant("Deoptimization::Unpack_exception", Integer.class);
    public final int deoptimizationUnpackUncommonTrap = getConstant("Deoptimization::Unpack_uncommon_trap", Integer.class);
    public final int deoptimizationUnpackReexecute = getConstant("Deoptimization::Unpack_reexecute", Integer.class);

    public final int deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset = getFieldOffset("Deoptimization::UnrollBlock::_size_of_deoptimized_frame", Integer.class, "int");
    public final int deoptimizationUnrollBlockCallerAdjustmentOffset = getFieldOffset("Deoptimization::UnrollBlock::_caller_adjustment", Integer.class, "int");
    public final int deoptimizationUnrollBlockNumberOfFramesOffset = getFieldOffset("Deoptimization::UnrollBlock::_number_of_frames", Integer.class, "int");
    public final int deoptimizationUnrollBlockTotalFrameSizesOffset = getFieldOffset("Deoptimization::UnrollBlock::_total_frame_sizes", Integer.class, "int");
    public final int deoptimizationUnrollBlockUnpackKindOffset = getFieldOffset("Deoptimization::UnrollBlock::_unpack_kind", Integer.class, "int");
    public final int deoptimizationUnrollBlockFrameSizesOffset = getFieldOffset("Deoptimization::UnrollBlock::_frame_sizes", Integer.class, "intptr_t*");
    public final int deoptimizationUnrollBlockFramePcsOffset = getFieldOffset("Deoptimization::UnrollBlock::_frame_pcs", Integer.class, "address*");
    public final int deoptimizationUnrollBlockInitialInfoOffset = getFieldOffset("Deoptimization::UnrollBlock::_initial_info", Integer.class, "intptr_t");

    public final int vmIntrinsicInvokeBasic = getConstant("vmIntrinsics::_invokeBasic", Integer.class);
    public final int vmIntrinsicLinkToVirtual = getConstant("vmIntrinsics::_linkToVirtual", Integer.class);
    public final int vmIntrinsicLinkToStatic = getConstant("vmIntrinsics::_linkToStatic", Integer.class);
    public final int vmIntrinsicLinkToSpecial = getConstant("vmIntrinsics::_linkToSpecial", Integer.class);
    public final int vmIntrinsicLinkToInterface = getConstant("vmIntrinsics::_linkToInterface", Integer.class);

    public final int codeInstallResultOk = getConstant("JVMCIEnv::ok", Integer.class);
    public final int codeInstallResultDependenciesFailed = getConstant("JVMCIEnv::dependencies_failed", Integer.class);
    public final int codeInstallResultDependenciesInvalid = getConstant("JVMCIEnv::dependencies_invalid", Integer.class);
    public final int codeInstallResultCacheFull = getConstant("JVMCIEnv::cache_full", Integer.class);
    public final int codeInstallResultCodeTooLarge = getConstant("JVMCIEnv::code_too_large", Integer.class);

    public String getCodeInstallResultDescription(int codeInstallResult) {
        if (codeInstallResult == codeInstallResultOk) {
            return "ok";
        }
        if (codeInstallResult == codeInstallResultDependenciesFailed) {
            return "dependencies failed";
        }
        if (codeInstallResult == codeInstallResultDependenciesInvalid) {
            return "dependencies invalid";
        }
        if (codeInstallResult == codeInstallResultCacheFull) {
            return "code cache is full";
        }
        if (codeInstallResult == codeInstallResultCodeTooLarge) {
            return "code is too large";
        }
        assert false : codeInstallResult;
        return "unknown";
    }

    // Checkstyle: stop
    public final int MARKID_VERIFIED_ENTRY = getConstant("CodeInstaller::VERIFIED_ENTRY", Integer.class);
    public final int MARKID_UNVERIFIED_ENTRY = getConstant("CodeInstaller::UNVERIFIED_ENTRY", Integer.class);
    public final int MARKID_OSR_ENTRY = getConstant("CodeInstaller::OSR_ENTRY", Integer.class);
    public final int MARKID_EXCEPTION_HANDLER_ENTRY = getConstant("CodeInstaller::EXCEPTION_HANDLER_ENTRY", Integer.class);
    public final int MARKID_DEOPT_HANDLER_ENTRY = getConstant("CodeInstaller::DEOPT_HANDLER_ENTRY", Integer.class);
    public final int MARKID_INVOKEINTERFACE = getConstant("CodeInstaller::INVOKEINTERFACE", Integer.class);
    public final int MARKID_INVOKEVIRTUAL = getConstant("CodeInstaller::INVOKEVIRTUAL", Integer.class);
    public final int MARKID_INVOKESTATIC = getConstant("CodeInstaller::INVOKESTATIC", Integer.class);
    public final int MARKID_INVOKESPECIAL = getConstant("CodeInstaller::INVOKESPECIAL", Integer.class);
    public final int MARKID_INLINE_INVOKE = getConstant("CodeInstaller::INLINE_INVOKE", Integer.class);
    public final int MARKID_POLL_NEAR = getConstant("CodeInstaller::POLL_NEAR", Integer.class);
    public final int MARKID_POLL_RETURN_NEAR = getConstant("CodeInstaller::POLL_RETURN_NEAR", Integer.class);
    public final int MARKID_POLL_FAR = getConstant("CodeInstaller::POLL_FAR", Integer.class);
    public final int MARKID_POLL_RETURN_FAR = getConstant("CodeInstaller::POLL_RETURN_FAR", Integer.class);
    public final int MARKID_CARD_TABLE_SHIFT = getConstant("CodeInstaller::CARD_TABLE_SHIFT", Integer.class);
    public final int MARKID_CARD_TABLE_ADDRESS = getConstant("CodeInstaller::CARD_TABLE_ADDRESS", Integer.class);
    public final int MARKID_HEAP_TOP_ADDRESS = getConstant("CodeInstaller::HEAP_TOP_ADDRESS", Integer.class);
    public final int MARKID_HEAP_END_ADDRESS = getConstant("CodeInstaller::HEAP_END_ADDRESS", Integer.class);
    public final int MARKID_NARROW_KLASS_BASE_ADDRESS = getConstant("CodeInstaller::NARROW_KLASS_BASE_ADDRESS", Integer.class);
    public final int MARKID_CRC_TABLE_ADDRESS = getConstant("CodeInstaller::CRC_TABLE_ADDRESS", Integer.class);
    public final int MARKID_INVOKE_INVALID = getConstant("CodeInstaller::INVOKE_INVALID", Integer.class);

    public final int bitDataExceptionSeenFlag = getConstant("BitData::exception_seen_flag", Integer.class);
    public final int bitDataNullSeenFlag = getConstant("BitData::null_seen_flag", Integer.class);
    public final int methodDataCountOffset = getConstant("CounterData::count_off", Integer.class);
    public final int jumpDataTakenOffset = getConstant("JumpData::taken_off_set", Integer.class);
    public final int jumpDataDisplacementOffset = getConstant("JumpData::displacement_off_set", Integer.class);
    public final int receiverTypeDataNonprofiledCountOffset = getConstant("ReceiverTypeData::nonprofiled_count_off_set", Integer.class);
    public final int receiverTypeDataReceiverTypeRowCellCount = getConstant("ReceiverTypeData::receiver_type_row_cell_count", Integer.class);
    public final int receiverTypeDataReceiver0Offset = getConstant("ReceiverTypeData::receiver0_offset", Integer.class);
    public final int receiverTypeDataCount0Offset = getConstant("ReceiverTypeData::count0_offset", Integer.class);
    public final int branchDataNotTakenOffset = getConstant("BranchData::not_taken_off_set", Integer.class);
    public final int arrayDataArrayLenOffset = getConstant("ArrayData::array_len_off_set", Integer.class);
    public final int arrayDataArrayStartOffset = getConstant("ArrayData::array_start_off_set", Integer.class);
    public final int multiBranchDataPerCaseCellCount = getConstant("MultiBranchData::per_case_cell_count", Integer.class);

    // Checkstyle: resume

    private boolean check() {
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
}
