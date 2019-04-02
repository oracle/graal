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
package com.oracle.svm.truffle;

//Checkstyle: allow reflection

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.graal.hosted.GraalObjectReplacer;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;

import jdk.vm.ci.meta.MetaAccessProvider;

public class NodeClassFeature implements Feature {

    private GraalObjectReplacer graalObjectReplacer;
    private MetaAccessProvider metaAccess;

    private final Set<Class<?>> registeredClasses = new HashSet<>();

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(TruffleFeature.class, GraalFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess config) {
        graalObjectReplacer = ImageSingletons.lookup(GraalFeature.class).getObjectReplacer();
        config.registerObjectReplacer(this::replaceNodeFieldAccessor);
    }

    @SuppressWarnings("deprecation")
    private Object replaceNodeFieldAccessor(Object source) {
        if (source instanceof com.oracle.truffle.api.nodes.NodeFieldAccessor ||
                        (source instanceof com.oracle.truffle.api.nodes.NodeFieldAccessor[] && ((com.oracle.truffle.api.nodes.NodeFieldAccessor[]) source).length > 0)) {
            throw VMError.shouldNotReachHere("Cannot have NodeFieldAccessor in image, they must be created lazily");

        } else if (source instanceof NodeClass && !(source instanceof SubstrateType)) {
            NodeClass nodeClass = (NodeClass) source;
            NodeClass replacement = graalObjectReplacer.createType(metaAccess.lookupJavaType(nodeClass.getType()));
            assert replacement != null;
            return replacement;
        }
        return source;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        metaAccess = access.getMetaAccess();
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        for (Class<? extends Node> clazz : access.findSubclasses(Node.class)) {
            AnalysisType type;
            try {
                type = access.getMetaAccess().lookupJavaType(clazz);
            } catch (UnsupportedFeatureException ex) {
                /* The node class is not available on Substrate VM, so ignore it. */
                continue;
            }

            if (type.isInstantiated()) {
                for (Class<?> cur = clazz; cur != Object.class; cur = cur.getSuperclass()) {
                    registerUnsafeAccess(access, clazz);
                }

                graalObjectReplacer.createType(type);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void registerUnsafeAccess(DuringAnalysisAccessImpl access, Class<? extends Node> clazz) {
        if (registeredClasses.contains(clazz)) {
            return;
        }
        registeredClasses.add(clazz);

        NodeClass nodeClass = NodeClass.get(clazz);

        for (com.oracle.truffle.api.nodes.NodeFieldAccessor accessor : nodeClass.getFields()) {
            Field field;
            try {
                field = accessor.getDeclaringClass().getDeclaredField(accessor.getName());
            } catch (NoSuchFieldException ex) {
                throw shouldNotReachHere(ex);
            }

            if (accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.PARENT ||
                            accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILD ||
                            accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILDREN) {
                /*
                 * It's a field which represents an edge in the graph. Such fields are written with
                 * Unsafe in the NodeClass, e.g. when making changes in the graph.
                 */
                // TODO register unsafe accessed Truffle nodes in a separate partition?
                access.registerAsUnsafeAccessed(field);
            }

            if (accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.DATA) {
                /*
                 * It's a normal non-child data field of the node. Such fields are written with
                 * Unsafe in the NodeUtil.deepCopyImpl.
                 */
                access.registerAsFrozenUnsafeAccessed(field);
            }

            /* All other fields are only read with Unsafe. */
            access.registerAsAccessed(field);
        }

        access.requireAnalysisIteration();
    }
}

@TargetClass(className = "com.oracle.truffle.api.nodes.NodeClass", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_nodes_NodeClass {

    @Substitute
    public static NodeClass get(Class<?> clazz) {
        CompilerAsserts.neverPartOfCompilation();

        NodeClass nodeClass = (NodeClass) DynamicHub.fromClass(clazz).getMetaType();
        if (nodeClass == null) {
            throw shouldNotReachHere("Unknown node class: " + clazz.getName());
        }
        return nodeClass;
    }
}

@TargetClass(className = "com.oracle.truffle.api.nodes.Node", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_nodes_Node {
    @AnnotateOriginal
    @NeverInline("")
    public native void adoptChildren();
}
