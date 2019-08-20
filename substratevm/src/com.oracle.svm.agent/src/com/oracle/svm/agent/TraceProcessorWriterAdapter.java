/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.oracle.svm.configure.trace.TraceProcessor;

public class TraceProcessorWriterAdapter extends TraceWriter {
    private final TraceProcessor processor;

    TraceProcessorWriterAdapter(TraceProcessor processor) {
        this.processor = processor;
    }

    TraceProcessor getProcessor() {
        return processor;
    }

    @Override
    void traceEntry(Map<String, Object> entry) {
        processor.processEntry(arraysToLists(entry));
    }

    /** {@link TraceProcessor} expects {@link List} objects instead of plain arrays. */
    private Map<String, Object> arraysToLists(Map<String, Object> map) {
        for (Map.Entry<String, Object> mapEntry : map.entrySet()) {
            if (mapEntry.getValue() instanceof Object[]) {
                mapEntry.setValue(arraysToLists((Object[]) mapEntry.getValue()));
            }
        }
        return map;
    }

    private List<?> arraysToLists(Object[] array) {
        Object[] newArray = Arrays.copyOf(array, array.length);
        for (int i = 0; i < newArray.length; i++) {
            if (newArray[i] instanceof Object[]) {
                newArray[i] = arraysToLists((Object[]) newArray[i]);
            }
        }
        return Arrays.asList(newArray);
    }

    @Override
    public void close() {
    }
}
