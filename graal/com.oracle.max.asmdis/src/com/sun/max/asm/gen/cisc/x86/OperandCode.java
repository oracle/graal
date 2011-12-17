/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.cisc.x86;

import static com.sun.max.asm.gen.cisc.x86.AddressingMethodCode.*;
import static com.sun.max.asm.gen.cisc.x86.OperandTypeCode.*;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.util.*;

/**
 */
public enum OperandCode implements WrappableSpecification {

    Ap(A, p),
    Cd(C, d),
    Cq(C, q),
    Dd(D, d),
    Dq(D, q),
    Eb(E, b),
    Ed(E, d),
    Ed_q(E, d_q),
    Ev(E, v),
    Ew(E, w),
    Fv(F, v),
    Gb(G, b),
    Gd(G, d),
    Gd_q(G, d_q),
    Gv(G, v),
    Gw(G, w),
    Gq(G, q),
    Gz(G, z),
    Ib(I, b),
    ICb(IC, b),
    Iv(I, v),
    Iw(I, w),
    Iz(I, z),
    Jb(J, b),
    Jv(J, v),
    Jz(J, z),
    Ma(M, a),
    Mb(M, b),
    Md(M, d),
    Md_q(M, d_q),
    Mp(M, p),
    Mq(M, q),
    Mdq(M, dq),
    Ms(M, s),
    Mv(M, v),
    Mw(M, w),
    Nb(N, b),
    Nd(N, d),
    Nd_q(N, d_q),
    Nv(N, v),
    Ob(O, b),
    Ov(O, v),
    Pd(P, d),
    Pdq(P, dq),
    Pq(P, q),
    PRq(PR, q),
    Qd(Q, d),
    Qq(Q, q),
    Rd(R, d),
    Rq(R, q),
    Rv(R, v),
    Sw(S, w),
    Vdq(V, dq),
    Vpd(V, pd),
    Vps(V, ps),
    Vq(V, q),
    Vsd(V, sd),
    Vss(V, ss),
    VRdq(VR, dq),
    VRpd(VR, pd),
    VRps(VR, ps),
    VRq(VR, q),
    Wdq(W, dq),
    Wpd(W, pd),
    Wps(W, ps),
    Wq(W, q),
    Wsd(W, sd),
    Wss(W, ss),
    Xb(X, b),
    Xv(X, v),
    Xz(X, z),
    Yb(Y, b),
    Yv(Y, v),
    Yz(Y, z);

    private final AddressingMethodCode addressingMethodCode;
    private final OperandTypeCode operandTypeCode;

    private OperandCode(AddressingMethodCode addressingMethodCode, OperandTypeCode operandTypeCode) {
        this.addressingMethodCode = addressingMethodCode;
        this.operandTypeCode = operandTypeCode;
    }

    public AddressingMethodCode addressingMethodCode() {
        return addressingMethodCode;
    }

    public OperandTypeCode operandTypeCode() {
        return operandTypeCode;
    }

    public TestArgumentExclusion excludeDisassemblerTestArguments(Argument... arguments) {
        return new TestArgumentExclusion(AssemblyTestComponent.DISASSEMBLER, this, new HashSet<Argument>(Arrays.asList(arguments)));
    }

    public TestArgumentExclusion excludeExternalTestArguments(Argument... arguments) {
        return new TestArgumentExclusion(AssemblyTestComponent.EXTERNAL_ASSEMBLER, this, new HashSet<Argument>(Arrays.asList(arguments)));
    }

    public TestArgumentExclusion excludeExternalTestArguments(Enumerator... argumentEnumerators) {
        final Set<Argument> arguments = new HashSet<Argument>();
        for (Enumerator argumentEnumerator : argumentEnumerators) {
            for (Object e : argumentEnumerator) {
                arguments.add((Argument) e);
            }
        }
        return new TestArgumentExclusion(AssemblyTestComponent.EXTERNAL_ASSEMBLER, this, arguments);
    }

    public ArgumentRange range(long minValue, long maxValue) {
        return new ArgumentRange(this, minValue, maxValue);
    }

    public ArgumentRange externalRange(long minValue, long maxValue) {
        final ArgumentRange argumentRange = new ArgumentRange(this, minValue, maxValue);
        argumentRange.doNotApplyInternally();
        return argumentRange;
    }
}
