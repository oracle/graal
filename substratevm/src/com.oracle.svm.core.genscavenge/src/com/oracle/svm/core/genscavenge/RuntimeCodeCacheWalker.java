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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeCache.CodeInfoVisitor;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.genscavenge.RuntimeCodeCacheReachabilityAnalyzer.UnreachableObjectsException;
import com.oracle.svm.core.util.DuplicatedInNativeCode;

import jdk.graal.compiler.word.Word;

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
    private final GreyToBlackObjRefVisitor greyToBlackObjectVisitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeCacheWalker(GreyToBlackObjRefVisitor greyToBlackObjectVisitor) {
        this.checkForUnreachableObjectsVisitor = new RuntimeCodeCacheReachabilityAnalyzer();
        this.greyToBlackObjectVisitor = greyToBlackObjectVisitor;
    }

    @Override
    @DuplicatedInNativeCode
    public void visitCode(CodeInfo codeInfo) {
        if (RuntimeCodeInfoAccess.areAllObjectsOnImageHeap(codeInfo)) {
            return;
        }

        /*
         * Before this method is called, the GC already visited *all* CodeInfo objects that are
         * reachable from the stack as strong roots. This is an essential prerequisite for the
         * reachability analysis that is done below. Otherwise, we would wrongly invalidate too much
         * code.
         */
        boolean invalidateCodeThatReferencesUnreachableObjects = SubstrateGCOptions.TreatRuntimeCodeInfoReferencesAsWeak.getValue();

        // Read the (possibly forwarded) tether object.
        Object tether = UntetheredCodeInfoAccess.getTetherUnsafe(codeInfo);
        if (tether != null && !isReachable(tether)) {
            int state = CodeInfoAccess.getState(codeInfo);
            if (state == CodeInfo.STATE_REMOVED_FROM_CODE_CACHE) {
                /*
                 * The tether object is not reachable and the CodeInfo was already removed from the
                 * code cache, so we only need to visit references that will be accessed before the
                 * unmanaged memory is freed during this garbage collection.
                 */
                RuntimeCodeInfoAccess.walkObjectFields(codeInfo, greyToBlackObjectVisitor);
                CodeInfoAccess.setState(codeInfo, CodeInfo.STATE_PENDING_FREE);
                return;
            }

            /*
             * We don't want to keep heap objects unnecessarily alive. So, we check if the CodeInfo
             * has weak references to otherwise unreachable objects. If so, we remove the CodeInfo
             * from the code cache and free the CodeInfo during the current safepoint (see
             * RuntimeCodeCacheCleaner). However, we need to make sure that all the objects that are
             * accessed while doing so remain reachable. Those objects can only be collected in a
             * subsequent garbage collection.
             */
            if (state == CodeInfo.STATE_NON_ENTRANT || invalidateCodeThatReferencesUnreachableObjects && state == CodeInfo.STATE_CODE_CONSTANTS_LIVE && hasWeakReferenceToUnreachableObject(codeInfo)) {
                RuntimeCodeInfoAccess.walkObjectFields(codeInfo, greyToBlackObjectVisitor);
                CodeInfoAccess.setState(codeInfo, CodeInfo.STATE_PENDING_REMOVAL_FROM_CODE_CACHE);
                return;
            }
        }

        /*
         * As long as the tether object is strongly reachable, we need to keep the CodeInfo object
         * alive (most likely, someone explicitly acquired the tether, e.g., for stack walking). If
         * the tether is still null, then we also need to keep the CodeInfo object alive as it is
         * not fully initialized yet. If the tether object is not strongly reachable but all weakly
         * referenced objects are still strongly reachable via some other references, then it is
         * safe to keep the CodeInfo object around as it might be used in the future.
         */
        RuntimeCodeInfoAccess.walkStrongReferences(codeInfo, greyToBlackObjectVisitor);
        RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, greyToBlackObjectVisitor);
    }

    private static boolean isReachable(Object possiblyForwardedObject) {
        return RuntimeCodeCacheReachabilityAnalyzer.isReachable(Word.objectToUntrackedPointer(possiblyForwardedObject));
    }

    private boolean hasWeakReferenceToUnreachableObject(CodeInfo codeInfo) {
        try {
            RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, checkForUnreachableObjectsVisitor);
            return false;
        } catch (UnreachableObjectsException e) {
            return true;
        }
    }
}
