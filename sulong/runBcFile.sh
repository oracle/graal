#!/bin/bash

mx --dy /tools lli --inspect --experimental-options --llvm.enableLVI $1".bc" $2
