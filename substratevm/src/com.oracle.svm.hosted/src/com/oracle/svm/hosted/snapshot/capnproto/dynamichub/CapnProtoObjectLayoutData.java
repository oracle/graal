/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.snapshot.capnproto.dynamichub;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.ObjectLayout;
import com.oracle.svm.hosted.snapshot.dynamichub.ObjectLayoutData;

public final class CapnProtoObjectLayoutData {
    public static ObjectLayoutData.Writer writer(ObjectLayout.Builder delegate) {
        return new ObjectLayoutWriterAdapter(delegate);
    }

    public static ObjectLayoutData.Loader loader(ObjectLayout.Reader delegate) {
        return new ObjectLayoutLoaderAdapter(delegate);
    }
}

record ObjectLayoutWriterAdapter(ObjectLayout.Builder delegate) implements ObjectLayoutData.Writer {
    @Override
    public void setInstalled(boolean value) {
        delegate.setInstalled(value);
    }

    @Override
    public void setLayoutEncoding(int value) {
        delegate.setLayoutEncoding(value);
    }

    @Override
    public void setMonitorOffset(int value) {
        delegate.setMonitorOffset(value);
    }

    @Override
    public void setIdentityHashOffset(int value) {
        delegate.setIdentityHashOffset(value);
    }
}

record ObjectLayoutLoaderAdapter(ObjectLayout.Reader delegate) implements ObjectLayoutData.Loader {
    @Override
    public boolean getInstalled() {
        return delegate.getInstalled();
    }

    @Override
    public int getLayoutEncoding() {
        return delegate.getLayoutEncoding();
    }

    @Override
    public int getMonitorOffset() {
        return delegate.getMonitorOffset();
    }

    @Override
    public int getIdentityHashOffset() {
        return delegate.getIdentityHashOffset();
    }
}
