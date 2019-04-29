/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "common.h"

class NativeAPI {
public:
    TruffleObject (*createNewObject)();
    int (*readIntField)(TruffleObject object, const char *field);
    void (*writeIntField)(TruffleObject object, const char *field, int value);
};

EXPORT NativeAPI *initialize_api(
        TruffleEnv *env,
        TruffleObject (*createNewObject)(),
        int (*readIntField)(TruffleObject, const char *),
        void (*writeIntField)(TruffleObject, const char *, int)) {
    NativeAPI *ret = new NativeAPI();
    ret->createNewObject = env->dupClosureRef(createNewObject);
    ret->readIntField = env->dupClosureRef(readIntField);
    ret->writeIntField = env->dupClosureRef(writeIntField);
    return ret;
}

EXPORT void delete_api(TruffleEnv *env, NativeAPI *api) {
    env->releaseClosureRef(api->createNewObject);
    env->releaseClosureRef(api->readIntField);
    env->releaseClosureRef(api->writeIntField);
    delete api;
}

EXPORT TruffleObject copy_and_increment(TruffleEnv *env, NativeAPI *api, TruffleObject original) {
    TruffleObject copy = api->createNewObject();
    int value = api->readIntField(original, "intField");
    api->writeIntField(copy, "intField", value + 1);
    return env->releaseAndReturn(copy);
}


class NativeStorage {
public:
    TruffleObject obj;
};

EXPORT NativeStorage *keep_new_object(NativeAPI *api) {
    NativeStorage *ret = new NativeStorage();
    ret->obj = api->createNewObject();
    api->writeIntField(ret->obj, "intField", 8472);
    return ret;
}

EXPORT NativeStorage *keep_existing_object(TruffleEnv *env, TruffleObject object) {
    NativeStorage *ret = new NativeStorage();
    ret->obj = env->newObjectRef(object);
    return ret;
}

EXPORT TruffleObject free_and_get_object(TruffleEnv *env, NativeStorage *storage) {
    TruffleObject ret = storage->obj;
    delete storage;
    return env->releaseAndReturn(ret);
}

EXPORT int free_and_get_content(TruffleEnv *env, NativeAPI *api, NativeStorage *storage) {
    int ret = api->readIntField(storage->obj, "intField");
    env->releaseObjectRef(storage->obj);
    delete storage;
    return ret;
}

EXPORT TruffleObject pass_object(TruffleObject objArg, TruffleObject (*getObject)(), TruffleObject (*verifyObject)(TruffleObject, TruffleObject)) {
    TruffleObject objLocal = getObject();
    return verifyObject(objArg, objLocal);
}

EXPORT int compare_existing_object(TruffleEnv *env, NativeStorage *storage1, NativeStorage *storage2) {
    return env->isSameObject(storage1->obj, storage2->obj);
}
