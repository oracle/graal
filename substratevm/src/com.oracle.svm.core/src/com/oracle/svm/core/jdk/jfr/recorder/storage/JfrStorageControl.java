/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.storage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class JfrStorageControl {

    private final int memoryDiscardThreshhold;
    private final ReentrantLock bufferLock;

    private final int toDiskThreshhold = 0;
    private int scavengeThreshhold = 0;
    private boolean toDisk = false;

    private int fullCount = 0;
    private final AtomicInteger deadCount = new AtomicInteger(0);

    public JfrStorageControl(int memoryDiscardThreshhold, ReentrantLock bufferLock) {
        this.memoryDiscardThreshhold = memoryDiscardThreshhold;
        this.bufferLock = bufferLock;
    }

    public boolean shouldScavenge() {
        return deadCount() >= this.scavengeThreshhold;
    }

    private int deadCount() {
        return this.deadCount.get();
    }

    public void setScavengeThreshhold(int scavengeThreshhold) {
        this.scavengeThreshhold = scavengeThreshhold;
    }

    public boolean incrementFull() {
        assert (bufferLock.isHeldByCurrentThread());
        fullCount++;

        return toDisk() && this.fullCount > this.toDiskThreshhold;
    }

    public int decrementFull() {
        assert (bufferLock.isHeldByCurrentThread());
        return --fullCount;
    }

    public int incrementDead() {
        return deadCount.incrementAndGet();
    }

    public boolean shouldDiscard() {
        return !toDisk() && fullCount() >= this.memoryDiscardThreshhold;
    }

    public int fullCount() {
        return this.fullCount;
    }

    public boolean toDisk() {
        return this.toDisk;
    }

    public void setToDisk(boolean toDisk) {
        this.toDisk = toDisk;
    }
}
