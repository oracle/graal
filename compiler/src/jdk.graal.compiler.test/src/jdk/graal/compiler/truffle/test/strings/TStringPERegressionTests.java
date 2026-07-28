/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.truffle.test.PartialEvaluationTest;

public class TStringPERegressionTests extends PartialEvaluationTest {

    @Test
    public void testSubstringWithMutableAndImmutableReceiver() {
        byte[] bytes = {'a', 'b', 'c', 'd', 'e'};
        TruffleString immutable = TruffleString.fromByteArrayUncached(bytes, UTF_8);
        MutableTruffleString mutable = MutableTruffleString.fromByteArrayUncached(bytes, 0, bytes.length, UTF_8, true);
        RootNode root = new RootNode(null) {

            @Child TruffleString.SubstringNode substringNode = TruffleString.SubstringNode.create();
            @Child TruffleString.SubstringByteIndexNode substringByteIndexNode = TruffleString.SubstringByteIndexNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                AbstractTruffleString string = (AbstractTruffleString) frame.getArguments()[0];
                TruffleString substring = substringNode.execute(string, 1, 2, UTF_8, true);
                TruffleString substringByteIndex = substringByteIndexNode.execute(string, 1, 2, UTF_8, true);
                return substring.byteLength(UTF_8) + substringByteIndex.byteLength(UTF_8);
            }
        };
        OptimizedCallTarget callTarget = (OptimizedCallTarget) root.getCallTarget();
        callTarget.call(immutable);
        callTarget.call(mutable);
        partialEval(callTarget, new Object[]{immutable});
        partialEval(callTarget, new Object[]{mutable});
    }
}
