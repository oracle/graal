/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include <trufflenfi.h>
#include <stdlib.h>

struct NativeEnv {
    TruffleObject (*createNewObject)();
    int (*readIntField)(TruffleObject object, const char *field);
    void (*writeIntField)(TruffleObject object, const char *field, int value);
};

struct NativeEnv *initialize_env(
        TruffleObject (*createNewObject)(),
        int (*readIntField)(TruffleObject, const char *),
        void (*writeIntField)(TruffleObject, const char *, int)) {
    struct NativeEnv *ret = malloc(sizeof(*ret));
    ret->createNewObject = dupClosureRef(createNewObject);
    ret->readIntField = dupClosureRef(readIntField);
    ret->writeIntField = dupClosureRef(writeIntField);
    return ret;
}

void delete_env(struct NativeEnv *env) {
    releaseClosureRef(env->createNewObject);
    releaseClosureRef(env->readIntField);
    releaseClosureRef(env->writeIntField);
    free(env);
}

TruffleObject copy_and_increment(struct NativeEnv *env, TruffleObject original) {
    TruffleObject copy = env->createNewObject();
    int value = env->readIntField(original, "intField");
    env->writeIntField(copy, "intField", value + 1);
    return releaseAndReturn(copy);
}


struct NativeStorage {
    TruffleObject obj;
};

struct NativeStorage *keep_new_object(struct NativeEnv *env) {
    struct NativeStorage *ret = malloc(sizeof(*ret));
    ret->obj = env->createNewObject();
    env->writeIntField(ret->obj, "intField", 8472);
    return ret;
}

struct NativeStorage *keep_existing_object(TruffleObject object) {
    struct NativeStorage *ret = malloc(sizeof(*ret));
    ret->obj = newObjectRef(object);
    return ret;
}

TruffleObject free_and_get_object(struct NativeStorage *storage) {
    TruffleObject ret = storage->obj;
    free(storage);
    return releaseAndReturn(ret);
}

int free_and_get_content(struct NativeEnv *env, struct NativeStorage *storage) {
    int ret = env->readIntField(storage->obj, "intField");
    releaseObjectRef(storage->obj);
    free(storage);
    return ret;
}

TruffleObject pass_object(TruffleObject objArg, TruffleObject (*getObject)(), TruffleObject (*verifyObject)(TruffleObject, TruffleObject)) {
    TruffleObject objLocal = getObject();
    return verifyObject(objArg, objLocal);
}
