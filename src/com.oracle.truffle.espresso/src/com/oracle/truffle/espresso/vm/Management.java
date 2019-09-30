/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.Callback;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.jni.JniImpl;
import com.oracle.truffle.espresso.jni.NFIType;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

/**
 * Espresso implementation of the management interface.
 */
public final class Management extends NativeEnv implements ContextAccess {

    // Checkstyle: stop
    // jmmLongAttribute;
    public static final int JMM_CLASS_LOADED_COUNT = 1; /* Total number of loaded classes */
    public static final int JMM_CLASS_UNLOADED_COUNT = 2; /* Total number of unloaded classes */
    public static final int JMM_THREAD_TOTAL_COUNT = 3; /*
                                                         * Total number of threads that have been
                                                         * started
                                                         */
    public static final int JMM_THREAD_LIVE_COUNT = 4; /* Current number of live threads */
    public static final int JMM_THREAD_PEAK_COUNT = 5; /* Peak number of live threads */
    public static final int JMM_THREAD_DAEMON_COUNT = 6; /* Current number of daemon threads */
    public static final int JMM_JVM_INIT_DONE_TIME_MS = 7; /*
                                                            * Time when the JVM finished
                                                            * initialization
                                                            */
    public static final int JMM_COMPILE_TOTAL_TIME_MS = 8; /*
                                                            * Total accumulated time spent in
                                                            * compilation
                                                            */
    public static final int JMM_GC_TIME_MS = 9; /* Total accumulated time spent in collection */
    public static final int JMM_GC_COUNT = 10; /* Total number of collections */
    public static final int JMM_JVM_UPTIME_MS = 11; /* The JVM uptime in milliseconds */
    public static final int JMM_INTERNAL_ATTRIBUTE_INDEX = 100;
    public static final int JMM_CLASS_LOADED_BYTES = 101; /*
                                                           * Number of bytes loaded instance classes
                                                           */
    public static final int JMM_CLASS_UNLOADED_BYTES = 102; /*
                                                             * Number of bytes unloaded instance
                                                             * classes
                                                             */
    public static final int JMM_TOTAL_CLASSLOAD_TIME_MS = 103; /*
                                                                * Accumulated VM class loader time
                                                                * (TraceClassLoadingTime)
                                                                */
    public static final int JMM_VM_GLOBAL_COUNT = 104; /* Number of VM internal flags */
    public static final int JMM_SAFEPOINT_COUNT = 105; /* Total number of safepoints */
    public static final int JMM_TOTAL_SAFEPOINTSYNC_TIME_MS = 106; /*
                                                                    * Accumulated time spent getting
                                                                    * to safepoints
                                                                    */
    public static final int JMM_TOTAL_STOPPED_TIME_MS = 107; /*
                                                              * Accumulated time spent at safepoints
                                                              */
    public static final int JMM_TOTAL_APP_TIME_MS = 108; /*
                                                          * Accumulated time spent in Java
                                                          * application
                                                          */
    public static final int JMM_VM_THREAD_COUNT = 109; /* Current number of VM internal threads */
    public static final int JMM_CLASS_INIT_TOTAL_COUNT = 110; /*
                                                               * Number of classes for which
                                                               * initializers were run
                                                               */
    public static final int JMM_CLASS_INIT_TOTAL_TIME_MS = 111; /*
                                                                 * Accumulated time spent in class
                                                                 * initializers
                                                                 */
    public static final int JMM_METHOD_DATA_SIZE_BYTES = 112; /* Size of method data in memory */
    public static final int JMM_CLASS_VERIFY_TOTAL_TIME_MS = 113; /*
                                                                   * Accumulated time spent in class
                                                                   * verifier
                                                                   */
    public static final int JMM_SHARED_CLASS_LOADED_COUNT = 114; /*
                                                                  * Number of shared classes loaded
                                                                  */
    public static final int JMM_SHARED_CLASS_UNLOADED_COUNT = 115; /*
                                                                    * Number of shared classes
                                                                    * unloaded
                                                                    */
    public static final int JMM_SHARED_CLASS_LOADED_BYTES = 116; /*
                                                                  * Number of bytes loaded shared
                                                                  * classes
                                                                  */
    public static final int JMM_SHARED_CLASS_UNLOADED_BYTES = 117; /*
                                                                    * Number of bytes unloaded
                                                                    * shared classes
                                                                    */
    public static final int JMM_OS_ATTRIBUTE_INDEX = 200;
    public static final int JMM_OS_PROCESS_ID = 201; /* Process id of the JVM */
    public static final int JMM_OS_MEM_TOTAL_PHYSICAL_BYTES = 202; /* Physical memory size */
    public static final int JMM_GC_EXT_ATTRIBUTE_INFO_SIZE = 401; /*
                                                                   * the size of the GC specific
                                                                   * attributes for a given GC
                                                                   * memory manager
                                                                   */

    // jmmBoolAttribute;
    public static final int JMM_VERBOSE_GC = 21;
    public static final int JMM_VERBOSE_CLASS = 22;
    public static final int JMM_THREAD_CONTENTION_MONITORING = 23;
    public static final int JMM_THREAD_CPU_TIME = 24;
    public static final int JMM_THREAD_ALLOCATED_MEMORY = 25;

    // Checkstyle: resume

    private final TruffleObject initializeManagementContext;
    private final TruffleObject disposeManagementContext;

    private final JniEnv jniEnv;
    private int version;
    long managementPtr;

    Management(JniEnv jniEnv, TruffleObject mokapotLibrary, int version) {
        this.jniEnv = jniEnv;
        this.version = version;
        try {
            EspressoProperties props = getContext().getVmProperties();

            initializeManagementContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "initializeManagementContext", "(env, (string): pointer): sint64");

            disposeManagementContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "disposeManagementContext",
                            "(env, sint64): void");

            Callback lookupVmImplCallback = new Callback(LOOKUP_VM_IMPL_PARAMETER_COUNT, new Callback.Function() {
                @Override
                public Object call(Object... args) {
                    try {
                        return Management.this.lookupManagementImpl((String) args[0]);
                    } catch (ClassCastException e) {
                        throw EspressoError.shouldNotReachHere(e);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw EspressoError.shouldNotReachHere(e);
                    }
                }
            });
            this.managementPtr = (long) InteropLibrary.getFactory().getUncached().execute(initializeManagementContext, lookupVmImplCallback);

            assert this.managementPtr != 0;

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static final int LOOKUP_VM_IMPL_PARAMETER_COUNT = 1;

    private static Map<String, VMSubstitutor> buildVmMethods() {
        Map<String, VMSubstitutor> map = new HashMap<>();
        for (VMSubstitutor method : VMCollector.getInstance()) {
            assert !map.containsKey(method.methodName()) : "VmImpl for " + method + " already exists";
            map.put(method.methodName(), method);
        }
        return Collections.unmodifiableMap(map);
    }

    public Callback vmMethodWrapper(java.lang.reflect.Method m) {
        int extraArg = 1; // (m.isJni()) ? 1 : 0;

        return new Callback(m.getParameterCount() + extraArg, new Callback.Function() {
            @Override
            @CompilerDirectives.TruffleBoundary
            public Object call(Object... args) {
                boolean isJni = true; // m.isJni();
                try {
                    // Substitute raw pointer by proper `this` reference.
                    // System.err.print("Call DEFINED method: " + m.getName() +
                    // Arrays.toString(shiftedArgs));
                    assert (long) args[0] == jniEnv.getNativePointer();

                    Class<?>[] paramTypes = m.getParameterTypes();

                    args = Arrays.copyOfRange(args, 1, args.length);
                    for (int i = 0; i < args.length; ++i) {
                        if (InteropLibrary.getFactory().getUncached().isNull(args[i])) {
                            args[i] = StaticObject.NULL;
                        } else if (paramTypes[i] == boolean.class) {
                            args[i] = ((byte) args[i]) != 0;
                        }
                    }
                    Object result =  m.invoke(Management.this, args);

                    return result;
                } catch (EspressoException e) {
                    if (isJni) {
                        jniEnv.getThreadLocalPendingException().set(e.getException());
                        return defaultValue(m.getReturnType().toString());
                    }
                    throw EspressoError.shouldNotReachHere(e);
                } catch (StackOverflowError | OutOfMemoryError e) {
                    if (isJni) {
                        // This will most likely SOE again. Nothing we can do about that
                        // unfortunately.
                        jniEnv.getThreadLocalPendingException().set(getMeta().initEx(e.getClass()));
                        return defaultValue(m.getReturnType().toString());
                    }
                    throw e;
                } catch (RuntimeException | VirtualMachineError e) {
                    throw e;
                } catch (ThreadDeath e) {
                    throw getMeta().throwEx(ThreadDeath.class);
                } catch (Throwable e) {
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        });
    }

    private static final Map<String, VMSubstitutor> vmMethods = buildVmMethods();

    public TruffleObject lookupManagementImpl(String methodName) {
        java.lang.reflect.Method[] methods = Management.class.getDeclaredMethods();

        java.lang.reflect.Method found = null;
        for (java.lang.reflect.Method m : methods) {
            if (methodName.equals(m.getName())) {
                found = m;
                break;
            }
        }

        // VMSubstitutor m = managementMethods.get(methodName);
        try {
            // Dummy placeholder for unimplemented/unknown methods.
            if (found == null) {
                // System.err.println("Fetching unknown/unimplemented VM method: " + methodName);
                return (TruffleObject) InteropLibrary.getFactory().getUncached().execute(jniEnv.dupClosureRefAndCast("(pointer): void"),
                                new Callback(1, new Callback.Function() {
                                    @Override
                                    public Object call(Object... args) {
                                        CompilerDirectives.transferToInterpreter();
                                        System.err.println("Calling unimplemented VM method: " + methodName);
                                        throw EspressoError.unimplemented("VM method: " + methodName);
                                    }
                                }));
            }

            String signature = jniNativeSignature(found);
            Callback target = vmMethodWrapper(found);
            return (TruffleObject) InteropLibrary.getFactory().getUncached().execute(jniEnv.dupClosureRefAndCast(signature), target);

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    static String jniNativeSignature(java.lang.reflect.Method method) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        sb.append(NativeSimpleType.SINT64);
        for (Parameter param : method.getParameters()) {
            sb.append(", ");

            // Override NFI type.
            NFIType nfiType = param.getAnnotatedType().getAnnotation(NFIType.class);
            if (nfiType != null) {
                sb.append(NativeSimpleType.valueOf(nfiType.value().toUpperCase()));
            } else {
                sb.append(classToType(param.getType(), false));
            }
        }
        sb.append("): ").append(classToType(method.getReturnType(), true));
        return sb.toString();
    }

    // region Management methods

    final static int JMM_VERSION = 0x20010203;

    @JniImpl
    public int GetVersion() {
        return JMM_VERSION;
    }

    @JniImpl
    public int GetOptionalSupport(long /* jmmOptionalSupport **/ supportPtr) {
        if (supportPtr != 0L) {
            ByteBuffer supportBuf = directByteBuffer(supportPtr, 8);
            supportBuf.putInt(0); // nothing optional is supported
            return 0;
        }
        return -1;
    }

    /*
     * This is used by JDK 6 and earlier. For JDK 7 and after, use GetInputArgumentArray.
     */
    @JniImpl
    public Object GetInputArguments() {
        throw EspressoError.unimplemented("GetInputArguments");
    }

    @JniImpl
    public int GetThreadInfo(@Host(long[].class) StaticObject ids, int maxDepth, @Host(Object[].class) StaticObject infoArray) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object[].class) StaticObject GetInputArgumentArray() {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object[].class) StaticObject GetMemoryPools(Object mgr) {
        Klass memoryPoolMXBean = getMeta().loadKlass(Type.MemoryPoolMXBean, StaticObject.NULL);
        return memoryPoolMXBean.allocateArray(1, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int value) {
                // (String name, boolean isHeap, long uThreshold, long gcThreshold)
                return (StaticObject) getMeta().sun_management_ManagementFactory_createMemoryPool.invokeDirect(null,
                        /* String name */ getMeta().toGuestString("foo"),
                        /* boolean isHeap */ true,
                        /* long uThreshold */ -1L,
                        /* long gcThreshold */ 0L);
            }
        });
    }

    @JniImpl
    public @Host(Object[].class) StaticObject GetMemoryManagers(@Host(Object.class) StaticObject pool) {
        Klass memoryManagerMXBean = getMeta().loadKlass(Type.MemoryManagerMXBean, StaticObject.NULL);
        return memoryManagerMXBean.allocateArray(1, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int value) {
                // (String name, String type)
                return (StaticObject) getMeta().sun_management_ManagementFactory_createMemoryManager.invokeDirect(null,
                        /* String name */ getMeta().toGuestString("foo"),
                        /* String type */ StaticObject.NULL);
            }
        });
    }

    @JniImpl
    public @Host(Object.class) StaticObject GetMemoryPoolUsage(@Host(Object.class) StaticObject pool) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        Method init = getMeta().MemoryUsage.lookupDeclaredMethod(Symbol.Name.INIT, getSignatures().makeRaw(Type._void, Type._long, Type._long, Type._long, Type._long));
        StaticObject instance = getMeta().MemoryUsage.allocateInstance();
        init.invokeDirect(instance, 0L, 0L, 0L, 0L);
        return instance;
    }

    @Host(Object.class)
    StaticObject GetPeakMemoryPoolUsage(@Host(Object.class) StaticObject pool) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        Method init = getMeta().MemoryUsage.lookupDeclaredMethod(Symbol.Name.INIT, getSignatures().makeRaw(Type._void, Type._long, Type._long, Type._long, Type._long));
        StaticObject instance = getMeta().MemoryUsage.allocateInstance();
        init.invokeDirect(instance, 0L, 0L, 0L, 0L);
        return instance;
    }

    @JniImpl
    public void GetThreadAllocatedMemory(@Host(long[].class) StaticObject ids, @Host(long[].class) StaticObject sizeArray) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object.class) StaticObject GetMemoryUsage(boolean heap) {
        Method init = getMeta().MemoryUsage.lookupDeclaredMethod(Symbol.Name.INIT, getSignatures().makeRaw(Type._void, Type._long, Type._long, Type._long, Type._long));
        StaticObject instance = getMeta().MemoryUsage.allocateInstance();
        init.invokeDirect(instance, 0L, 0L, 0L, 0L);
        return instance;
    }

    @JniImpl
    public long GetLongAttribute(@Host(Object.class) StaticObject obj, /* jmmLongAttribute */ int att) {
        switch (att) {
            case JMM_JVM_INIT_DONE_TIME_MS: return getContext().initVMDoneMs;
            case JMM_CLASS_LOADED_COUNT: return getRegistries().getLoadedClassesCount();
            case JMM_CLASS_UNLOADED_COUNT: return 0L;
        }
        throw EspressoError.unimplemented("GetLongAttribute " + att);
    }

    private boolean JMM_VERBOSE_GC_state = false;
    private boolean JMM_VERBOSE_CLASS_state = false;
    private boolean JMM_THREAD_CONTENTION_MONITORING_state = false;
    private boolean JMM_THREAD_CPU_TIME_state = false;
    private boolean JMM_THREAD_ALLOCATED_MEMORY_state = false;

    @JniImpl
    public boolean GetBoolAttribute(/* jmmBoolAttribute */ int att) {
        switch (att) {
            case JMM_VERBOSE_GC: return JMM_VERBOSE_GC_state;
            case JMM_VERBOSE_CLASS: return JMM_VERBOSE_CLASS_state;
            case JMM_THREAD_CONTENTION_MONITORING: return JMM_THREAD_CONTENTION_MONITORING_state;
            case JMM_THREAD_CPU_TIME: return JMM_THREAD_CPU_TIME_state;
            case JMM_THREAD_ALLOCATED_MEMORY: return JMM_THREAD_ALLOCATED_MEMORY_state;
        }
        throw EspressoError.unimplemented("GetBoolAttribute " + att);
    }

    @JniImpl
    public boolean SetBoolAttribute(/* jmmBoolAttribute */ int att, boolean flag) {
        switch (att) {
            case JMM_VERBOSE_GC: return JMM_VERBOSE_GC_state = flag;
            case JMM_VERBOSE_CLASS: return JMM_VERBOSE_CLASS_state = flag;
            case JMM_THREAD_CONTENTION_MONITORING: return JMM_THREAD_CONTENTION_MONITORING_state = flag;
            case JMM_THREAD_CPU_TIME: return JMM_THREAD_CPU_TIME_state = flag;
            case JMM_THREAD_ALLOCATED_MEMORY: return JMM_THREAD_ALLOCATED_MEMORY_state = flag;
        }
        throw EspressoError.unimplemented("SetBoolAttribute " + att);
    }

    @JniImpl
    public int GetLongAttributes(@Host(Object.class) StaticObject obj, /* jmmLongAttribute* */ long attsPtr, int count, /* long* */ long resultPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object[].class) StaticObject FindCircularBlockedThreads() {
        throw EspressoError.unimplemented();
    }

    // Not used in JDK 6 or JDK 7
    @JniImpl
    public long GetThreadCpuTime(long thread_id) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object[].class) StaticObject GetVMGlobalNames() {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public int GetVMGlobals(@Host(Object[].class) StaticObject names, /* jmmVMGlobal* */ long globalsPtr, int count) {
        if (globalsPtr == 0L) {
            throw getMeta().throwEx(NullPointerException.class);
        }
        if (StaticObject.notNull(names)) {
            if (!names.getKlass().equals(getMeta().String.array())) {
                throw getMeta().throwExWithMessage(IllegalArgumentException.class,   "Array element type is not String class");
            }

            StaticObject[] entries = names.unwrap();
            for (StaticObject entry : entries) {
                if (StaticObject.isNull(entry)) {
                    throw getMeta().throwEx(NullPointerException.class);
                }
                System.err.println("GetVMGlobals: " + Meta.toHostString(entry));
            }
        }
        return 0;
    }

    @JniImpl
    public int GetInternalThreadTimes(@Host(Object[].class) StaticObject names, @Host(long[].class) StaticObject times) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public boolean ResetStatistic( /* jvalue */ long obj, /* jmmStatisticType */ long type) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void SetPoolSensor(@Host(Object.class) StaticObject pool, /* jmmThresholdType */ long type, @Host(Object.class) StaticObject sensor) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public long SetPoolThreshold(@Host(Object.class) StaticObject pool, /* jmmThresholdType */ long type, long threshold) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object.class) StaticObject GetPoolCollectionUsage(@Host(Object.class) StaticObject pool) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public int GetGCExtAttributeInfo(@Host(Object.class) StaticObject mgr, /*
                                                                            * jmmExtAttributeInfo *
                                                                            */ long ext_infoPtr, int count) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void GetLastGCStat(@Host(Object.class) StaticObject mgr, /* jmmGCStat * */ long gc_statPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public long GetThreadCpuTimeWithKind(long thread_id, boolean user_sys_cpu_time) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void GetThreadCpuTimesWithKind(@Host(long[].class) StaticObject ids, @Host(long[].class) StaticObject timeArray, boolean user_sys_cpu_time) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public int DumpHeap0(@Host(String.class) StaticObject outputfile, boolean live) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object[].class) StaticObject FindDeadlocks(boolean object_monitors_only) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void SetVMGlobal(@Host(String.class) StaticObject flag_name, /* jvalue */ long new_value) {
        throw EspressoError.unimplemented();
    }

    // void* reserved6;

    @JniImpl
    public @Host(Object[].class) StaticObject DumpThreads(@Host(long[].class) StaticObject ids, boolean lockedMonitors, boolean lockedSynchronizers) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void SetGCNotificationEnabled(@Host(Object.class) StaticObject mgr, boolean enabled) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(Object[].class) StaticObject GetDiagnosticCommands() {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void GetDiagnosticCommandInfo(@Host(Object[].class) StaticObject cmds, /* dcmdInfo * */ long infoArrayPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void GetDiagnosticCommandArgumentsInfo(@Host(String.class) StaticObject commandName, long /* dcmdArgInfo* */ infoArrayPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public @Host(String.class) StaticObject ExecuteDiagnosticCommand(@Host(String.class) StaticObject command) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void SetDiagnosticFrameworkNotificationEnabled(boolean enabled) {
        throw EspressoError.unimplemented();
    }

    @Override
    public EspressoContext getContext() {
        return jniEnv.getContext();
    }
}