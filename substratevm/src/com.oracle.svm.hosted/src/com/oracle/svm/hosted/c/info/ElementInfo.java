/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.info;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.oracle.svm.util.ClassUtil;

public abstract class ElementInfo {

    protected final String name;
    private final List<ElementInfo> children;
    protected ElementInfo parent;

    private static final String ID_DELIMINATOR = ":";

    protected ElementInfo(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public ElementInfo getParent() {
        return parent;
    }

    public List<ElementInfo> getChildren() {
        return children;
    }

    protected <T extends ElementInfo> T adoptChild(T newChild) {
        assert newChild.parent == null;
        newChild.parent = this;
        children.add(newChild);
        return newChild;
    }

    protected void adoptChildren(Collection<? extends ElementInfo> newChildren) {
        for (ElementInfo child : newChildren) {
            adoptChild(child);
        }
    }

    public void mergeChildrenAndDelete(ElementInfo target) {
        for (ElementInfo child : children) {
            assert child.parent == this;
            child.parent = target;
            target.children.add(child);
        }
        assert parent.children.contains(this);
        parent.children.remove(this);
    }

    /**
     * Returns a unique identifier string for this element <i>iif</i> this element is a leaf node.
     * <p>
     * </p>
     * Note: This identifier is not unique for intermediate nodes. For example the following enum
     * infos:
     * <ul>
     * <li>{@code NativeCodeInfo:PosixDirectives:EnumInfo:int:EnumConstantInfo:SIGPOLL}</li>
     * <li>{@code NativeCodeInfo:PosixDirectives:EnumInfo:int:EnumConstantInfo:SIGABRT}</li>
     * </ul>
     * 
     * each have an ancestor with the "unique" ID {@code NativeCodeInfo:PosixDirectives:EnumInfo}
     * which actually refers to a different {@link EnumInfo} object, originating from different
     * annotated classes:
     * <ul>
     * <li>{@code com.oracle.svm.core.posix.headers.Signal.LinuxSignalEnum}</li>
     * <li>{@code com.oracle.svm.core.posix.headers.Signal.SignalEnum}</li>
     * </ul>
     */
    public final String getUniqueID() {
        StringBuilder result = new StringBuilder();
        if (getParent() != null) {
            result.append(getParent().getUniqueID()).append(ID_DELIMINATOR);
        }
        result.append(ClassUtil.getUnqualifiedName(getClass())).append(ID_DELIMINATOR).append(getName().replaceAll("\\W", "_"));
        return result.toString();
    }

    public abstract Object getAnnotatedElement();

    public abstract void accept(InfoTreeVisitor visitor);

    @Override
    public final String toString() {
        return getUniqueID();
    }
}
