/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.regex.Pattern;

public final class RequestFilter {

    private final int requestId;
    private final byte eventKind;
    private Pattern[] classExcludePatterns = new Pattern[0];
    private final KlassRef[] klassRefPatterns;
    private int nextIndex;
    private boolean stepping;
    private int count = 0;
    private Object thread;
    private Pattern[] positivePatterns = new Pattern[0];
    private BreakpointInfo breakpointInfo;
    private long thisFilterId = 0;

    public RequestFilter(int requestId, byte eventKind, int modifiers) {
        this.requestId = requestId;
        this.eventKind = eventKind;
        this.klassRefPatterns = new KlassRef[modifiers];
    }

    public int getRequestId() {
        return requestId;
    }

    public byte getEventKind() {
        return eventKind;
    }

    public void addExcludePattern(Pattern pattern) {
        Pattern[] temp = new Pattern[classExcludePatterns.length + 1];
        System.arraycopy(classExcludePatterns, 0, temp, 0, classExcludePatterns.length);
        temp[classExcludePatterns.length] = pattern;
        classExcludePatterns = temp;
    }

    public void setStepping(boolean stepping) {
        this.stepping = stepping;
    }

    public boolean isStepping() {
        return stepping;
    }

    public void addRefTypeLimit(KlassRef klassRef) {
        klassRefPatterns[nextIndex] = klassRef;
        nextIndex++;
    }

    public boolean isKlassExcluded(KlassRef klass) {
        for (Pattern pattern : classExcludePatterns) {
            if (pattern != null) {
                if (pattern.matcher(klass.getNameAsString()).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addEventCount(int eventCount) {
        this.count = eventCount;
    }

    public int getIgnoreCount() {
        return count;
    }

    public void addThread(Object guestThread) {
        this.thread = guestThread;
    }

    public Object getThread() {
        return thread;
    }

    public void addPositivePattern(Pattern pattern) {
        Pattern[] temp = new Pattern[positivePatterns.length + 1];
        System.arraycopy(positivePatterns, 0, temp, 0, positivePatterns.length);
        temp[positivePatterns.length] = pattern;
        positivePatterns = temp;
    }

    public Pattern[] getIncludePatterns() {
        return positivePatterns;
    }

    public void addBreakpointInfo(BreakpointInfo info) {
        this.breakpointInfo = info;
    }

    public BreakpointInfo getBreakpointInfo() {
        return breakpointInfo;
    }

    public Pattern[] getExcludePatterns() {
        return classExcludePatterns;
    }

    public void addThisFilterId(long thisId) {
        this.thisFilterId = thisId;
    }

    public long getThisFilterId() {
        return thisFilterId;
    }
}
