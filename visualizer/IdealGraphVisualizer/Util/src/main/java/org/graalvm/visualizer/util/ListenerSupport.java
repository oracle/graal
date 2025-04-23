/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.util;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import org.openide.util.Utilities;

import javax.swing.SwingUtilities;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event listener helpers
 */
public class ListenerSupport {
    private static final Logger LOG = Logger.getLogger(ListenerSupport.class.getName());

    /**
     * Creates a listener that only weakly references the delegate. WeakListeners should be used
     * between UI/data layers, so that UI layer can be eventually evicted by GC when it is no longer
     * displayed.
     * <p/>
     * When the reference to the real listener expires (is queued by GC), the implementation will
     * call {@link ChangedEvent#removeListener} to clean up obsolete references.
     * <p/>
     * The returned Listener (proxy) may be needed to manually unregister by calling
     * {@link ChangedEvent#removeListener} as appropriate for the caller.
     * <p/>
     * <b>Important note:</b> as the real Listener is only weakly reachable from the returned proxy
     * <b>and</b> from the event source, do keep a reference to it, otherwise it may be collected
     * prematurely.
     *
     * @param delegate the real listener to be called
     * @param source   event source, will be called to unregister
     * @return listener for explicit listener removal
     */
    public static <T, U extends T> ChangedListener<U> addWeakListener(ChangedListener<T> delegate, ChangedEvent<U> source) {
        ChangedListener<U> l = new CHLImpl<>(delegate, source);
        source.addListener(l);
        return l;
    }

    private static class CHLImpl<T, U extends T> extends WeakReference<ChangedListener<T>> implements Runnable, ChangedListener<U> {
        private final ChangedEvent<U> source;
        private Object sourceData;

        public CHLImpl(ChangedListener<T> delegate, ChangedEvent<U> source) {
            super(delegate, Utilities.activeReferenceQueue());
            this.source = source;
        }

        @Override
        public void changed(U eventData) {
            ChangedListener<T> l = get();
            if (l != null) {
                synchronized (this) {
                    if (sourceData == null) {
                        this.sourceData = eventData;
                    } else if (this.sourceData != eventData) {
                        LOG.log(Level.WARNING, "WeakListener for {0} registered to multiple sources; will not unregister from stale data: {1}",
                                new Object[]{l, eventData});
                    }
                }
                l.changed(eventData);
            }
        }

        /**
         * Unregisters listener from the source. Must synchronize into EDT
         */
        public void run() {
            if (SwingUtilities.isEventDispatchThread()) {
                source.removeListener(this);
            } else {
                SwingUtilities.invokeLater(this);
            }
        }
    }
}
