#include <string>
#include <algorithm>
#include <vector>
#include <iostream>
#include <process.h>

int main(int argc, char *argv[]) {
    if (argc < 2) {
        std::cerr << "Runner does not contain enough arguments." << std::endl;
        return -99;
    }

    std::string prog = argv[1];
    replace(prog.begin(), prog.end(), '/', '\\');

    argv += 2;
    std::vector<const char *> args;
    args.push_back(prog.c_str());
    for (; *argv; argv++) {
        args.push_back(*argv);
    }
    args.push_back(0);

    return execv(prog.c_str(), args.data());
}
