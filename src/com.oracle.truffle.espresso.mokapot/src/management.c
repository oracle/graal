#include "jmm.h"

#include <trufflenfi.h>
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define MANAGEMENT_METHOD_LIST(V) \
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


jlong initializeManagementContext(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *)) {

  struct jmmInterface_1_ *management = (JmmInterface*) malloc(sizeof(struct jmmInterface_1_));

  void *fn_ptr = NULL;
  #define INIT__(name) \
      fn_ptr = fetch_by_name(#name); \
      (*truffle_env)->newClosureRef(truffle_env, fn_ptr); \
      management->name = fn_ptr;
  MANAGEMENT_METHOD_LIST(INIT__)
  #undef INIT_

  return (jlong) management;
}

void disposeManagementContext(TruffleEnv *truffle_env, jlong management_ptr) {
  struct jmmInterface_1_ *management = (struct jmmInterface_1_*) management_ptr;

  #define DISPOSE__(name) \
       (*truffle_env)->releaseClosureRef(truffle_env, management->name); \
       management->name = NULL;

  MANAGEMENT_METHOD_LIST(DISPOSE__)
  #undef DISPOSE__
 
  free(management);
}
