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

package com.oracle.truffle.espresso.jvmti;

/*-
 * typedef struct {
         //   50 : VM Initialization Event
    jvmtiEventVMInit VMInit;
         //   51 : VM Death Event
    jvmtiEventVMDeath VMDeath;
         //   52 : Thread Start
    jvmtiEventThreadStart ThreadStart;
         //   53 : Thread End
    jvmtiEventThreadEnd ThreadEnd;
         //   54 : Class File Load Hook
    jvmtiEventClassFileLoadHook ClassFileLoadHook;
         //   55 : Class Load
    jvmtiEventClassLoad ClassLoad;
         //   56 : Class Prepare
    jvmtiEventClassPrepare ClassPrepare;
         //   57 : VM Start Event
    jvmtiEventVMStart VMStart;
         //   58 : Exception
    jvmtiEventException Exception;
         //   59 : Exception Catch
    jvmtiEventExceptionCatch ExceptionCatch;
         //   60 : Single Step
    jvmtiEventSingleStep SingleStep;
         //   61 : Frame Pop
    jvmtiEventFramePop FramePop;
         //   62 : Breakpoint
    jvmtiEventBreakpoint Breakpoint;
         //   63 : Field Access
    jvmtiEventFieldAccess FieldAccess;
         //   64 : Field Modification
    jvmtiEventFieldModification FieldModification;
         //   65 : Method Entry
    jvmtiEventMethodEntry MethodEntry;
         //   66 : Method Exit
    jvmtiEventMethodExit MethodExit;
         //   67 : Native Method Bind
    jvmtiEventNativeMethodBind NativeMethodBind;
         //   68 : Compiled Method Load
    jvmtiEventCompiledMethodLoad CompiledMethodLoad;
         //   69 : Compiled Method Unload
    jvmtiEventCompiledMethodUnload CompiledMethodUnload;
         //   70 : Dynamic Code Generated
    jvmtiEventDynamicCodeGenerated DynamicCodeGenerated;
         //   71 : Data Dump Request
    jvmtiEventDataDumpRequest DataDumpRequest;
         //   72
    jvmtiEventReserved reserved72;
         //   73 : Monitor Wait
    jvmtiEventMonitorWait MonitorWait;
         //   74 : Monitor Waited
    jvmtiEventMonitorWaited MonitorWaited;
         //   75 : Monitor Contended Enter
    jvmtiEventMonitorContendedEnter MonitorContendedEnter;
         //   76 : Monitor Contended Entered
    jvmtiEventMonitorContendedEntered MonitorContendedEntered;
         //   77
    jvmtiEventReserved reserved77;
         //   78
    jvmtiEventReserved reserved78;
         //   79
    jvmtiEventReserved reserved79;
         //   80 : Resource Exhausted
    jvmtiEventResourceExhausted ResourceExhausted;
         //   81 : Garbage Collection Start
    jvmtiEventGarbageCollectionStart GarbageCollectionStart;
         //   82 : Garbage Collection Finish
    jvmtiEventGarbageCollectionFinish GarbageCollectionFinish;
         //   83 : Object Free
    jvmtiEventObjectFree ObjectFree;
         //   84 : VM Object Allocation
    jvmtiEventVMObjectAlloc VMObjectAlloc;
         //   85
    jvmtiEventReserved reserved85;
         //   86 : Sampled Object Allocation
    jvmtiEventSampledObjectAlloc SampledObjectAlloc;
    } jvmtiEventCallbacks;
 */
public class EventCallback {
}
