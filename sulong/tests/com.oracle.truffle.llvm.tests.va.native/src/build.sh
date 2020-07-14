export TOOLCHAIN=`mx lli --print-toolchain-api-paths PATH`

gcc -c vahandler.c -o vahandler.o
$TOOLCHAIN/graalvm-native-clang -dynamiclib -Wl,-undefined,dynamic_lookup -o libvahandler.dylib vahandler.o
$TOOLCHAIN/graalvm-native-clang -emit-llvm -c main.c -o main.o
#$TOOLCHAIN/graalvm-native-clang -I/Users/zslajchrt/work/graaldev/graal/sulong/mxbuild/darwin-amd64/SULONG_HOME/include/ -emit-llvm -c main.c -o main.o
#$TOOLCHAIN/graalvm-native-clang -o main main.o -L. -L/Users/zslajchrt/work/graaldev/graal/sulong/mxbuild/darwin-amd64/SULONG_HOME/native/lib -lvahandler -lpolyglot-mock
$TOOLCHAIN/graalvm-native-clang -o main main.o -L. -lvahandler
install_name_tool -change libvahandler.dylib `pwd`/libvahandler.dylib main
