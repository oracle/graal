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
 * Functions needed to handle signals for Java.
 *
 * The signal handler itself, cSunMiscSignal_countingHandler, runs on a borrowed thread stack and
 * will not have access to any VM thread-local information or the Java heap base register. Therefore
 * it is written in C code rather than in Java code.
 *
 * Any data the signal handler references must not be in the Java heap, so it is allocated here. The
 * data consists of a table indexed by signal numbers of atomic counters, and a semaphore for
 * notifying of increments to the values of the counters.
 *
 * The other public functions here are operations on the data allocated here, for the convenience of
 * the code in Target_sun_misc_Signal.
 *
 * The state for handling signals is global to a process, and has no knowledge of isolates. That
 * imposes the restriction that only one isolate at a time may register to receive signals.
 * Otherwise I would have to notify multiple isolates when a signal arrived, which violates the rule
 * against per-isolate knowledge.
 */

/*
 * Forward declarations of functions.
 */

/* Public functions. */
int cSunMiscSignal_open();
int cSunMiscSignal_close();
int cSunMiscSignal_await();
int cSunMiscSignal_post();
int cSunMiscSignal_signalRangeCheck(int const index);
long cSunMiscSignal_getCount(int const signal);
long cSunMiscSignal_decrementCount(int const signal);
sig_t cSunMiscSignal_countingHandlerFunctionPointer();

/* Private functions. */
static void cSunMiscSignal_countingHandler(int const signal);
static int haveSemaphore();
static long cSunMiscSignal_atomicIncrement(volatile long* const address);
static long cSunMiscSignal_atomicDecrementToZero(volatile long* const address);
static int cSunMiscSignal_atomicCompareAndSwap_int(volatile int* const ptr, int const oldval, int const newval);
static long cSunMiscSignal_atomicCompareAndSwap_long(volatile long* const ptr, long const oldval, long const newval);

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

/* Open the C signal handler mechanism. */
int cSunMiscSignal_open() {
	/* Claim ownership. */
	int const previousState = cSunMiscSignal_atomicCompareAndSwap_int(&cSunMiscSignal_state, cSunMiscSignal_CLOSED, cSunMiscSignal_OPEN);
	if (previousState == cSunMiscSignal_CLOSED) {
		/* Reset all signal counts */
	    int i = 0;
	    while (i < NSIG) {
		  cSunMiscSignal_table[i] = 0;
		  i += 1;
	    }
#ifdef __linux__
		/*
		 * Linux supports unnamed semaphore.
		 */
		cSunMiscSignal_semaphore = &cSunMiscSignal_semaphore_value;
		if (sem_init(cSunMiscSignal_semaphore, 0, 0) != 0) {
			cSunMiscSignal_semaphore = NULL;
			return -1;
		}
#else /* __linux__ */
		/*
		 * Use named semaphore elsewhere (e.g. OSX).
		 */
		/* Get a process-specific name for the semaphore. */
		char cSunMiscSignal_semaphoreName[NAME_MAX];
		const char* const nameFormat = "/cSunMiscSignal-%d";
		int const pid = getpid();
		int const snprintfResult = snprintf(cSunMiscSignal_semaphoreName, NAME_MAX, nameFormat, pid);
		if ((snprintfResult <= 0) || (snprintfResult >= NAME_MAX))  {
			return -1;
		}
		/* Initialize the semaphore. */
		int const oflag = O_CREAT;
		int const mode = (S_IRUSR | S_IWUSR);
		cSunMiscSignal_semaphore = sem_open(cSunMiscSignal_semaphoreName, oflag, mode, 0);
		if (cSunMiscSignal_semaphore == SEM_FAILED) {
			return -1;
		}
		/* Unlink the semaphore so it can be destroyed when it is closed. */
		int const unlinkResult = sem_unlink(cSunMiscSignal_semaphoreName);
		if (unlinkResult != 0) {
			return unlinkResult;
		}
#endif /* __linux__ */
		return 0;
	}
	errno = EBUSY;
	return -1;
}

/* Close the C signal handler mechanism. */
int cSunMiscSignal_close() {
	if (haveSemaphore()) {
#ifdef __linux__
		int const semCloseResult = sem_destroy(cSunMiscSignal_semaphore);
#else /* __linux__ */
		int const semCloseResult = sem_close(cSunMiscSignal_semaphore);
#endif /* __linux__ */
		if (semCloseResult != 0) {
			return semCloseResult;
		}
		cSunMiscSignal_semaphore = NULL;
	}

	cSunMiscSignal_state = cSunMiscSignal_CLOSED;
	return 0;
}

/* Wait for a notification on the semaphore. */
int cSunMiscSignal_await() {
	if (haveSemaphore()) {
		int const semWaitResult = sem_wait(cSunMiscSignal_semaphore);
		/* Treat interruption (by a signal handler) like a notification. */
		if (semWaitResult == EINTR) {
			return 0;
		}
		return semWaitResult;
	}
	errno = EINVAL;
	return -1;
}

/* Notify a thread waiting on the semaphore. */
int cSunMiscSignal_post() {
	if (haveSemaphore()) {
		int const semPostResult = sem_post(cSunMiscSignal_semaphore);
		return semPostResult;
	}
	errno = EINVAL;
	return -1;
}

/* Check that an index into the table is in (0 .. NSIG). */
int cSunMiscSignal_signalRangeCheck(int const index) {
	return ((index > 0) && (index < NSIG));
}

/* Return the count of outstanding signals. */
long cSunMiscSignal_getCount(int const signal) {
	if (cSunMiscSignal_signalRangeCheck(signal)) {
		return cSunMiscSignal_table[signal];
	}
	errno = EINVAL;
	return -1;
}

/* Decrement a counter towards zero, given a signal number.
 * Returns the previous value,  or -1 if the signal is out of bounds.
 */
long cSunMiscSignal_decrementCount(int const signal) {
	if (cSunMiscSignal_signalRangeCheck(signal)) {
		return cSunMiscSignal_atomicDecrementToZero(&cSunMiscSignal_table[signal]);
	}
	errno = EINVAL;
	return -1;
}

/* Return the address of the counting signal handler. */
sig_t cSunMiscSignal_countingHandlerFunctionPointer() {
	return cSunMiscSignal_countingHandler;
}

/*
 * Private functions.
 *
 * The functions called from cSunMiscSignal_countingHandler could be macros
 * if I were worried about using stack frames in the handler.
 * Static methods seem to allow the compiler to inline the calls.
 */

/* A signal handler that increments the count for a signal and notifies on the semaphore. */
static void cSunMiscSignal_countingHandler(int signal) {
	int const savedErrno = errno;
	if (cSunMiscSignal_signalRangeCheck(signal)) {
		long const previousValue = cSunMiscSignal_atomicIncrement(&cSunMiscSignal_table[signal]);
		cSunMiscSignal_post();
	}
	errno = savedErrno;
}

/* A wrapper around the gcc/clang built-in for atomic add. */
static long cSunMiscSignal_atomicIncrement(volatile long* const address) {
	return __sync_fetch_and_add(address, 1);
}

/* Do I have a valid semaphore? */
static int haveSemaphore() {
	return ((cSunMiscSignal_semaphore != NULL) && (cSunMiscSignal_semaphore != SEM_FAILED));
}

/* Atomic subtract down to zero.  Returns the previous value. */
static long cSunMiscSignal_atomicDecrementToZero(volatile long* const address) {
	long result = 0;
	long sample;
	do {
		sample = *address;
		/* Check if the decrement is possible. */
		if (sample <= 0) {
			break;
		}
		/* Atomic decrement, returning the previous value. */
		result = cSunMiscSignal_atomicCompareAndSwap_long(address, sample, sample - 1);
	} while (result != sample);
	return result;
}

/* A wrapper around the gcc/clang built-in for atomic int compare and exchange. */
static int cSunMiscSignal_atomicCompareAndSwap_int(volatile int* const ptr, int const oldval, int const newval) {
	return __sync_val_compare_and_swap(ptr, oldval, newval);
}

/* A wrapper around the gcc/clang built-in for atomic long compare and exchange. */
static long cSunMiscSignal_atomicCompareAndSwap_long(volatile long* const ptr, long const oldval, long const newval) {
	return __sync_val_compare_and_swap(ptr, oldval, newval);
}
#endif
