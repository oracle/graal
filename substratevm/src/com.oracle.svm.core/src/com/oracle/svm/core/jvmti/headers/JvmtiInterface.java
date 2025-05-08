/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

@CContext(JvmtiDirectives.class)
@CStruct(value = "jvmtiInterface_1_", addStructKeyword = true)
public interface JvmtiInterface extends PointerBase {
    @CField
    VoidPointer reserved1();

    @CField
    CFunctionPointer getSetEventNotificationMode();

    @CField
    void setSetEventNotificationMode(CFunctionPointer value);

    @CField
    CFunctionPointer getGetAllModules();

    @CField
    void setGetAllModules(CFunctionPointer value);

    @CField
    CFunctionPointer getGetAllThreads();

    @CField
    void setGetAllThreads(CFunctionPointer value);

    @CField
    CFunctionPointer getSuspendThread();

    @CField
    void setSuspendThread(CFunctionPointer value);

    @CField
    CFunctionPointer getResumeThread();

    @CField
    void setResumeThread(CFunctionPointer value);

    @CField
    CFunctionPointer getStopThread();

    @CField
    void setStopThread(CFunctionPointer value);

    @CField
    CFunctionPointer getInterruptThread();

    @CField
    void setInterruptThread(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadInfo();

    @CField
    void setGetThreadInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetOwnedMonitorInfo();

    @CField
    void setGetOwnedMonitorInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetCurrentContendedMonitor();

    @CField
    void setGetCurrentContendedMonitor(CFunctionPointer value);

    @CField
    CFunctionPointer getRunAgentThread();

    @CField
    void setRunAgentThread(CFunctionPointer value);

    @CField
    CFunctionPointer getGetTopThreadGroups();

    @CField
    void setGetTopThreadGroups(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadGroupInfo();

    @CField
    void setGetThreadGroupInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadGroupChildren();

    @CField
    void setGetThreadGroupChildren(CFunctionPointer value);

    @CField
    CFunctionPointer getGetFrameCount();

    @CField
    void setGetFrameCount(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadState();

    @CField
    void setGetThreadState(CFunctionPointer value);

    @CField
    CFunctionPointer getGetCurrentThread();

    @CField
    void setGetCurrentThread(CFunctionPointer value);

    @CField
    CFunctionPointer getGetFrameLocation();

    @CField
    void setGetFrameLocation(CFunctionPointer value);

    @CField
    CFunctionPointer getNotifyFramePop();

    @CField
    void setNotifyFramePop(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalObject();

    @CField
    void setGetLocalObject(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalInt();

    @CField
    void setGetLocalInt(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalLong();

    @CField
    void setGetLocalLong(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalFloat();

    @CField
    void setGetLocalFloat(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalDouble();

    @CField
    void setGetLocalDouble(CFunctionPointer value);

    @CField
    CFunctionPointer getSetLocalObject();

    @CField
    void setSetLocalObject(CFunctionPointer value);

    @CField
    CFunctionPointer getSetLocalInt();

    @CField
    void setSetLocalInt(CFunctionPointer value);

    @CField
    CFunctionPointer getSetLocalLong();

    @CField
    void setSetLocalLong(CFunctionPointer value);

    @CField
    CFunctionPointer getSetLocalFloat();

    @CField
    void setSetLocalFloat(CFunctionPointer value);

    @CField
    CFunctionPointer getSetLocalDouble();

    @CField
    void setSetLocalDouble(CFunctionPointer value);

    @CField
    CFunctionPointer getCreateRawMonitor();

    @CField
    void setCreateRawMonitor(CFunctionPointer value);

    @CField
    CFunctionPointer getDestroyRawMonitor();

    @CField
    void setDestroyRawMonitor(CFunctionPointer value);

    @CField
    CFunctionPointer getRawMonitorEnter();

    @CField
    void setRawMonitorEnter(CFunctionPointer value);

    @CField
    CFunctionPointer getRawMonitorExit();

    @CField
    void setRawMonitorExit(CFunctionPointer value);

    @CField
    CFunctionPointer getRawMonitorWait();

    @CField
    void setRawMonitorWait(CFunctionPointer value);

    @CField
    CFunctionPointer getRawMonitorNotify();

    @CField
    void setRawMonitorNotify(CFunctionPointer value);

    @CField
    CFunctionPointer getRawMonitorNotifyAll();

    @CField
    void setRawMonitorNotifyAll(CFunctionPointer value);

    @CField
    CFunctionPointer getSetBreakpoint();

    @CField
    void setSetBreakpoint(CFunctionPointer value);

    @CField
    CFunctionPointer getClearBreakpoint();

    @CField
    void setClearBreakpoint(CFunctionPointer value);

    @CField
    CFunctionPointer getGetNamedModule();

    @CField
    void setGetNamedModule(CFunctionPointer value);

    @CField
    CFunctionPointer getSetFieldAccessWatch();

    @CField
    void setSetFieldAccessWatch(CFunctionPointer value);

    @CField
    CFunctionPointer getClearFieldAccessWatch();

    @CField
    void setClearFieldAccessWatch(CFunctionPointer value);

    @CField
    CFunctionPointer getSetFieldModificationWatch();

    @CField
    void setSetFieldModificationWatch(CFunctionPointer value);

    @CField
    CFunctionPointer getClearFieldModificationWatch();

    @CField
    void setClearFieldModificationWatch(CFunctionPointer value);

    @CField
    CFunctionPointer getIsModifiableClass();

    @CField
    void setIsModifiableClass(CFunctionPointer value);

    @CField
    CFunctionPointer getAllocate();

    @CField
    void setAllocate(CFunctionPointer value);

    @CField
    CFunctionPointer getDeallocate();

    @CField
    void setDeallocate(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassSignature();

    @CField
    void setGetClassSignature(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassStatus();

    @CField
    void setGetClassStatus(CFunctionPointer value);

    @CField
    CFunctionPointer getGetSourceFileName();

    @CField
    void setGetSourceFileName(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassModifiers();

    @CField
    void setGetClassModifiers(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassMethods();

    @CField
    void setGetClassMethods(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassFields();

    @CField
    void setGetClassFields(CFunctionPointer value);

    @CField
    CFunctionPointer getGetImplementedInterfaces();

    @CField
    void setGetImplementedInterfaces(CFunctionPointer value);

    @CField
    CFunctionPointer getIsInterface();

    @CField
    void setIsInterface(CFunctionPointer value);

    @CField
    CFunctionPointer getIsArrayClass();

    @CField
    void setIsArrayClass(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassLoader();

    @CField
    void setGetClassLoader(CFunctionPointer value);

    @CField
    CFunctionPointer getGetObjectHashCode();

    @CField
    void setGetObjectHashCode(CFunctionPointer value);

    @CField
    CFunctionPointer getGetObjectMonitorUsage();

    @CField
    void setGetObjectMonitorUsage(CFunctionPointer value);

    @CField
    CFunctionPointer getGetFieldName();

    @CField
    void setGetFieldName(CFunctionPointer value);

    @CField
    CFunctionPointer getGetFieldDeclaringClass();

    @CField
    void setGetFieldDeclaringClass(CFunctionPointer value);

    @CField
    CFunctionPointer getGetFieldModifiers();

    @CField
    void setGetFieldModifiers(CFunctionPointer value);

    @CField
    CFunctionPointer getIsFieldSynthetic();

    @CField
    void setIsFieldSynthetic(CFunctionPointer value);

    @CField
    CFunctionPointer getGetMethodName();

    @CField
    void setGetMethodName(CFunctionPointer value);

    @CField
    CFunctionPointer getGetMethodDeclaringClass();

    @CField
    void setGetMethodDeclaringClass(CFunctionPointer value);

    @CField
    CFunctionPointer getGetMethodModifiers();

    @CField
    void setGetMethodModifiers(CFunctionPointer value);

    @CField
    CFunctionPointer getClearAllFramePops();

    @CField
    void setClearAllFramePops(CFunctionPointer value);

    @CField
    CFunctionPointer getGetMaxLocals();

    @CField
    void setGetMaxLocals(CFunctionPointer value);

    @CField
    CFunctionPointer getGetArgumentsSize();

    @CField
    void setGetArgumentsSize(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLineNumberTable();

    @CField
    void setGetLineNumberTable(CFunctionPointer value);

    @CField
    CFunctionPointer getGetMethodLocation();

    @CField
    void setGetMethodLocation(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalVariableTable();

    @CField
    void setGetLocalVariableTable(CFunctionPointer value);

    @CField
    CFunctionPointer getSetNativeMethodPrefix();

    @CField
    void setSetNativeMethodPrefix(CFunctionPointer value);

    @CField
    CFunctionPointer getSetNativeMethodPrefixes();

    @CField
    void setSetNativeMethodPrefixes(CFunctionPointer value);

    @CField
    CFunctionPointer getGetBytecodes();

    @CField
    void setGetBytecodes(CFunctionPointer value);

    @CField
    CFunctionPointer getIsMethodNative();

    @CField
    void setIsMethodNative(CFunctionPointer value);

    @CField
    CFunctionPointer getIsMethodSynthetic();

    @CField
    void setIsMethodSynthetic(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLoadedClasses();

    @CField
    void setGetLoadedClasses(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassLoaderClasses();

    @CField
    void setGetClassLoaderClasses(CFunctionPointer value);

    @CField
    CFunctionPointer getPopFrame();

    @CField
    void setPopFrame(CFunctionPointer value);

    @CField
    CFunctionPointer getForceEarlyReturnObject();

    @CField
    void setForceEarlyReturnObject(CFunctionPointer value);

    @CField
    CFunctionPointer getForceEarlyReturnInt();

    @CField
    void setForceEarlyReturnInt(CFunctionPointer value);

    @CField
    CFunctionPointer getForceEarlyReturnLong();

    @CField
    void setForceEarlyReturnLong(CFunctionPointer value);

    @CField
    CFunctionPointer getForceEarlyReturnFloat();

    @CField
    void setForceEarlyReturnFloat(CFunctionPointer value);

    @CField
    CFunctionPointer getForceEarlyReturnDouble();

    @CField
    void setForceEarlyReturnDouble(CFunctionPointer value);

    @CField
    CFunctionPointer getForceEarlyReturnVoid();

    @CField
    void setForceEarlyReturnVoid(CFunctionPointer value);

    @CField
    CFunctionPointer getRedefineClasses();

    @CField
    void setRedefineClasses(CFunctionPointer value);

    @CField
    CFunctionPointer getGetVersionNumber();

    @CField
    void setGetVersionNumber(CFunctionPointer value);

    @CField
    CFunctionPointer getGetCapabilities();

    @CField
    void setGetCapabilities(CFunctionPointer value);

    @CField
    CFunctionPointer getGetSourceDebugExtension();

    @CField
    void setGetSourceDebugExtension(CFunctionPointer value);

    @CField
    CFunctionPointer getIsMethodObsolete();

    @CField
    void setIsMethodObsolete(CFunctionPointer value);

    @CField
    CFunctionPointer getSuspendThreadList();

    @CField
    void setSuspendThreadList(CFunctionPointer value);

    @CField
    CFunctionPointer getResumeThreadList();

    @CField
    void setResumeThreadList(CFunctionPointer value);

    @CField
    CFunctionPointer getAddModuleReads();

    @CField
    void setAddModuleReads(CFunctionPointer value);

    @CField
    CFunctionPointer getAddModuleExports();

    @CField
    void setAddModuleExports(CFunctionPointer value);

    @CField
    CFunctionPointer getAddModuleOpens();

    @CField
    void setAddModuleOpens(CFunctionPointer value);

    @CField
    CFunctionPointer getAddModuleUses();

    @CField
    void setAddModuleUses(CFunctionPointer value);

    @CField
    CFunctionPointer getAddModuleProvides();

    @CField
    void setAddModuleProvides(CFunctionPointer value);

    @CField
    CFunctionPointer getIsModifiableModule();

    @CField
    void setIsModifiableModule(CFunctionPointer value);

    @CField
    CFunctionPointer getGetAllStackTraces();

    @CField
    void setGetAllStackTraces(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadListStackTraces();

    @CField
    void setGetThreadListStackTraces(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadLocalStorage();

    @CField
    void setGetThreadLocalStorage(CFunctionPointer value);

    @CField
    CFunctionPointer getSetThreadLocalStorage();

    @CField
    void setSetThreadLocalStorage(CFunctionPointer value);

    @CField
    CFunctionPointer getGetStackTrace();

    @CField
    void setGetStackTrace(CFunctionPointer value);

    @CField
    CFunctionPointer reserved105();

    @CField
    CFunctionPointer getGetTag();

    @CField
    void setGetTag(CFunctionPointer value);

    @CField
    CFunctionPointer getSetTag();

    @CField
    void setSetTag(CFunctionPointer value);

    @CField
    CFunctionPointer getForceGarbageCollection();

    @CField
    void setForceGarbageCollection(CFunctionPointer value);

    @CField
    CFunctionPointer getIterateOverObjectsReachableFromObject();

    @CField
    void setIterateOverObjectsReachableFromObject(CFunctionPointer value);

    @CField
    CFunctionPointer getIterateOverReachableObjects();

    @CField
    void setIterateOverReachableObjects(CFunctionPointer value);

    @CField
    CFunctionPointer getIterateOverHeap();

    @CField
    void setIterateOverHeap(CFunctionPointer value);

    @CField
    CFunctionPointer getIterateOverInstancesOfClass();

    @CField
    void setIterateOverInstancesOfClass(CFunctionPointer value);

    @CField
    CFunctionPointer reserved113();

    @CField
    CFunctionPointer getGetObjectsWithTags();

    @CField
    void setGetObjectsWithTags(CFunctionPointer value);

    @CField
    CFunctionPointer getFollowReferences();

    @CField
    void setFollowReferences(CFunctionPointer value);

    @CField
    CFunctionPointer getIterateThroughHeap();

    @CField
    void setIterateThroughHeap(CFunctionPointer value);

    @CField
    CFunctionPointer reserved117();

    @CField
    CFunctionPointer getSuspendAllVirtualThreads();

    @CField
    void setSuspendAllVirtualThreads(CFunctionPointer value);

    @CField
    CFunctionPointer getResumeAllVirtualThreads();

    @CField
    void setResumeAllVirtualThreads(CFunctionPointer value);

    @CField
    CFunctionPointer getSetJNIFunctionTable();

    @CField
    void setSetJNIFunctionTable(CFunctionPointer value);

    @CField
    CFunctionPointer getGetJNIFunctionTable();

    @CField
    void setGetJNIFunctionTable(CFunctionPointer value);

    @CField
    CFunctionPointer getSetEventCallbacks();

    @CField
    void setSetEventCallbacks(CFunctionPointer value);

    @CField
    CFunctionPointer getGenerateEvents();

    @CField
    void setGenerateEvents(CFunctionPointer value);

    @CField
    CFunctionPointer getGetExtensionFunctions();

    @CField
    void setGetExtensionFunctions(CFunctionPointer value);

    @CField
    CFunctionPointer getGetExtensionEvents();

    @CField
    void setGetExtensionEvents(CFunctionPointer value);

    @CField
    CFunctionPointer getSetExtensionEventCallback();

    @CField
    void setSetExtensionEventCallback(CFunctionPointer value);

    @CField
    CFunctionPointer getDisposeEnvironment();

    @CField
    void setDisposeEnvironment(CFunctionPointer value);

    @CField
    CFunctionPointer getGetErrorName();

    @CField
    void setGetErrorName(CFunctionPointer value);

    @CField
    CFunctionPointer getGetJLocationFormat();

    @CField
    void setGetJLocationFormat(CFunctionPointer value);

    @CField
    CFunctionPointer getGetSystemProperties();

    @CField
    void setGetSystemProperties(CFunctionPointer value);

    @CField
    CFunctionPointer getGetSystemProperty();

    @CField
    void setGetSystemProperty(CFunctionPointer value);

    @CField
    CFunctionPointer getSetSystemProperty();

    @CField
    void setSetSystemProperty(CFunctionPointer value);

    @CField
    CFunctionPointer getGetPhase();

    @CField
    void setGetPhase(CFunctionPointer value);

    @CField
    CFunctionPointer getGetCurrentThreadCpuTimerInfo();

    @CField
    void setGetCurrentThreadCpuTimerInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetCurrentThreadCpuTime();

    @CField
    void setGetCurrentThreadCpuTime(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadCpuTimerInfo();

    @CField
    void setGetThreadCpuTimerInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetThreadCpuTime();

    @CField
    void setGetThreadCpuTime(CFunctionPointer value);

    @CField
    CFunctionPointer getGetTimerInfo();

    @CField
    void setGetTimerInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetTime();

    @CField
    void setGetTime(CFunctionPointer value);

    @CField
    CFunctionPointer getGetPotentialCapabilities();

    @CField
    void setGetPotentialCapabilities(CFunctionPointer value);

    @CField
    CFunctionPointer reserved141();

    @CField
    CFunctionPointer getAddCapabilities();

    @CField
    void setAddCapabilities(CFunctionPointer value);

    @CField
    CFunctionPointer getRelinquishCapabilities();

    @CField
    void setRelinquishCapabilities(CFunctionPointer value);

    @CField
    CFunctionPointer getGetAvailableProcessors();

    @CField
    void setGetAvailableProcessors(CFunctionPointer value);

    @CField
    CFunctionPointer getGetClassVersionNumbers();

    @CField
    void setGetClassVersionNumbers(CFunctionPointer value);

    @CField
    CFunctionPointer getGetConstantPool();

    @CField
    void setGetConstantPool(CFunctionPointer value);

    @CField
    CFunctionPointer getGetEnvironmentLocalStorage();

    @CField
    void setGetEnvironmentLocalStorage(CFunctionPointer value);

    @CField
    CFunctionPointer getSetEnvironmentLocalStorage();

    @CField
    void setSetEnvironmentLocalStorage(CFunctionPointer value);

    @CField
    CFunctionPointer getAddToBootstrapClassLoaderSearch();

    @CField
    void setAddToBootstrapClassLoaderSearch(CFunctionPointer value);

    @CField
    CFunctionPointer getSetVerboseFlag();

    @CField
    void setSetVerboseFlag(CFunctionPointer value);

    @CField
    CFunctionPointer getAddToSystemClassLoaderSearch();

    @CField
    void setAddToSystemClassLoaderSearch(CFunctionPointer value);

    @CField
    CFunctionPointer getRetransformClasses();

    @CField
    void setRetransformClasses(CFunctionPointer value);

    @CField
    CFunctionPointer getGetOwnedMonitorStackDepthInfo();

    @CField
    void setGetOwnedMonitorStackDepthInfo(CFunctionPointer value);

    @CField
    CFunctionPointer getGetObjectSize();

    @CField
    void setGetObjectSize(CFunctionPointer value);

    @CField
    CFunctionPointer getGetLocalInstance();

    @CField
    void setGetLocalInstance(CFunctionPointer value);

    @CField
    CFunctionPointer getSetHeapSamplingInterval();

    @CField
    void setSetHeapSamplingInterval(CFunctionPointer value);
}
