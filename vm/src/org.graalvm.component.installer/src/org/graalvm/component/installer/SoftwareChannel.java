/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 * An abstraction of software delivery channel. The channel provides a Registry
 * of available components. It can augment or change the download process and
 * it can interpret the downloaded files to support different file formats.
 * <p/>
 * @author sdedic
 */
public interface SoftwareChannel {
    /**
     * True, if the channel is willing to handle the URL. URL is passed as a 
     * String so that custom protocols may be used without registering an
     * URLStreamHandlerFactory.
     * 
     * @param urlString url string, including the scheme
     * @return true, if the channel is willing to work with the URL
     */
    boolean setupLocation(String urlString);
    
    /**
     * Binds the channel implementation to I/O
     * @param input input from the commandline
     * @param output user i/o
     */
    void    init(CommandInput input, Feedback output);
    
    /**
     * Loads and provides access to the component registry
     * @return registry instance
     */
    ComponentRegistry   getRegistry();
    
    /**
     * Configures the downloader with specific options. The downloader may be
     * even replaced with a different instance.
     * @param dn the downloader to configure 
     * @return the downloader instance.
     */
    FileDownloader     configureDownloader(FileDownloader dn);
    
    /**
     * Creates metadata + archive loader from a downloaded file.
     * @param localFile the local file.
     * @return 
     */
    MetadataLoader      createLocalFileLoader(Path localFile) throws IOException;
    
    /**
     * Adds options to the set of global options. Global options allow
     * to accept specific options from commandline, which would otherwise
     * cause an error (unknown option).
     * 
     * @return global options to add.
     */
    default Map<String, String> globalOptions() {
        return Collections.emptyMap();
    }
    
    default String globalOptionsHelp() {
        return null;
    }
}
