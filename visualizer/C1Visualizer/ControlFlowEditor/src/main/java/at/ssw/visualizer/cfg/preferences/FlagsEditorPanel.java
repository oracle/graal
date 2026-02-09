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
package at.ssw.visualizer.cfg.preferences;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.StringTokenizer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author Bernhard Stiftner
 * @author Rumpfhuber Stefan
 */
public class FlagsEditorPanel extends JPanel implements ActionListener,
        ListSelectionListener {

    FlagListModel listModel;
    JList list;
    ColorChooserButton colorButton;
    JButton newButton;
    JButton removeButton;
    JButton upButton;
    JButton downButton;


    /** Creates a new instance of FlagsEditorPanel */
    public FlagsEditorPanel(String flagString) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        listModel = new FlagListModel(flagString);
        list = new JList(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(this);
        add(new JScrollPane(list));

        add(Box.createHorizontalStrut(3));

        Box buttonBox = new Box(BoxLayout.Y_AXIS);
        buttonBox.add(colorButton = new ColorChooserButton());
        buttonBox.add(newButton = new JButton("New..."));
        buttonBox.add(removeButton = new JButton("Remove"));
        buttonBox.add(upButton = new JButton("Up"));
        buttonBox.add(downButton = new JButton("Down"));
        buttonBox.add(Box.createVerticalGlue());
        add(buttonBox);
        layoutButtonContainer(buttonBox);

        colorButton.setColorChooserEnabled(false);
        colorButton.addActionListener(this);
        newButton.addActionListener(this);
        removeButton.addActionListener(this);
        upButton.addActionListener(this);
        downButton.addActionListener(this);

        selectionChanged(-1); // no selection
    }

    /**
     * Ugly helper to make a nice layout for vertically aligned buttons.
     */
    private static void layoutButtonContainer(JComponent buttonContainer) {
        int width = 0;
        int height = 0;

        for (int i=0; i<buttonContainer.getComponentCount(); i++) {
            Component c = buttonContainer.getComponent(i);
            if (c instanceof JButton) {
                JButton b = (JButton)c;
                if (width < b.getPreferredSize().width) {
                    width = b.getPreferredSize().width;
                }
                if (height < b.getPreferredSize().height) {
                    height = b.getPreferredSize().height;
                }
            }
        }

        Dimension commonButtonSize = new Dimension(width, height);

        for (int i=0; i<buttonContainer.getComponentCount(); i++) {
            Component c = buttonContainer.getComponent(i);
            if (c instanceof JButton) {
                JButton b = (JButton)c;
                b.setMinimumSize(commonButtonSize);
                b.setPreferredSize(commonButtonSize);
                b.setMaximumSize(commonButtonSize);
            }
        }

    }

    public String getFlagString() {
        return listModel.getFlagString();
    }

    public void setFlagString(String flagString) {
        listModel.setFlagString(flagString);
    }
    
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == colorButton) {
            changeColor();
        } else if (source == newButton) {
            String s = JOptionPane.showInputDialog(this,
                    "Type in the flag which should fill the block with a specific color");
            if (s == null) {
                return;
            }
            s = s.replace('(', ' ');
            s = s.replace(';', ' ');
            int index = list.getSelectedIndex()+1;
            listModel.insertElementAt(new FlagListItem(s, Color.WHITE), index);
        } else if (source == removeButton) {
            listModel.removeElementAt(list.getSelectedIndex());
        } else if (source == upButton) {
            int index = list.getSelectedIndex();
            if (index == 0) {
                return;
            }
            Object o = listModel.getElementAt(index);
            listModel.removeElementAt(index);
            listModel.insertElementAt(o, index-1);
        } else if (source == downButton) {
            int index = list.getSelectedIndex();
            if (index >= listModel.size()-1) {
                return;
            }
            Object o = listModel.getElementAt(index);
            listModel.removeElementAt(index);
            listModel.insertElementAt(o, index+1);  
        }
    }

    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return; // another event will be fired soon
        }
        selectionChanged(list.getSelectedIndex());
    }

    protected void selectionChanged(int index) { //index is -1 if there is no selection           
        colorButton.setEnabled(index >= 0);
        removeButton.setEnabled(index >= 0);
        upButton.setEnabled(index > 0);
        downButton.setEnabled(index >= 0 && index < listModel.getSize()-1);

        if (index >= 0) {
            FlagListItem item = (FlagListItem)listModel.elementAt(index);
            colorButton.setColor(item.getColor());
            list.setSelectedIndex(index);
        } else {
            colorButton.setColor(getBackground());
        }
    }

    protected void changeColor() {
        int selectedIndex = list.getSelectedIndex();
        FlagListItem item = (FlagListItem)listModel.elementAt(selectedIndex);
        Color c = JColorChooser.showDialog(this, "Choose color", item.getColor());

        if (c != null) {
            item.setColor(c);
            colorButton.setColor(c);
        }
    }

    class FlagListModel extends DefaultListModel {

        public FlagListModel(String flagString) {
            if (flagString != null) {
                setFlagString(flagString);
            }
        }

        public String getFlagString() {
            StringBuffer sb = new StringBuffer();
            Enumeration e = elements();
            while (e.hasMoreElements()) {
                FlagListItem item = (FlagListItem)e.nextElement();
                sb.append(item.getFlagString());
                Color c = item.getColor();
                sb.append("(").append(c.getRed()).append(",").append(c.getGreen()).append(",").append(c.getBlue()).append(")");
                if (e.hasMoreElements()) {
                    sb.append(";");
                }
            }
            return sb.toString();
        }

        public void setFlagString(String flagString) {
            clear();
            StringTokenizer st = new StringTokenizer(flagString, ";");
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                String flag = s.split("\\(")[0];
                Color color = FlagsSetting.toColor(s);
                addElement(new FlagListItem(flag, color));
            }
        }

    }

    class FlagListItem {

        Color color;
        String flagString;

        public FlagListItem(String flagString, Color color) {
            this.flagString = flagString;
            this.color = color;
        }

        public Color getColor() {
            return color;
        }

        public String getFlagString() {
            return flagString;
        }

        public void setColor(Color c) {
            color = c;
        }

        @Override
        public String toString() {
            return flagString;
        }
    }

}
