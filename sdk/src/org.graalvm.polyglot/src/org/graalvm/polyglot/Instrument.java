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
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentImpl;

/**
 * A handle for an <em>instrument</em> installed in an {@link Engine engine}.The instrument is
 * usable from other threads. The handle provides access to the instrument's metadata and allows to
 * {@link #lookup(Class) lookup} instrument specific services.
 * <p>
 * All methods here, as well as instrumentation services in general, can be used safely from any
 * thread.
 *
 * @see Engine#getInstruments()
 * @since 1.0
 */
public final class Instrument {

    final AbstractInstrumentImpl impl;

    Instrument(AbstractInstrumentImpl impl) {
        this.impl = impl;
    }

    /**
     * Gets the id clients can use to acquire this instrument.
     *
     * @return this instrument's unique id
     * @since 1.0
     */
    public String getId() {
        return impl.getId();
    }

    /**
     * Gets a human readable name of this instrument.
     *
     * @return this instrument's user-friendly name
     * @since 1.0
     */
    public String getName() {
        return impl.getName();
    }

    /**
     * Gets the options available for this instrument.
     *
     * @return the options as {@link OptionDescriptors}
     * @since 1.0
     */
    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    /**
     * Gets the version of this instrument.
     *
     * @return this instrument's version
     * @since 1.0
     */
    public String getVersion() {
        return impl.getVersion();
    }

    /**
     * Looks an additional internal service up that is provided by this instrument using a Java
     * type. Please note that services returned by this method are implementation specific and
     * subject to change without notice.
     *
     * @param <T> the type of the internal service
     * @param type class of the service that is being requested
     * @return instance of requested type, <code>null</code> if no such service is available
     * @since 1.0
     */
    public <T> T lookup(Class<T> type) {
        return impl.lookup(type);
    }

}
