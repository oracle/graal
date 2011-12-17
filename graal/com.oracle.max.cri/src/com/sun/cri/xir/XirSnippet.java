/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.xir;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.xir.CiXirAssembler.*;

/**
 * Represents a {@link XirTemplate template of XIR} along with the {@link XirArgument arguments} to be passed to the
 * template. The runtime generates such snippets for each bytecode being compiled at the request of the compiler, and
 * the compiler can generate machine code for the XIR snippet.
 */
public class XirSnippet {

    public final XirArgument[] arguments;
    public final XirTemplate template;
    public final Map<XirMark, Mark> marks;

    public XirSnippet(XirTemplate template, XirArgument... inputs) {
        assert template != null;
        this.template = template;
        this.arguments = inputs;
        this.marks = (template.marks != null && template.marks.length > 0) ? new HashMap<XirMark, Mark>() : null;
        assert assertArgumentsCorrect();
    }

    private boolean assertArgumentsCorrect() {
        int argLength = arguments == null ? 0 : arguments.length;
        int paramLength = template.parameters == null ? 0 : template.parameters.length;
        assert argLength == paramLength : "expected param count: " + paramLength + ", actual: " + argLength;
        for (int i = 0; i < arguments.length; i++) {
            assert assertArgumentCorrect(template.parameters[i], arguments[i]) : "mismatch in parameter " + i + ": " + arguments[i] + " instead of " + template.parameters[i];
        }
        return true;
    }

    private boolean assertArgumentCorrect(XirParameter param, XirArgument arg) {
        if (param.kind == CiKind.Illegal || param.kind == CiKind.Void) {
            if (arg != null) {
                return false;
            }
        } else {
            if (arg == null) {
                return false;
            }
            if (arg.constant != null) {
                if (arg.constant != null && arg.constant.kind != param.kind) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {

        StringBuffer sb = new StringBuffer();

        sb.append(template.toString());
        sb.append("(");
        for (XirArgument a : arguments) {
            sb.append(" ");
            sb.append(a);
        }

        sb.append(" )");

        return sb.toString();
    }
}
