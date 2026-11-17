/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include "common.h"
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <trufflenfi.h>

EXPORT void native_just_call(void (*callback)()) {
    callback();
}

EXPORT void native_just_call_2(void (*callback1)(), void (*callback2)()) {
    callback1();
    callback2();
}

EXPORT void native_call_after_exception(void (*fail)(), int (*success)(), void (*observe)(int)) {
    fail();
    observe(success());
}

EXPORT int native_exception_check(TruffleEnv *env) {
    return (*env)->exceptionCheck(env);
}

EXPORT void native_exception_flag_after_callback(TruffleEnv *env, void (*fail)(), void (*success)(), void (*observe)(int, int)) {
    fail();
    int after_failure = (*env)->exceptionCheck(env);
    success();
    int after_success = (*env)->exceptionCheck(env);
    observe(after_failure, after_success);
}

EXPORT void native_object_after_exception(TruffleEnv *env, void (*fail)(), TruffleObject (*success)(), TruffleObject expected, void (*observe)(int)) {
    fail();
    TruffleObject result = success();
    int same = result != NULL && (*env)->isSameObject(env, result, expected);
    if (result != NULL) {
        (*env)->releaseObjectRef(env, result);
    }
    observe(same);
}

EXPORT void native_string_after_exception(void (*fail)(), char *(*success)(), void (*observe)(int)) {
    fail();
    char *result = success();
    int same = result != NULL && strcmp(result, "callback result") == 0;
    free(result);
    observe(same);
}

EXPORT void native_check_exception(TruffleEnv *env, void (*callback)(), void (*onSuccess)()) {
    callback();
    if ((*env)->exceptionCheck(env)) {
        return;
    }

    onSuccess();
}
