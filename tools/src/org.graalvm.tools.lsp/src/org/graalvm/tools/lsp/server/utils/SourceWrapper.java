/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

public final class SourceWrapper {
    private Source source;
    private boolean parsingSuccessful = false;
    /**
     * Needed to have a strong reference to the RootNode so that it and its children will not be
     * garbage collected.
     */
    // TODO: Review why this needs to be held
    @SuppressWarnings("unused") private CallTarget callTarget;

    public SourceWrapper(Source source) {
        this.setSource(source);
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public boolean isParsingSuccessful() {
        return parsingSuccessful;
    }

    public void setParsingSuccessful(boolean parsingSuccessful) {
        this.parsingSuccessful = parsingSuccessful;
    }

    public void setCallTarget(CallTarget callTarget) {
        this.callTarget = callTarget;
    }
}
