/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.subsystems;

import java.io.*;
import java.net.*;
import java.util.*;

import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Manages the features loaded into Ruby. This basically means which library files are loaded, but
 * Ruby often talks about requiring features, not files.
 * 
 */
public class FeatureManager {

    private RubyContext context;

    private final Set<String> requiredFiles = new HashSet<>();

    public FeatureManager(RubyContext context) {
        this.context = context;
    }

    public boolean require(String feature) throws IOException {
        // Some features are handled specially

        if (feature.equals("stringio")) {
            context.implementationMessage("stringio not yet implemented");
            return true;
        }

        if (feature.equals("rbconfig")) {
            // Kernel#rbconfig is always there
            return true;
        }

        if (feature.equals("pp")) {
            // Kernel#pretty_inspect is always there
            return true;
        }

        // Get the load path

        final Object loadPathObject = context.getCoreLibrary().getGlobalVariablesObject().getInstanceVariable("$:");

        if (!(loadPathObject instanceof RubyArray)) {
            throw new RuntimeException("$: is not an array");
        }

        final List<Object> loadPath = ((RubyArray) loadPathObject).asList();

        // Try as a full path

        if (requireInPath("", feature)) {
            return true;
        }

        // Try each load path in turn

        for (Object pathObject : loadPath) {
            final String path = pathObject.toString();

            if (requireInPath(path, feature)) {
                return true;
            }
        }

        // Didn't find the feature

        throw new RaiseException(context.getCoreLibrary().loadErrorCannotLoad(feature));
    }

    public boolean requireInPath(String path, String feature) throws IOException {
        if (requireFile(feature)) {
            return true;
        }

        if (requireFile(feature + ".rb")) {
            return true;
        }

        if (requireFile(path + File.separator + feature)) {
            return true;
        }

        if (requireFile(path + File.separator + feature + ".rb")) {
            return true;
        }

        return false;
    }

    private boolean requireFile(String fileName) throws IOException {
        if (requiredFiles.contains(fileName)) {
            return true;
        }

        /*
         * There is unfortunately no way to check if a string is a file path, or a URL. file:foo.txt
         * is a valid file name, as well as a valid URL. We try as a file path first.
         */

        if (new File(fileName).isFile()) {
            context.loadFile(fileName);
            requiredFiles.add(fileName);
            return true;
        } else {
            URL url;

            try {
                url = new URL(fileName);
            } catch (MalformedURLException e) {
                return false;
            }

            InputStream inputStream;

            try {
                inputStream = url.openConnection().getInputStream();
            } catch (IOException e) {
                return false;
            }

            context.load(context.getSourceManager().get(url.toString(), inputStream));
            requiredFiles.add(fileName);
            return true;
        }
    }

}
