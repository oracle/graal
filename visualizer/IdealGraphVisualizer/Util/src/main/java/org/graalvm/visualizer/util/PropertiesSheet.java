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

import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Property;
import org.openide.explorer.propertysheet.ExPropertyEditor;
import org.openide.explorer.propertysheet.PropertyEnv;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;

@NbBundle.Messages({
        "CAT_Properties=Properties",
        "ERR_PropertyReadOnly=The property cannot be changed"
})
public class PropertiesSheet {

    /**
     * Provides indexed access to array properties. Note that {@link Property} os a lightweight wrapper, so
     * changes made to Properties are not reflected in existing Property instances. The value has to be fetched
     * every time from the Properties object.
     */
    private static class IndexedObjectProperty extends Node.IndexedProperty<Object[], String> {
        private final Properties props;
        private final String name;

        public IndexedObjectProperty(Properties props, String name) {
            super(Object[].class, String.class);
            this.props = props;
            this.name = name;
        }

        @Override
        public boolean canIndexedRead() {
            return true;
        }

        @Override
        public String getIndexedValue(int i) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            Object[] o = props.get(name, Object[].class);
            if (o == null) {
                return null;
            }
            int l = Array.getLength(o);
            if (i >= l) {
                throw new ArrayIndexOutOfBoundsException(i);
            }
            return Objects.toString(((Object[]) o)[i]);
        }

        @Override
        public boolean canIndexedWrite() {
            return false;
        }

        @Override
        public void setIndexedValue(int i, String e) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            throw new UnsupportedOperationException(Bundle.ERR_PropertyReadOnly());
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public Object[] getValue() throws IllegalAccessException, InvocationTargetException {
            return props.get(name, Object[].class);
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public void setValue(Object[] t) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            throw new UnsupportedOperationException(Bundle.ERR_PropertyReadOnly());
        }
    }

    static final class IndexedListProperty extends Node.IndexedProperty<Object[], Object> {
        private final Properties props;
        private final String name;

        public IndexedListProperty(Properties props, String name, Class itemType) {
            super(Object[].class, itemType);
            this.props = props;
            this.name = name;
        }

        @Override
        public boolean canIndexedRead() {
            return true;
        }

        private List l() {
            return props.get(name, List.class);
        }

        @Override
        public Object getIndexedValue(int i) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            return l().get(i);
        }

        @Override
        public boolean canIndexedWrite() {
            return false;
        }

        @Override
        public void setIndexedValue(int i, Object e) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public Object[] getValue() throws IllegalAccessException, InvocationTargetException {
            return l().toArray();
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public void setValue(Object[] t) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            throw new UnsupportedOperationException("Not supported.");
        }
    }

    private static class IndexedPrimitive extends Node.IndexedProperty<Object, String> {
        private final Properties props;
        private final String name;

        public IndexedPrimitive(Class c, Properties props, String name) {
            super(c, String.class);
            this.props = props;
            this.name = name;
        }

        @Override
        public boolean canIndexedRead() {
            return true;
        }

        @Override
        public String getIndexedValue(int i) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            Object o = props.get(name);
            if (o == null) {
                return null;
            }
            int l = Array.getLength(o);
            if (i >= l) {
                throw new ArrayIndexOutOfBoundsException(i);
            }
            return Objects.toString(Array.get(o, i));
        }

        @Override
        public boolean canIndexedWrite() {
            return false;
        }

        @Override
        public void setIndexedValue(int i, String e) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            throw new UnsupportedOperationException(Bundle.ERR_PropertyReadOnly());
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public Object getValue() throws IllegalAccessException, InvocationTargetException {
            return props.get(name);
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public void setValue(Object t) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            throw new UnsupportedOperationException(Bundle.ERR_PropertyReadOnly());
        }
    }

    public static void initializeSheet(final Properties properties, Sheet s) {

        Sheet.Set set1 = Sheet.createPropertiesSet();
        populateSheetSet(set1, properties);
        s.put(set1);
    }

    private static void populateSheetSet(final Sheet.Set set1, Properties properties) {
        set1.setDisplayName(Bundle.CAT_Properties());
        properties.forEach(p -> {
            if (!p.getName().startsWith("!")) { // NOI18N
                set1.put(createSheetProperty(p.getName(), properties));
            }
        });
    }

    public static Node.Property createSheetProperty(String n, Properties properties) {
        return createSheetProperty(n, null, properties);
    }

    public static Node.Property createSheetProperty(String n, String dispName, Properties properties) {
        final Object v = properties.get(n);
        Node.Property prop;
        Class c;
        if (v != null && (c = v.getClass()).isArray()) {
            if (c.getComponentType().isPrimitive()) {
                prop = new IndexedPrimitive(c, properties, n);
            } else {
                prop = new IndexedObjectProperty(properties, n);
            }
        } else if ((v instanceof List) && (!((List) v).isEmpty())) {
            prop = new IndexedListProperty(properties, n, ((List) v).get(0).getClass());
        } else {
            c = Object.class;
            if (v != null) {
                c = v.getClass();
            }
            prop = new Node.Property<Object>(c) {
                @Override
                public boolean canRead() {
                    return true;
                }

                @Override
                public Object getValue() throws IllegalAccessException, InvocationTargetException {
                    return properties.get(n);
                }

                @Override
                public boolean canWrite() {
                    return false;
                }

                @Override
                public void setValue(Object arg0) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
                    throw new UnsupportedOperationException(Bundle.ERR_PropertyReadOnly());
                }

                @Override
                public PropertyEditor getPropertyEditor() {
                    PropertyEditor pe = super.getPropertyEditor();
                    if (pe == null || v == null) {
                        pe = new StringDelegatePropertyEditor();
                    }
                    return pe;
                }

                ;
            };
        }
        prop.setName(dispName == null ? n : dispName);
        return prop;
    }

    /**
     * Adapts property value to String before passing to the standard Property Editor.
     * Property editor instance registered (presumably) by NB platform is used as
     * a delegate for all calls
     */
    private static class StringDelegatePropertyEditor implements ExPropertyEditor {
        private final PropertyEditor delegate;

        public StringDelegatePropertyEditor() {
            this.delegate = PropertyEditorManager.findEditor(String.class);
        }

        @Override
        public void setValue(Object value) {
            delegate.setValue(Objects.toString(value));
        }

        @Override
        public Object getValue() {
            return delegate.getValue();
        }

        @Override
        public boolean isPaintable() {
            return delegate.isPaintable();
        }

        @Override
        public void paintValue(Graphics gfx, Rectangle box) {
            delegate.paintValue(gfx, box);
        }

        @Override
        public String getJavaInitializationString() {
            return delegate.getJavaInitializationString();
        }

        @Override
        public String getAsText() {
            return delegate.getAsText();
        }

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            delegate.setAsText(text);
        }

        @Override
        public String[] getTags() {
            return delegate.getTags();
        }

        @Override
        public Component getCustomEditor() {
            return delegate.getCustomEditor();
        }

        @Override
        public boolean supportsCustomEditor() {
            return true;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            delegate.addPropertyChangeListener(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            delegate.removePropertyChangeListener(listener);
        }

        @Override
        public void attachEnv(PropertyEnv pe) {
            if (delegate instanceof ExPropertyEditor) {
                ((ExPropertyEditor) delegate).attachEnv(pe);
            }
        }
    }
}
