/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

public final class Language {

    final AbstractLanguageImpl impl;

    Language(AbstractLanguageImpl impl) {
        this.impl = impl;
    }

    public String getId() {
        return impl.getId();
    }

    public String getName() {
        return impl.getName();
    }

    public String getVersion() {
        return impl.getVersion();
    }

    public boolean isInteractive() {
        return impl.isInteractive();
    }

    public boolean isHost() {
        return impl.isHost();
    }

    public Context createContext() {
        return createContextBuilder().build();
    }

    public Context.Builder createContextBuilder() {
        return new Context.Builder(impl);
    }

    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    Engine getEngine() {
        return impl.getEngineAPI();
    }

}
