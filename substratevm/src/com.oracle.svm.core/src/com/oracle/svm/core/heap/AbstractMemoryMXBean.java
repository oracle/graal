/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

//Checkstyle: stop
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeCache.CodeInfoVisitor;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;

import sun.management.Util;
//Checkstyle: resume

public abstract class AbstractMemoryMXBean implements MemoryMXBean, NotificationEmitter {
    protected static final long UNDEFINED_MEMORY_USAGE = -1L;

    private final MemoryMXBeanCodeInfoVisitor codeInfoVisitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractMemoryMXBean() {
        this.codeInfoVisitor = new MemoryMXBeanCodeInfoVisitor();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
    }

    @Override
    public int getObjectPendingFinalizationCount() {
        // SVM does not have any finalization support.
        return 0;
    }

    @Override
    public MemoryUsage getNonHeapMemoryUsage() {
        codeInfoVisitor.reset();
        RuntimeCodeInfoMemory.singleton().walkRuntimeMethodsUninterruptibly(codeInfoVisitor);
        long used = codeInfoVisitor.getRuntimeCodeInfoSize().rawValue();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, used, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public boolean isVerbose() {
        return SubstrateGCOptions.PrintGC.getValue();
    }

    @Override
    public void setVerbose(boolean value) {
        RuntimeOptionValues.singleton().update(SubstrateGCOptions.PrintGC, value);
    }

    @Override
    public void gc() {
        System.gc();
    }

    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) {
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0];
    }

    private static final class MemoryMXBeanCodeInfoVisitor implements CodeInfoVisitor {
        private UnsignedWord runtimeCodeInfoSize;

        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryMXBeanCodeInfoVisitor() {
            reset();
        }

        public UnsignedWord getRuntimeCodeInfoSize() {
            return runtimeCodeInfoSize;
        }

        public void reset() {
            runtimeCodeInfoSize = WordFactory.zero();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        public <T extends CodeInfo> boolean visitCode(T codeInfo) {
            runtimeCodeInfoSize = runtimeCodeInfoSize.add(CodeInfoAccess.getCodeAndDataMemorySize(codeInfo)).add(CodeInfoAccess.getNativeMetadataSize(codeInfo));
            return true;
        }
    }
}
