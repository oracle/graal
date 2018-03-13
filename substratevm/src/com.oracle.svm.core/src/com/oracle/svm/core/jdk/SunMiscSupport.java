/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.ref.Reference;

/* Allow the import of {@link sun.misc.Cleaner}: Checkstyle: allow reflection. */
import sun.misc.Cleaner;

/** Access to methods in support of {@link sun.misc}. */
public class SunMiscSupport {

    public static void drainCleanerQueue() {
        for (; /* return */;) {
            final Reference<?> entry = Target_sun_misc_Cleaner.dummyQueue.poll();
            if (entry == null) {
                return;
            }
            if (entry instanceof Cleaner) {
                final Cleaner cleaner = (Cleaner) entry;
                cleaner.clean();
            }
        }
    }
}
