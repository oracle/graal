/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#ifndef _WIN64

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <semaphore.h>
#include <signal.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

/**
 * The Java signal handler mechanism may only be used by a single isolate at a time. The signal handler
 * itself, cSunMiscSignal_signalHandler(int), runs on a borrowed thread stack and will not have access
 * to any VM thread-local information or the Java heap base register. Therefore, it is written in C code
 * rather than Java code.
 *
 * Any data that the signal handler references must not be in the Java heap, so it is allocated here. The
 * data consists of a table indexed by signal numbers of atomic counters, and a semaphore for notifying
 * of increments to the values of the counters.
 */

/*
 * Forward declarations of functions.
 */
static void cSunMiscSignal_signalHandler(int signal);
static long cSunMiscSignal_atomicDecrement(volatile long* address);
static int cSunMiscSignal_atomicCompareAndSwap_int(volatile int* ptr, int oldval, int newval);

/*
 * State.
 */

/* Is the signal handler in use? */
#define cSunMiscSignal_CLOSED (0)
#define cSunMiscSignal_OPEN   (1)
static volatile int cSunMiscSignal_state = cSunMiscSignal_CLOSED;

/* A table of counters indexed by signal number. */
static volatile long cSunMiscSignal_table[NSIG];

/* A semaphore to notify of increments to the map. */
static sem_t* cSunMiscSignal_semaphore = NULL;
#ifdef __linux__
static sem_t cSunMiscSignal_semaphore_value;
#endif

/*
 * Public functions.
 */

/*
 * Open the Java signal handler mechanism. Multiple isolates may execute this method in parallel
 * but only a single isolate may claim ownership.
 *
 * Returns 0 on success, 1 if the signal handler mechanism was already claimed by another isolate,
 * or some other value if an error occurred during initialization.
 */
int cSunMiscSignal_open() {
	/* Try to claim ownership over the signal handler mechanism. */
	int previousState = cSunMiscSignal_atomicCompareAndSwap_int(&cSunMiscSignal_state, cSunMiscSignal_CLOSED, cSunMiscSignal_OPEN);
	if (previousState != cSunMiscSignal_CLOSED) {
		/* Another isolate already owns the signal handler mechanism. */
		return 1;
	}

	/* Reset all signal counts */
	for (int i = 0; i < NSIG; i++) {
	  cSunMiscSignal_table[i] = 0;
	}

#ifdef __linux__
	/* Linux supports unnamed semaphore. */
	cSunMiscSignal_semaphore = &cSunMiscSignal_semaphore_value;
	if (sem_init(cSunMiscSignal_semaphore, 0, 0) != 0) {
		return -1;
	}
#else
	/* On other platforms (e.g. macOS), use a named semaphore with a process-specific name. */
	char cSunMiscSignal_semaphoreName[NAME_MAX];
	const char* nameFormat = "/cSunMiscSignal-%d";
	int pid = getpid();
	int snprintfResult = snprintf(cSunMiscSignal_semaphoreName, NAME_MAX, nameFormat, pid);
	if (snprintfResult <= 0 || snprintfResult >= NAME_MAX)  {
		return -1;
	}

	/* Initialize the semaphore. */
	int oflag = O_CREAT;
	int mode = (S_IRUSR | S_IWUSR);
	cSunMiscSignal_semaphore = sem_open(cSunMiscSignal_semaphoreName, oflag, mode, 0);
	if (cSunMiscSignal_semaphore == SEM_FAILED) {
		return -1;
	}

	/* Unlink the semaphore so it can be destroyed when it is closed. */
	int unlinkResult = sem_unlink(cSunMiscSignal_semaphoreName);
	if (unlinkResult != 0) {
		return -1;
	}
#endif // __linux__
	return 0;
}

/* Close the Java signal handler mechanism. Returns 0 on success, or some non-zero value if an error occurred. */
int cSunMiscSignal_close() {
#ifdef __linux__
	int semCloseResult = sem_destroy(cSunMiscSignal_semaphore);
#else // __linux__
	int semCloseResult = sem_close(cSunMiscSignal_semaphore);
#endif // __linux__
	if (semCloseResult != 0) {
		return semCloseResult;
	}

	cSunMiscSignal_semaphore = NULL;
	cSunMiscSignal_state = cSunMiscSignal_CLOSED;
	return 0;
}

/* Wait for a notification on the semaphore. */
int cSunMiscSignal_awaitSemaphore() {
	int semWaitResult = sem_wait(cSunMiscSignal_semaphore);
	/* Treat interruption (by a signal handler) like a notification. */
	if (semWaitResult == -1 && errno == EINTR) {
		return 0;
	}
	return semWaitResult;
}

/* Notify a thread waiting on the semaphore. */
int cSunMiscSignal_signalSemaphore() {
	return sem_post(cSunMiscSignal_semaphore);
}

/* Returns true if the signal is in the range of (0..NSIG). */
bool cSunMiscSignal_signalRangeCheck(int index) {
	return index > 0 && index < NSIG;
}

/*
 * Returns the number of the first pending signal, or -1 if no signal is pending. May only be
 * called by a single thread (i.e., the signal dispatcher thread).
 */
int cSunMiscSignal_checkPendingSignal() {
	for (int i = 0; i < NSIG; i++) {
		if (cSunMiscSignal_table[i] > 0) {
			cSunMiscSignal_atomicDecrement(&cSunMiscSignal_table[i]);
			return i;
		}
	}
	return -1;
}

/* Returns a function pointer to the C signal handler. */
sig_t cSunMiscSignal_signalHandlerFunctionPointer() {
	return cSunMiscSignal_signalHandler;
}

/*
 * Private functions.
 */

/* A wrapper around the gcc/clang built-in for atomic add. */
static long cSunMiscSignal_atomicIncrement(volatile long* address) {
	return __sync_fetch_and_add(address, 1);
}

/* A wrapper around the gcc/clang built-in for atomic add. */
static long cSunMiscSignal_atomicDecrement(volatile long* address) {
	return __sync_fetch_and_add(address, -1);
}

/* A wrapper around the gcc/clang built-in for atomic int compare and exchange. */
static int cSunMiscSignal_atomicCompareAndSwap_int(volatile int* ptr, int oldval, int newval) {
	return __sync_val_compare_and_swap(ptr, oldval, newval);
}

/* A wrapper around the gcc/clang built-in for atomic long compare and exchange. */
static long cSunMiscSignal_atomicCompareAndSwap_long(volatile long* ptr, long oldval, long newval) {
	return __sync_val_compare_and_swap(ptr, oldval, newval);
}

/* A signal handler that increments the count for the received signal and notifies on the semaphore. */
static void cSunMiscSignal_signalHandler(int signal) {
	int savedErrno = errno;
	if (cSunMiscSignal_signalRangeCheck(signal)) {
		cSunMiscSignal_atomicIncrement(&cSunMiscSignal_table[signal]);
		cSunMiscSignal_signalSemaphore();
	}
	errno = savedErrno;
}

#endif // !_WIN64
