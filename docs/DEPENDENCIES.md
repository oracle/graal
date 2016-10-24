## Distribution specific installation tasks

### Mac

* How to install GCC: [#116](https://github.com/graalvm/sulong/issues/116), [#106](https://github.com/graalvm/sulong/issues/106)

### Ubuntu

#### 14.04

You can look at the [.travis.yml](/.travis.yml) config on how we set up
Travis (which runs on Ubuntu 14.04) for Sulong.

#### 16.04

@lxp explained (see [#258](https://github.com/graalvm/sulong/issues/258))
how he made Sulong run on Ubuntu 16.04:

My solution is not really perfect. I just added the old trusty repositories
and set the default release to xenial, so that only selected packages or
packages not existing in xenial are used from trusty.

Add trusty repository: (You might want to use a different mirror)

```
echo 'deb http://at.archive.ubuntu.com/ubuntu/ trusty main universe
deb http://at.archive.ubuntu.com/ubuntu/ trusty-updates main universe
deb http://security.ubuntu.com/ubuntu trusty-security main universe' | sudo tee /etc/apt/sources.list.d/trusty.list
```

Set the default release:

```
echo 'APT::Default-Release "xenial";' | sudo tee /etc/apt/apt.conf.d/20defaultrelease
```

Updated package lists and install dependencies:

```
sudo aptitude update
sudo aptitude install g++-4.6 gcc-4.6 gcc-4.6-plugin-dev gfortran-4.6
      gobjc++-4.6 llvm-3.3 pylint/trusty python-astroid/trusty
```

I am not really sure if all packages are necessary. I just installed
everything listed in the Travis configuration.

### archlinux

#### install general dependencies

```
sudo pacman -S git mercurial pcre
```

#### install gcc-4.6

```
export LD_PRELOAD=/usr/lib/libstdc++.so.6
yaourt -S gcc47
```

#### install gcc-4.7

if you want to install gcc-4.7 instead of gcc-4.6, you have to do some modifications
to PKGBUILD after executing yaourt:

* add ```fortran``` to ```--enable-languages```
* add the option ```--disable-libquadmath-support``` (see [bugreport](https://gcc.gnu.org/bugzilla/show_bug.cgi?id=47648))

```
yaourt -S gcc47
```

You also need to set the following enviroment variables in ```sulong/mx.sulong/env```:

```
SULONG_GCC=gcc-4.7
SULONG_GPP=g++-4.7
```

#### add symlink for pcre

```
sudo ln -s /usr/lib/libpcre.so.1.2.7 /usr/lib/libpcre.so.3
sudo ldconfig
```
