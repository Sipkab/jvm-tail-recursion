# jvm-tail-recursion

![Build status](https://img.shields.io/azure-devops/build/sipkab/cebd28c3-5a6a-462e-8750-516bfbe96bb4/4/master) [![Latest version](https://mirror.nest.saker.build/badges/sipka.jvm.tailrec/version.svg)](https://nest.saker.build/package/sipka.jvm.tailrec "sipka.jvm.tailrec | saker.nest")

Java library performing tail recursion optimizations on Java bytecode. It simply replaces the final recursive method calls in a function to a goto to the start of the same function.

The project uses [ASM](https://asm.ow2.io/) to perform bytecode manipulation.


  * [Examples](#examples)
    + [Count down to zero](#count-down-to-zero)
    + [List numbers in a string](#list-numbers-in-a-string)
    + [Enumerate all interfaces that a class implements](#enumerate-all-interfaces-that-a-class-implements)
    + [Side effect free instruction removal](#side-effect-free-instruction-removal)
    + [this instance change](#this-instance-change)
  * [Limitations](#limitations)
    + [Recommendations](#recommendations)
  * [Usage](#usage)
    + [Part of the build process](#part-of-the-build-process)
      - [ZIP transformer](#zip-transformer)
      - [Class directory optimization](#class-directory-optimization)
      - [Optimize an existing archive](#optimize-an-existing-archive)
    + [Command line usage](#command-line-usage)
      - [With saker.build](#with-sakerbuild)
  * [Benchmarks](#benchmarks)
    + [Optimized](#optimized)
    + [Unoptimized](#unoptimized)
  * [Building the project](#building-the-project)
  * [Repository structure](#repository-structure)
  * [Should I use this?](#should-i-use-this)
  * [License](#license)


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

### Enumerate all interfaces that a class implements

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

### this instance change

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
    * Another reason is why virtual methods cannot be optimized is that they can be called from the subclass using the `super.method(...)` expression. If a subclass calls the method then the tail recursive calls would need to be dispatched back to the subclass. This causes virtual methods to not be optimizable.
2. Synchronized instance methods cannot be optimized. See the previous point for the reasons.
    * `static` method **can** be synchronized.
3. If you throw an exception in the method, the stacktrace will only show the method once, as the tail recursive calls are optimized.

### Recommendations

The methods you want to be subject to optimization should be any of the following:

* `static` method.
* `private` instance method.
* `final` instance method.    

## Usage

The project is released as the [sipka.jvm.tailrec](https://nest.saker.build/package/sipka.jvm.tailrec?tab=bundles) package on the [saker.nest repository](https://nest.saker.build/).\
You can [**download the latest release using this link**](https://api.nest.saker.build/bundle/download/sipka.jvm.tailrec-v0.8.2) or by selecting a version and clicking *Download* on the *Bundles* tab on the [sipka.jvm.tailrec](https://nest.saker.build/package/sipka.jvm.tailrec?tab=bundles) package page.

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

```plaintext
java -jar sipka.jvm.tailrec.jar -output my_jar_opt.jar my_jar.jar
```

The above will optimize `my_jar.jar` and create the output of `my_jar_opt.jar`. You can also overwrite the input:

```plaintext
java -jar sipka.jvm.tailrec.jar -overwrite my_jar.jar
```

Which causes the input JAR to be overwritten with the result.

The input can also be a class directory.

See `--help` for more usage information.

#### With saker.build

If you already have the [saker.build system](https://github.com/sakerbuild/saker.build) at hand, you don't have to bother with downloading. You can use the `main` action of saker.nest to invoke the library:

```plaintext
java -jar saker.build.jar action main sipka.jvm.tailrec --help
```

## Benchmarks

Our results are the following: (**Higher values are better**)

See the [benchmark](/benchmark) directory for more information.

### Optimized

```plaintext
Benchmark                            Mode  Cnt        Score      Error  Units
TailRecursionBenchmark.countTest    thrpt   25   436354,616 ? 2208,882  ops/s
TailRecursionBenchmark.factTest     thrpt   25  1201126,490 ? 8081,594  ops/s
TailRecursionBenchmark.numbersTest  thrpt   25     2183,977 ?   62,684  ops/s
```

### Unoptimized

```plaintext
Benchmark                            Mode  Cnt        Score      Error  Units
TailRecursionBenchmark.countTest    thrpt   25   257429,802 ? 1501,296  ops/s
TailRecursionBenchmark.factTest     thrpt   25   831008,693 ? 9108,785  ops/s
TailRecursionBenchmark.numbersTest  thrpt   25     2083,716 ?   14,563  ops/s
```

## Building the project

The project uses the [saker.build system](https://github.com/sakerbuild/saker.build) for building.

Use the following command, or do build it inside an IDE:

```plaintext
java -jar saker.build.jar -build-directory build export
```

See the [build script](/saker.build) for the executable build targets.

## Repository structure

* `src`: The source files for the project
    * Sources for the ASM library are under the package `sipka.jvm.tailrec.thirdparty`.
* `resources`: Resource files for the created JAR files
* `test/src`: Test Java sources
* `test/resources`: Resource files for test cases which need them

## Should I use this?

You should use it, but you **should not** rely on it.\
In general, when you're writing production code, you'll most likely already optimize your methods in ways that it already avoids issues that are solvable with tail recursion optimization.

My recommendation is that in general you shouldn't rely on a specific optimization being performed for you. They are subject to the circumstances, and can easily break without the intention of breaking it. For example, by not paying attention and accidentally adding a new instruction after the tail recursive call that you want to optimize, will cause the optimization to not be performed. This could cause your code to break unexpectedly. \
If you want an optimization done for you, you should do it yourself, or be extremely explicit about it. Make sure to add tests for the scenarios that you want to work in a specific way.

This project serves mainly educational purposes, and is also fun as you can write infinite loops like this:

```java
public static void loopForever() {
    System.out.println("Hello world!");
    
    loopForever();
}
```

Magnificent!

## License

The source code for the project is licensed under *GNU General Public License v3.0 only*.

Short identifier: [`GPL-3.0-only`](https://spdx.org/licenses/GPL-3.0-only.html).

Official releases of the project (and parts of it) may be licensed under different terms. See the particular releases for more information.
