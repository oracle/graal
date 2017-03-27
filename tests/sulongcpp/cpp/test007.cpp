#include <typeinfo>

class someClass { };

int main(int argc, char* argv[]) {
    int a;
    someClass b;
    return typeid(a) == typeid(b) ? 1 : 0;
    
}