# CallPathPlugin-Intellij

### Plugin for IntelliJ IDEA to find call paths in Java code.

Plugin uses PSI (Program Structure Interface) to analyze Java code and find call path.
After clicking on a method, and selecting "Find Call Path to Method", user will be prompted to input a method name.
The plugin will find all methods with given name and try to find call paths from the selected method to the given method.

### Example

```java
void foo() {
  bar();
}

void bar() {
  baz();
}

void baz() {
  interestingMethod();
}
```
In this example, if user clicks on `foo()` and selects "Find Call Path to Method",
and inputs `interestingMethod()`, the plugin will find the following call path:

```foo -> bar -> baz```

### Technical Details
Plugin starts to form a path from every method with the same name as the method user is looking for.
Then algorithm searches for references to the methods that call the method user is looking for.
It lasts until the algorithm finds a method that plugin was invoked from.
In other words, algorithm goes backwards from the method user is looking for to the method user clicked on.
Algorithm do it for every method with the same name as the method user is looking for!
If method user is looking for is executed in other thread, plugin will not find the path as those methods may
just work in parallel and not call each other.

### Running:
1. Clone the repository:
    ``` 
    git clone https://github.com/Axan18/CallPathPlugin-Intellij.git 
   ```
2. Open cloned project in Intellij Idea
3. Open Gradle tool window (View -> Tool Windows -> Gradle) and reload project
4. Run the plugin by right-clicking on any method name and input name of the method that you want to find call path.