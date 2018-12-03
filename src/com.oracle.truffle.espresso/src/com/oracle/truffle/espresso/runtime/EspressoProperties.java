package com.oracle.truffle.espresso.runtime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import com.oracle.truffle.espresso.EspressoOptions;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.espresso.EspressoLanguage;

public interface EspressoProperties {
    String getJavaHome();

    String getBootClasspath();

    String getJavaLibraryPath();

    String getBootLibraryPath();

    String getExtDirs();

    static EspressoProperties getDefault() {
        if (ImageInfo.inImageCode()) {
            return new EspressoPropertiesSVM();
        }
        return new EspressoPropertiesHotSpot();
    }

    default EspressoProperties processOptions(OptionValues options) {

        String bootClasspath = getBootClasspath();

        if (options.hasBeenSet(EspressoOptions.BootClasspath)) {
            bootClasspath = options.get(EspressoOptions.BootClasspath);
        }
        if (options.hasBeenSet(EspressoOptions.BootClasspathAppend)) {
            bootClasspath = bootClasspath + File.separator + options.get(EspressoOptions.BootClasspathAppend);
        }

        if (options.hasBeenSet(EspressoOptions.BootClasspathPrepend)) {
            bootClasspath = options.get(EspressoOptions.BootClasspathPrepend) + File.separator + bootClasspath;
        }

        // No override.
        if (bootClasspath.equals(getBootClasspath())) {
            return this;
        }

        return new Builder(this).setBootClasspath(bootClasspath).build();
    }

    class Builder {

        private String javaHome;
        private String bootClasspath;
        private String javaLibraryPath;
        private String bootLibraryPath;
        private String extDirs;

        private final EspressoProperties fallback;

        Builder(EspressoProperties fallback) {
            this.fallback = fallback;
        }

        public Builder setJavaHome(String javaHome) {
            this.javaHome = javaHome;
            return this;
        }

        public Builder setBootClasspath(String bootClasspath) {
            this.bootClasspath = bootClasspath;
            return this;
        }

        public Builder setJavaLibraryPath(String javaLibraryPath) {
            this.javaLibraryPath = javaLibraryPath;
            return this;
        }

        public Builder setBootLibraryPath(String bootLibraryPath) {
            this.bootLibraryPath = bootLibraryPath;
            return this;
        }

        public Builder setExtDirs(String extDirs) {
            this.extDirs = extDirs;
            return this;
        }

        public EspressoProperties build() {
            return new EspressoProperties() {
                @Override
                public String getJavaHome() {
                    return javaHome != null ? javaHome : fallback.getJavaHome();
                }

                @Override
                public String getBootClasspath() {
                    return bootClasspath != null ? bootClasspath : fallback.getBootClasspath();
                }

                @Override
                public String getJavaLibraryPath() {
                    return javaLibraryPath != null ? javaLibraryPath : fallback.getJavaLibraryPath();
                }

                @Override
                public String getBootLibraryPath() {
                    return bootLibraryPath != null ? bootLibraryPath : fallback.getBootLibraryPath();
                }

                @Override
                public String getExtDirs() {
                    return extDirs != null ? extDirs : fallback.getExtDirs();
                }
            };
        }
    }
}


class EspressoPropertiesHotSpot implements EspressoProperties {

    private final String javaHome = System.getProperty("java.home");
    private final String bootClasspath = System.getProperty("sun.boot.class.path");
    private final String javaLibraryPath = System.getProperty("java.library.path");
    private final String bootLibraryPath = System.getProperty("sun.boot.library.path");
    private final String extDirs = System.getProperty("java.ext.dirs");

    @Override
    public String getJavaHome() {
        return javaHome;
    }

    @Override
    public String getBootClasspath() {
        return bootClasspath;
    }

    @Override
    public String getJavaLibraryPath() {
        return javaLibraryPath;
    }

    @Override
    public String getBootLibraryPath() {
        return bootLibraryPath;
    }

    @Override
    public String getExtDirs() {
        return extDirs;
    }
}

class EspressoPropertiesSVM implements EspressoProperties {

    private final String javaHome;
    private final String bootClasspath;
    private final String javaLibraryPath;
    private final String bootLibraryPath;
    private final String extDirs;

    public EspressoPropertiesSVM() {
        OptionValues options = EspressoLanguage.getCurrentContext().getEnv().getOptions();
        Path espressoHome = EspressoLanguage.getCurrentContext().getLanguage().getEspressoHome();
        espressoHome.resolve("lib");

        // Extensions directories.
        // Base path of extensions installed on the system.
        final String SYS_EXT_DIR = "/usr/java/packages";
        final String EXTENSIONS_DIR = "/lib/ext";
        final String DEFAULT_LIBPATH = "/usr/lib64:/lib64:/lib:/usr/lib";
        final String cpuArch = "amd64";

        Path graalVmHome = Engine.findHome();

        Path maybeJre = graalVmHome.resolve("jre");

        if (Files.isDirectory(maybeJre)) {
            javaHome = maybeJre.toString();
        } else {
            javaHome = graalVmHome.toString();
        }

        bootClasspath = String.format(
                        String.join(File.separator,
                                        "%1$/lib/resources.jar",
                                        "%1$/lib/rt.jar",
                                        "%1$/lib/sunrsasign.jar",
                                        "%1$/lib/jsse.jar",
                                        "%1$/lib/jce.jar",
                                        "%1$/lib/charsets.jar",
                                        "%1$/lib/jfr.jar",
                                        "%1$/classes").replaceAll("/", File.pathSeparator),
                        javaHome);
        extDirs = javaHome + EXTENSIONS_DIR + File.separator + SYS_EXT_DIR + EXTENSIONS_DIR;
        javaLibraryPath = String.format(SYS_EXT_DIR + "/lib/%s:" + DEFAULT_LIBPATH, cpuArch);
        bootLibraryPath = javaHome + "/lib/" + cpuArch;
    }

    @Override
    public String getJavaHome() {
        return javaHome;
    }

    @Override
    public String getBootClasspath() {
        return bootClasspath;
    }

    @Override
    public String getJavaLibraryPath() {
        return javaLibraryPath;
    }

    @Override
    public String getBootLibraryPath() {
        return bootLibraryPath;
    }

    @Override
    public String getExtDirs() {
        return extDirs;
    }
}
