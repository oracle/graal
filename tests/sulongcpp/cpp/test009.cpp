
class someClass { virtual void x() {}};

class someOtherClass:public someClass { };

bool instanceof(someClass *c) {
	someOtherClass *other = dynamic_cast<someOtherClass*> (c);
	return other != 0;
}

int main() {
	someClass c;
	if (instanceof(&c) == true) return 1;
	someOtherClass c2;
	if (instanceof(&c2) == false) return 1;
	return 0;
}