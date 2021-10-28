/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "management.h"
#include "jmm1.h"

#include <trufflenfi.h>
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define MANAGEMENT_METHOD_LIST_1(V) \
    V(GetVersion) \
    V(GetOptionalSupport) \
    V(GetInputArguments) \
    V(GetThreadInfo) \
    V(GetInputArgumentArray) \
    V(GetMemoryPools) \
    V(GetMemoryManagers) \
    V(GetMemoryPoolUsage) \
    V(GetPeakMemoryPoolUsage) \
    V(GetThreadAllocatedMemory) \
    V(GetMemoryUsage) \
    V(GetLongAttribute) \
    V(GetBoolAttribute) \
    V(SetBoolAttribute) \
    V(GetLongAttributes) \
    V(FindCircularBlockedThreads) \
    V(GetThreadCpuTime) \
    V(GetVMGlobalNames) \
    V(GetVMGlobals) \
    V(GetInternalThreadTimes) \
    V(ResetStatistic) \
    V(SetPoolSensor) \
    V(SetPoolThreshold) \
    V(GetPoolCollectionUsage) \
    V(GetGCExtAttributeInfo) \
    V(GetLastGCStat) \
    V(GetThreadCpuTimeWithKind) \
    V(GetThreadCpuTimesWithKind) \
    V(DumpHeap0) \
    V(FindDeadlocks) \
    V(SetVMGlobal) \
    V(DumpThreads) \
    V(SetGCNotificationEnabled) \
    V(GetDiagnosticCommands) \
    V(GetDiagnosticCommandInfo) \
    V(GetDiagnosticCommandArgumentsInfo) \
    V(ExecuteDiagnosticCommand) \
    V(SetDiagnosticFrameworkNotificationEnabled)
   


void* initializeManagementContext1(void* (*fetch_by_name)(const char *)) {

  struct jmmInterface_1 *management = (JmmInterface*) malloc(sizeof(struct jmmInterface_1));

  #define INIT__(name) \
      management->name = fetch_by_name(#name);

  MANAGEMENT_METHOD_LIST_1(INIT__)
  #undef INIT_

  return management;
}

void disposeManagementContext1(void *management_ptr, void (*release_closure)(void *)) {
  struct jmmInterface_1 *management = (struct jmmInterface_1*) management_ptr;

  #define DISPOSE__(name) \
    if (release_closure != NULL) { \
      release_closure(management->name); \
    } \
    management->name = NULL;

  MANAGEMENT_METHOD_LIST_1(DISPOSE__)
  #undef DISPOSE__
 
  free(management);
}

