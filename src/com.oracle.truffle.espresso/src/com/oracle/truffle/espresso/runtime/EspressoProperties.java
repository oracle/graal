package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;

import java.io.File;

public interface EspressoProperties {
    String getJavaHome();

    String getBootClasspath();

    String getJavaLibraryPath();

    String getBootLibraryPath();

    String getExtDirs();

    String getEspressoLibraryPath();

    static EspressoProperties getDefault() {
        if (EspressoOptions.RUNNING_ON_SVM) {
            return new EspressoPropertiesSVM();
        }
        return new EspressoPropertiesHotSpot();
    }

    default EspressoProperties processOptions(OptionValues options) {

        Builder builder = new Builder(this);

        {
            // Process boot classpath + append and prepend options.
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
            builder.setBootClasspath(bootClasspath);
        }

        if (options.hasBeenSet(EspressoOptions.JavaHome)) {
            builder.setJavaHome(options.get(EspressoOptions.JavaHome));
        }

        return builder.build();
    }

    class Builder {

        private String javaHome;
        private String bootClasspath;
        private String javaLibraryPath;
        private String bootLibraryPath;
        private String extDirs;
        private String espressoLibraryPath;

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

        public Builder setEspressoLibraryPath(String espressoLibraryPath) {
            this.espressoLibraryPath = espressoLibraryPath;
            return this;
        }

        public EspressoProperties build() {
            if ((javaHome == null || javaHome.equals(fallback.getJavaHome())) &&
                            (bootClasspath == null || bootClasspath.equals(fallback.getBootClasspath())) &&
                            (javaLibraryPath == null || javaLibraryPath.equals(fallback.getJavaLibraryPath())) &&
                            (bootLibraryPath == null || bootLibraryPath.equals(fallback.getBootLibraryPath())) &&
                            (extDirs == null || extDirs.equals(fallback.getExtDirs())) &&
                            (espressoLibraryPath == null || espressoLibraryPath.equals(fallback.getEspressoLibraryPath()))) {
                // No overrides.
                return fallback;
            }

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

                @Override
                public String getEspressoLibraryPath() {
                    return espressoLibraryPath != null ? espressoLibraryPath : fallback.getEspressoLibraryPath();
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
    private final String espressoLibraryPath = System.getProperty("espresso.library.path");

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

    @Override
    public String getEspressoLibraryPath() {
        return espressoLibraryPath;
    }
}

class EspressoPropertiesSVM implements EspressoProperties {

    private final String javaHome;
    private final String bootClasspath;
    private final String javaLibraryPath;
    private final String bootLibraryPath;
    private final String extDirs;
    private final String espressoLibraryPath;

    public EspressoPropertiesSVM() {
        String espressoHome = EspressoLanguage.getCurrentContext().getLanguage().getEspressoHome();
        espressoLibraryPath = espressoHome + "/lib";
        // Extensions directories.
        // Base path of extensions installed on the system.
        final String SYS_EXT_DIR = "/usr/java/packages";
        final String EXTENSIONS_DIR = "/lib/ext";
        final String DEFAULT_LIBPATH = "/usr/lib64:/lib64:/lib:/usr/lib";
        final String cpuArch = "amd64";

        String graalVmHome = Engine.findHome().toString();
        javaHome = graalVmHome + "/jre";
        bootClasspath = String.join(File.pathSeparator,
                        javaHome + "/lib/resources.jar",
                        javaHome + "/lib/rt.jar",
                        javaHome + "/lib/sunrsasign.jar",
                        javaHome + "/lib/jsse.jar",
                        javaHome + "/lib/jce.jar",
                        javaHome + "/lib/charsets.jar",
                        javaHome + "/lib/jfr.jar",
                        javaHome + "/classes");
        extDirs = javaHome + EXTENSIONS_DIR + File.pathSeparator + SYS_EXT_DIR + EXTENSIONS_DIR;
        javaLibraryPath = SYS_EXT_DIR + "/lib/" + cpuArch + ":" + DEFAULT_LIBPATH;
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

    @Override
    public String getEspressoLibraryPath() {
        return espressoLibraryPath;
    }
}
