A simple tool to check the differences between jars, either two specific jars or built by Maven and Gradle.

**WARNING** although written in java, the project relies on bash tools like `find` and `diff`

## Maven vs Gradle

Run in the project directory. 

Files with following suffixes are omitted from the check: "-tests.jar", "-sources.jar", "-javadoc.jar", "-benchmarks.jar"
 
Usage:
```bash
java -DmvnVersion=<version used in the maven build> -DgradleVersion=<version used in the gradle build> -jar <jardif jar> mvnVsGradle 
```                                                                                          

## Two specific jars
Usage:
```bash
java -jar <jardif jar> diff <jar1> <jar2> 
```

