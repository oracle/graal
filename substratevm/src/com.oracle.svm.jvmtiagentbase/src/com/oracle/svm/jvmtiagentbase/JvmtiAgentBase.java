/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jvmtiagentbase;

import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.check;
import static com.oracle.svm.jvmtiagentbase.Support.checkJni;
import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_THREAD_END;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_VM_DEATH;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_VM_INIT;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_VM_START;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventMode.JVMTI_ENABLE;
import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIVersion;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks;

/**
 * Base class for a JVMTI agent.
 *
 * In order to create a new JVMTI agent, you must:
 * <ol>
 * <li>(Optional) Subclass {@link JNIHandleSet}.</li>
 * <li>Subclass this class and parametrize it with your @{link {@link JNIHandleSet} class}.</li>
 * <li>Create a {@link org.graalvm.nativeimage.hosted.Feature} that will register the agent using
 * {@link #registerAgent(JvmtiAgentBase)} in its'
 * {@link org.graalvm.nativeimage.hosted.Feature#afterRegistration} callback.</li>
 * </ol>
 * The created feature must then be passed to native-image on creation of shared library.
 *
 * This agent contains the bare minimum of the JVMTI events. In order to receive a a JVMTI event
 * that is not implemented here, you must:
 * <ol>
 * <li>Create a callback method annotated with {@link CEntryPoint} that matches the signature
 * (parameters and the return type) of the desired JVMTI event. The method must also be annotated
 * with {@link CEntryPointOptions} and must use the {@link AgentIsolate.Prologue} prologue.</li>
 * <li>Create a {@link CEntryPointLiteral} from your callback method.</li>
 * <li>Set the callback in the callbacks argument of your {@link #onLoadCallback} method.</li>
 * <lI>Enable the event using {@link com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventMode}.</lI>
 * </ol>
 *
 * Only one agent can be created in a single native image.
 *
 * @param <T> The class or subclass of {@link JNIHandleSet} containg the JNI handles this agent
 *            requires.
 */
@SuppressWarnings({"JavadocReference", "try"})
public abstract class JvmtiAgentBase<T extends JNIHandleSet> {

    private static final CEntryPointLiteral<CFunctionPointer> onVMInitLiteral = CEntryPointLiteral.create(JvmtiAgentBase.class, "onVMInit", JvmtiEnv.class, JNIEnvironment.class,
                    JNIObjectHandle.class);
    private static final CEntryPointLiteral<CFunctionPointer> onVMStartLiteral = CEntryPointLiteral.create(JvmtiAgentBase.class, "onVMStart", JvmtiEnv.class, JNIEnvironment.class);
    private static final CEntryPointLiteral<CFunctionPointer> onVMDeathLiteral = CEntryPointLiteral.create(JvmtiAgentBase.class, "onVMDeath", JvmtiEnv.class, JNIEnvironment.class);
    private static final CEntryPointLiteral<CFunctionPointer> onThreadEndLiteral = CEntryPointLiteral.create(JvmtiAgentBase.class, "onThreadEnd", JvmtiEnv.class, JNIEnvironment.class,
                    JNIObjectHandle.class);

    @SuppressWarnings({"rawtypes"}) private static JvmtiAgentBase singleton;

    private boolean destroyed = false;

    /**
     * Callback method that should create your subclass of {@link JNIHandleSet}.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @return An instance of your {@link JNIHandleSet} subclass.
     */
    protected abstract JNIHandleSet constructJavaHandles(JNIEnvironment env);

    /**
     * JVMTI Agent_OnLoad callback.
     *
     * @param vm The JNI Java VM.
     * @param jvmti The JVMTI environment.
     * @param callbacks A structure that allows setting the callbacks for JVMTI events.
     * @param options Command line options passed to the agent.
     * @return 0 on success, anything else on failure.
     */
    protected abstract int onLoadCallback(JNIJavaVM vm, JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, String options);

    /**
     * JVMTI Agent_OnUnload callback.
     *
     * @param vm The JNI Java VM.
     * @return 0 on success, anything else on failure.
     */
    protected abstract int onUnloadCallback(JNIJavaVM vm);

    /**
     * JVMTI VMStart event callback.
     *
     * @param jvmti The JVMTI environment.
     * @param jni The JNI environment of the thread running the JVMTI callback.
     */
    protected abstract void onVMStartCallback(JvmtiEnv jvmti, JNIEnvironment jni);

    /**
     * JVMTI VMInit event callback.
     *
     * @param jvmti The JVMTI environment.
     * @param jni The JNI environment of the thread running the JVMTI callback.
     * @param thread The initial thread.
     */
    protected abstract void onVMInitCallback(JvmtiEnv jvmti, JNIEnvironment jni, @SuppressWarnings("unused") JNIObjectHandle thread);

    /**
     * JVMTI VMDeath event callback.
     *
     * @param jvmti The JVMTI environment.
     * @param jni The JNi environment of the thead running the JVMTI callback.
     */
    protected abstract void onVMDeathCallback(JvmtiEnv jvmti, @SuppressWarnings("unused") JNIEnvironment jni);

    /**
     * Returns the JVMTI version required by the agent.
     *
     * @return The JVMTI version required by the agent.
     */
    protected abstract int getRequiredJvmtiVersion();

    /**
     * Checks if the agent cleanup code had ran.
     *
     * @return Whether the agent cleanup code had ran.
     */
    public boolean isDestroyed() {
        return destroyed;
    }

    private T handles;

    /**
     * Returns the JNI handle set constructed with {@link #constructJavaHandles}. The handles are
     * guaranteed to be created before {@link #onLoadCallback} is called.
     *
     * @return The JNI handle set constructed with {@link #constructJavaHandles}.
     */
    public T handles() {
        return handles;
    }

    @SuppressWarnings("unchecked")
    public static <T extends JNIHandleSet, U extends JvmtiAgentBase<T>> U singleton() {
        VMError.guarantee(singleton != null, "No agent has been registered but an instance was requested.");
        return (U) singleton;
    }

    /**
     * Registers the agent singleton. This method must be called only once during a native image
     * build.
     *
     * @param agentSingleton The agent implementation.
     */
    @SuppressWarnings({"rawtypes"})
    protected static <T extends JNIHandleSet> void registerAgent(JvmtiAgentBase<T> agentSingleton) {
        VMError.guarantee(singleton == null, "The agent has been registered multiple times.");
        singleton = agentSingleton;
    }

    @CEntryPoint(name = "Agent_OnLoad")
    @CEntryPointOptions(prologue = CEntryPointSetup.EnterCreateIsolatePrologue.class)
    @SuppressWarnings("unused")
    public static int onLoad(JNIJavaVM vm, CCharPointer options, @SuppressWarnings("unused") PointerBase reserved) {
        /*
         * A single, agent wide isolate is spawned that will be used by all threads during event
         * handling.
         */
        AgentIsolate.setGlobalIsolate(CurrentIsolate.getIsolate());
        String optionsString = options.isNonNull() ? fromCString(options) : "";

        WordPointer jvmtiPtr = StackValue.get(WordPointer.class);
        checkJni(vm.getFunctions().getGetEnv().invoke(vm, jvmtiPtr, singleton().getRequiredJvmtiVersion()));
        JvmtiEnv jvmti = jvmtiPtr.read();

        JvmtiEventCallbacks callbacks = UnmanagedMemory.calloc(SizeOf.get(JvmtiEventCallbacks.class));
        callbacks.setVMStart(onVMStartLiteral.getFunctionPointer());
        callbacks.setVMInit(onVMInitLiteral.getFunctionPointer());
        callbacks.setVMDeath(onVMDeathLiteral.getFunctionPointer());
        callbacks.setThreadEnd(onThreadEndLiteral.getFunctionPointer());

        int ret = singleton().onLoadCallback(vm, jvmti, callbacks, optionsString);
        if (ret != 0) {
            return ret;
        }

        check(jvmti.getFunctions().SetEventCallbacks().invoke(jvmti, callbacks, SizeOf.get(JvmtiEventCallbacks.class)));
        UnmanagedMemory.free(callbacks);

        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullHandle()));
        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullHandle()));
        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullHandle()));
        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_THREAD_END, nullHandle()));
        return 0;
    }

    /**
     * Releases all global JNI references and resources aquired by the framework.
     *
     * This function may only be called once and must be called from the {@link #onUnloadCallback}.
     * During the call to unload some JVMTI event handlers may still be running. As such, it is
     * necessary to provide proper synchronization that ensures all events finish before this call,
     * and events that happen after it do not access any JNI handles. To check if this function has
     * been called, use {@link #isDestroyed}.
     *
     * @param vm The JNIJavaVM received from the {@link #onUnloadCallback} function.
     */
    @SuppressWarnings("unused")
    protected void unload(JNIJavaVM vm) {
        VMError.guarantee(!destroyed, "The unload function from the JvmtiAgentBase must only be called once.");
        singleton().destroyed = true;

        WordPointer jniPtr = StackValue.get(WordPointer.class);
        if (vm.getFunctions().getGetEnv().invoke(vm, jniPtr, JNIVersion.JNI_VERSION_1_6()) != JNIErrors.JNI_OK()) {
            jniPtr.write(nullPointer());
        }
        JNIEnvironment env = jniPtr.read();
        singleton().handles.destroy(env);
        Support.destroy();
        AgentIsolate.resetGlobalIsolate();
    }

    @CEntryPoint(name = "Agent_OnUnload")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    @SuppressWarnings("unused")
    public static int onUnload(JNIJavaVM vm) {
        int ret = singleton().onUnloadCallback(vm);
        return ret;
    }

    @CEntryPoint()
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    @SuppressWarnings("unused")
    public static void onVMStart(JvmtiEnv jvmti, JNIEnvironment jni) {
        Support.initialize(jvmti);
        singleton().handles = singleton().constructJavaHandles(jni);
        singleton().onVMStartCallback(jvmti, jni);
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    @SuppressWarnings("unused")
    public static void onVMInit(JvmtiEnv jvmti, JNIEnvironment jni, @SuppressWarnings("unused") JNIObjectHandle thread) {
        singleton().onVMInitCallback(jvmti, jni, thread);
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    @SuppressWarnings("unused")
    public static void onVMDeath(JvmtiEnv jvmti, @SuppressWarnings("unused") JNIEnvironment jni) {
        singleton().onVMDeathCallback(jvmti, jni);
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.EnterOrBailoutPrologue.class, epilogue = CEntryPointSetup.LeaveDetachThreadEpilogue.class)
    @SuppressWarnings("unused")
    public static void onThreadEnd(JvmtiEnv jvmti, JNIEnvironment jni, JNIObjectHandle thread) {
        /*
         * Track when threads end and detach them, which otherwise could cause a significant leak
         * with applications that launch many short-lived threads which trigger events.
         */
    }
}
