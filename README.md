A simple tool to check the differences between maven and gradle builds.

Run in the project directory. 

Files with following suffixes are omitted from the check: "-tests.jar", "-sources.jar", "-javadoc.jar", "-benchmarks.jar"
 
Usage:
```bash
java -DmvnVersion=<version used in the maven build> -DgradleVersion=<version used in the gradle build> -jar <jardif jar> 
```
**WARNING** although written in java, the project relies on bash tools like `find` and `diff`
