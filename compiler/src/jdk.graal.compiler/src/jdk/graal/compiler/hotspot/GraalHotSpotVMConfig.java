/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Used to access native configuration details.
 */
public class GraalHotSpotVMConfig extends GraalHotSpotVMConfigAccess {

    /**
     * Sentinel value to use for an {@linkplain InjectedParameter injected}
     * {@link GraalHotSpotVMConfig} parameter to a {@linkplain Fold foldable} method.
     */
    public static final GraalHotSpotVMConfig INJECTED_VMCONFIG = null;

    /**
     * Sentinel value to use for an {@linkplain InjectedParameter injected}
     * {@link MetaAccessProvider} parameter to a {@linkplain Fold foldable} method.
     */
    public static final MetaAccessProvider INJECTED_METAACCESS = null;

    /**
     * Sentinel value to use for an {@linkplain InjectedParameter injected} {@link OptionValues}
     * parameter to a {@linkplain Fold foldable} method.
     */
    public static final OptionValues INJECTED_OPTIONVALUES = null;

    GraalHotSpotVMConfig(HotSpotVMConfigStore store) {
        super(store);

        int logMinObjAlignment = logMinObjAlignment();
        assert narrowOopShift <= logMinObjAlignment : Assertions.errorMessageContext("narrowOopShift", narrowOopShift, "logMinObjAlignment", logMinObjAlignment);
        oopEncoding = new CompressEncoding(narrowOopBase, narrowOopShift);
        klassEncoding = new CompressEncoding(narrowKlassBase, narrowKlassShift);

        assert check();
        reportErrors();
        populateMarkConstants();
    }

    private final CompressEncoding oopEncoding;
    private final CompressEncoding klassEncoding;
    private final String markWord = "markWord"; // JDK-8229258

    public CompressEncoding getOopEncoding() {
        return oopEncoding;
    }

    public CompressEncoding getKlassEncoding() {
        return klassEncoding;
    }

    public boolean useSerialGC() {
        return gc == HotSpotGraalRuntime.HotSpotGC.Serial;
    }

    public boolean useG1GC() {
        return gc == HotSpotGraalRuntime.HotSpotGC.G1;
    }

    public final HotSpotGraalRuntime.HotSpotGC gc = getSelectedGC();

    private HotSpotGraalRuntime.HotSpotGC getSelectedGC() throws GraalError {
        HotSpotGraalRuntime.HotSpotGC selected = null;
        for (HotSpotGraalRuntime.HotSpotGC value : HotSpotGraalRuntime.HotSpotGC.values()) {
            if (value.isSelected(this)) {
                if (!value.supported) {
                    throw new GraalError(value.name() + " garbage collector is not supported by Graal");
                }
                if (selected != null) {
                    throw new GraalError("Multiple garbage collectors selected: " + selected + " " + value);
                }
                selected = value;
            }
        }
        if (selected == null) {
            // Exactly one GC flag is guaranteed to be selected.
            selected = HotSpotGraalRuntime.HotSpotGC.Serial;
        }
        return selected;
    }

    /**
     * Determines if {@code -Xcomp} (or the equivalent thereof) was specified as a JVM argument.
     */
    public final boolean xcompMode = !access.getFlag("UseInterpreter", Boolean.class);

    public final boolean ropProtection = access.getFieldValue("VM_Version::_rop_protection", Boolean.class, "bool", false);

    public final boolean cAssertions = getConstant("ASSERT", Boolean.class);

    public final int codeEntryAlignment = getFlag("CodeEntryAlignment", Integer.class);

    public final boolean verifyOops = getFlag("VerifyOops", Boolean.class);
    public final boolean dontCompileHugeMethods = getFlag("DontCompileHugeMethods", Boolean.class);
    public final int hugeMethodLimit = getFlag("HugeMethodLimit", Integer.class);
    public final boolean printInlining = getFlag("PrintInlining", Boolean.class);
    public final boolean inline = getFlag("Inline", Boolean.class);

    // There are 3 available locking modes:
    // LM_MONITOR uses only heavy monitors for locking;
    // LM_LEGACY uses stack-locking, with monitors as 2nd tier;
    // LM_LIGHTWEIGHT uses thread-local space for storing locked objects. This avoids the overload
    // of the mark word.
    public final int lockingMode = getFlag("LockingMode", Integer.class);

    public final int lockingModeStack = getConstant("LockingMode::LM_LEGACY", Integer.class, 1, JDK >= 22);
    public final int lockingModeLightweight = getConstant("LockingMode::LM_LIGHTWEIGHT", Integer.class, 2, JDK >= 22);

    public final boolean foldStableValues = getFlag("FoldStableValues", Boolean.class);
    public final int maxVectorSize = getFlag("MaxVectorSize", Integer.class);

    public final boolean verifyBeforeGC = getFlag("VerifyBeforeGC", Boolean.class);
    public final boolean verifyAfterGC = getFlag("VerifyAfterGC", Boolean.class);

    public final boolean useTLAB = getFlag("UseTLAB", Boolean.class);
    public final boolean usePopCountInstruction = getFlag("UsePopCountInstruction", Boolean.class);
    public final boolean useUnalignedAccesses = getFlag("UseUnalignedAccesses", Boolean.class);
    public final boolean useFMAIntrinsics = getFlag("UseFMA", Boolean.class);
    public final boolean useVectorizedMismatchIntrinsic = getFlag("UseVectorizedMismatchIntrinsic", Boolean.class);
    public final boolean useCharacterCompareIntrinsics = getFlag("UseCharacterCompareIntrinsics", Boolean.class);
    public final int useAVX3Threshold = getFlag("AVX3Threshold", Integer.class, 4096, osArch.equals("amd64"));
    public final boolean alwaysSafeConstructors = getFlag("AlwaysSafeConstructors", Boolean.class);

    public final String onSpinWaitInst = getFlag("OnSpinWaitInst", String.class, "none", osArch.equals("aarch64"));
    public final int onSpinWaitInstCount = getFlag("OnSpinWaitInstCount", Integer.class, 0, osArch.equals("aarch64"));

    private final boolean preserveFramePointer = getFlag("PreserveFramePointer", Boolean.class);

    // Force standard frame prolog for stubs so that stack walking works correctly. This avoids
    // the problem where an hs_err stack trace is missing the Java caller frame.
    public boolean preserveFramePointer(boolean isStub) {
        return preserveFramePointer || ropProtection || isStub;
    }

    public final int diagnoseSyncOnValueBasedClasses = getFlag("DiagnoseSyncOnValueBasedClasses", Integer.class);

    public boolean inlineNotify() {
        return notifyAddress != 0;
    }

    public boolean inlineNotifyAll() {
        return notifyAllAddress != 0;
    }

    public final int allocatePrefetchStyle = getFlag("AllocatePrefetchStyle", Integer.class);
    public final int allocatePrefetchInstr = getFlag("AllocatePrefetchInstr", Integer.class);
    public final int allocatePrefetchLines = getFlag("AllocatePrefetchLines", Integer.class);
    public final int allocateInstancePrefetchLines = getFlag("AllocateInstancePrefetchLines", Integer.class);
    public final int allocatePrefetchStepSize = getFlag("AllocatePrefetchStepSize", Integer.class);
    public final int allocatePrefetchDistance = getFlag("AllocatePrefetchDistance", Integer.class);

    private final long universeCollectedHeap = getFieldValue("CompilerToVM::Data::Universe_collectedHeap", Long.class, "CollectedHeap*");
    private final int collectedHeapTotalCollectionsOffset = getFieldOffset("CollectedHeap::_total_collections", Integer.class, "unsigned int");

    public long gcTotalCollectionsAddress() {
        return universeCollectedHeap + collectedHeapTotalCollectionsOffset;
    }

    public final boolean useDeferredInitBarriers = getFlag("ReduceInitialCardMarks", Boolean.class);

    // Compressed Oops related values.
    public final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);
    public final boolean useCompressedClassPointers = getFlag("UseCompressedClassPointers", Boolean.class);
    // JDK-8305895 allows storing the compressed class pointer in the upper 22 bits of the mark
    // word. This runtime optimization is guarded by the flag UseCompactObjectHeaders. It depends
    // on compressed class pointers, meaning that if useCompactObjectHeaders is true,
    // useCompressedClassPointers is certainly true.
    public final boolean useCompactObjectHeaders = getFlag("UseCompactObjectHeaders", Boolean.class, false, JDK >= 24);

    public final long narrowOopBase = getFieldValue("CompilerToVM::Data::Universe_narrow_oop_base", Long.class, "address");
    public final int narrowOopShift = getFieldValue("CompilerToVM::Data::Universe_narrow_oop_shift", Integer.class, "int");
    public final int objectAlignment = getFlag("ObjectAlignmentInBytes", Integer.class);

    public final int logMinObjAlignment() {
        return (int) (Math.log(objectAlignment) / Math.log(2));
    }

    public final int narrowKlassSize = getFieldValue("CompilerToVM::Data::sizeof_narrowKlass", Integer.class, "int");
    public final long narrowKlassBase = getFieldValue("CompilerToVM::Data::Universe_narrow_klass_base", Long.class, "address");
    public final int narrowKlassShift = getFieldValue("CompilerToVM::Data::Universe_narrow_klass_shift", Integer.class, "int");

    public final int stackShadowPages = getFlag("StackShadowPages", Integer.class);
    public final int vmPageSize = getFieldValue("CompilerToVM::Data::vm_page_size", Integer.class, "size_t");

    public final int softwarePrefetchHintDistance = getFlag("SoftwarePrefetchHintDistance", Integer.class, -1, "aarch64".equals(osArch));

    public final int markOffset = getFieldOffset("oopDesc::_mark", Integer.class, markWord);
    public final int hubOffset = getFieldOffset("oopDesc::_metadata._klass", Integer.class, "Klass*");

    public final boolean useSecondarySupersCache = getFlag("UseSecondarySupersCache", Boolean.class, true, JDK >= 23);
    public final boolean useSecondarySupersTable = getFlag("UseSecondarySupersTable", Boolean.class, true, JDK >= 23);

    public final int superCheckOffsetOffset = getFieldOffset("Klass::_super_check_offset", Integer.class, "juint");
    public final int secondarySuperCacheOffset = getFieldOffset("Klass::_secondary_super_cache", Integer.class, "Klass*");
    public final int secondarySupersOffset = getFieldOffset("Klass::_secondary_supers", Integer.class, "Array<Klass*>*");
    public final int klassHashSlotOffset = getFieldOffset("Klass::_hash_slot", Integer.class, "uint8_t", 0, JDK >= 23);
    public final int klassBitmapOffset = getFieldOffset("Klass::_secondary_supers_bitmap", Integer.class, "uintx", 0, JDK >= 24);

    // JDK-8186777
    public final int classMirrorOffset = getFieldOffset("Klass::_java_mirror", Integer.class, "OopHandle");

    public final int klassSuperKlassOffset = getFieldOffset("Klass::_super", Integer.class, "Klass*");
    public final int klassModifierFlagsOffset = getFieldOffset("Klass::_modifier_flags", Integer.class, JDK == 21? "jint" : "u2");
    public final int klassAccessFlagsOffset = getFieldOffset("Klass::_access_flags", Integer.class, "AccessFlags");
    public final int klassMiscFlagsOffset = getFieldOffset("Klass::_misc_flags._flags", Integer.class, "u1", 0, JDK >= 24);
    public final int klassLayoutHelperOffset = getFieldOffset("Klass::_layout_helper", Integer.class, "jint");

    public final int klassLayoutHelperNeutralValue = getConstant("Klass::_lh_neutral_value", Integer.class);
    public final int layoutHelperLog2ElementSizeShift = getConstant("Klass::_lh_log2_element_size_shift", Integer.class);
    public final int layoutHelperLog2ElementSizeMask = getConstant("Klass::_lh_log2_element_size_mask", Integer.class);
    public final int layoutHelperHeaderSizeShift = getConstant("Klass::_lh_header_size_shift", Integer.class);
    public final int layoutHelperHeaderSizeMask = getConstant("Klass::_lh_header_size_mask", Integer.class);

    public final int instanceKlassInitStateOffset = getFieldOffset("InstanceKlass::_init_state", Integer.class, "InstanceKlass::ClassState");
    public final int instanceKlassInitThreadOffset = getFieldOffset("InstanceKlass::_init_thread", Integer.class, "JavaThread*");

    public final int instanceKlassStateBeingInitialized = getConstant("InstanceKlass::being_initialized", Integer.class);
    public final int instanceKlassStateFullyInitialized = getConstant("InstanceKlass::fully_initialized", Integer.class);

    public final int arrayOopDescSize = getFieldValue("CompilerToVM::Data::sizeof_arrayOopDesc", Integer.class, "int");

    public final int arrayLengthOffsetInBytes = getFieldValue("CompilerToVM::Data::arrayOopDesc_length_offset_in_bytes", Integer.class, "int", -1, JDK >= 24);

    /**
     * The offset of the array length word in an array object's header.
     * <p>
     * See {@code arrayOopDesc::length_offset_in_bytes()}.
     */
    public final int arrayOopDescLengthOffset() {
        if (JDK >= 24) {
            return arrayLengthOffsetInBytes;
        }
        return useCompressedClassPointers ? hubOffset + narrowKlassSize : arrayOopDescSize;
    }

    public final int metaspaceArrayLengthOffset = getFieldOffset("Array<Klass*>::_length", Integer.class, "int");
    public final int metaspaceArrayBaseOffset = getFieldOffset("Array<Klass*>::_data[0]", Integer.class, "Klass*");

    public final int arrayClassElementOffset = getFieldOffset("ObjArrayKlass::_element_klass", Integer.class, "Klass*");

    public final int jvmAccWrittenFlags = getConstant("JVM_ACC_WRITTEN_FLAGS", Integer.class, -1, JDK == 21);
    public final int jvmAccIsHiddenClass = JDK >= 24 ? getConstant("KlassFlags::_misc_is_hidden_class", Integer.class) : getConstant("JVM_ACC_IS_HIDDEN_CLASS", Integer.class);
    public final int jvmAccIsValueBasedClass = JDK >= 24 ? getConstant("KlassFlags::_misc_is_value_based_class", Integer.class) : getConstant("JVM_ACC_IS_VALUE_BASED_CLASS", Integer.class);
    public final int jvmAccHasFinalizer = JDK >= 24 ? getConstant("KlassFlags::_misc_has_finalizer", Integer.class) : getConstant("JVM_ACC_HAS_FINALIZER", Integer.class);

    public final int jvmciCompileStateCanPostOnExceptionsOffset = getFieldOffset("JVMCICompileState::_jvmti_can_post_on_exceptions", Integer.class, "jbyte");
    public final int jvmciCompileStateCanPopFrameOffset = getFieldOffset("JVMCICompileState::_jvmti_can_pop_frame", Integer.class, "jbyte");
    public final int jvmciCompileStateCanAccessLocalVariablesOffset = getFieldOffset("JVMCICompileState::_jvmti_can_access_local_variables", Integer.class, "jbyte");

    public final int threadTlabOffset = getFieldOffset("Thread::_tlab", Integer.class, "ThreadLocalAllocBuffer");
    public final int javaThreadAnchorOffset = getFieldOffset("JavaThread::_anchor", Integer.class, "JavaFrameAnchor");
    public final int javaThreadShouldPostOnExceptionsFlagOffset = getFieldOffset("JavaThread::_should_post_on_exceptions_flag", Integer.class, "int");

    /**
     * The offset of the {@code JavaThread} field containing the reference to the current thread.
     * With virtual threads, the value returned by Thread.currentThread() is the new _vthread field.
     * The _threadObj field is still present but refers to the carrier thread. The two fields have
     * the same value in non-virtual threads.
     */
    public final int threadCurrentThreadObjectOffset = getFieldOffset("JavaThread::_vthread", Integer.class, "OopHandle");
    public final boolean doJVMTIVirtualThreadTransitions = getFlag("DoJVMTIVirtualThreadTransitions", Boolean.class);

    public final int threadCarrierThreadObjectOffset = getFieldOffset("JavaThread::_threadObj", Integer.class, "OopHandle");
    public final int threadScopedValueCacheOffset = getFieldOffset("JavaThread::_scopedValueCache", Integer.class, "OopHandle");

    public final int javaThreadMonitorOwnerIDOffset = getFieldOffset("JavaThread::_monitor_owner_id", Integer.class, "int64_t", -1, JDK > 21);

    public final int threadIsInVTMSTransitionOffset = getFieldOffset("JavaThread::_is_in_VTMS_transition", Integer.class, "bool");
    public final int threadIsInTmpVTMSTransitionOffset = getFieldOffset("JavaThread::_is_in_tmp_VTMS_transition", Integer.class, "bool", -1, JDK == 21);
    public final int threadIsDisableSuspendOffset = getFieldOffset("JavaThread::_is_disable_suspend", Integer.class, "bool", -1, JDK >= 22);

    public final int javaLangThreadJFREpochOffset = getFieldValue("java_lang_Thread::_jfr_epoch_offset", Integer.class, "int");
    public final int javaLangThreadTIDOffset = getFieldValue("java_lang_Thread::_tid_offset", Integer.class, "int");

    public final int javaLangThreadIsInVTMSTransitonOffset = getFieldValue("java_lang_Thread::_jvmti_is_in_VTMS_transition_offset", Integer.class, "int");
    public final long virtualThreadVTMSNotifyJvmtiEvents = getFieldAddress("JvmtiVTMSTransitionDisabler::_VTMS_notify_jvmti_events", "bool");

    public final int threadJFRThreadLocalOffset = getFieldOffset("Thread::_jfr_thread_local", Integer.class, "JfrThreadLocal");

    public final int jfrThreadLocalVthreadIDOffset = getFieldOffset("JfrThreadLocal::_vthread_id", Integer.class, "traceid");
    public final int jfrThreadLocalVthreadEpochOffset = getFieldOffset("JfrThreadLocal::_vthread_epoch", Integer.class, "u2");
    public final int jfrThreadLocalVthreadExcludedOffset = getFieldOffset("JfrThreadLocal::_vthread_excluded", Integer.class, "bool");
    public final int jfrThreadLocalVthreadOffset = getFieldOffset("JfrThreadLocal::_vthread", Integer.class, "bool");

    public final int osThreadOffset = getFieldOffset("JavaThread::_osthread", Integer.class, "OSThread*");
    public final int threadObjectResultOffset = getFieldOffset("JavaThread::_vm_result", Integer.class, "oop");
    public final int jvmciCountersThreadOffset = getFieldOffset("JavaThread::_jvmci_counters", Integer.class, "jlong*");

    public final int jvmciReserved0Offset = getFieldOffset("JavaThread::_jvmci_reserved0", Integer.class, "jlong");
    public final int jvmciReservedReference0Offset = getFieldOffset("JavaThread::_jvmci_reserved_oop0", Integer.class, "oop");

    public final int doingUnsafeAccessOffset = getFieldOffset("JavaThread::_doing_unsafe_access", Integer.class, "bool");
    public final int javaThreadReservedStackActivationOffset = getFieldOffset("JavaThread::_stack_overflow_state._reserved_stack_activation", Integer.class, "address"); // JDK-8253717
    public final int jniEnvironmentOffset = getFieldOffset("JavaThread::_jni_environment", Integer.class, "JNIEnv");

    public boolean requiresReservedStackCheck(List<ResolvedJavaMethod> methods) {
        if (enableStackReservedZoneAddress != 0 && methods != null) {
            for (ResolvedJavaMethod method : methods) {
                if (method instanceof HotSpotResolvedJavaMethod && ((HotSpotResolvedJavaMethod) method).hasReservedStackAccess()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This field is used to pass exception objects into and out of the runtime system during
     * exception handling for compiled code.
     */
    public final int threadExceptionOopOffset = getFieldOffset("JavaThread::_exception_oop", Integer.class, "oop");
    public final int threadExceptionPcOffset = getFieldOffset("JavaThread::_exception_pc", Integer.class, "address");
    public final int pendingExceptionOffset = getFieldOffset("ThreadShadow::_pending_exception", Integer.class, "oop");

    public final int pendingDeoptimizationOffset = getFieldOffset("JavaThread::_pending_deoptimization", Integer.class, "int");
    public final int pendingTransferToInterpreterOffset = getFieldOffset("JavaThread::_pending_transfer_to_interpreter", Integer.class, "bool");

    private final int javaFrameAnchorLastJavaSpOffset = getFieldOffset("JavaFrameAnchor::_last_Java_sp", Integer.class, "intptr_t*");
    private final int javaFrameAnchorLastJavaPcOffset = getFieldOffset("JavaFrameAnchor::_last_Java_pc", Integer.class, "address");

    public final int pendingFailedSpeculationOffset = getFieldOffset("JavaThread::_pending_failed_speculation", Integer.class, "jlong");

    public int threadLastJavaSpOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaSpOffset;
    }

    public int threadLastJavaPcOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaPcOffset;
    }

    public int threadLastJavaFpOffset() {
        assert osArch.equals("aarch64") || osArch.equals("amd64");
        return javaThreadAnchorOffset + getFieldOffset("JavaFrameAnchor::_last_Java_fp", Integer.class, "intptr_t*");
    }

    public final int frameInterpreterFrameSenderSpOffset = getConstant("frame::interpreter_frame_sender_sp_offset", Integer.class, 0, osArch.equals("amd64"));
    public final int frameInterpreterFrameLastSpOffset = getConstant("frame::interpreter_frame_last_sp_offset", Integer.class, 0, osArch.equals("amd64"));

    public final long markWordLockMaskInPlace = getConstant("markWord::lock_mask_in_place", Long.class);
    public final long markWordHashMask = getConstant("markWord::hash_mask", Long.class);

    public final long markWordNoHashInPlace = getConstant("markWord::no_hash_in_place", Long.class);
    public final long markWordNoLockInPlace = getConstant("markWord::no_lock_in_place", Long.class);

    // Mark word right shift to get identity hash code.
    public final int markWordHashCodeShift = getConstant("markWord::hash_shift", Integer.class);
    // Mark word right shift to get compressed klass pointer
    public final int markWordKlassShift = getConstant("markWord::klass_shift", Integer.class, 0, JDK >= 24);

    // The following three constants are declared as 64 bits uintptr_t, but known to be 32 bits
    public final int unlockedValue = getConstant("markWord::unlocked_value", Integer.class);
    public final int monitorValue = getConstant("markWord::monitor_value", Integer.class);
    public final int ageMaskInPlace = getConstant("markWord::age_mask_in_place", Integer.class);
    public final int unusedMark = getConstant("markWord::marked_value", Integer.class, 3, JDK > 21);
    // Identity hash code value when uninitialized.
    public final int uninitializedIdentityHashCodeValue = getConstant("markWord::no_hash", Integer.class);

    // This field has no type in vmStructs.cpp
    public final int objectMonitorOwner = getFieldOffset("ObjectMonitor::_owner", Integer.class, JDK > 21 ? "int64_t" : null);
    public final int objectMonitorRecursions = getFieldOffset("ObjectMonitor::_recursions", Integer.class, "intptr_t");
    public final int objectMonitorCxq = getFieldOffset("ObjectMonitor::_cxq", Integer.class, "ObjectWaiter*");
    public final int objectMonitorEntryList = getFieldOffset("ObjectMonitor::_EntryList", Integer.class, "ObjectWaiter*");
    public final int objectMonitorSucc = getFieldOffset("ObjectMonitor::_succ", Integer.class, JDK > 21 ? "int64_t" : "JavaThread*");

    public final int contEntry = getFieldOffset("JavaThread::_cont_entry", Integer.class, "ContinuationEntry*", -1, JDK >= 24);
    public final int pinCount = getFieldOffset("ContinuationEntry::_pin_count", Integer.class, "uint32_t", -1, JDK >= 24);

    public final int methodCompiledEntryOffset = getFieldOffset("Method::_from_compiled_entry", Integer.class, "address");

    public final int compilationLevelFullOptimization = getConstant("CompLevel_full_optimization", Integer.class);

    public final int heapWordSize = getConstant("HeapWordSize", Integer.class);

    /**
     * Bit pattern that represents a non-oop. Neither the high bits nor the low bits of this value
     * are allowed to look like (respectively) the high or low bits of a real oop.
     */
    public final long nonOopBits = getFieldValue("CompilerToVM::Data::Universe_non_oop_bits", Long.class, "void*");

    public final long verifyOopCounterAddress = getFieldAddress("StubRoutines::_verify_oop_count", "jint");
    public final long verifyOopMask = getFieldValue("CompilerToVM::Data::Universe_verify_oop_mask", Long.class, "uintptr_t");
    public final long verifyOopBits = getFieldValue("CompilerToVM::Data::Universe_verify_oop_bits", Long.class, "uintptr_t");

    public final int logOfHRGrainBytes = getFieldValue(JDK >= 23 ? "G1HeapRegion::LogOfHRGrainBytes" : "HeapRegion::LogOfHRGrainBytes", Integer.class, JDK >= 22 ? "uint" : "int");

    public final int cardtableShift = getFieldValue("CompilerToVM::Data::cardtable_shift", Integer.class, "int");
    public final long cardtableStartAddress = getFieldValue("CompilerToVM::Data::cardtable_start_address", Long.class, "CardTable::CardValue*");

    /**
     * This is the largest stack offset encodeable in an OopMapValue. Offsets larger than this will
     * throw an exception during code installation.
     */
    public final int maxOopMapStackOffset = getFieldValue("CompilerToVM::Data::_max_oop_map_stack_offset", Integer.class, "int");

    /**
     * Address of {@code _should_notify_object_alloc}, which, if non-zero, modifies the intrinsic
     * for {@code Unsafe.allocateInstance} to deopt when
     * {@code *shouldNotifyObjectAllocAddress != 0}.
     */
    public final long shouldNotifyObjectAllocAddress = getFieldValue("CompilerToVM::Data::_should_notify_object_alloc", Long.class, "int*", 0L, JDK >= 23);

    // G1 Collector Related Values.
    public final byte dirtyCardValue = getConstant("CardTable::dirty_card", Byte.class);
    public final byte g1YoungCardValue = getConstant("G1CardTable::g1_young_gen", Byte.class);
    public final int g1SATBQueueMarkingActiveOffset = getConstant("G1ThreadLocalData::satb_mark_queue_active_offset", Integer.class);
    public final int g1SATBQueueIndexOffset = getConstant("G1ThreadLocalData::satb_mark_queue_index_offset", Integer.class);
    public final int g1SATBQueueBufferOffset = getConstant("G1ThreadLocalData::satb_mark_queue_buffer_offset", Integer.class);
    public final int g1CardQueueIndexOffset = getConstant("G1ThreadLocalData::dirty_card_queue_index_offset", Integer.class);
    public final int g1CardQueueBufferOffset = getConstant("G1ThreadLocalData::dirty_card_queue_buffer_offset", Integer.class);

    public final int klassOffset = getFieldValue("java_lang_Class::_klass_offset", Integer.class, "int");
    public final int arrayKlassOffset = getFieldValue("java_lang_Class::_array_klass_offset", Integer.class, "int");

    public final int basicLockSize = getFieldValue("CompilerToVM::Data::sizeof_BasicLock", Integer.class, "int");
    public final int basicLockMetadataOffset = JDK >= 24 ? getFieldOffset("BasicLock::_metadata", Integer.class, "uintptr_t") : getFieldOffset("BasicLock::_displaced_header", Integer.class, markWord);

    public final boolean useObjectMonitorTable = getFlag("UseObjectMonitorTable", Boolean.class, false, JDK >= 24);

    // JDK-8253180 & JDK-8265932
    public final int threadPollDataOffset = JDK >= 24 ? getFieldOffset("Thread::_poll_data", Integer.class, "SafepointMechanism::ThreadData")
                    : getFieldOffset("JavaThread::_poll_data", Integer.class, "SafepointMechanism::ThreadData");
    public final int threadPollingPageOffset = threadPollDataOffset + getFieldOffset("SafepointMechanism::ThreadData::_polling_page", Integer.class, "volatile uintptr_t");
    public final int threadPollingWordOffset = threadPollDataOffset + getFieldOffset("SafepointMechanism::ThreadData::_polling_word", Integer.class, "volatile uintptr_t");
    public final int savedExceptionPCOffset = getFieldOffset("JavaThread::_saved_exception_pc", Integer.class, "address");

    private final int threadLocalAllocBufferEndOffset = getFieldOffset("ThreadLocalAllocBuffer::_end", Integer.class, "HeapWord*");
    private final int threadLocalAllocBufferTopOffset = getFieldOffset("ThreadLocalAllocBuffer::_top", Integer.class, "HeapWord*");

    public int threadTlabEndOffset() {
        return threadTlabOffset + threadLocalAllocBufferEndOffset;
    }

    public int threadTlabTopOffset() {
        return threadTlabOffset + threadLocalAllocBufferTopOffset;
    }

    public final int zvaLength = access.getFieldValue("VM_Version::_zva_length", Integer.class, "int", 0);

    public final long inlineCacheMissStub = getFieldValue("CompilerToVM::Data::SharedRuntime_ic_miss_stub", Long.class, "address");

    public final long deoptBlobUnpack = getFieldValue("CompilerToVM::Data::SharedRuntime_deopt_blob_unpack", Long.class, "address");
    public final long deoptBlobUnpackWithExceptionInTLS = getFieldValue("CompilerToVM::Data::SharedRuntime_deopt_blob_unpack_with_exception_in_tls", Long.class, "address");
    public final long pollingPageReturnHandler = getFieldValue("CompilerToVM::Data::SharedRuntime_polling_page_return_handler", Long.class, "address");
    public final long deoptBlobUncommonTrap = getFieldValue("CompilerToVM::Data::SharedRuntime_deopt_blob_uncommon_trap", Long.class, "address");

    public final long updateBytesCRC32Stub = getFieldValue("StubRoutines::_updateBytesCRC32", Long.class, "address");
    public final long crcTableAddress = getFieldValue("StubRoutines::_crc_table_adr", Long.class, "address");

    public final long md5ImplCompressMultiBlock = getFieldValue("StubRoutines::_md5_implCompressMB", Long.class, "address");
    public final long sha1ImplCompressMultiBlock = getFieldValue("StubRoutines::_sha1_implCompressMB", Long.class, "address");
    public final long sha256ImplCompressMultiBlock = getFieldValue("StubRoutines::_sha256_implCompressMB", Long.class, "address");
    public final long sha512ImplCompressMultiBlock = getFieldValue("StubRoutines::_sha512_implCompressMB", Long.class, "address");
    public final long sha3ImplCompressMultiBlock = getFieldValue("StubRoutines::_sha3_implCompressMB", Long.class, "address");

    public final long base64EncodeBlock = getFieldValue("StubRoutines::_base64_encodeBlock", Long.class, "address");
    public final long base64DecodeBlock = getFieldValue("StubRoutines::_base64_decodeBlock", Long.class, "address");

    public final long updateBytesCRC32C = getFieldValue("StubRoutines::_updateBytesCRC32C", Long.class, "address");
    public final long updateBytesAdler32 = getFieldValue("StubRoutines::_updateBytesAdler32", Long.class, "address");
    public final long montgomeryMultiply = getFieldValue("StubRoutines::_montgomeryMultiply", Long.class, "address");
    public final long montgomerySquare = getFieldValue("StubRoutines::_montgomerySquare", Long.class, "address");

    public final long bigIntegerLeftShiftWorker = getFieldValue("StubRoutines::_bigIntegerLeftShiftWorker", Long.class, "address");
    public final long bigIntegerRightShiftWorker = getFieldValue("StubRoutines::_bigIntegerRightShiftWorker", Long.class, "address");

    public final long electronicCodeBookEncrypt = getFieldValue("StubRoutines::_electronicCodeBook_encryptAESCrypt", Long.class, "address");
    public final long electronicCodeBookDecrypt = getFieldValue("StubRoutines::_electronicCodeBook_decryptAESCrypt", Long.class, "address");

    public final long galoisCounterModeCrypt = getFieldValue("StubRoutines::_galoisCounterMode_AESCrypt", Long.class, "address");

    public final long poly1305ProcessBlocks = getFieldValue("StubRoutines::_poly1305_processBlocks", Long.class, "address");
    public final long chacha20Block = getFieldValue("StubRoutines::_chacha20Block", Long.class, "address");

    public final long intpolyMontgomeryMultP256 = getFieldValue("StubRoutines::_intpoly_montgomeryMult_P256", Long.class, "address", 0L, JDK >= 23);
    public final long intpolyAssign = getFieldValue("StubRoutines::_intpoly_assign", Long.class, "address", 0L, JDK >= 23);

    public final long throwDelayedStackOverflowErrorEntry = getFieldValue(JDK >= 24 ? "CompilerToVM::Data::SharedRuntime_throw_delayed_StackOverflowError_entry"
                    : "StubRoutines::_throw_delayed_StackOverflowError_entry", Long.class, "address");

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
    public final long unsafeSetMemory = getFieldValue("StubRoutines::_unsafe_setmemory", Long.class, "address", 0L, JDK >= 23);

    // Allocation stubs that return null when allocation fails
    public final long newInstanceOrNullAddress = getAddress("JVMCIRuntime::new_instance_or_null");
    public final long newArrayOrNullAddress = getAddress("JVMCIRuntime::new_array_or_null");
    public final long newMultiArrayOrNullAddress = getAddress("JVMCIRuntime::new_multi_array_or_null");
    public final long dynamicNewInstanceOrNullAddress = getAddress("JVMCIRuntime::dynamic_new_instance_or_null");

    public final long invokeJavaMethodAddress = getAddress("JVMCIRuntime::invoke_static_method_one_arg");

    /**
     * Checks that HotSpot implements all or none of the allocate-or-null stubs.
     */
    private boolean checkNullAllocationStubs() {
        assert newInstanceOrNullAddress != 0L;
        assert newArrayOrNullAddress != 0L;
        assert newMultiArrayOrNullAddress != 0L;
        assert dynamicNewInstanceOrNullAddress != 0L;
        return true;
    }

    public final long vmMessageAddress = getAddress("JVMCIRuntime::vm_message");
    public final long identityHashCodeAddress = getAddress("JVMCIRuntime::identity_hash_code");
    public final long exceptionHandlerForPcAddress = getAddress("JVMCIRuntime::exception_handler_for_pc");
    public final long monitorenterAddress = getAddress("JVMCIRuntime::monitorenter");
    public final long monitorexitAddress = getAddress("JVMCIRuntime::monitorexit");
    public final long notifyAddress = getAddress("JVMCIRuntime::object_notify");
    public final long notifyAllAddress = getAddress("JVMCIRuntime::object_notifyAll");

    // This flag indicates that support for loom is enabled.
    public final boolean continuationsEnabled = getFieldValue("CompilerToVM::Data::continuations_enabled", Boolean.class, "bool");

    // If the nmethod_entry_barrier field is non-null then an entry barrier must be emitted
    public final int threadDisarmedOffset = getFieldValue("CompilerToVM::Data::thread_disarmed_guard_value_offset", Integer.class, "int");
    public final long nmethodEntryBarrier = getFieldValue("CompilerToVM::Data::nmethod_entry_barrier", Long.class, "address");

    // Check whether generational ZGC support is available
    public final boolean zgcSupport = getStore().getFields().containsKey("CompilerToVM::Data::sizeof_ZStoreBarrierEntry");

    private long getZGCAddressField(String name) {
        // Some of these names are aliased between generational ZGC and non-generational so we avoid
        // the lookup if we don't expect generational ZGC support.
        long address = zgcSupport ? getAddress(name, 0L, zgcSupport) : 0;
        GraalError.guarantee(gc != HotSpotGraalRuntime.HotSpotGC.Z || address != 0, "Unexpected null value for %s", name);
        return address;
    }

    // @formatter:off
    public final int sizeofZStoreBarrierEntry = getFieldValue("CompilerToVM::Data::sizeof_ZStoreBarrierEntry", Integer.class, "int", -1, zgcSupport);
    public final int ZThreadLocalData_store_good_mask_offset = getConstant("ZThreadLocalData::store_good_mask_offset", Integer.class, -1, zgcSupport);
    public final int ZThreadLocalData_store_barrier_buffer_offset = getConstant("ZThreadLocalData::store_barrier_buffer_offset", Integer.class, -1, zgcSupport);
    public final int ZStoreBarrierBuffer_current_offset = getConstant("ZStoreBarrierBuffer::current_offset", Integer.class, -1, zgcSupport);
    public final int ZStoreBarrierBuffer_buffer_offset = getConstant("ZStoreBarrierBuffer::buffer_offset", Integer.class, -1, zgcSupport);
    public final int ZStoreBarrierEntry_p_offset = getConstant("ZStoreBarrierEntry::p_offset", Integer.class, -1, zgcSupport);
    public final int ZStoreBarrierEntry_prev_offset = getConstant("ZStoreBarrierEntry::prev_offset", Integer.class, -1, zgcSupport);
    // @formatter:on

    /*
     * Generational ZGC support
     */
    public final long zBarrierSetRuntimeLoadBarrierOnOopFieldPreloaded = getZGCAddressField("ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded");
    public final long zBarrierSetRuntimeLoadBarrierOnWeakOopFieldPreloaded = getZGCAddressField("ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded");
    public final long zBarrierSetRuntimeNoKeepaliveLoadBarrierOnWeakOopFieldPreloaded = getZGCAddressField(
                    "ZBarrierSetRuntime::no_keepalive_load_barrier_on_weak_oop_field_preloaded");
    public final long zBarrierSetRuntimeNoKeepaliveLoadBarrierOnPhantomOopFieldPreloaded = getZGCAddressField(
                    "ZBarrierSetRuntime::no_keepalive_load_barrier_on_phantom_oop_field_preloaded");
    public final long zBarrierSetRuntimeNoKeepaliveStoreBarrierOnOopFieldWithoutHealing = getZGCAddressField(
                    "ZBarrierSetRuntime::no_keepalive_store_barrier_on_oop_field_without_healing");
    public final long zBarrierSetRuntimeStoreBarrierOnNativeOopFieldWithoutHealing = getZGCAddressField("ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing");
    public final long zBarrierSetRuntimeStoreBarrierOnOopFieldWithHealing = getZGCAddressField("ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing");
    public final long zBarrierSetRuntimeStoreBarrierOnOopFieldWithoutHealing = getZGCAddressField("ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing");
    public final long zBarrierSetRuntimeLoadBarrierOnOopArray = getZGCAddressField("ZBarrierSetRuntime::load_barrier_on_oop_array");
    public final int zPointerLoadShift = getConstant("ZPointerLoadShift", Integer.class, -1, osArch.equals("aarch64") && zgcSupport);

    // aarch64 specific nmethod entry barrier support
    // @formatter:off
    public final int BarrierSetAssembler_nmethod_patching_type = getFieldValue("CompilerToVM::Data::BarrierSetAssembler_nmethod_patching_type", Integer.class, "int", -1, osArch.equals("aarch64"));
    public final long BarrierSetAssembler_patching_epoch_addr = getFieldValue("CompilerToVM::Data::BarrierSetAssembler_patching_epoch_addr", Long.class, "address", -1L,
                    osArch.equals("aarch64") && zgcSupport);
    public final int NMethodPatchingType_stw_instruction_and_data_patch = getConstant("NMethodPatchingType::stw_instruction_and_data_patch", Integer.class, -1, osArch.equals("aarch64"));
    public final int NMethodPatchingType_conc_instruction_and_data_patch = getConstant("NMethodPatchingType::conc_instruction_and_data_patch", Integer.class, -1, osArch.equals("aarch64"));
    public final int NMethodPatchingType_conc_data_patch = getConstant("NMethodPatchingType::conc_data_patch", Integer.class, -1, osArch.equals("aarch64"));
    // @formatter:on

    {
        if (osArch.equals("aarch64")) {
            if (BarrierSetAssembler_nmethod_patching_type != NMethodPatchingType_stw_instruction_and_data_patch &&
                            BarrierSetAssembler_nmethod_patching_type != NMethodPatchingType_conc_instruction_and_data_patch &&
                            BarrierSetAssembler_nmethod_patching_type != NMethodPatchingType_conc_data_patch) {
                throw new IllegalArgumentException("unsupported barrier sequence " + BarrierSetAssembler_nmethod_patching_type);
            }
        }
    }

    // Tracking of the number of monitors held by the current thread. This is used by loom but in
    // JDK 20 was enabled by default to ensure it was correctly implemented.
    public final int threadHeldMonitorCountOffset = getFieldOffset("JavaThread::_held_monitor_count", Integer.class, JDK >= 22 ? "intx" : "int64_t");
    public final int threadLockStackOffset = getFieldOffset("JavaThread::_lock_stack", Integer.class, "LockStack", -1, JDK >= 22);
    public final int lockStackTopOffset = getFieldOffset("LockStack::_top", Integer.class, "uint32_t", -1, JDK >= 22);
    public final int lockStackEndOffset = getConstant("LockStack::_end_offset", Integer.class, -1, JDK >= 22);
    public final int threadOmCacheOffset = getFieldOffset("JavaThread::_om_cache", Integer.class, "OMCache", -1, JDK >= 24);
    public final int threadUnlockedInflatedMonitorOffset = getFieldOffset("JavaThread::_unlocked_inflated_monitor", Integer.class, "ObjectMonitor*", -1, JDK >= 24);
    public final int omCacheOopToOopDifference = getConstant("OMCache::oop_to_oop_difference", Integer.class, -1, JDK >= 24);
    public final int omCacheOopToMonitorDifference = getConstant("OMCache::oop_to_monitor_difference", Integer.class, -1, JDK >= 24);

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
    public final long arithmeticTanhAddress = getFieldValue("CompilerToVM::Data::dtanh", Long.class, "address", 0L, JDK >= 24);
    public final long arithmeticExpAddress = getFieldValue("CompilerToVM::Data::dexp", Long.class, "address");
    public final long arithmeticLogAddress = getFieldValue("CompilerToVM::Data::dlog", Long.class, "address");
    public final long arithmeticLog10Address = getFieldValue("CompilerToVM::Data::dlog10", Long.class, "address");
    public final long arithmeticPowAddress = getFieldValue("CompilerToVM::Data::dpow", Long.class, "address");

    public final long fremAddress = getAddress("SharedRuntime::frem");
    public final long dremAddress = getAddress("SharedRuntime::drem");

    public final long jvmtiVThreadStart = getAddress("SharedRuntime::notify_jvmti_vthread_start");
    public final long jvmtiVThreadEnd = getAddress("SharedRuntime::notify_jvmti_vthread_end");
    public final long jvmtiVThreadMount = getAddress("SharedRuntime::notify_jvmti_vthread_mount");
    public final long jvmtiVThreadUnmount = getAddress("SharedRuntime::notify_jvmti_vthread_unmount");

    public boolean supportJVMTIVThreadNotification() {
        return jvmtiVThreadStart != 0L && jvmtiVThreadEnd != 0L && jvmtiVThreadMount != 0L && jvmtiVThreadUnmount != 0L;
    }

    // JDK-8322630
    public final int icSpeculatedKlassOffset = getFieldOffset("CompiledICData::_speculated_klass", Integer.class, "uintptr_t", Integer.MAX_VALUE, JDK >= 23);

    public final int jvmciCountersSize = getFlag("JVMCICounterSize", Integer.class);

    // JDK-8231756, GR-16685
    public final boolean deoptimizationSupportLargeAccessByteArrayVirtualization = //
                    getConstant("Deoptimization::_support_large_access_byte_array_virtualization", Boolean.class);

    public final int l1LineSize = getFieldValue("CompilerToVM::Data::L1_line_size", Integer.class, "int", 0, JDK >= 24 && "amd64".equals(osArch));

    // Checkstyle: stop
    public final int VMINTRINSIC_FIRST_MH_SIG_POLY = getConstant("vmIntrinsics::FIRST_MH_SIG_POLY", Integer.class);
    public final int VMINTRINSIC_LAST_MH_SIG_POLY = getConstant("vmIntrinsics::LAST_MH_SIG_POLY", Integer.class);
    public final int VMINTRINSIC_INVOKE_GENERIC = getConstant("vmIntrinsics::_invokeGeneric", Integer.class);
    public final int VMINTRINSIC_COMPILED_LAMBDA_FORM = getConstant("vmIntrinsics::_compiledLambdaForm", Integer.class);

    public final boolean CPU_HAS_INTEL_JCC_ERRATUM = getFieldValue("VM_Version::_has_intel_jcc_erratum", Boolean.class, "bool",
                    true, "amd64".equals(osArch));
    // Checkstyle: resume

    private void populateMarkConstants() {
        Map<String, Long> constants = getStore().getConstants();
        for (HotSpotMarkId markId : HotSpotMarkId.values()) {
            String key = "CodeInstaller::" + markId.name();
            Long result = constants.get(key);
            if (result == null) {
                if (markId.getArch() != null && !osArch.equals(markId.getArch())) {
                    continue;
                }
                if (zgcSupport) {
                    GraalHotSpotVMConfigAccess.reportError("Unsupported Mark " + markId);
                }
                continue;
            }
            markId.setValue(result.intValue());
        }
    }

    protected boolean check() {
        for (Field f : getClass().getDeclaredFields()) {
            int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                assert Modifier.isFinal(modifiers) : "field should be final: " + f;
            }
        }

        assert codeEntryAlignment > 0 : codeEntryAlignment;
        assert checkNullAllocationStubs();
        return true;
    }

    public boolean isMethodHandleCall(HotSpotResolvedJavaMethod targetMethod) {
        int intrinsicId = targetMethod.intrinsicId();
        return ((intrinsicId >= VMINTRINSIC_FIRST_MH_SIG_POLY && intrinsicId <= VMINTRINSIC_LAST_MH_SIG_POLY) // MethodHandles::is_signature_polymorphic
                        && intrinsicId != VMINTRINSIC_INVOKE_GENERIC) // MethodHandles::is_signature_polymorphic_intrinsic
                        || intrinsicId == VMINTRINSIC_COMPILED_LAMBDA_FORM; // ciMethod::is_compiled_lambda_form
    }

    public boolean supportsMethodHandleDeoptimizationEntry() {
        return HotSpotMarkId.DEOPT_MH_HANDLER_ENTRY.isAvailable() && VMINTRINSIC_FIRST_MH_SIG_POLY != -1 && VMINTRINSIC_LAST_MH_SIG_POLY != -1 && VMINTRINSIC_INVOKE_GENERIC != -1 &&
                        VMINTRINSIC_COMPILED_LAMBDA_FORM != -1;
    }
}
