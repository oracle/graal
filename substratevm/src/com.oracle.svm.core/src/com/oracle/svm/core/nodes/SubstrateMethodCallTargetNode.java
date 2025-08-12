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
package com.oracle.svm.core.nodes;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Extension of {@link MethodCallTargetNode} that adds a {@link JavaMethodProfile} when available.
 * Note that the replacement from {@link MethodCallTargetNode} to
 * {@link SubstrateMethodCallTargetNode} is not mandatory. SVM code can introduce new
 * {@link MethodCallTargetNode}s.
 */
@NodeInfo
public final class SubstrateMethodCallTargetNode extends MethodCallTargetNode {
    public static final NodeClass<SubstrateMethodCallTargetNode> TYPE = NodeClass.create(SubstrateMethodCallTargetNode.class);

    /**
     * Method profile inferred from a static type profile.
     */
    private JavaMethodProfile staticMethodProfile;
    /**
     * Either the static method profile, or a dynamic method profile inferred from, for example,
     * sampling data.
     */
    private JavaMethodProfile methodProfile;

    /*
     * Type profile inferred by the static analysis. We use static profiles to track information
     * about virtual invokes and distinguish analysed and AOT collected profiles.
     */
    private JavaTypeProfile staticTypeProfile;

    public SubstrateMethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, StampPair returnStamp) {
        this(invokeKind, targetMethod, arguments, returnStamp, null);
    }

    public SubstrateMethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, StampPair returnStamp, JavaTypeProfile typeProfile) {
        super(TYPE, invokeKind, targetMethod, arguments, returnStamp, typeProfile);
    }

    public void setProfiles(JavaTypeProfile dynamicTypeProfile, JavaTypeProfile staticTypeProfile, JavaMethodProfile dynamicMethodProfile, JavaMethodProfile staticMethodProfile) {
        this.typeProfile = dynamicTypeProfile;
        this.methodProfile = dynamicMethodProfile;
        this.staticTypeProfile = staticTypeProfile;
        this.staticMethodProfile = staticMethodProfile;
    }

    public void setDynamicProfiles(JavaTypeProfile typeProfile, JavaMethodProfile methodProfile) {
        this.typeProfile = typeProfile;
        this.methodProfile = methodProfile;
    }

    /**
     * Returns a dynamic method profile if one exists. Otherwise, returns a static method profile if
     * one exists. If neither exist, returns null.
     */
    public JavaMethodProfile getMethodProfile() {
        return methodProfile;
    }

    /**
     * Returns a static type profile if one exists. Otherwise, returns null.
     */
    public JavaTypeProfile getStaticTypeProfile() {
        return staticTypeProfile;
    }

    /**
     * Returns a static method profile if one exists. Otherwise, returns null.
     */
    public JavaMethodProfile getStaticMethodProfile() {
        return staticMethodProfile;
    }

    /**
     * Set a dynamic method profile.
     */
    public void setJavaMethodProfile(JavaMethodProfile profile) {
        methodProfile = profile;
    }

    /**
     * Returns true iff {@code this} has a dynamic type profile. A dynamic type profile can be
     * gathered by, for example, instrumentation. A static type profile (as produced by the static
     * analysis) does not count as a dynamic profile.
     */
    public boolean hasDynamicTypeProfile() {
        if (typeProfile == null) {
            return false;
        }
        return !typeProfile.equals(staticTypeProfile);
    }

    /**
     * Returns true iff {@code this} has a dynamic method profile. A dynamic method profile can be
     * gathered by, for example, instrumentation or perf samples. A static method profile (as
     * produced by the static analysis) does not count as a dynamic profile.
     */
    public boolean hasDynamicMethodProfile() {
        if (methodProfile == null) {
            return false;
        }
        return !methodProfile.equals(staticMethodProfile);
    }
}
