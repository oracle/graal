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
package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeCache.CodeInfoVisitor;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;

/**
 * References from the runtime compiled code to the Java heap must be considered either strong or
 * weak references, depending on whether the code is currently on the execution stack. Otherwise,
 * constant folding could create memory leaks as it can make heap objects reachable from code.
 * <p>
 * This class analyzes which runtime-compiled code references otherwise unreachable Java heap
 * objects. Based on that information, it determines which parts of the code cache can be freed and
 * it makes sure that the GC visits all object references of code that may stay alive.
 */
final class RuntimeCodeCacheWalker implements CodeInfoVisitor {
    private final RuntimeCodeCacheReachabilityAnalyzer checkForUnreachableObjectsVisitor;
    private final ObjectReferenceVisitor greyToBlackObjectVisitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeCacheWalker(ObjectReferenceVisitor greyToBlackObjectVisitor) {
        this.checkForUnreachableObjectsVisitor = new RuntimeCodeCacheReachabilityAnalyzer();
        this.greyToBlackObjectVisitor = greyToBlackObjectVisitor;
    }

    @Override
    public <T extends CodeInfo> boolean visitCode(T codeInfo) {
        /*
         * Before this method is called, the GC already visited *all* CodeInfo objects that are
         * reachable from the stack as strong roots. This is is an essential prerequisite for the
         * reachability analysis that is done below. Otherwise, we would wrongly invalidate too much
         * code.
         */

        // Read the (possibly forwarded) tether object.
        Object tether = UntetheredCodeInfoAccess.getTetherUnsafe(codeInfo);
        if (tether != null && !isReachable(tether)) {
            if (CodeInfoAccess.getState(codeInfo) == CodeInfo.STATE_PARTIALLY_FREED) {
                // The tether object is not reachable and the CodeInfo was already invalidated, so
                // we don't need to visit any references and will free the unmanaged memory during
                // this garbage collection.
                CodeInfoAccess.setState(codeInfo, CodeInfo.STATE_UNREACHABLE);
                return true;
            }

            // We don't want to keep heap objects unnecessarily alive, so invalidate and free the
            // CodeInfo if it references otherwise unreachable objects. However, we need to make
            // sure that all the objects that are accessed during the invalidation remain reachable.
            // Those objects can only be collected in a subsequent garbage collection.
            if (referencesUnreachableObjects(codeInfo)) {
                referencesUnreachableObjects(codeInfo);
                RuntimeCodeInfoAccess.walkObjectFields(codeInfo, greyToBlackObjectVisitor);
                CodeInfoAccess.setState(codeInfo, CodeInfo.STATE_READY_FOR_INVALIDATION);
                return true;
            }
        }

        // If the tether object is reachable, then we need to keep the CodeInfo alive. If the tether
        // object is not reachable but all weakly referenced objects are still reachable, then we
        // assume that the CodeInfo is still used and we keep it around.
        RuntimeCodeInfoAccess.walkStrongReferences(codeInfo, greyToBlackObjectVisitor);
        RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, greyToBlackObjectVisitor);
        return true;
    }

    private static boolean isReachable(Object possiblyForwardedObject) {
        return RuntimeCodeCacheReachabilityAnalyzer.isReachable(Word.objectToUntrackedPointer(possiblyForwardedObject));
    }

    private boolean referencesUnreachableObjects(CodeInfo codeInfo) {
        checkForUnreachableObjectsVisitor.initialize();
        RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, checkForUnreachableObjectsVisitor);
        return checkForUnreachableObjectsVisitor.hasUnreachableObjects();
    }
}
