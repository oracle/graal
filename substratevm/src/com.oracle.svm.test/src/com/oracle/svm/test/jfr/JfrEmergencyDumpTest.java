/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;

public abstract class JfrEmergencyDumpTest extends JfrRecordingTest {
    protected static AbstractJfrEmergencyDumpSupport getEmergencyDumpSupport() {
        return (AbstractJfrEmergencyDumpSupport) JfrEmergencyDumpSupport.singleton();
    }

    protected static long getPathBufferAddress(AbstractJfrEmergencyDumpSupport support) {
        return AbstractJfrEmergencyDumpSupport.TestingBackdoor.getPathBufferAddress(support);
    }

    protected static int getEmergencyChunkPathCallCount(AbstractJfrEmergencyDumpSupport support) {
        return AbstractJfrEmergencyDumpSupport.TestingBackdoor.getEmergencyChunkPathCallCount(support);
    }

    protected static void resetEmergencyChunkPathCallCount(AbstractJfrEmergencyDumpSupport support) {
        AbstractJfrEmergencyDumpSupport.TestingBackdoor.resetEmergencyChunkPathCallCount(support);
    }

    protected static void clearCachedCwd(AbstractJfrEmergencyDumpSupport support) {
        AbstractJfrEmergencyDumpSupport.TestingBackdoor.clearCachedCwd(support);
    }

    protected static void clearRepositoryLocation(AbstractJfrEmergencyDumpSupport support) {
        AbstractJfrEmergencyDumpSupport.TestingBackdoor.clearRepositoryLocation(support);
    }
}
