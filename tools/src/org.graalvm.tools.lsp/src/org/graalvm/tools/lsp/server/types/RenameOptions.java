/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import org.graalvm.shadowed.org.json.JSONObject;
import java.util.Objects;

/**
 * Provider options for a [RenameRequest](#RenameRequest).
 */
public class RenameOptions extends WorkDoneProgressOptions {

    RenameOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Renames should be checked and tested before being executed.
     *
     * @since version 3.12.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getPrepareProvider() {
        return jsonData.has("prepareProvider") ? jsonData.getBoolean("prepareProvider") : null;
    }

    public RenameOptions setPrepareProvider(Boolean prepareProvider) {
        jsonData.putOpt("prepareProvider", prepareProvider);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        RenameOptions other = (RenameOptions) obj;
        if (!Objects.equals(this.getPrepareProvider(), other.getPrepareProvider())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneProgress(), other.getWorkDoneProgress())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getPrepareProvider() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getPrepareProvider());
        }
        if (this.getWorkDoneProgress() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getWorkDoneProgress());
        }
        return hash;
    }

    public static RenameOptions create() {
        final JSONObject json = new JSONObject();
        return new RenameOptions(json);
    }
}
