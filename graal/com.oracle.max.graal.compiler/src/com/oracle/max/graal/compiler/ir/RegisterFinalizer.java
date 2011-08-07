/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This instruction is used to perform the finalizer registration at the end of the java.lang.Object constructor.
 */
public final class RegisterFinalizer extends StateSplit {
    @Input private Value object;

    public Value object() {
        return object;
    }

    public void setObject(Value x) {
        updateUsages(object, x);
        object = x;
    }

    public RegisterFinalizer(Value object, Graph graph) {
        super(CiKind.Void, graph);
        setObject(object);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitRegisterFinalizer(this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static final CanonicalizerOp CANONICALIZER = new CanonicalizerOp() {

        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            RegisterFinalizer finalizer = (RegisterFinalizer) node;
            Value object = finalizer.object();

            RiType declaredType = object.declaredType();
            RiType exactType = object.exactType();
            if (exactType == null && declaredType != null) {
                exactType = declaredType.exactType();
            }

            boolean needsCheck = true;
            if (exactType != null) {
                // we have an exact type
                needsCheck = exactType.hasFinalizer();
            } else {
                // if either the declared type of receiver or the holder can be assumed to have no finalizers
                if (declaredType != null && !declaredType.hasFinalizableSubclass()) {
                    if (((CompilerGraph) node.graph()).assumptions().recordNoFinalizableSubclassAssumption(declaredType)) {
                        needsCheck = false;
                    }
                }
            }

            if (needsCheck) {
                if (GraalOptions.TraceCanonicalizer) {
                    TTY.println("Could not canonicalize finalizer " + object + " (declaredType=" + declaredType + ", exactType=" + exactType + ")");
                }
            } else {
                if (GraalOptions.TraceCanonicalizer) {
                    TTY.println("Canonicalized finalizer for object " + object);
                }
                return finalizer.next();
            }

            return finalizer;
        }
    };

    @Override
    public void print(LogStream out) {
        out.print("register finalizer ").print(object());
    }

    @Override
    public Node copy(Graph into) {
        RegisterFinalizer x = new RegisterFinalizer(null, into);
        super.copyInto(x);
        return x;
    }
}
