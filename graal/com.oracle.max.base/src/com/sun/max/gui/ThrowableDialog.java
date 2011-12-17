/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;
import javax.swing.border.*;

/**
 * A dialog for displaying the details of an exception. Initially the dialog only shows the exception's
 * {@linkplain Throwable#getMessage() message}. It includes a button for expanding the dialog to also show the stack
 * trace. When the stack trace is shown, pressing the same button hides the stack trace.
 */
public final class ThrowableDialog extends JDialog {

    /**
     * Creates a dialog to display the details of an exception and makes it visible.
     *
     * @param throwable the exception whose details are being displayed
     * @param owner the {@code Frame} from which the dialog is displayed
     * @param title  the {@code String} to display in the dialog's title bar
     */
    public static void show(Throwable throwable, Frame owner, String title) {
        new ThrowableDialog(throwable, owner, title).setVisible(true);
    }

    /**
     * Creates a dialog to display the details of an exception and makes it visible on the
     * {@linkplain SwingUtilities#invokeLater(Runnable) AWT dispatching thread}.
     *
     * @param throwable the exception whose details are being displayed
     * @param owner the {@code Frame} from which the dialog is displayed
     * @param title the {@code String} to display in the dialog's title bar
     */
    public static void showLater(Throwable throwable, Frame owner, String title) {
        final ThrowableDialog dialog = new ThrowableDialog(throwable, owner, title);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                dialog.setVisible(true);
            }
        });
    }

    /**
     * Creates a dialog to display the details of an exception. The dialog is not displayed by this method.
     *
     * @param throwable the exception whose details are being displayed
     * @param owner the {@code Frame} from which the dialog is displayed
     * @param title  the {@code String} to display in the dialog's title bar
     */
    private ThrowableDialog(Throwable throwable, Frame owner, String title) {
        super(owner, title, false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);

        final JToggleButton stackTraceButton = new JToggleButton("Show stack trace");
        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                dispose();
            }
        });

        final JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(closeButton);
        buttonsPanel.add(stackTraceButton);

        final Container mainPane = getContentPane();
        //mainPane.setLayout(new FlowLayout());
        mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.PAGE_AXIS));
        final JLabel message = new JLabel(throwable.getMessage());
        final JPanel messagePanel = new JPanel();
        messagePanel.add(message);
        mainPane.add(messagePanel);
        mainPane.add(buttonsPanel);
        pack();

        final Dimension dialogWithoutStackTracePreferredSize = getPreferredSize();

        final JTextArea stackTraceText = new JTextArea(20, 40);
        throwable.printStackTrace(new PrintWriter(new Writer() {

            @Override
            public void close() throws IOException {
            }

            @Override
            public void flush() throws IOException {
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                stackTraceText.append(new String(cbuf, off, len));
            }
        }));

        final JScrollPane stackTracePanel = new JScrollPane(stackTraceText, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        final Dimension stackTraceTextPreferredSize = stackTraceText.getPreferredSize();
        stackTracePanel.setBorder(new TitledBorder("Stack trace"));
        final Dimension stackTracePanelPreferredSize = new Dimension(
                        stackTraceTextPreferredSize.width + stackTracePanel.getVerticalScrollBar().getPreferredSize().width * 2,
                        stackTraceTextPreferredSize.height + stackTracePanel.getHorizontalScrollBar().getPreferredSize().height);
        stackTracePanel.setPreferredSize(stackTracePanelPreferredSize);
        stackTracePanel.setVisible(false);

        stackTraceButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (stackTraceButton.isSelected()) {
                    stackTraceButton.setText("Hide stack trace");
                    stackTracePanel.setVisible(true);
                    setSize(new Dimension(Math.max(dialogWithoutStackTracePreferredSize.width, stackTracePanelPreferredSize.width),
                                    dialogWithoutStackTracePreferredSize.height + Math.min(1000, stackTracePanelPreferredSize.height)));
                } else {
                    stackTraceButton.setText("Show stack trace");
                    stackTracePanel.setVisible(false);
                    setSize(dialogWithoutStackTracePreferredSize);
                }
                validate();
            }
        });
        mainPane.add(stackTracePanel);

        setSize(dialogWithoutStackTracePreferredSize);
        pack();
        setLocationRelativeTo(owner);
    }

    // Test code

    public static void main(String[] args) {
        try {
            recurse(0);
        } catch (RuntimeException runtimeException) {
            new ThrowableDialog(runtimeException, null, "Runtime Exception");
        }
    }

    static void recurse(int more) {
        if (more > 345) {
            throw new RuntimeException("This is a test. Repeat. This is a test.");
        }
        recurse(more + 1);
    }
}
