## Installation of Sulong

First create a new directory, which will contain the needed GraalVM
projects:

```
mkdir sulong-dev && cd sulong-dev
```

Then, download mx, which is the build tool used by Sulong:

```
git clone https://github.com/graalvm/mx
export PATH=$PWD/mx:$PATH
```

Next, use git to clone the Sulong project and its dependencies:

```
git clone https://github.com/graalvm/sulong
```

Next, you need to download a recent
[labsjdk](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html).
Extract it inside the `sulong-dev` directory:

```
tar -zxf labsjdk-8u92-jvmci-0.20-linux-amd64.tar.gz
```

Set `JAVA_HOME` to point to the extracted labsjdk from above:

```
echo JAVA_HOME=`pwd`/labsjdk1.8.0_92-jvmci-0.20 > sulong/mx.sulong/env
```

Finally, build the project:

```
cd sulong && mx build
```

The mx tool will ask you to choose between its server and jvmci
configuration. For now, just select server. You can read the differences
between the configurations on
[the Graal wiki](https://wiki.openjdk.java.net/display/Graal/Instructions). The first
build will take some time because mx has not only to build Sulong,
but also its dependencies and the Graal VM.

## Distribution specific installation tasks

### archlinux

#### install general dependencies

```
sudo pacman -S git mercurial pcre ruby
```

#### install gcc-4.7

gcc-4.6 had some installation problems, so I used gcc-4.7 instead. (check out the comments on [archlinux.org](https://aur.archlinux.org/packages/gcc46) how they can probably be fixed)

You need to compile gcc-4.7 by yourself using the AUR. Please note some modifications of PKGBUILD are required to get it working for Sulong:

* add ```fortran``` to ```--enable-languages```
* add the option ```--disable-libquadmath-support``` (see [bugreport](https://gcc.gnu.org/bugzilla/show_bug.cgi?id=47648))

```
yaourt -S gcc47
```

#### add symlink for pcre

```
sudo ln -s /usr/lib/libpcre.so.1.2.7 /usr/lib/libpcre.so.3
sudo ldconfig
```
