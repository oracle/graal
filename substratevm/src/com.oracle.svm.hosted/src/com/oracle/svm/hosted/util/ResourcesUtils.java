package com.oracle.svm.hosted.util;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ResourcesUtils {

    /**
     * Returns jar path from the given url
     */
    private static String urlToJarPath(URL url) {
        try {
            return ((JarURLConnection) url.openConnection()).getJarFileURL().toURI().getPath();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns directory that contains resource on the given url
     */
    public static String getResourceSource(URL url, String resource, boolean fromJar) {
        String source = fromJar ? Path.of(urlToJarPath(url)).toUri().toString() : Path.of(url.getPath()).toUri().toString();
        if (!fromJar) {
            // -1 removes trailing slash from path of directory that contains resource
            source = source.substring(0, source.length() - resource.length() - 1);
            if (source.endsWith("/")) {
                // if resource was directory we still have one slash at the end
                source = source.substring(0, source.length() - 1);
            }
        }

        return source;
    }

    /**
     * Returns whether the given resource is directory or not
     */
    public static boolean resourceIsDirectory(URL url, boolean fromJar, String resource) throws IOException, URISyntaxException {
        if (fromJar) {
            try (JarFile jf = new JarFile(urlToJarPath(url))) {
                return jf.getEntry(resource).isDirectory();
            }
        } else {
            return Files.isDirectory(Path.of(url.toURI()));
        }
    }

    /**
     * Returns directory content of the resource from the given path
     */
    public static String getDirectoryContent(String path, boolean fromJar) throws IOException {
        Set<String> content = new TreeSet<>();
        if (fromJar) {
            try (JarFile jf = new JarFile(urlToJarPath(URI.create(path).toURL()))) {
                String pathSeparator = FileSystems.getDefault().getSeparator();
                String directoryPath = path.split("!")[1];

                // we are removing leading slash because jar entry names don't start with slash
                if (directoryPath.startsWith(pathSeparator)) {
                    directoryPath = directoryPath.substring(1);
                }

                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    if (entry.startsWith(directoryPath)) {
                        String contentEntry = entry.substring(directoryPath.length());

                        // remove the leading slash
                        if (contentEntry.startsWith(pathSeparator)) {
                            contentEntry = contentEntry.substring(1);
                        }

                        // prevent adding empty strings as a content
                        if (!contentEntry.isEmpty()) {
                            // get top level content only
                            int firstSlash = contentEntry.indexOf(pathSeparator);
                            if (firstSlash != -1) {
                                content.add(contentEntry.substring(0, firstSlash));
                            } else {
                                content.add(contentEntry);
                            }
                        }
                    }
                }

            }
        } else {
            try (Stream<Path> contentStream = Files.list(Path.of(path))) {
                content = new TreeSet<>(contentStream
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .toList());
            }
        }

        return String.join(System.lineSeparator(), content);
    }

}
