/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.core.selection;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Christian Wimmer
 */
public class Selection {
    private Map<Class, Object> elements;
    private List<ChangeListener> listeners;
    private Timer eventTimer;

    private ActionListener eventTimerListener = new ActionListener() {
        public void actionPerformed(ActionEvent event) {
            doFireChangeEvent();
        }
    };

    public Selection() {
        elements = new HashMap<Class, Object>();
        listeners = new ArrayList<ChangeListener>();
        eventTimer = new Timer(100, eventTimerListener);
        eventTimer.setRepeats(false);
    }

    private void doPut(Class<?> clazz, Object element) {
        elements.put(clazz, element);
        for (Class<?> i : clazz.getInterfaces()) {
            doPut(i, element);
        }
    }
    
    public void put(Object element) {
        doPut(element.getClass(), element);
        fireChangeEvent();
        SelectionManager.getDefault().fireChangeEvent();
    }

    @SuppressWarnings(value = "unchecked")
    public <T> T get(Class<T> clazz) {
        return (T) elements.get(clazz);
    }


    protected void doFireChangeEvent() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners.toArray(new ChangeListener[listeners.size()])) {
            listener.stateChanged(event);
        }
    }

    protected void fireChangeEvent() {
        eventTimer.restart();
    }

    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }
}
