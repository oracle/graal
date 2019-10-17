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
package com.oracle.svm.core.code;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Every {@link CodeInfo} object has a corresponding {@link CodeInfoTether} that is used for
 * managing the lifecycle of the unmanaged memory that is used for the {@link CodeInfo} object. As
 * long as the {@link CodeInfoTether} is reachable by the GC, the {@link CodeInfo} object and its
 * data will stay alive as well. If the {@link CodeInfoTether} is unreachable, the GC can decide to
 * free the unmanaged memory at any subsequent safepoint.
 * <p>
 * Note however that frames on the stack can be deoptimized at any safepoint check, regardless of
 * the reachability of the code's corresponding {@link CodeInfoTether} object. Later lookups via the
 * instruction pointer can fail, so only the explicitly kept alive {@link CodeInfo} must be used.
 * This also applies to usages within {@link VMOperation}s as a GC can also be triggered there.
 */
public class CodeInfoTether {
    private final UninterruptibleUtils.AtomicInteger count;

    public CodeInfoTether(boolean acquired) {
        this.count = CodeInfoAccess.haveAssertions() ? new UninterruptibleUtils.AtomicInteger(acquired ? 1 : 0) : null;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getCount() {
        return count.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int incrementCount() {
        return count.incrementAndGet();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int decrementCount() {
        return count.decrementAndGet();
    }
}
