/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.thread;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.heap.ReferenceMapDecoder;
import com.oracle.svm.core.thread.VMThreads;

/**
 * The class is registered with the {@link GC} to process VM thread local variables of type
 * {@link Object}.
 */
public class VMThreadLocalMTObjectReferenceWalker extends ObjectReferenceWalker {
    /*
     * The field values are assigned after static analysis, so static analysis needs to treat them
     * as unknown.
     */
    @UnknownPrimitiveField public int vmThreadSize = -1;
    @UnknownObjectField(types = {byte[].class}) public byte[] vmThreadReferenceMapEncoding;
    @UnknownPrimitiveField public long vmThreadReferenceMapIndex;

    @Override
    public boolean walk(ObjectReferenceVisitor referenceVisitor) {
        for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
            if (!ReferenceMapDecoder.walkOffsetsFromPointer(vmThread, vmThreadReferenceMapEncoding, vmThreadReferenceMapIndex, referenceVisitor)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean containsPointer(Pointer p) {
        for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
            Pointer threadPtr = (Pointer) vmThread;
            if (p.aboveOrEqual(threadPtr) && p.belowThan(threadPtr.add(vmThreadSize))) {
                return true;
            }
        }
        return false;
    }
}
