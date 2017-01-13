## Distribution specific installation tasks

### Mac

* How to install GCC: [#116](https://github.com/graalvm/sulong/issues/116), [#106](https://github.com/graalvm/sulong/issues/106)

### Ubuntu

#### 14.04

You can look at the [.travis.yml](/.travis.yml) config on how we set up
Travis (which runs on Ubuntu 14.04) for Sulong.

#### 16.04

Follow the same instructions as for 14.04.
gcc-4.6 is not available for 16.04, which requires us to temporarily add
`trusty main universe` to `/etc/apt/sources.list`.

Open `/etc/apt/sources.list`:

```
sudo gedit /etc/apt/sources.list
```

Then add two new lines to the file :

```
deb http://dk.archive.ubuntu.com/ubuntu/ trusty main universe
deb http://dk.archive.ubuntu.com/ubuntu/ trusty-updates main universe
```

Then run:

```
sudo apt-get update
```

Install all gcc-4.6 related tools.
Afterwards revert the changes in `/etc/apt/sources.list`
and run `sudo apt-get update` again.

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
