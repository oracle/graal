/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr.events;

import com.oracle.svm.core.heap.Heap;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.internal.Type;

@Label("Class Loading Statistics")
@Category("Java Application, Statistics")
@StackTrace(false)
@Name(Type.EVENT_NAME_PREFIX + "ClassLoadingStatistics")
@Period(value = "everyChunk")
public class ClassLoadingStatistics extends Event {

    @Label("Loaded Class Count") @Description("Number of classes loaded since JVM start") long loadedClassCount;

    @Label("Unloaded Class Count") @Description("Number of classes unloaded since JVM start") long unloadedClassCount;

    public static void emitClassLoadingStats() {
        ClassLoadingStatistics classStats = new ClassLoadingStatistics();

        classStats.loadedClassCount = Heap.getHeap().getClassCount();
        classStats.unloadedClassCount = 0;
        classStats.commit();
    }
}
