/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

public class ObjectCloneSnippets implements Snippets {

    public static final Method instanceCloneMethod = getCloneMethod("instanceClone");
    public static final Method arrayCloneMethod = getCloneMethod("arrayClone");
    public static final Method genericCloneMethod = getCloneMethod("genericClone");

    private static Method getCloneMethod(String name) {
        try {
            return ObjectCloneSnippets.class.getDeclaredMethod(name, Object.class);
        } catch (SecurityException | NoSuchMethodException e) {
            throw new GraalInternalError(e);
        }
    }

    private static Object instanceClone(Object src, Word hub, int layoutHelper) {
        int instanceSize = layoutHelper;
        Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION);
        Object result = NewObjectSnippets.allocateInstance(instanceSize, hub, prototypeMarkWord, false);

        for (int offset = instanceHeaderSize(); offset < instanceSize; offset += wordSize()) {
            /*
             * TODO atomicity problem on 32-bit architectures: The JVM spec requires double values
             * to be copied atomically, but here they are copied as two 4-byte word values.
             */
            ObjectAccess.writeWord(result, offset, ObjectAccess.readWord(src, offset, ANY_LOCATION), ANY_LOCATION);
        }

        return result;
    }

    private static Object arrayClone(Object src, Word hub, int layoutHelper) {
        int arrayLength = ArrayLengthNode.arrayLength(src);
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift()) & layoutHelperLog2ElementSizeMask();
        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift()) & layoutHelperHeaderSizeMask();
        int sizeInBytes = NewObjectSnippets.computeArrayAllocationSize(arrayLength, wordSize(), headerSize, log2ElementSize);

        Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION);
        Object result = NewObjectSnippets.allocateArray(hub, arrayLength, prototypeMarkWord, headerSize, log2ElementSize, false);

        for (int offset = headerSize; offset < sizeInBytes; offset += wordSize()) {
            /*
             * TODO atomicity problem on 32-bit architectures: The JVM spec requires double values
             * to be copied atomically, but here they are copied as two 4-byte word values.
             */
            ObjectAccess.writeWord(result, offset, ObjectAccess.readWord(src, offset, ANY_LOCATION), ANY_LOCATION);
        }
        return result;
    }

    private static Word getAndCheckHub(Object src) {
        Word hub = loadHub(src);
        if (!(src instanceof Cloneable)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return hub;
    }

    @Snippet
    public static Object instanceClone(Object src) {
        instanceCloneCounter.inc();
        Word hub = getAndCheckHub(src);
        return instanceClone(src, hub, hub.readInt(layoutHelperOffset(), FINAL_LOCATION));
    }

    @Snippet
    public static Object arrayClone(Object src) {
        arrayCloneCounter.inc();
        Word hub = getAndCheckHub(src);
        int layoutHelper = hub.readInt(layoutHelperOffset(), FINAL_LOCATION);
        return arrayClone(src, hub, layoutHelper);
    }

    @Snippet
    public static Object genericClone(Object src) {
        genericCloneCounter.inc();
        Word hub = getAndCheckHub(src);
        int layoutHelper = hub.readInt(layoutHelperOffset(), FINAL_LOCATION);
        if (probability(LIKELY_PROBABILITY, layoutHelper < 0)) {
            genericArrayCloneCounter.inc();
            return arrayClone(src, hub, layoutHelper);
        } else {
            genericInstanceCloneCounter.inc();
            return instanceClone(src, hub, layoutHelper);
        }
    }

    private static final SnippetCounter.Group cloneCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("Object.clone") : null;
    private static final SnippetCounter instanceCloneCounter = new SnippetCounter(cloneCounters, "instanceClone", "clone snippet for instances");
    private static final SnippetCounter arrayCloneCounter = new SnippetCounter(cloneCounters, "arrayClone", "clone snippet for arrays");
    private static final SnippetCounter genericCloneCounter = new SnippetCounter(cloneCounters, "genericClone", "clone snippet for arrays and instances");

    private static final SnippetCounter.Group genericCloneCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("Object.clone generic snippet") : null;
    private static final SnippetCounter genericInstanceCloneCounter = new SnippetCounter(genericCloneCounters, "genericInstanceClone", "generic clone implementation took instance path");
    private static final SnippetCounter genericArrayCloneCounter = new SnippetCounter(genericCloneCounters, "genericArrayClone", "generic clone implementation took array path");

}
