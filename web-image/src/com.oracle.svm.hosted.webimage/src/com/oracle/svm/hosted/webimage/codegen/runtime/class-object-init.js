/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
/**
 * Initializes the hash code field in an object (or array).
 */
function object_init_hash(target, hashCode) {
    if (hashCode != 0) {
        target[HASH_CODE_FIELD] = hashCode;
    }
}

/**
 * Set the given properties of the target object.
 *
 * fieldListId is an index into the list of field lists that contains property
 * names to which 'values' correspond.
 */
function object_init(target, fieldListId, hashCode, ...values) {
    const names = fieldLists[fieldListId].slice(1);

    if (names.length !== values.length) {
        throw new Error();
    }

    object_init_hash(target, hashCode);

    for (let i = 0; i < names.length; i++) {
        target[names[i][0]] = values[i];
    }
}

function ensureInitialized(hub) {
    $t["com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics"].$m["ensureInitialized"](hub);
}
