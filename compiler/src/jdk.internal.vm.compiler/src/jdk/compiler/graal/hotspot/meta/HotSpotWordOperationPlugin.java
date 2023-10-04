/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.meta;

import static jdk.compiler.graal.hotspot.word.HotSpotOperation.HotspotOpcode.POINTER_EQ;
import static jdk.compiler.graal.hotspot.word.HotSpotOperation.HotspotOpcode.POINTER_NE;
import static jdk.compiler.graal.nodes.ConstantNode.forBoolean;
import static org.graalvm.word.LocationIdentity.any;

import jdk.compiler.graal.api.replacements.SnippetReflectionProvider;
import jdk.compiler.graal.bytecode.BridgeMethodUtils;
import jdk.compiler.graal.core.common.memory.BarrierType;
import jdk.compiler.graal.core.common.memory.MemoryOrderMode;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.hotspot.nodes.type.KlassPointerStamp;
import jdk.compiler.graal.hotspot.nodes.type.MetaspacePointerStamp;
import jdk.compiler.graal.hotspot.nodes.type.MethodPointerStamp;
import jdk.compiler.graal.hotspot.nodes.LoadIndexedPointerNode;
import jdk.compiler.graal.hotspot.word.HotSpotOperation;
import jdk.compiler.graal.hotspot.word.HotSpotOperation.HotspotOpcode;
import jdk.compiler.graal.hotspot.word.PointerCastNode;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.ConditionalNode;
import jdk.compiler.graal.nodes.calc.IsNullNode;
import jdk.compiler.graal.nodes.calc.PointerEqualsNode;
import jdk.compiler.graal.nodes.extended.GuardingNode;
import jdk.compiler.graal.nodes.gc.BarrierSet;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.java.LoadIndexedNode;
import jdk.compiler.graal.nodes.memory.ReadNode;
import jdk.compiler.graal.nodes.memory.address.AddressNode;
import jdk.compiler.graal.nodes.type.StampTool;
import jdk.compiler.graal.word.WordOperationPlugin;
import jdk.compiler.graal.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extends {@link WordOperationPlugin} to handle {@linkplain HotSpotOperation HotSpot word
 * operations}.
 */
public class HotSpotWordOperationPlugin extends WordOperationPlugin {
    HotSpotWordOperationPlugin(SnippetReflectionProvider snippetReflection, ConstantReflectionProvider constantReflection, WordTypes wordTypes, BarrierSet barrierSet) {
        super(snippetReflection, constantReflection, wordTypes, barrierSet);
    }

    @Override
    protected LoadIndexedNode createLoadIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        Stamp componentStamp = wordTypes.getWordStamp(arrayType.getComponentType());
        if (componentStamp instanceof MetaspacePointerStamp) {
            return new LoadIndexedPointerNode(componentStamp, array, index, boundsCheck);
        } else {
            return super.createLoadIndexedNode(array, index, boundsCheck);
        }
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            return false;
        }

        HotSpotOperation operation = BridgeMethodUtils.getAnnotation(HotSpotOperation.class, method);
        if (operation == null) {
            processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
            return true;
        }
        processHotSpotWordOperation(b, method, args, operation);
        return true;
    }

    protected void processHotSpotWordOperation(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, HotSpotOperation operation) {
        JavaKind returnKind = method.getSignature().getReturnKind();
        switch (operation.opcode()) {
            case POINTER_EQ:
            case POINTER_NE:
                assert args.length == 2;
                HotspotOpcode opcode = operation.opcode();
                ValueNode left = args[0];
                ValueNode right = args[1];
                assert left.stamp(NodeView.DEFAULT) instanceof MetaspacePointerStamp : left + " " + left.stamp(NodeView.DEFAULT);
                assert right.stamp(NodeView.DEFAULT) instanceof MetaspacePointerStamp : right + " " + right.stamp(NodeView.DEFAULT);
                assert opcode == POINTER_EQ || opcode == POINTER_NE;

                PointerEqualsNode comparison = b.add(new PointerEqualsNode(left, right));
                ValueNode eqValue = b.add(forBoolean(opcode == POINTER_EQ));
                ValueNode neValue = b.add(forBoolean(opcode == POINTER_NE));
                b.addPush(returnKind, ConditionalNode.create(comparison, eqValue, neValue, NodeView.DEFAULT));
                break;

            case IS_NULL:
                assert args.length == 1;
                ValueNode pointer = args[0];
                assert pointer.stamp(NodeView.DEFAULT) instanceof MetaspacePointerStamp;

                LogicNode isNull = b.add(IsNullNode.create(pointer));
                b.addPush(returnKind, ConditionalNode.create(isNull, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                break;

            case FROM_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, PointerCastNode.create(StampFactory.forKind(wordKind), args[0]));
                break;

            case TO_KLASS_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, PointerCastNode.create(KlassPointerStamp.klass(), args[0]));
                break;

            case TO_METHOD_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, PointerCastNode.create(MethodPointerStamp.method(), args[0]));
                break;

            case READ_KLASS_POINTER:
                assert args.length == 2 || args.length == 3;
                Stamp readStamp = KlassPointerStamp.klass();
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 2) {
                    location = any();
                } else {
                    assert args[2].isConstant();
                    location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                }
                ReadNode read = b.add(new ReadNode(address, location, readStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
                b.push(returnKind, read);
                break;

            default:
                throw GraalError.shouldNotReachHere("unknown operation: " + operation.opcode()); // ExcludeFromJacocoGeneratedReport
        }
    }
}
