/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import org.graalvm.component.installer.CatalogIterable.RemoteComponentParam;

public class DownloadURLIterable implements ComponentIterable {
    private final Feedback feedback;
    private final CommandInput input;
    private boolean verifyJars;

    public DownloadURLIterable(Feedback feedback, CommandInput input) {
        this.feedback = feedback;
        this.input = input;
        this.verifyJars = false;
    }

    @Override
    public Iterator<ComponentParam> iterator() {
        return new It();
    }

    @Override
    public void setVerifyJars(boolean verify) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    class It implements Iterator<ComponentParam> {

        @Override
        public boolean hasNext() {
            return input.hasParameter();
        }

        @Override
        public ComponentParam next() {
            String s = input.nextParameter();
            URL u;
            try {
                u = new URL(s);
            } catch (MalformedURLException ex) {
                throw feedback.failure("URL_InvalidDownloadURL", ex, s, ex.getLocalizedMessage());
            }
            boolean progress = input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null;
            RemoteComponentParam p = new RemoteComponentParam(u, s, s, feedback, progress);
            p.setVerifyJars(verifyJars);
            return p;
        }
    }

}
