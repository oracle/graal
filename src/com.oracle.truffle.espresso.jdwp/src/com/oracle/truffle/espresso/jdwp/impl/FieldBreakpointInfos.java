/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.FieldRef;

public class FieldBreakpointInfos {

    private final FieldIds ids = new FieldIds();
    private FieldBreakpointInfo[][] infos = new FieldBreakpointInfo[0][0];

    public void addInfo(FieldBreakpointInfo info) {
        FieldRef field = info.getField();
        int index = ids.getId(field, true);

        if (infos.length <= index) {
            // need to make room for one more array then
            FieldBreakpointInfo[][] temp = new FieldBreakpointInfo[infos.length + 1][];
            // copy the current infos
            for (int i = 0; i < infos.length; i++) {
                temp[i] = infos[i];
            }
            infos = temp;
        }
        FieldBreakpointInfo[] array = infos[index];
        if (array == null) {
            // first info in array
            array = new FieldBreakpointInfo[1];
            array[0] = info;
            infos[index] = array;
        } else {
            // not first info for this field
            // expand the inner array, insert and replace
            FieldBreakpointInfo[] temp = new FieldBreakpointInfo[array.length + 1];
            System.arraycopy(array, 0, temp, 0, array.length);
            temp[array.length] = info;
            infos[index] = temp;
        }
    }

    public void removeInfo(int requestId) {
        for (int i = 0; i < infos.length; i++) {
            for (int j = 0; i < infos[i].length; j++) {
                if (infos[i][j].getRequestId() == requestId) {
                    infos[i][j] = null;
                    // currently no cleanup done, so we're leaking space
                }
            }
        }
    }

    public FieldBreakpointInfo[] getInfos(FieldRef field) {
        int index = ids.getId(field, false);

        if (index == -1) {
            return new FieldBreakpointInfo[0];
        } else {
            return infos[index];
        }
    }

    private static class FieldIds {
        private volatile int uniqueId;

        private FieldRef[] fieldRefs = new FieldRef[0];

        public int getId(FieldRef field, boolean create) {
            // lookup in cache
            for (int i = 0; i < fieldRefs.length; i++) {
                // slow lookup path
                FieldRef obj = fieldRefs[i];
                if (obj == field) {
                    return i;
                }
            }
            // cache miss, so generate a new ID
            if (create) {
                return generateUniqueId(field);
            } else {
                return -1;
            }
        }

        private synchronized int generateUniqueId(FieldRef field) {
            int id = uniqueId++;

            FieldRef[] expandedArray = new FieldRef[fieldRefs.length + 1];
            System.arraycopy(fieldRefs, 0, expandedArray, 0, fieldRefs.length);
            expandedArray[fieldRefs.length] = field;
            fieldRefs = expandedArray;
            return id;
        }
    }
}
