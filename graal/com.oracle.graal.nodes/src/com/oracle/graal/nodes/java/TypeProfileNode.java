/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import jdk.vm.ci.meta.JavaTypeProfile;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;

@NodeInfo
public final class TypeProfileNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<TypeProfileNode> TYPE = NodeClass.create(TypeProfileNode.class);
    private JavaTypeProfile profile;

    protected TypeProfileNode(JavaTypeProfile profile) {
        super(TYPE, StampFactory.forVoid());
        this.profile = profile;
    }

    public static TypeProfileNode createWithoutProfile() {
        return create(null);
    }

    public static TypeProfileNode create(JavaTypeProfile profile) {
        return new TypeProfileNode(profile);
    }

    public Node canonical(CanonicalizerTool tool) {
        if (this.usages().isEmpty()) {
            return null;
        }
        return this;
    }

    public JavaTypeProfile getProfile() {
        return profile;
    }

    public void setProfile(JavaTypeProfile profile) {
        this.profile = profile;
    }
}
