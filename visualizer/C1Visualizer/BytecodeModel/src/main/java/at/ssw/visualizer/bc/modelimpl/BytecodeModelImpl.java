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
package at.ssw.visualizer.bc.modelimpl;

import at.ssw.visualizer.bc.model.BytecodeModel;
import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.CompilationElement;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.Repository;

/**
 * This class holds the classpaths and the method bytecodes.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public class BytecodeModelImpl implements BytecodeModel {

    private String[] classPaths;
    private Map<ControlFlowGraph, Bytecodes> loadedBytecodes;

    /**
     * Initializes a BytecodeModel.
     */
    public BytecodeModelImpl() {
        loadClassPaths();

        // Use a synchronized map to avoid all possible threading issues.
        // Use a weak map because elements are never removed explicitly.
        loadedBytecodes = Collections.synchronizedMap(new WeakHashMap<ControlFlowGraph, Bytecodes>());
    }

    /** The prefix of all control flow graphs in the early bytecode parsing phase of the compiler. */
    private static final String BLOCK_LIST_PREFIX = "BlockListBuilder ";

    private ControlFlowGraph getBlockListCFG(ControlFlowGraph cfg) {
        if (cfg.getBytecodes() != null) {
            return cfg;
        }
        if (cfg.getName().startsWith(BLOCK_LIST_PREFIX)) {
            return cfg;
        }

        ControlFlowGraph result = null;
        for (CompilationElement ce : cfg.getCompilation().getElements()) {
            if (ce instanceof ControlFlowGraph && ce.getName().startsWith(BLOCK_LIST_PREFIX)) {
                if (result == null) {
                    result = (ControlFlowGraph) ce;
                    if (result.getElements().size() > 0) {
                        // Child elements are present, so methods were inlined.
                        return null;
                    }
                } else {
                    // More than one BlockListBuilder, so methods were inlined.
                    return null;
                }
            }
        }
        return result;
    }

    private String getMethodName(ControlFlowGraph cfg) {
        assert cfg.getName().startsWith(BLOCK_LIST_PREFIX);
        return cfg.getName().substring(BLOCK_LIST_PREFIX.length());
    }


    public boolean hasBytecodes(ControlFlowGraph cfg) {
        return getBlockListCFG(cfg) != null;
    }
/*
    public boolean bytecodesLoaded(ControlFlowGraph cfg) {
        return getBytecodes(cfg) != null;
    }
*/
    public String noBytecodesMsg(ControlFlowGraph cfg) {
        StringBuilder result = new StringBuilder();
        result.append("No bytecodes available for ").append(cfg.getCompilation().getName()).append(" - ").append(cfg.getName());
        if (!hasBytecodes(cfg)) {
            result.append("\nThe compiler inlined methods during compilation, so there is no single method to take the bytecodes from.");
        } else {
            result.append("\nThe method is not present in the classpath. Configure the claspath using Tools->Options.");
        }
        return result.toString();
    }

    public Bytecodes getBytecodes(ControlFlowGraph cfg) {
        Bytecodes result = loadedBytecodes.get(cfg);
        if (result != null) {
            return result;
        }

        cfg = getBlockListCFG(cfg);
        if (cfg == null) {
            return null;
        }
        
        if (cfg.getBytecodes() != null) {
            return cfg.getBytecodes();
        }
        
        String methodName = getMethodName(cfg);
        List<Bytecodes> mbcs = BytecodesParser.readMethod(cfg, methodName, classPaths);
        if (mbcs.size() == 0) {
            return null;
        } else if (mbcs.size() == 1) {
            result = mbcs.get(0);
        } else {
            result = (Bytecodes) JOptionPane.showInputDialog(null, "Ambiguous methodname: \n"  + methodName, "Select method", JOptionPane.QUESTION_MESSAGE, null, mbcs.toArray(), mbcs.get(0));
            if (result == null) {
                return null;
            }
        }

        loadedBytecodes.put(cfg, result);
        return result;
    }

    private FileObject getOptionLocation() {
        return Repository.getDefault().getDefaultFileSystem().findResource("at-ssw-visualizer-bc/classpaths");
    }

    /**
     * Loads the classpath information from the system filesystem.
     */
    private void loadClassPaths() {
        List<String> data = new ArrayList<String>();
        try {
            InputStream stream = getOptionLocation().getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            String cp = reader.readLine();
            while (cp != null) {
                data.add(cp);
                cp = reader.readLine();
            }
            reader.close();
        } catch (IOException ex) {
            Logger log = Logger.getLogger(BytecodeModel.class.getName());
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }

        if (data.size() > 0) {
            classPaths = data.toArray(new String[data.size()]);
        } else {
            classPaths = getDefaultClassPaths();
        }
    }

    /**
     * Stores the classpath infotmation to the system filesystem.
     */
    private void saveClassPaths() {
        try {
            OutputStream stream = getOptionLocation().getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));

            for (String cp : classPaths) {
                writer.write(cp);
                writer.newLine();
            }
            writer.close();
        } catch (IOException ex) {
            Logger log = Logger.getLogger(BytecodeModel.class.getName());
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    public String[] getDefaultClassPaths() {
        return System.getProperty("sun.boot.class.path", "").split(System.getProperty("path.separator"));
    }

    /**
     * Returns an array of all classpaths stored in the model.
     *
     * @return      String array with all classpaths
     */
    public String[] getClassPaths() {
        return classPaths;
    }

    public void setClassPaths(String[] classPaths) {
        this.classPaths = classPaths;

        loadedBytecodes.clear();
        saveClassPaths();
    }
}
