/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.RegexBodyNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexProfile;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;

public class TRegexLazyCaptureGroupsRootNode extends RegexBodyNode {

    @Child private TRegexDFAExecutorEntryNode entryNode;
    private final RegexProfile.TracksRegexProfile profiler;

    public TRegexLazyCaptureGroupsRootNode(RegexLanguage language, RegexSource source, TRegexDFAExecutorNode captureGroupNode, RegexProfile.TracksRegexProfile profiler) {
        super(language, source);
        this.entryNode = TRegexDFAExecutorEntryNode.create(captureGroupNode);
        this.profiler = profiler;
    }

    @Override
    public final Void execute(VirtualFrame frame) {
        final Object[] args = frame.getArguments();
        assert args.length == 3;
        final LazyCaptureGroupsResult receiver = (LazyCaptureGroupsResult) args[0];
        final int startIndex = (int) args[1];
        final int max = (int) args[2];
        final int[] result = (int[]) entryNode.execute(receiver.getInput(), receiver.getFromIndex(), startIndex, max);
        if (CompilerDirectives.inInterpreter()) {
            RegexProfile profile = profiler.getRegexProfile();
            profile.profileCaptureGroupAccess(result[1] - result[0], result[1] - (receiver.getFromIndex() + 1));
        }
        receiver.setResult(result);
        return null;
    }

    @Override
    protected String getEngineLabel() {
        return "TRegex cg";
    }
}
