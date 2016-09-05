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
