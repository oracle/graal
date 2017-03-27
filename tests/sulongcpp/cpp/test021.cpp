#include <stdio.h>
#include <exception>

using namespace std;

class fooexception: public exception {
	virtual const char* what() const throw() {
		return "WUHU";
	}
} exc;

int main() {
	try {
		throw exc;
	} catch(exception &e) {
		printf("%s\n", e.what());
		return 0;
	}
	return 1;
}