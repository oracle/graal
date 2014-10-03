/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A node that attaches a type profile to a proxied input node.
 */
@NodeInfo
public class TypeProfileProxyNode extends UnaryNode implements IterableNodeType, ValueProxy {

    protected JavaTypeProfile profile;
    protected transient ResolvedJavaType lastCheckedType;
    protected transient JavaTypeProfile lastCheckedProfile;

    public static ValueNode proxify(ValueNode object, JavaTypeProfile profile) {
        if (StampTool.isExactType(object)) {
            return object;
        }
        if (profile == null) {
            // No profile, so create no node.
            return object;
        }
        if (profile.getTypes().length == 0) {
            // Only null profiling is not beneficial enough to keep the node around.
            return object;
        }
        return object.graph().addWithoutUnique(create(object, profile));
    }

    public static ValueNode create(ValueNode object, JavaTypeProfile profile) {
        return USE_GENERATED_NODES ? new TypeProfileProxyNodeGen(object, profile) : new TypeProfileProxyNode(object, profile);
    }

    protected TypeProfileProxyNode(ValueNode value, JavaTypeProfile profile) {
        super(value.stamp(), value);
        this.profile = profile;
    }

    public JavaTypeProfile getProfile() {
        return profile;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(getValue().stamp());
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (StampTool.isExactType(forValue)) {
            // The profile is useless - we know the type!
            return forValue;
        } else if (forValue instanceof TypeProfileProxyNode) {
            TypeProfileProxyNode other = (TypeProfileProxyNode) forValue;
            JavaTypeProfile otherProfile = other.getProfile();
            if (otherProfile == lastCheckedProfile) {
                // We have already incorporated the knowledge about this profile => abort.
                return this;
            }
            lastCheckedProfile = otherProfile;
            JavaTypeProfile newProfile = this.profile.restrict(otherProfile);
            if (newProfile.equals(otherProfile)) {
                // We are useless - just use the other proxy node.
                Debug.log("Canonicalize with other proxy node.");
                return forValue;
            }
            if (newProfile != this.profile) {
                Debug.log("Improved profile via other profile.");
                return TypeProfileProxyNode.create(forValue, newProfile);
            }
        } else if (StampTool.typeOrNull(forValue) != null) {
            ResolvedJavaType type = StampTool.typeOrNull(forValue);
            ResolvedJavaType uniqueConcrete = type.findUniqueConcreteSubtype();
            if (uniqueConcrete != null) {
                // Profile is useless => remove.
                Debug.log("Profile useless, there is enough static type information available.");
                return forValue;
            }
            if (Objects.equals(type, lastCheckedType)) {
                // We have already incorporate the knowledge about this type => abort.
                return this;
            }
            lastCheckedType = type;
            JavaTypeProfile newProfile = this.profile.restrict(type, StampTool.isObjectNonNull(forValue));
            if (newProfile != this.profile) {
                Debug.log("Improved profile via static type information.");
                if (newProfile.getTypes().length == 0) {
                    // Only null profiling is not beneficial enough to keep the node around.
                    return forValue;
                }
                return TypeProfileProxyNode.create(forValue, newProfile);
            }
        }
        return this;
    }

    @Override
    public ValueNode getOriginalNode() {
        return getValue();
    }
}
