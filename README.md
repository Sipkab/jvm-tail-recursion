# jvm-tail-recursion

Java library performing tail recursion optimizations on Java bytecode. It simply replaces the final recursive method calls in a function to a goto to the start of the same function.

The project uses [ASM](https://asm.ow2.io/) to perform bytecode manipulation.

## Examples

### Count down to zero

A simple tail recursive function that counts down to zero:

<table><tr>
<th>Before</th>
<th>After</th>
</tr><tr><td>


```java
static void count(int n) {

    if (n == 0) {
        return;
    }
    count(n - 1);
    
}
```


</td><td>
    
```java
static void count(int n) {
    while (true) {
        if (n == 0) {
            return;
        }
        n = n - 1;
    }
}
```

</td></tr></table>

### List numbers in a string

If you've ever wanted a sequence of numbers in a string:

<table><tr>
<th>Before</th>
<th>After</th>
</tr><tr><td>

```java
static String numbers(int n, String s) {

    if (n == 0) {
        return s + "0";
    }
    return numbers(n - 1, s + n + ",");
    
}
```


</td><td>
    
```java
static String numbers(int n, String s) {
    while (true) {
        if (n == 0) {
            return s + "0";
        }
        s = s + n + ",";
        n = n - 1;
    }
}
```

</td></tr></table>

### Enumerate *all* interfaces that a class implements

Tail recursive class can also be optimized inside an if-else condition. Note that here only the recursive call at the end is optimized, not the one in the loop!

<table><tr>
<th>Before</th>
<th>After</th>
</tr><tr><td>


```java
static void collectInterfaces(
        Class<?> clazz, 
        Set<Class<?>> result) {
        
    for (Class<?> itf : clazz.getInterfaces()) {
        if (result.add(itf)) 
            collectInterfaces(itf, result);
    }
    Class<?> sclass = clazz.getSuperclass();
    if (sclass != null) {
        collectInterfaces(sclass, result);
        
    }
    
    
}
```


</td><td>
    
```java
static void collectInterfaces(
        Class<?> clazz, 
        Set<Class<?>> result) {
    while (true) {
        for (Class<?> itf : clazz.getInterfaces()) {
            if (result.add(itf)) 
                collectInterfaces(itf, result);
        }
        Class<?> sclass = clazz.getSuperclass();
        if (sclass != null) {
            clazz = sclass;
            continue;
        }
        return;
    }
}
```

</td></tr></table>

### Side effect free instruction removal

If there are some instructions in the return path of a tail recursive call which can be removed, the library will do so. Some of these are:

* Unused variables
* Unused allocated arrays
* Unused field or array accesses

<table><tr>
<th>Before</th>
<th>After</th>
</tr><tr><td>


```java
final void count(int n) {

    if (n == 0) {
        return;
    }
    count(n - 1);
    Object b = new int[Integer.MAX_INT];
    Object f = this.myField;
    Object a = this.myArray[30];
}
```


</td><td>
    
```java
final void count(int n) {
    while (true) {
        if (n == 0) {
            return;
        }
        n = n - 1;
    }

    
}
```

</td></tr></table>

Note that this causes some exceptions to not be thrown in case of programming errors. E.g. No `OutOfMemoryError` will be thrown in the optimized code as the `new int[Integer.MAX_INT]` instruction is optimized out, and no `NullPointerException`s are thrown if the `myArray` field is `null`.

### `this` instance change

The optimization can be performed even if the tail recursive call is done on a different instance: (See limitations)

<table><tr>
<th>Before</th>
<th>After</th>
</tr><tr><td>


```java
public class MyClass {
    public final void count(int n) {

        if (n == 0) {
            return;
        }
        new MyClass().count(n - 1);
        
        
    }
}
```


</td><td>
    
```java
public class MyClass {
    public final void count(int n) {
        while (true) {
            if (n == 0) {
                return;
            }
            this = new MyClass();
            n = n - 1;
        }
    }
}
```

</td></tr></table>

Note that setting the `this` variable is not valid Java code, but it is possible in bytecode.

## Limitations

There are some limitations to the optimization:

1. The method must not be virtual. A virtual method is called based on the dynamic type of the object. This means that instance methods which are virtual cannot be optimized, as if the object on which the method is being invoked on changes, the recursive call cannot be simplified to a jump.
    * This could be improved in the future by analyizing the bytecode and ensuring that the recursive call can only be made on `this`.
    * However, default interface methods **can** be optimized.
2. Synchronized instance methods cannot be optimized. See the previous point for the reasons.
    * `static` method **can** be synchronized.

### Recommendations

The methods you want to be subject to optimization should be any of the following:

* `static` method.
* `private` instance method.
* `final` instance method.    

## Usage

The project is released as the [sipka.jvm.tailrec](https://nest.saker.build/package/sipka.jvm.tailrec?tab=bundles) package on the [saker.nest repository](https://nest.saker.build/).\
You can [**download the latest release using this link**](TODO) or by selecting a version and clicking *Download* on the *Bundles* tab on the [sipka.jvm.tailrec](https://nest.saker.build/package/sipka.jvm.tailrec?tab=bundles) package page.

It can be used in the following ways:

### Part of the build process

The project integrates with the [saker.build system](https://github.com/sakerbuild/saker.build) in the following ways:

#### ZIP transformer

Using the `sipka.jvm.tailrec.zip.transformer()` task to retrieve a ZIP transformer when creating your JAR or ZIP archive will cause each `.class` file to be optimized by the library.

```sakerscript
$javac = saker.java.compile(src)
saker.jar.create(
    Resources: {
        Directory: $javac[ClassDirectory],
        Resources: **,
    },
    Transformers: sipka.jvm.tailrec.zip.transformer(),
)
```

The above is an example for compiling all Java sources in the `src` directory, and creating a JAR file with the compiled and optimized classes.

#### Class directory optimization

You can use the `sipka.jvm.tailrec.optimize()` task to optimize a directory with the `.class` files in it.

```sakerscript
$javac = saker.java.compile(src)

$path = sipka.jvm.tailrec.optimize($javac[ClassDirectory])
```

The above will simply optimize all the `.class` files that are the output of the Java compilation. The optimized classes are written into the build directory, and a path to it is returned by the task. (`$path`)

#### Optimize an existing archive

If you already have an archive that you want to optimize, use the ZIP transformer as seen previously, but specify the inputs as your archive:

```sakerscript
saker.jar.create(
    Include: my_jar_to_optimize.jar,
    Transformers: sipka.jvm.tailrec.zip.transformer(),
)
```

This will result in a new archive being created that contains everything from the included JAR, and each `.class` file will be optimized.

### Command line usage

The optimization can also be performed on the command line:

```shell
java -jar sipka.jvm.tailrec.jar -output my_jar_opt.jar my_jar.jar
```

The above will optimize `my_jar.jar` and create the output of `my_jar_opt.jar`. You can also overwrite the input:

```shell
java -jar sipka.jvm.tailrec.jar -overwrite my_jar.jar
```

Which causes the input JAR to be overwritten with the result.

The input can also be a class directory.

See `--help` for more usage information.

## License

The source code for the project is licensed under *GNU General Public License v3.0 only*.

Short identifier: [`GPL-3.0-only`](https://spdx.org/licenses/GPL-3.0-only.html).

Official releases of the project (and parts of it) may be licensed under different terms. See the particular releases for more information.
