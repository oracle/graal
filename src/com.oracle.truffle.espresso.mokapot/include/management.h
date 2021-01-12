/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
#ifndef _MANAGEMENT_H
#define _MANAGEMENT_H

#include <trufflenfi.h>
#include <jni.h>

JNIEXPORT void* JNICALL initializeManagementContext(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *), const int version);

JNIEXPORT void JNICALL disposeManagementContext(TruffleEnv *truffle_env, void *management_ptr, int version);

/* 
 * JMM interface changes dramatically between 8 and 11, changing
 * functions offset, thus breaking compatibility. 
 * 
 * Since the differentiation between an espresso 8 or espresso 11 is 
 * done at runtime, we need to provide the native part of espresso with
 * a way to choose which of the interface to create, depending on the 
 * requested version.
 */

void* initializeManagementContext1(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *));

void disposeManagementContext1(TruffleEnv *truffle_env, void *management_ptr);

void* initializeManagementContext2(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *));

void disposeManagementContext2(TruffleEnv *truffle_env, void *management_ptr);

void* initializeManagementContext3(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *));

void disposeManagementContext3(TruffleEnv *truffle_env, void *management_ptr);

#endif // _MANAGEMENT_H
