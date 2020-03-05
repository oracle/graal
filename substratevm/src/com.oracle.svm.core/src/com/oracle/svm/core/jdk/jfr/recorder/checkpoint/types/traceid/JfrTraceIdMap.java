/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid;

import java.util.HashMap;
import java.util.Map;

public class JfrTraceIdMap {
    private final HashMap<Object, Long> map = new HashMap<>();
    private final HashMap<String, Long> packageMap = new HashMap<>();

    synchronized long getId(Object key) {
        Long id = map.get(key);
        if (id != null) {
            return id;
        } else {
            return -1;
        }
    }

    synchronized void setId(Object key, long id) {
        this.map.put(key, id);
    }

    synchronized void clearId(Object key) {
        this.map.remove(key);
    }

    synchronized Map<String, Long> getPackageMap() {
        return this.packageMap;
    }
}
