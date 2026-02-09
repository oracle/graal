/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <papi.h>
#include <pthread.h>
#include <stdlib.h>

/**
 * Initializes the PAPI library.
 *
 * @return JNI_TRUE if the initialization is successful, JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_jdk_graal_compiler_hotspot_replaycomp_HardwarePerformanceCounters_initialize(JNIEnv *env, jclass cls) {
    printf("Initializing PAPI\n");
    int retval = PAPI_library_init(PAPI_VER_CURRENT);
    if (retval != PAPI_VER_CURRENT) {
        fprintf(stderr, "Failed to initialize PAPI %s\n", PAPI_strerror(retval));
        return JNI_FALSE;
    }
    if ((retval = PAPI_thread_init(pthread_self)) != PAPI_OK) {
        fprintf(stderr, "Failed to initialize threads for PAPI %s\n", PAPI_strerror(retval));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/**
 * Creates a new PAPI event set and adds the specified events to it.
 *
 * @param env The JNI environment.
 * @param cls The Java class that this native method belongs to.
 * @param eventNames An array of event names to be added to the event set.
 *
 * @return The handle of the created event set, or PAPI_NULL if an error occurs.
 */
JNIEXPORT jint JNICALL Java_jdk_graal_compiler_hotspot_replaycomp_HardwarePerformanceCounters_createEventSet(JNIEnv *env, jclass cls,
                                                                                                             jobjectArray eventNames) {
    int eventset = PAPI_NULL;
    int retval = PAPI_create_eventset(&eventset);
    if (retval != PAPI_OK) {
        fprintf(stderr, "Error creating an event set: %s\n", PAPI_strerror(retval));
        return PAPI_NULL;
    }
    jsize arrayLen = (*env)->GetArrayLength(env, eventNames);
    for (jsize i = 0; i < arrayLen; i++) {
        jstring eventNameString = (jstring) (*env)->GetObjectArrayElement(env, eventNames, i);
        const char *eventName = (*env)->GetStringUTFChars(env, eventNameString, 0);
        if ((retval = PAPI_add_named_event(eventset, eventName)) != PAPI_OK) {
            fprintf(stderr, "Error adding %s to event set %d: %s\n", eventName, eventset, PAPI_strerror(retval));
            (*env)->ReleaseStringUTFChars(env, eventNameString, eventName);
            (*env)->DeleteLocalRef(env, eventNameString);
            PAPI_destroy_eventset(&eventset);
            return PAPI_NULL;
        }
        (*env)->ReleaseStringUTFChars(env, eventNameString, eventName);
        (*env)->DeleteLocalRef(env, eventNameString);
    }
    return eventset;
}

/**
 * Returns the PAPI_NULL constant.
 *
 * @return The PAPI_NULL constant.
 */
JNIEXPORT jint JNICALL Java_jdk_graal_compiler_hotspot_replaycomp_HardwarePerformanceCounters_getNull(JNIEnv *env, jclass cls) {
    return PAPI_NULL;
}

/**
 * Cleans up and destroys the specified PAPI event set.
 *
 * @param env The JNI environment.
 * @param cls The Java class that this native method belongs to.
 * @param eventset The handle of the event set to be cleaned up and destroyed.
 *
 * @return JNI_TRUE if the operation is successful, JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_jdk_graal_compiler_hotspot_replaycomp_HardwarePerformanceCounters_cleanAndDestroyEventSet(JNIEnv *env, jclass cls,
                                                                                                                          jint eventset) {
    int retval = 0;
    if ((retval = PAPI_cleanup_eventset(eventset)) != PAPI_OK) {
        fprintf(stderr, "Error cleaning up event set %d: %s\n", eventset, PAPI_strerror(retval));
        return JNI_FALSE;
    }
    if ((retval = PAPI_destroy_eventset(&eventset)) != PAPI_OK) {
        fprintf(stderr, "Error destroying event set %d: %s\n", eventset, PAPI_strerror(retval));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/**
 * Starts the measurements for the specified PAPI event set.
 *
 * @param env The JNI environment.
 * @param cls The Java class that this native method belongs to.
 * @param eventset The handle of the event set to be started.
 *
 * @return JNI_TRUE if the operation is successful, JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_jdk_graal_compiler_hotspot_replaycomp_HardwarePerformanceCounters_start(JNIEnv *env, jclass cls, jint eventset) {
    int retval = PAPI_start(eventset);
    if (retval != PAPI_OK) {
        fprintf(stderr, "Error starting measurements for event set %d: %s\n", eventset, PAPI_strerror(retval));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/**
 * Stops the measurements for the specified PAPI event set and returns the counts.
 *
 * @param env The JNI environment.
 * @param cls The Java class that this native method belongs to.
 * @param eventset The handle of the event set to be stopped.
 *
 * @return A Java long array containing the event counts, or NULL if an error occurs.
 */
JNIEXPORT jlongArray JNICALL Java_jdk_graal_compiler_hotspot_replaycomp_HardwarePerformanceCounters_stop(JNIEnv *env, jclass cls, jint eventset) {
    int numberOfEvents = 0;
    int retval = PAPI_list_events(eventset, NULL, &numberOfEvents);
    if (retval != PAPI_OK || numberOfEvents <= 0) {
        fprintf(stderr, "Error: unable to retrieve event count for event set %d: %s\n", eventset, PAPI_strerror(retval));
        return NULL;
    }
    long long *counts = (long long *) malloc(numberOfEvents * sizeof(long long));
    if (counts == NULL) {
        fprintf(stderr, "Error: out of memory for counts\n");
        return NULL;
    }
    retval = PAPI_stop(eventset, counts);
    if (retval != PAPI_OK) {
        fprintf(stderr, "Error stopping measurements for event set %d: %s\n", eventset, PAPI_strerror(retval));
        free(counts);
        return NULL;
    }
    jlongArray result = (*env)->NewLongArray(env, numberOfEvents);
    if (result == NULL) {
        fprintf(stderr, "Error: could not allocate Java long array\n");
        free(counts);
        return NULL;
    }
    (*env)->SetLongArrayRegion(env, result, 0, numberOfEvents, (jlong *) counts);
    free(counts);
    return result;
}
