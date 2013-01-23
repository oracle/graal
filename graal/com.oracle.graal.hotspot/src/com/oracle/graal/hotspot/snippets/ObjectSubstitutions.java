/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.snippets;

import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.nodes.extended.UnsafeCastNode.*;
import static com.oracle.graal.snippets.nodes.BranchProbabilityNode.*;

import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.ClassSubstitution.MethodSubstitution;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@link java.lang.Object} methods.
 */
@ClassSubstitution(java.lang.Object.class)
public class ObjectSubstitutions {

    @MethodSubstitution(isStatic = false)
    public static Class<?> getClass(final Object thisObj) {
        Word hub = loadHub(thisObj);
        return unsafeCast(hub.readFinalObject(Word.signed(classMirrorOffset())), Class.class, true, true);
    }

    @MethodSubstitution(isStatic = false)
    public static int hashCode(final Object thisObj) {
        Word mark = loadWordFromObject(thisObj, markOffset());

        // this code is independent from biased locking (although it does not look that way)
        final Word biasedLock = mark.and(biasedLockMaskInPlace());
        if (biasedLock == Word.unsigned(unlockedMask())) {
            probability(0.99);
            int hash = (int) mark.unsignedShiftRight(identityHashCodeShift()).rawValue();
            if (hash != uninitializedIdentityHashCodeValue()) {
                probability(0.99);
                return hash;
            }
        }

        return IdentityHashCodeStubCall.call(thisObj);
    }
}
