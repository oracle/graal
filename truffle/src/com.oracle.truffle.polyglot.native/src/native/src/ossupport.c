#include <sys/resource.h>
#include <sys/types.h>
#include <unistd.h>
#include <syscall.h>

#include "com_oracle_truffle_polyglot_OSSupport.h"

JNIEXPORT jint JNICALL Java_com_oracle_truffle_polyglot_OSSupport_getNativeThreadPriority
  (JNIEnv *env, jclass c) {
    int prio = 0;
    #ifdef __linux__
      pid_t tid = syscall(__NR_gettid);
      prio = getpriority(PRIO_PROCESS, tid);
    #endif
  	return prio;
  }
