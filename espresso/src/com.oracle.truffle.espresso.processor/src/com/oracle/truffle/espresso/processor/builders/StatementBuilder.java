/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.processor.builders;

import java.util.ArrayList;
import java.util.List;

public class StatementBuilder extends AbstractCodeBuilder {
    private final List<AbstractCodeBuilder> contents = new ArrayList<>();

    public StatementBuilder addContent(AbstractCodeBuilder content) {
        contents.add(content);
        return this;
    }

    public StatementBuilder addContent(Object... content) {
        contents.add(new RawBuilder(content));
        return this;
    }

    @Override
    void buildImpl(IndentingStringBuilder isb) {
        for (AbstractCodeBuilder code : contents) {
            code.buildImpl(isb);
        }
    }

    private static class RawBuilder extends AbstractCodeBuilder {
        private final Object[] rawString;

        RawBuilder(Object[] rawString) {
            this.rawString = rawString;
        }

        @Override
        void buildImpl(IndentingStringBuilder isb) {
            for (Object s : rawString) {
                isb.append(s.toString());
            }
        }
    }
}
