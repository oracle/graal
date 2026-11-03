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
package com.oracle.svm.hosted.snapshot.capnproto.constant;

import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListLoader;
import static com.oracle.svm.hosted.snapshot.capnproto.CapnProtoAdapters.wrapIntListWriter;

import com.oracle.svm.hosted.snapshot.capnproto.generated.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;

public final class CapnProtoRelinkingData {
    public static RelinkingData.Writer writer(Relinking.Builder delegate) {
        return new RelinkingWriterAdapter(delegate);
    }

    public static RelinkingData.Loader loader(Relinking.Reader delegate) {
        return new RelinkingLoaderAdapter(delegate);
    }
}

record RelinkingWriterAdapter(Relinking.Builder delegate) implements RelinkingData.Writer {
    @Override
    public RelinkingData.ClassConstant.Writer initClassConstant() {
        return new ClassConstantWriterAdapter(delegate.initClassConstant());
    }

    @Override
    public RelinkingData.StringConstant.Writer initStringConstant() {
        return new StringConstantWriterAdapter(delegate.initStringConstant());
    }

    @Override
    public RelinkingData.EnumConstant.Writer initEnumConstant() {
        return new EnumConstantWriterAdapter(delegate.initEnumConstant());
    }

    @Override
    public RelinkingData.FieldConstant.Writer initFieldConstant() {
        return new FieldConstantWriterAdapter(delegate.initFieldConstant());
    }

    @Override
    public RelinkingData.MethodTypeConstant.Writer initMethodTypeConstant() {
        return new MethodTypeConstantWriterAdapter(delegate.initMethodTypeConstant());
    }
}

record RelinkingLoaderAdapter(Relinking.Reader delegate) implements RelinkingData.Loader {
    @Override
    public boolean isNotRelinked() {
        return delegate.isNotRelinked();
    }

    @Override
    public boolean isClassConstant() {
        return delegate.isClassConstant();
    }

    @Override
    public RelinkingData.ClassConstant.Loader getClassConstant() {
        return new ClassConstantLoaderAdapter(delegate.getClassConstant());
    }

    @Override
    public boolean isStringConstant() {
        return delegate.isStringConstant();
    }

    @Override
    public RelinkingData.StringConstant.Loader getStringConstant() {
        return new StringConstantLoaderAdapter(delegate.getStringConstant());
    }

    @Override
    public boolean isEnumConstant() {
        return delegate.isEnumConstant();
    }

    @Override
    public RelinkingData.EnumConstant.Loader getEnumConstant() {
        return new EnumConstantLoaderAdapter(delegate.getEnumConstant());
    }

    @Override
    public boolean isFieldConstant() {
        return delegate.isFieldConstant();
    }

    @Override
    public RelinkingData.FieldConstant.Loader getFieldConstant() {
        return new FieldConstantLoaderAdapter(delegate.getFieldConstant());
    }

    @Override
    public boolean isMethodTypeConstant() {
        return delegate.isMethodTypeConstant();
    }

    @Override
    public RelinkingData.MethodTypeConstant.Loader getMethodTypeConstant() {
        return new MethodTypeConstantLoaderAdapter(delegate.getMethodTypeConstant());
    }
}

record ClassConstantWriterAdapter(Relinking.ClassConstant.Builder delegate) implements RelinkingData.ClassConstant.Writer {
    @Override
    public void setTypeId(int value) {
        delegate.setTypeId(value);
    }
}

record ClassConstantLoaderAdapter(Relinking.ClassConstant.Reader delegate) implements RelinkingData.ClassConstant.Loader {
    @Override
    public int getTypeId() {
        return delegate.getTypeId();
    }
}

record StringConstantWriterAdapter(Relinking.StringConstant.Builder delegate) implements RelinkingData.StringConstant.Writer {
    @Override
    public void setValue(String value) {
        delegate.setValue(value);
    }
}

record StringConstantLoaderAdapter(Relinking.StringConstant.Reader delegate) implements RelinkingData.StringConstant.Loader {
    @Override
    public boolean hasValue() {
        return delegate.hasValue();
    }

    @Override
    public String getValue() {
        return delegate.getValue().toString();
    }
}

record EnumConstantWriterAdapter(Relinking.EnumConstant.Builder delegate) implements RelinkingData.EnumConstant.Writer {
    @Override
    public void setEnumClass(String value) {
        delegate.setEnumClass(value);
    }

    @Override
    public void setEnumName(String value) {
        delegate.setEnumName(value);
    }
}

record EnumConstantLoaderAdapter(Relinking.EnumConstant.Reader delegate) implements RelinkingData.EnumConstant.Loader {
    @Override
    public String getEnumClass() {
        return delegate.getEnumClass().toString();
    }

    @Override
    public String getEnumName() {
        return delegate.getEnumName().toString();
    }
}

record FieldConstantWriterAdapter(Relinking.FieldConstant.Builder delegate) implements RelinkingData.FieldConstant.Writer {
    @Override
    public void setOriginFieldId(int value) {
        delegate.setOriginFieldId(value);
    }

    @Override
    public void setRequiresLateLoading(boolean value) {
        delegate.setRequiresLateLoading(value);
    }
}

record FieldConstantLoaderAdapter(Relinking.FieldConstant.Reader delegate) implements RelinkingData.FieldConstant.Loader {
    @Override
    public int getOriginFieldId() {
        return delegate.getOriginFieldId();
    }

    @Override
    public boolean getRequiresLateLoading() {
        return delegate.getRequiresLateLoading();
    }
}

record MethodTypeConstantWriterAdapter(Relinking.MethodTypeConstant.Builder delegate) implements RelinkingData.MethodTypeConstant.Writer {
    @Override
    public void setReturnTypeId(int value) {
        delegate.setReturnTypeId(value);
    }

    @Override
    public SnapshotPrimitiveList.Int.Writer initParameterTypeIds(int size) {
        return wrapIntListWriter(delegate.initParameterTypeIds(size));
    }
}

record MethodTypeConstantLoaderAdapter(Relinking.MethodTypeConstant.Reader delegate) implements RelinkingData.MethodTypeConstant.Loader {
    @Override
    public int getReturnTypeId() {
        return delegate.getReturnTypeId();
    }

    @Override
    public SnapshotPrimitiveList.Int.Loader getParameterTypeIds() {
        return wrapIntListLoader(delegate.getParameterTypeIds());
    }
}
