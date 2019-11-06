#include <sys/resource.h>
#include <sys/types.h>
#include <unistd.h>

#include "com_oracle_truffle_polyglot_OSSupport.h"

int adjustPriority(int val) {
	pid_t pid = getpid();
	int prio = getpriority(PRIO_PROCESS, pid);
	int ret = setpriority(PRIO_PROCESS, pid, prio+val);
	return ret;
}

JNIEXPORT jboolean JNICALL Java_com_oracle_truffle_polyglot_OSSupport_canLowerThreadPriority
  (JNIEnv *env, jclass c) {
  	int ret = adjustPriority(1);
	if (ret) {
		return 0;
	}
	return 1;
  }

JNIEXPORT jboolean JNICALL Java_com_oracle_truffle_polyglot_OSSupport_canRaiseThreadPriority
  (JNIEnv *env, jclass c){
  	int ret = adjustPriority(-1);
	if (ret) {
		return 0;
	}
	return 1;
}

JNIEXPORT jint JNICALL Java_com_oracle_truffle_polyglot_OSSupport_getNativeThreadPriority
  (JNIEnv *env, jclass c) {
  	pid_t pid = getpid();
  	int prio = getpriority(PRIO_PROCESS, pid);
  	return prio;
  }
