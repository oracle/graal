/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.vm;

import static com.oracle.truffle.espresso.jni.JniEnv.JNI_OK;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntFunction;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.CallableFromNative;
import com.oracle.truffle.espresso.substitutions.GenerateNativeEnv;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.threads.EspressoThreadRegistry;
import com.oracle.truffle.espresso.threads.State;
import com.oracle.truffle.espresso.threads.ThreadsAccess;

@GenerateNativeEnv(target = ManagementImpl.class, prependEnv = true)
public final class Management extends NativeEnv {
    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, Management.class);
    // Partial/incomplete implementation disclaimer!
    //
    // This is a partial implementation of the {@link java.lang.management} APIs. Some APIs go
    // beyond Espresso reach e.g. GC stats. Espresso implements the hard bits by just
    // forwarding to the host implementation, but this approach is not ideal:
    // - In some cases it's not possible to gather stats per-context e.g. host GC stats are VM-wide.
    // - SubstrateVM implements a bare-minimum subset of the management APIs.
    //
    // Some implementations below are just partially correct due to limitations of Espresso itself
    // e.g. dumping stacktraces for all threads.

    // @formatter:off
    // enum jmmLongAttribute
    public static final int JMM_CLASS_LOADED_COUNT             = 1;    /* Total number of loaded classes */
    public static final int JMM_CLASS_UNLOADED_COUNT           = 2;    /* Total number of unloaded classes */
    public static final int JMM_THREAD_TOTAL_COUNT             = 3;    /* Total number of threads that have been started */
    public static final int JMM_THREAD_LIVE_COUNT              = 4;    /* Current number of live threads */
    public static final int JMM_THREAD_PEAK_COUNT              = 5;    /* Peak number of live threads */
    public static final int JMM_THREAD_DAEMON_COUNT            = 6;    /* Current number of daemon threads */
    public static final int JMM_JVM_INIT_DONE_TIME_MS          = 7;    /* Time when the JVM finished initialization */
    public static final int JMM_COMPILE_TOTAL_TIME_MS          = 8;    /* Total accumulated time spent in compilation */
    public static final int JMM_GC_TIME_MS                     = 9;    /* Total accumulated time spent in collection */
    public static final int JMM_GC_COUNT                       = 10;   /* Total number of collections */
    public static final int JMM_JVM_UPTIME_MS                  = 11;   /* The JVM uptime in milliseconds */
    public static final int JMM_INTERNAL_ATTRIBUTE_INDEX       = 100;
    public static final int JMM_CLASS_LOADED_BYTES             = 101;  /* Number of bytes loaded instance classes */
    public static final int JMM_CLASS_UNLOADED_BYTES           = 102;  /* Number of bytes unloaded instance classes */
    public static final int JMM_TOTAL_CLASSLOAD_TIME_MS        = 103;  /* Accumulated VM class loader time (TraceClassLoadingTime) */
    public static final int JMM_VM_GLOBAL_COUNT                = 104;  /* Number of VM internal flags */
    public static final int JMM_SAFEPOINT_COUNT                = 105;  /* Total number of safepoints */
    public static final int JMM_TOTAL_SAFEPOINTSYNC_TIME_MS    = 106;  /* Accumulated time spent getting to safepoints */
    public static final int JMM_TOTAL_STOPPED_TIME_MS          = 107;  /* Accumulated time spent at safepoints */
    public static final int JMM_TOTAL_APP_TIME_MS              = 108;  /* Accumulated time spent in Java application */
    public static final int JMM_VM_THREAD_COUNT                = 109;  /* Current number of VM internal threads */
    public static final int JMM_CLASS_INIT_TOTAL_COUNT         = 110;  /* Number of classes for which initializers were run */
    public static final int JMM_CLASS_INIT_TOTAL_TIME_MS       = 111;  /* Accumulated time spent in class initializers */
    public static final int JMM_METHOD_DATA_SIZE_BYTES         = 112;  /* Size of method data in memory */
    public static final int JMM_CLASS_VERIFY_TOTAL_TIME_MS     = 113;  /* Accumulated time spent in class verifier */
    public static final int JMM_SHARED_CLASS_LOADED_COUNT      = 114;  /* Number of shared classes loaded */
    public static final int JMM_SHARED_CLASS_UNLOADED_COUNT    = 115;  /* Number of shared classes unloaded */
    public static final int JMM_SHARED_CLASS_LOADED_BYTES      = 116;  /* Number of bytes loaded shared classes */
    public static final int JMM_SHARED_CLASS_UNLOADED_BYTES    = 117;  /* Number of bytes unloaded shared classes */
    public static final int JMM_OS_ATTRIBUTE_INDEX             = 200;
    public static final int JMM_OS_PROCESS_ID                  = 201;  /* Process id of the JVM */
    public static final int JMM_OS_MEM_TOTAL_PHYSICAL_BYTES    = 202;  /* Physical memory size */
    public static final int JMM_GC_EXT_ATTRIBUTE_INFO_SIZE     = 401;  /* the size of the GC specific attributes for a given GC memory manager */
    // @formatter:on

    // enum jmmBoolAttribute
    public static final int JMM_VERBOSE_GC = 21;
    public static final int JMM_VERBOSE_CLASS = 22;
    public static final int JMM_THREAD_CONTENTION_MONITORING = 23;
    public static final int JMM_THREAD_CPU_TIME = 24;
    public static final int JMM_THREAD_ALLOCATED_MEMORY = 25;

    // enum jmmStatisticType
    public static final int JMM_STAT_PEAK_THREAD_COUNT = 801;
    public static final int JMM_STAT_THREAD_CONTENTION_COUNT = 802;
    public static final int JMM_STAT_THREAD_CONTENTION_TIME = 803;
    public static final int JMM_STAT_THREAD_CONTENTION_STAT = 804;
    public static final int JMM_STAT_PEAK_POOL_USAGE = 805;
    public static final int JMM_STAT_GC_STAT = 806;

    // enum
    public static final int JMM_VERSION_1 = 0x20010000;
    public static final int JMM_VERSION_1_0 = 0x20010000;
    public static final int JMM_VERSION_1_1 = 0x20010100; // JDK 6
    public static final int JMM_VERSION_1_2 = 0x20010200; // JDK 7
    public static final int JMM_VERSION_1_2_1 = 0x20010201; // JDK 7 GA
    public static final int JMM_VERSION_1_2_2 = 0x20010202;
    public static final int JMM_VERSION_1_2_3 = 0x20010203;
    public static final int JMM_VERSION_2 = 0x20020000; // JDK 10
    public static final int JMM_VERSION_3 = 0x20030000; // JDK 11.0.9 and 14
    public static final int JMM_VERSION_4 = 0x20040000; // JDK 21

    @CompilationFinal //
    private @Pointer TruffleObject managementPtr;
    @CompilationFinal //
    private int managementVersion;

    private final @Pointer TruffleObject initializeManagementContext;
    private final @Pointer TruffleObject disposeManagementContext;

    public Management(EspressoContext context, TruffleObject mokapotLibrary) {
        super(context);
        assert context.getEspressoEnv().EnableManagement;
        this.initializeManagementContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary, "initializeManagementContext",
                        NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.INT));

        this.disposeManagementContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary, "disposeManagementContext",
                        NativeSignature.create(NativeType.VOID, NativeType.POINTER, NativeType.INT, NativeType.POINTER));
    }

    @Override
    protected TruffleLogger getLogger() {
        return LOGGER;
    }

    /**
     * Procedure to support a new management version in Espresso:
     * <ul>
     * <li>Add the new version to support in this method.</li>
     * <li>Add the version to the version enum in <code>jmm_common.h</code> in the mokapot include
     * directory.</li>
     * <li>Create and update accordingly with the new changes (most certainly a new function)
     * <code>jmm_.h</code> and <code>management_.c</code> in the mokapot include and source
     * directory</li>
     * <li>Add to <code>management.h</code> the new <code>initializeManagementContext_</code> and
     * <code>disposeManagementContext_</code> functions.</li>
     * <li>Update <code>management.c</code> to select these new method depending on the requested
     * version</li>
     * <li>Ideally implement the method in this class.</li>
     * </ul>
     */
    public static boolean isSupportedManagementVersion(int version) {
        return version == JMM_VERSION_1 || version == JMM_VERSION_2 || version == JMM_VERSION_3 || version == JMM_VERSION_4;
    }

    public TruffleObject getManagement(int version) {
        if (!isSupportedManagementVersion(version)) {
            return RawPointer.nullInstance();
        }
        if (managementPtr == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            managementPtr = initializeAndGetEnv(initializeManagementContext, version);
            managementVersion = version;
            assert getUncached().isPointer(managementPtr);
            assert managementPtr != null && !getUncached().isNull(managementPtr);
        } else if (version != managementVersion) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getContext().getLogger().warning("Asking for a different management version that previously requested.\n" +
                            "Previously requested: " + managementVersion + ", currently requested: " + version);
            return RawPointer.nullInstance();
        }
        return managementPtr;
    }

    public void dispose() {
        if (managementPtr != null) {
            try {
                getUncached().execute(disposeManagementContext, managementPtr, managementVersion, RawPointer.nullInstance());
                this.managementPtr = null;
                this.managementVersion = 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere("Cannot dispose Espresso management (mokapot).");
            }
        }
    }

    private static final List<CallableFromNative.Factory> MANAGEMENT_IMPL_FACTORIES = ManagementImplCollector.getInstances(CallableFromNative.Factory.class);

    @Override
    protected List<CallableFromNative.Factory> getCollector() {
        return MANAGEMENT_IMPL_FACTORIES;
    }

    @Override
    protected String getName() {
        return "Management";
    }

    // Checkstyle: stop method name check

    @ManagementImpl
    public int GetVersion() {
        if (managementVersion <= JMM_VERSION_1_2_3) {
            return JMM_VERSION_1_2_3;
        } else {
            return managementVersion;
        }
    }

    @ManagementImpl
    public int GetOptionalSupport(@Pointer TruffleObject /* jmmOptionalSupport **/ supportPtr) {
        if (!getUncached().isNull(supportPtr)) {
            ByteBuffer supportBuf = NativeUtils.directByteBuffer(supportPtr, 8);
            supportBuf.putInt(0); // nothing optional is supported
            return 0;
        }
        return -1;
    }

    private static void validateThreadIdArray(EspressoLanguage language, Meta meta, @JavaType(long[].class) StaticObject threadIds, SubstitutionProfiler profiler) {
        assert threadIds.isArray();
        int numThreads = threadIds.length(language);
        for (int i = 0; i < numThreads; ++i) {
            long tid = threadIds.<long[]> unwrap(language)[i];
            if (tid <= 0) {
                profiler.profile(3);
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Invalid thread ID entry");
            }
        }
    }

    private static void validateThreadInfoArray(Meta meta, @JavaType(internalName = "[Ljava/lang/management/ThreadInfo;") StaticObject infoArray, SubstitutionProfiler profiler) {
        // check if the element of infoArray is of type ThreadInfo class
        Klass infoArrayKlass = infoArray.getKlass();
        if (infoArray.isArray()) {
            Klass component = ((ArrayKlass) infoArrayKlass).getComponentType();
            if (!meta.java_lang_management_ThreadInfo.equals(component)) {
                profiler.profile(4);
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "infoArray element type is not ThreadInfo class");
            }
        }
    }

    @ManagementImpl
    public int GetThreadInfo(@JavaType(long[].class) StaticObject ids, int maxDepth, @JavaType(Object[].class) StaticObject infoArray, @Inject EspressoLanguage language, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(ids)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        validateThreadIdArray(language, meta, ids, profiler);
        validateThreadInfoArray(meta, infoArray, profiler);

        int idsLength = ids.length(language);
        StaticObject[] activeThreads = getContext().getActiveThreads();
        StaticObject[] threads = new StaticObject[idsLength];
        for (int i = 0; i < idsLength; ++i) {
            long id = getInterpreterToVM().getArrayLong(language, i, ids);
            threads[i] = findThreadById(activeThreads, id);
        }

        fillThreadInfos(threads, infoArray, maxDepth, language, meta, profiler, null);

        return JNI_OK; // always 0
    }

    private StaticObject findThreadById(StaticObject[] activeThreads, long id) {
        StaticObject thread = StaticObject.NULL;
        for (int j = 0; j < activeThreads.length; ++j) {
            if (getThreadAccess().getThreadId(activeThreads[j]) == id) {
                thread = activeThreads[j];
                break;
            }
        }
        return thread;
    }

    private void fillThreadInfos(StaticObject[] threads, StaticObject infoArray, int maxDepth, EspressoLanguage language, Meta meta, SubstitutionProfiler profiler, Node node) {
        if (StaticObject.isNull(infoArray)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (maxDepth < -1) {
            profiler.profile(1);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Invalid maxDepth");
        }
        int actualMaxDepth = maxDepth < 0 ? InterpreterToVM.MAX_STACK_DEPTH : maxDepth;
        validateThreadInfoArray(meta, infoArray, profiler);

        if (threads.length != infoArray.length(language)) {
            profiler.profile(2);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "The length of the given ThreadInfo array does not match the length of the given array of thread IDs");
        }

        Method init = meta.java_lang_management_ThreadInfo.lookupDeclaredMethod(Symbol.Name._init_, getSignatures().makeRaw(
                        /* returns */Symbol.Type._void,
                        /* t */ Symbol.Type.java_lang_Thread,
                        /* state */ Symbol.Type._int,
                        /* lockObj */ Symbol.Type.java_lang_Object,
                        /* lockOwner */Symbol.Type.java_lang_Thread,
                        /* blockedCount */Symbol.Type._long,
                        /* blockedTime */Symbol.Type._long,
                        /* waitedCount */Symbol.Type._long,
                        /* waitedTime */Symbol.Type._long,
                        /* StackTraceElement[] */ Symbol.Type.java_lang_StackTraceElement_array));

        for (int i = 0; i < threads.length; i++) {
            StaticObject thread = threads[i];
            if (StaticObject.isNull(thread) || !getThreadAccess().isAlive(thread) || getThreadAccess().isVirtualThread(thread)) {
                getInterpreterToVM().setArrayObject(language, StaticObject.NULL, i, infoArray);
            } else {
                int threadStatus = meta.getThreadAccess().getState(thread);
                StaticObject lockObj = StaticObject.NULL;
                StaticObject lockOwner = StaticObject.NULL;
                if ((threadStatus & State.BLOCKED.value) != 0) {
                    lockObj = (StaticObject) meta.HIDDEN_THREAD_PENDING_MONITOR.getHiddenObject(thread);
                } else if ((threadStatus & (State.WAITING.value | State.TIMED_WAITING.value)) != 0) {
                    lockObj = (StaticObject) meta.HIDDEN_THREAD_WAITING_MONITOR.getHiddenObject(thread);
                }
                if (lockObj == null) {
                    lockObj = StaticObject.NULL;
                } else if (StaticObject.notNull(lockObj)) {
                    Thread hostOwner = StaticObject.isNull(lockObj)
                                    ? null
                                    : lockObj.getLock(getContext()).getOwnerThread();
                    if (hostOwner != null && hostOwner.isAlive()) {
                        lockOwner = getContext().getGuestThreadFromHost(hostOwner);
                        if (lockOwner == null) {
                            lockOwner = StaticObject.NULL;
                        }
                    }
                }

                long blockedCount = Target_java_lang_Thread.getThreadCounter(thread, meta.HIDDEN_THREAD_BLOCKED_COUNT);
                long waitedCount = Target_java_lang_Thread.getThreadCounter(thread, meta.HIDDEN_THREAD_WAITED_COUNT);

                StaticObject stackTrace = Target_java_lang_Thread.getStackTrace(thread, actualMaxDepth, getContext(), node);

                StaticObject threadInfo = meta.java_lang_management_ThreadInfo.allocateInstance(getContext());
                init.invokeDirect( /* this */ threadInfo,
                                /* t */ thread,
                                /* state */ threadStatus,
                                /* lockObj */ lockObj,
                                /* lockOwner */ lockOwner,
                                /* blockedCount */ blockedCount,
                                /* blockedTime */ -1L,
                                /* waitedCount */ waitedCount,
                                /* waitedTime */ -1L,
                                /* StackTraceElement[] */ stackTrace);
                getInterpreterToVM().setArrayObject(language, threadInfo, i, infoArray);
            }
        }
    }

    @ManagementImpl
    public @JavaType(String[].class) StaticObject GetInputArgumentArray(@Inject EspressoLanguage language) {
        return getVM().JVM_GetVmArguments(language);
    }

    @ManagementImpl
    public @JavaType(String[].class) StaticObject GetInputArguments(@Inject EspressoLanguage language) {
        return getVM().JVM_GetVmArguments(language);
    }

    private final ConcurrentHashMap<MemoryPoolMXBean, StaticObject> memoryPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StaticObject> memoryPoolsByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StaticObject, MemoryPoolMXBean> reverseMemoryPools = new ConcurrentHashMap<>();
    @CompilationFinal private Klass memoryPoolMXBeanKlass;

    private Klass getMemoryPoolMXBeanKlass() {
        if (memoryPoolMXBeanKlass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memoryPoolMXBeanKlass = getMeta().resolveSymbolOrFail(Symbol.Type.java_lang_management_MemoryPoolMXBean, StaticObject.NULL, StaticObject.NULL);
        }
        return memoryPoolMXBeanKlass;
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Object[].class) StaticObject GetMemoryPools(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject manager, @Inject Meta meta) {
        if (StaticObject.isNull(manager)) {
            if (memoryPoolsByName.isEmpty()) {
                List<MemoryPoolMXBean> hostBeans = ManagementFactory.getMemoryPoolMXBeans();
                return getMemoryPoolMXBeanKlass().allocateReferenceArray(hostBeans.size(), new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int i) {
                        MemoryPoolMXBean hostBean = hostBeans.get(i);
                        StaticObject guestBean = memoryPools.computeIfAbsent(hostBean, h -> {
                            getLogger().fine(() -> "GetMemoryPools: creating " + h.getName());
                            StaticObject name = meta.toGuestString(h.getName());
                            boolean isHeap = h.getType() == MemoryType.HEAP;
                            long usageThreshold = h.isUsageThresholdSupported() ? 0 : -1;
                            long gcThreshold = h.isCollectionUsageThresholdSupported() ? 0 : -1;
                            return (StaticObject) meta.sun_management_ManagementFactory_createMemoryPool.invokeDirect(null, name, isHeap, usageThreshold, gcThreshold);
                        });
                        MemoryPoolMXBean hostProbe = reverseMemoryPools.putIfAbsent(guestBean, hostBean);
                        assert hostProbe == null || hostProbe == hostBean;
                        StaticObject guestProbe = memoryPoolsByName.putIfAbsent(hostBean.getName(), guestBean);
                        assert guestProbe == null || guestProbe == guestBean;
                        return guestBean;
                    }
                });
            } else {
                Iterator<StaticObject> iterator = memoryPoolsByName.values().iterator();
                return getMemoryPoolMXBeanKlass().allocateReferenceArray(memoryPoolsByName.size(), new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(@SuppressWarnings("unused") int i) {
                        return iterator.next();
                    }
                });
            }
        } else {
            MemoryManagerMXBean hostBean = reverseMemoryManagers.get(manager);
            if (hostBean == null) {
                return StaticObject.NULL;
            }
            String[] poolNames = hostBean.getMemoryPoolNames();
            // initialize map
            GetMemoryPools(StaticObject.NULL, meta);
            return getMemoryPoolMXBeanKlass().allocateReferenceArray(poolNames.length, new IntFunction<StaticObject>() {
                @Override
                public StaticObject apply(int i) {
                    String name = poolNames[i];
                    StaticObject guestBean = memoryPoolsByName.get(name);
                    getLogger().fine(() -> "GetMemoryPools: found " + name + " by name");
                    assert guestBean != null;
                    return guestBean;
                }
            });
        }
    }

    private final ConcurrentHashMap<MemoryManagerMXBean, StaticObject> memoryManagers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StaticObject> memoryManagersByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StaticObject, MemoryManagerMXBean> reverseMemoryManagers = new ConcurrentHashMap<>();
    @CompilationFinal private Klass memoryManagerMXBeanKlass;

    private Klass getMemoryManagerMXBeanKlass() {
        if (memoryManagerMXBeanKlass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memoryManagerMXBeanKlass = getMeta().resolveSymbolOrFail(Symbol.Type.java_lang_management_MemoryManagerMXBean, StaticObject.NULL, StaticObject.NULL);
        }
        return memoryManagerMXBeanKlass;
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Object[].class) StaticObject GetMemoryManagers(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject pool, @Inject Meta meta) {
        if (StaticObject.isNull(pool)) {
            if (memoryManagersByName.isEmpty()) {
                List<MemoryManagerMXBean> hostBeans = ManagementFactory.getMemoryManagerMXBeans();
                return getMemoryManagerMXBeanKlass().allocateReferenceArray(hostBeans.size(), new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int i) {
                        MemoryManagerMXBean hostBean = hostBeans.get(i);
                        Function<MemoryManagerMXBean, StaticObject> factory;
                        if (hostBean instanceof GarbageCollectorMXBean) {
                            factory = h -> {
                                getLogger().fine(() -> "GetMemoryManagers: creating " + h.getName());
                                // TODO use GarbageCollectorExtImpl
                                return (StaticObject) meta.sun_management_ManagementFactory_createGarbageCollector.invokeDirect(null, meta.toGuestString(h.getName()), StaticObject.NULL);
                            };
                        } else {
                            factory = h -> {
                                getLogger().fine(() -> "GetMemoryManagers: creating " + h.getName());
                                return (StaticObject) meta.sun_management_ManagementFactory_createMemoryManager.invokeDirect(null, meta.toGuestString(h.getName()));
                            };
                        }
                        StaticObject guestBean = memoryManagers.computeIfAbsent(hostBean, factory);
                        MemoryManagerMXBean hostProbe = reverseMemoryManagers.putIfAbsent(guestBean, hostBean);
                        assert hostProbe == null || hostProbe == hostBean;
                        StaticObject guestProbe = memoryManagersByName.putIfAbsent(hostBean.getName(), guestBean);
                        assert guestProbe == null || guestProbe == guestBean;
                        return guestBean;
                    }
                });
            } else {
                Iterator<StaticObject> iterator = memoryManagersByName.values().iterator();
                return getMemoryManagerMXBeanKlass().allocateReferenceArray(memoryManagersByName.size(), new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(@SuppressWarnings("unused") int i) {
                        return iterator.next();
                    }
                });
            }
        } else {
            MemoryPoolMXBean hostBean = reverseMemoryPools.get(pool);
            if (hostBean == null) {
                return StaticObject.NULL;
            }
            String[] managerNames = hostBean.getMemoryManagerNames();
            // initialize map
            GetMemoryManagers(StaticObject.NULL, meta);
            return getMemoryManagerMXBeanKlass().allocateReferenceArray(managerNames.length, new IntFunction<StaticObject>() {
                @Override
                public StaticObject apply(int i) {
                    String name = managerNames[i];
                    StaticObject guestBean = memoryManagersByName.get(name);
                    getLogger().fine(() -> "GetMemoryManagers: found " + name + " by name");
                    assert guestBean != null;
                    return guestBean;
                }
            });
        }
    }

    @CompilationFinal private Method memoryUsageInit;

    private Method getMemoryUsageInit() {
        if (memoryUsageInit == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memoryUsageInit = getMeta().java_lang_management_MemoryUsage.lookupDeclaredMethod(Symbol.Name._init_,
                            getSignatures().makeRaw(Symbol.Type._void, Symbol.Type._long, Symbol.Type._long, Symbol.Type._long, Symbol.Type._long));
        }
        return memoryUsageInit;
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Object.class) StaticObject GetMemoryPoolUsage(@JavaType(Object.class) StaticObject pool, @Inject Meta meta) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        MemoryPoolMXBean hostBean = reverseMemoryPools.get(pool);
        if (hostBean == null) {
            return StaticObject.NULL;
        }
        return asGuestUsage(hostBean.getUsage(), meta);
    }

    private StaticObject asGuestUsage(MemoryUsage usage, Meta meta) {
        StaticObject guestUsage = meta.java_lang_management_MemoryUsage.allocateInstance(getContext());
        getMemoryUsageInit().invokeDirect(guestUsage, usage.getInit(), usage.getUsed(), usage.getCommitted(), usage.getMax());
        return guestUsage;
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Object.class) StaticObject GetPeakMemoryPoolUsage(@JavaType(Object.class) StaticObject pool, @Inject Meta meta) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        MemoryPoolMXBean hostBean = reverseMemoryPools.get(pool);
        if (hostBean == null) {
            return StaticObject.NULL;
        }
        return asGuestUsage(hostBean.getPeakUsage(), meta);
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Object.class) StaticObject GetPoolCollectionUsage(@JavaType(Object.class) StaticObject pool, @Inject Meta meta) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        MemoryPoolMXBean hostBean = reverseMemoryPools.get(pool);
        if (hostBean == null) {
            return StaticObject.NULL;
        }
        return asGuestUsage(hostBean.getCollectionUsage(), meta);
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Object.class) StaticObject GetMemoryUsage(@SuppressWarnings("unused") boolean heap, @Inject Meta meta) {
        MemoryUsage usage;
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        if (heap) {
            usage = memoryMXBean.getHeapMemoryUsage();
        } else {
            usage = memoryMXBean.getNonHeapMemoryUsage();
        }
        return asGuestUsage(usage, meta);
    }

    @ManagementImpl
    @TruffleBoundary // Lots of SVM + Windows methods blocked for PE.
    public long GetLongAttribute(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject obj,
                    /* jmmLongAttribute */ int att) {
        switch (att) {
            case JMM_JVM_INIT_DONE_TIME_MS:
                return TimeUnit.NANOSECONDS.toMillis(getContext().initDoneTimeNanos);
            case JMM_CLASS_LOADED_COUNT:
                return getRegistries().getLoadedClassesCount();
            case JMM_CLASS_UNLOADED_COUNT:
                return 0L;
            case JMM_JVM_UPTIME_MS:
                long elapsedNanos = System.nanoTime() - getContext().initDoneTimeNanos;
                return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            case JMM_OS_PROCESS_ID:
                return ProcessHandle.current().pid();
            case JMM_THREAD_DAEMON_COUNT:
                int daemonCount = 0;
                ThreadsAccess threadAccess = getContext().getThreadAccess();
                for (StaticObject t : getContext().getActiveThreads()) {
                    if (threadAccess.isDaemon(t)) {
                        ++daemonCount;
                    }
                }
                return daemonCount;

            case JMM_THREAD_PEAK_COUNT:
                return getContext().getPeakThreadCount();
            case JMM_THREAD_LIVE_COUNT:
                return getContext().getActiveThreads().length;
            case JMM_THREAD_TOTAL_COUNT:
                return getContext().getCreatedThreadCount();
        }
        getLogger().warning(() -> "Unknown long attribute: " + att);
        return -1L;
    }

    @ManagementImpl
    @TruffleBoundary
    public int GetLongAttributes(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject obj,
                    /* jmmLongAttribute* */ @Pointer TruffleObject atts,
                    int count,
                    /* long* */ @Pointer TruffleObject result) {
        int numAtts = 0;
        ByteBuffer attsBuffer = NativeUtils.directByteBuffer(atts, count, JavaKind.Int);
        ByteBuffer resBuffer = NativeUtils.directByteBuffer(result, count, JavaKind.Long);
        for (int i = 0; i < count; i++) {
            int att = attsBuffer.getInt();
            long res = GetLongAttribute(obj, att);
            resBuffer.putLong(res);
            if (res != -1L) {
                numAtts++;
            }
        }
        return numAtts;
    }

    private boolean JMM_VERBOSE_GC_state = false;
    private boolean JMM_VERBOSE_CLASS_state = false;
    private boolean JMM_THREAD_CONTENTION_MONITORING_state = false;
    private boolean JMM_THREAD_CPU_TIME_state = false;
    private boolean JMM_THREAD_ALLOCATED_MEMORY_state = false;

    @ManagementImpl
    public boolean GetBoolAttribute(/* jmmBoolAttribute */ int att) {
        switch (att) {
            case JMM_VERBOSE_GC:
                return JMM_VERBOSE_GC_state;
            case JMM_VERBOSE_CLASS:
                return JMM_VERBOSE_CLASS_state;
            case JMM_THREAD_CONTENTION_MONITORING:
                return JMM_THREAD_CONTENTION_MONITORING_state;
            case JMM_THREAD_CPU_TIME:
                return JMM_THREAD_CPU_TIME_state;
            case JMM_THREAD_ALLOCATED_MEMORY:
                return JMM_THREAD_ALLOCATED_MEMORY_state;
        }
        getLogger().warning(() -> "Unknown bool attribute: " + att);
        return false;
    }

    @ManagementImpl
    public boolean SetBoolAttribute(/* jmmBoolAttribute */ int att, boolean flag) {
        switch (att) {
            case JMM_VERBOSE_GC:
                return JMM_VERBOSE_GC_state = flag;
            case JMM_VERBOSE_CLASS:
                return JMM_VERBOSE_CLASS_state = flag;
            case JMM_THREAD_CONTENTION_MONITORING:
                return JMM_THREAD_CONTENTION_MONITORING_state = flag;
            case JMM_THREAD_CPU_TIME:
                return JMM_THREAD_CPU_TIME_state = flag;
            case JMM_THREAD_ALLOCATED_MEMORY:
                return JMM_THREAD_ALLOCATED_MEMORY_state = flag;
        }
        getLogger().warning(() -> "Unknown bool attribute: " + att);
        return false;
    }

    @ManagementImpl
    public int GetVMGlobals(@JavaType(Object[].class) StaticObject names, /* jmmVMGlobal* */ @Pointer TruffleObject globalsPtr, @SuppressWarnings("unused") int count,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (getUncached().isNull(globalsPtr)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (StaticObject.notNull(names)) {
            if (!names.getKlass().equals(meta.java_lang_String.array())) {
                profiler.profile(1);
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Array element type is not String class");
            }

            StaticObject[] entries = names.unwrap(language);
            for (StaticObject entry : entries) {
                if (StaticObject.isNull(entry)) {
                    profiler.profile(2);
                    throw meta.throwNullPointerException();
                }
                getLogger().fine(() -> "GetVMGlobals: " + meta.toHostString(entry));
            }
        }
        return 0;
    }

    @ManagementImpl
    @SuppressWarnings("unused")
    public @JavaType(internalName = "[Ljava/lang/management/ThreadInfo;") StaticObject DumpThreads(@JavaType(long[].class) StaticObject ids, boolean lockedMonitors, boolean lockedSynchronizers,
                    int maybeMaxDepth,
                    @Inject EspressoLanguage language, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        int maxDepth;
        if (managementVersion >= JMM_VERSION_2) {
            maxDepth = maybeMaxDepth;
        } else {
            maxDepth = InterpreterToVM.MAX_STACK_DEPTH;
        }
        if (StaticObject.isNull(ids)) {
            StaticObject[] activeThreads = getContext().getActiveThreads();
            StaticObject result = getMeta().java_lang_management_ThreadInfo.allocateReferenceArray(activeThreads.length);
            fillThreadInfos(activeThreads, result, maxDepth, language, meta, profiler, null);
            return result;
        } else {
            StaticObject result = getMeta().java_lang_management_ThreadInfo.allocateReferenceArray(ids.length(language));
            if (GetThreadInfo(ids, maxDepth, result, language, meta, profiler) != JNI_OK) {
                return StaticObject.NULL;
            }
            return result;
        }
    }

    @ManagementImpl
    public long GetOneThreadAllocatedMemory(long threadId) {
        StaticObject[] activeThreads = getContext().getActiveThreads();
        StaticObject thread = findThreadById(activeThreads, threadId);
        if (StaticObject.isNull(thread)) {
            return -1L;
        } else {
            return 0L;
        }
    }

    @ManagementImpl
    public void GetThreadAllocatedMemory(
                    @JavaType(long[].class) StaticObject ids,
                    @JavaType(long[].class) StaticObject sizeArray,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(ids) || StaticObject.isNull(sizeArray)) {
            profiler.profile(0);
            throw meta.throwException(meta.java_lang_NullPointerException);
        }
        validateThreadIdArray(language, meta, ids, profiler);
        if (ids.length(language) != sizeArray.length(language)) {
            profiler.profile(1);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "The length of the given long array does not match the length of the given array of thread IDs");
        }
        StaticObject[] activeThreads = getContext().getActiveThreads();

        for (int i = 0; i < ids.length(language); i++) {
            long id = getInterpreterToVM().getArrayLong(language, i, ids);
            StaticObject thread = findThreadById(activeThreads, id);
            if (StaticObject.isNull(thread)) {
                getInterpreterToVM().setArrayLong(language, -1L, i, sizeArray);
            } else {
                getInterpreterToVM().setArrayLong(language, 0L, i, sizeArray);
            }
        }
    }

    @ManagementImpl
    public @JavaType(Thread[].class) StaticObject FindCircularBlockedThreads(@Inject Meta meta) {
        return FindDeadlocks(true, meta);
    }

    @ManagementImpl
    @TruffleBoundary
    public @JavaType(Thread[].class) StaticObject FindDeadlocks(boolean objectMonitorsOnly, @Inject Meta meta) {
        if (!objectMonitorsOnly) {
            getLogger().warning(() -> "Calling unimplemented Management.FindDeadlocks(false)");
            return StaticObject.createArray(meta.java_lang_Thread.getArrayClass(), StaticObject.EMPTY_ARRAY, getContext());
        }
        Thread initiatingThread = Thread.currentThread();
        EspressoThreadRegistry threadRegistry = getContext().getEspressoEnv().getThreadRegistry();
        FindDeadLocksAction action = new FindDeadLocksAction(initiatingThread, threadRegistry, objectMonitorsOnly);
        Future<Void> future = getContext().getEnv().submitThreadLocal(null, action);
        TruffleSafepoint.setBlockedThreadInterruptible(null, f -> {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }, future);
        assert action.results != null;
        return StaticObject.createArray(meta.java_lang_Thread.getArrayClass(), action.results, getContext());
    }

    private static class FindDeadLocksAction extends ThreadLocalAction {
        private final Thread initiatingThread;
        private final EspressoThreadRegistry threadRegistry;
        private final boolean objectMonitorsOnly;
        private StaticObject[] results;

        public FindDeadLocksAction(Thread initiatingThread, EspressoThreadRegistry threadRegistry, boolean objectMonitorsOnly) {
            super(false, true);
            this.initiatingThread = initiatingThread;
            this.threadRegistry = threadRegistry;
            this.objectMonitorsOnly = objectMonitorsOnly;
        }

        @Override
        protected void perform(Access access) {
            if (access.getThread() == initiatingThread) {
                results = threadRegistry.findDeadlocks(objectMonitorsOnly);
            }
        }
    }

    @ManagementImpl
    public boolean ResetStatistic(@SuppressWarnings("unused") long obj, /* jmmStatisticType */ int type) {
        // obj is an abused jobject, so we have to manually handle it
        // obj - specify which instance the statistic associated with to be reset
        // For PEAK_POOL_USAGE and GC stat, obj is required to be a memory pool object.
        // For THREAD_CONTENTION_COUNT and TIME stat, obj is required to be a thread ID.
        switch (type) {
            case JMM_STAT_PEAK_THREAD_COUNT: {
                getContext().resetPeakThreadCount();
                return true;
            }
            default:
                getLogger().warning(() -> "Calling ResetStatistic with unimplemented type (" + type + ")");
        }
        return false;
    }

    // Checkstyle: resume method name check
}
