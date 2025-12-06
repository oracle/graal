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
package at.ssw.visualizer.cfg.action;

import at.ssw.visualizer.cfg.editor.CfgEditorTopComponent;
import at.ssw.visualizer.cfg.graph.CfgScene;
import java.io.File;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import java.io.*;
import java.awt.Graphics2D;

/**
 * Exports Scene to various Graphics Formats
 * 
 * @author Rumpfhuber Stefan
 */
public class ExportAction extends AbstractCfgEditorAction {
    
    public static final String DESCRIPTION_SVG = "Scaleable Vector Format (.svg)";
    public static final String EXT_SVG = "svg";

    String lastDirectory = null;
    
    @Override
    public void performAction() {       
        CfgEditorTopComponent tc = this.getEditor();
        CfgScene scene = tc.getCfgScene();
        JComponent view = scene.getView();
           
        JFileChooser chooser = new JFileChooser ();
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle (getName());
        chooser.setDialogType (JFileChooser.SAVE_DIALOG);
        chooser.setMultiSelectionEnabled (false);
        chooser.setFileSelectionMode (JFileChooser.FILES_ONLY);              
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(DESCRIPTION_SVG, EXT_SVG));
        if(lastDirectory != null)
            chooser.setCurrentDirectory(new File(lastDirectory));
        chooser.setSelectedFile(new File(tc.getName()));
        
                              
        if (chooser.showSaveDialog (tc) != JFileChooser.APPROVE_OPTION)
            return;

        File file = chooser.getSelectedFile ();
                
        if(file == null)
            return;
                
        FileNameExtensionFilter filter = (FileNameExtensionFilter) chooser.getFileFilter();
        String fn = file.getAbsolutePath().toLowerCase();
        String ext = filter.getExtensions()[0];
        if(!fn.endsWith("." + ext)){
            file = new File( file.getParentFile(), file.getName() + "." + ext);
        }
                        
        if (file.exists ()) {
            DialogDescriptor descriptor = new DialogDescriptor (
                "File (" + file.getAbsolutePath () + ") already exists. Do you want to overwrite it?",
                "File Exists", true, DialogDescriptor.YES_NO_OPTION, DialogDescriptor.NO_OPTION, null);
                DialogDisplayer.getDefault ().createDialog (descriptor).setVisible (true);
                    if (descriptor.getValue () != DialogDescriptor.YES_OPTION)
                        return;
        }   
        
        lastDirectory = chooser.getCurrentDirectory().getAbsolutePath();
                        
        if(ext.equals(EXT_SVG)){                   
            DOMImplementation dom = GenericDOMImplementation.getDOMImplementation();
            org.w3c.dom.Document document = dom.createDocument("http://www.w3.org/2000/svg", "svg", null);
            SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);
            ctx.setEmbeddedFontsOn(true);
            Graphics2D svgGenerator = new SVGGraphics2D(ctx, true);
            scene.paint(svgGenerator);
            FileOutputStream os = null;
            try {
                os = new FileOutputStream(file);
                Writer out = new OutputStreamWriter(os, "UTF-8");
                assert svgGenerator instanceof SVGGraphics2D;
                SVGGraphics2D svgGraphics = (SVGGraphics2D)svgGenerator;
                svgGraphics.stream(out, true);
            } catch (IOException e) {
                // NotifyDescriptor message = new NotifyDescriptor.Message(
                //         Bundle.EXPORT_BATIK_ErrorExportingSVG(e.getLocalizedMessage()), NotifyDescriptor.ERROR_MESSAGE);
                // DialogDisplayer.getDefault().notifyLater(message);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }          
       
    
    @Override
    public String getName() {     
        return "Export CFG";
    }
    
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
     @Override
    protected String iconResource() {
        return "at/ssw/visualizer/cfg/icons/disk.gif";    
    }

}
