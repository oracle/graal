## Distribution specific installation tasks

### archlinux

#### install general dependencies

```
sudo pacman -S git mercurial pcre ruby
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
