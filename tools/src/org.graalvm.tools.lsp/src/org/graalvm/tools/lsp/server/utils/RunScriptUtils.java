package org.graalvm.tools.lsp.server.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.tools.lsp.exceptions.InvalidCoverageScriptURI;

public class RunScriptUtils {

    private static final String RUN_SCRIPT_PATH = "RUN_SCRIPT_PATH:";

    public static URI extractScriptPath(TextDocumentSurrogate surrogate) throws InvalidCoverageScriptURI {
        String currentText = surrogate.getEditorText();
        String firstLine;
        try {
            firstLine = new BufferedReader(new StringReader(currentText)).readLine();
        } catch (IOException e1) {
            throw new IllegalStateException(e1);
        }
        int startIndex = firstLine != null ? firstLine.indexOf(RUN_SCRIPT_PATH) : -1;
        if (startIndex >= 0) {
            Path scriptPath;
            try {
                scriptPath = Paths.get(firstLine.substring(startIndex + RUN_SCRIPT_PATH.length()));
                if (!scriptPath.isAbsolute()) {
                    Path currentFile = Paths.get(surrogate.getUri());
                    scriptPath = currentFile.resolveSibling(scriptPath).normalize();
                }
            } catch (InvalidPathException e) {
                throw new InvalidCoverageScriptURI(e, startIndex + RUN_SCRIPT_PATH.length(), firstLine.length());
            }
            if (!Files.exists(scriptPath)) {
                throw new InvalidCoverageScriptURI(startIndex + RUN_SCRIPT_PATH.length(), "File not found: " + scriptPath.toString(), firstLine.length());
            }
            return scriptPath.toUri();
        }
        return null;
    }
}
