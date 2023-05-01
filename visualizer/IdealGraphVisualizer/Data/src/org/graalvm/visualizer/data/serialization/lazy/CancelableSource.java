/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import org.graalvm.visualizer.data.FolderElement;

/**
 * Simple data source, which checks for cancelled status and throws if the operation was cancelled
 * externally. Works with {@link Feedback} as well as with {@link ParseMonitor}.
 */
public final class CancelableSource extends BinarySource implements ParseMonitor {
    private final StreamEntry entry;
    private final Feedback feedback;
    private final ParseMonitor delegate;
    
    public CancelableSource(Object id, ParseMonitor delegate, ReadableByteChannel channel) {
        super(id, channel);
        this.delegate = delegate;
        this.entry = null;
        this.feedback = null;
    }

    CancelableSource(Object id, StreamEntry entry, Feedback feedback, ReadableByteChannel channel) {
        super(id, channel);
        this.entry = entry;
        this.feedback = feedback;
        this.delegate = null;
    }
    
    private int pos() {
        return (int)getMarkRelative();
    }

    @Override
    public void updateProgress() {
        if (feedback != null) {
            feedback.reportProgress(pos(), (int) entry.unfinishedSize(), null);
        }
    }

    @Override
    public void setState(String state) {
        if (feedback != null) {
            feedback.reportProgress(pos(), (int) entry.unfinishedSize(), state);
        }
    }

    @Override
    public boolean isCancelled() {
        if (feedback != null) {
            return feedback.isCancelled();
        } else if (delegate != null) {
            return delegate.isCancelled();
        } else {
            return false;
        }
    }

    @Override
    protected void fill() throws IOException {
        if (isCancelled()) {
            throw new InterruptedIOException();
        }
        super.fill();
    }

    @Override
    public void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage) {
        if (feedback != null) {
            feedback.reportError(parents, parentNames, name, errorMessage);
        } else if (delegate != null) {
            delegate.reportError(parents, parentNames, name, errorMessage);
        }
    }
}
