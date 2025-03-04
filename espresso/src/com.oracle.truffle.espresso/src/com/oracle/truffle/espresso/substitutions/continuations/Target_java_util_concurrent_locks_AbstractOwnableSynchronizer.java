/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.continuations;

import static com.oracle.truffle.espresso.substitutions.SubstitutionFlag.IsTrivial;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_java_util_concurrent_locks_AbstractOwnableSynchronizer {
    private Target_java_util_concurrent_locks_AbstractOwnableSynchronizer() {
    }

    @Substitution(hasReceiver = true, flags = {IsTrivial})
    public static void setExclusiveOwnerThread(StaticObject self, @JavaType(Thread.class) StaticObject thread, @Inject Meta meta) {
        if (InterpreterToVM.instanceOf(self, meta.java_util_concurrent_locks_ReentrantLock_Sync) ||
                        InterpreterToVM.instanceOf(self, meta.java_util_concurrent_locks_ReentrantReadWriteLock_Sync)) {
            // We don't want to have continuations that "own" Locks
            // We only handle ReentrantLock$Sync and ReentrantReadWriteLock$Sync which have well
            // known semantics that follow the assertions below.
            // There are other subclasses of AbstractOwnableSynchronizer in the JDK or in
            // user-defined code, but we don't want to guess their semantics or rely on how they use
            // this method for our own correctness.
            EspressoLanguage language = meta.getLanguage();
            if (StaticObject.isNull(thread)) {
                assert StaticObject.notNull(meta.java_util_concurrent_locks_AbstractOwnableSynchronizer_exclusiveOwnerThread.getObject(self));
                language.getThreadLocalState().unblockContinuationSuspension();
            } else if (language.getCurrentVirtualThread() == thread) {
                assert meta.java_util_concurrent_locks_AbstractOwnableSynchronizer_exclusiveOwnerThread.getObject(self) != thread;
                language.getThreadLocalState().blockContinuationSuspension();
            }
        }
        meta.java_util_concurrent_locks_AbstractOwnableSynchronizer_exclusiveOwnerThread.setObject(self, thread);
    }
}
