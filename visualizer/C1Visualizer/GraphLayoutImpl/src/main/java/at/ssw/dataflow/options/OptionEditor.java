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
package at.ssw.dataflow.options;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/**
 * This class implements a dialog for the modification of options provided
 * by an optionprovider.
 *
 * @author Stefan Loidl
 */
public class OptionEditor extends JDialog {
    private OptionProvider provider;
    private static final String DEFAULTTITEL="Options";
    private static final String BUTCLOSE="Close";

    /** Creates a new instance of OptionEditor */
    public OptionEditor(OptionProvider provider) {
        getContentPane().setLayout(new BorderLayout());
        this.provider=provider;
        setSize(300,300);

        //Center on screen
        setLocationRelativeTo(null);

        setTitle(DEFAULTTITEL);
        setModal(true);
        JTable tab=new JTable();
        JLabel errorLabel=new JLabel();
        errorLabel.setHorizontalAlignment(JLabel.CENTER);
        errorLabel.setForeground(Color.RED);


        tab.setModel(new Model(provider,errorLabel));


        tab.setColumnSelectionAllowed(true);

        JScrollPane sp=new JScrollPane(tab);
        getContentPane().add(sp,BorderLayout.CENTER);

        JPanel p=new JPanel();
        p.setLayout(new GridLayout(2,1));
        p.add(errorLabel);

        JButton b=new JButton(BUTCLOSE);
        b.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                OnClose();
            }
        });
        p.add(b);

        getContentPane().add(p,BorderLayout.SOUTH);
    }

    public void OnClose(){
        setVisible(false);
    }



    protected class Model extends AbstractTableModel{
        private OptionProvider provider;
        private final String[] TableHeader={"Option","Value"};
        private JLabel error;

        public Model(OptionProvider provider, JLabel error){
            this.provider=provider;
            this.error=error;
        }

        public int getRowCount() {
            return provider.getOptionKeys().length;
        }

        public int getColumnCount() {
            return 2;
        }

        public String getColumnName(int col){
            return TableHeader[col];
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            String key=provider.getOptionKeys()[rowIndex];
            if(columnIndex==0){
                return key;
            }else if(columnIndex==1){
                return provider.getOption(key);
            }
            return null;
        }

        public void setValueAt(Object o, int row, int col){
            if(col==0) return;
            String key=provider.getOptionKeys()[row];
            Validator v=provider.getOptionValidator(key);
            if(v.validate(o)){
                error.setText("");
                provider.setOption(key,o);
            } else error.setText(v.getLastErrorMessage());
        }

        public boolean isCellEditable(int row, int col){
            return col!=0;
        }

    }

}
