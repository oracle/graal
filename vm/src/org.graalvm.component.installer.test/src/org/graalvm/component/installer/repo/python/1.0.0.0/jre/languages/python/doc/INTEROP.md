## Interop

#### Interop from Python

You can import the `polyglot` module to interact with other languages.

```python
import polyglot
```

You can import a global value from the entire polyglot scope:
```python
imported_polyglot_global = polyglot.import_value("global_name")
```

This global should then work as expected; accessing attributes assumes it reads
from the `members` namespace; accessing items is supported both with strings and
numbers; calling methods on the result tries to do a straight invoke and falls
back to reading the member and trying to execute it.

You can evaluate some code in another language:
```python
polyglot.eval(string="1 + 1", language="ruby")
```

It also works with the path to a file:
```python
polyglot.eval(file="./my_ruby_file.rb", language="ruby")
```

If you pass a file, you can also try to rely on the file-based language detection:
```python
polyglot.eval(file="./my_ruby_file.rb")
```

To export something from Python to other Polyglot languages so they can import
it:
```python
foo = object()
polyglot.export_value(foo, name="python_foo")
```

The export function can be used as a decorator, in this case the function name
is used as the globally exported name:
```python
@polyglot.export_value
def python_method():
    return "Hello from Python!"
```

Finally, to interoperate with Java (only when running on the JVM), you can use
the `java` module:
```python
import java
BigInteger = java.type("java.math.BigInteger")
myBigInt = BigInteger(42)
myBigInt.shiftLeft(128)            # public Java methods can just be called
myBigInt["not"]()                  # Java method names that are keywords in
                                   # Python can be accessed using "[]"
byteArray = myBigInt.toByteArray()
print(list(byteArray))             # Java arrays can act like Python lists
```

For packages under the `java` package, you can also use the normal Python import
syntax:
```python
import java.util.ArrayList
from java.util import ArrayList

# these are the same class
java.util.ArrayList == ArrayList

al = ArrayList()
al.add(1)
al.add(12)
print(al) # prints [1, 12]
```

In addition to the `type` builtin method, the `java` module, exposes the following 
methods as well:

Builtin | Specification
--- | ---
`instanceof(obj, class)` | returns `True` if `obj` is an instance of `class` (`class` must be a foreign object class)
`is_function(obj)` | returns `True` if `obj` is a Java host language function wrapped using Truffle interop
`is_object(obj)` | returns `True` if `obj` if the argument is Java host language object wrapped using Truffle interop
`is_symbol(obj)` | returns `True` if `obj` if the argument is a Java host symbol, representing the constructor and static members of a Java class, as obtained by `java.type`

```python
import java
ArrayList = java.type('java.util.ArrayList')
my_list = ArrayList()
print(java.is_symbol(ArrayList))    # prints True
print(java.is_symbol(my_list))      # prints False, my_list is not a Java host symbol
print(java.is_object(ArrayList))    # prints True, symbols are also host objects 
print(java.is_function(my_list.add))# prints True, the add method of ArrayList
print(java.instanceof(my_list, ArrayList)) # prints True 
```
