package com.oracle.svm.core.sampling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.collections.PrefixTree;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.ProfilingSampler;

public class SamplingData {

    public Runnable dumpToFile() {
        return SamplingData::dumpProfiles;
    }

    public static void dumpProfiles() {
        BufferedWriter writer = null;
        try {
            Path path = Paths.get("sampling.iprof").toAbsolutePath();
            writer = new BufferedWriter(new FileWriter(path.toFile()));
            dumpFromTree(writer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static void dumpFromTree(BufferedWriter writer) throws IOException {
        PrefixTree prefixTree = ImageSingletons.lookup(ProfilingSampler.class).prefixTree();
        prefixTree.topDown(writer);
    }
}
