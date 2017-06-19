#include <stdio.h>

int foo(int a) {
   if(a == 0 ) {
      throw "Null!";
   }
   return a;
}

int main() {
	try {
		foo(0);
		return 0;
	} catch (const char* msg) {
		printf("%s\n", msg);
		return 1;
	}

}