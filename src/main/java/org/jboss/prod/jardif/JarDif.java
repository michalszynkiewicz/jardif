/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.prod.jardif;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.prod.jardif.utils.OSUtils;
import org.jboss.prod.jardif.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jboss.prod.jardif.utils.OSUtils.runCommand;


/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 3/22/17
 * Time: 10:10 AM
 */
public class JarDif {
    private static final String mvnVersion = System.getProperty("mvnVersion", "-0.4.9");
    private static final String gradleVersion = System.getProperty("gradleVersion", "-0.1.0-SNAPSHOT");
    private static final String[] neglected = new String[]{"-tests.jar", "-sources.jar", "-javadoc.jar", "-benchmarks.jar"};
    private static final File workDir = new File(".jardif");

    private static final Boolean verbose = Boolean.valueOf(System.getProperty("verbose", "false"));
    public static final Predicate<String> skipNotImportant = name -> Stream.of(neglected).noneMatch(name::endsWith);

    private List<String> gradleJars() {
        return runCommand("find . -path '*/build/*.jar'");
    }

    private List<String> mavenJars() {
        return runCommand("find . -path '*/target/*.jar'");
    }

    private Map<Path, Path> common() {
        Map<Path, Path> resultMap = new HashMap<>();
        List<Path> maven =
                mavenJars().stream()
                        .filter(skipNotImportant)
                        .map(Paths::get)
                        .collect(Collectors.toList());
        List<Path> gradle = gradleJars().stream()
                .filter(skipNotImportant)
                .map(Paths::get)
                .collect(Collectors.toList());

        maven.forEach(m -> {
            Optional<Path> gradlePath = findCorrespondingGradle(gradle, m);
            gradlePath.ifPresent(p -> {
                resultMap.put(m, p);
                gradle.remove(p);
            });
        });
        if (!gradle.isEmpty()) {
            System.err.println("MISSING MAVEN JARS: " + gradle);
        }
        List<String> missingMaven = new ArrayList<>();
        missingMaven.removeAll(resultMap.keySet());
        if (!missingMaven.isEmpty()) {
            System.err.println("MISSING GRADLE JARS: " + gradle);
        }

        return resultMap;
    }

    private Optional<Path> findCorrespondingGradle(List<Path> gradle, Path mavenJar) {
        String mvnJarName = mavenJar.getFileName().toString();
        String gradleJarName = mvnJarName.replaceAll(Pattern.quote(mvnVersion), gradleVersion);
        return gradle.stream().filter(p -> p.getFileName().toString().equals(gradleJarName)).findAny();
    }

    private void analyze() {
        Map<Path, Path> common = common();
        common.entrySet().stream().forEach(e -> compare(e.getKey(), e.getValue()));
    }

    private void compare(Path maven, Path gradle) {
        System.out.println("===============================================");
        System.out.println(maven + " vs " + gradle);
        System.out.println("===============================================");
        Path gradleDir = new File(workDir, "gradle-" + gradle.getFileName().toString()).toPath();
        Path mvnDir = new File(workDir, "maven-" + maven.getFileName().toString()).toPath();
        ZipUtils.unzip(maven, mvnDir);
        ZipUtils.unzip(gradle, gradleDir);

        List<String> mvnContents = OSUtils.runCommandIn("find . -type f", mvnDir);
        mvnContents.sort(Comparator.comparingInt(String::hashCode));
        List<String> gradleContents = OSUtils.runCommandIn("find . -type f", gradleDir);
        gradleContents.sort(Comparator.comparingInt(String::hashCode));

        Iterator<String> mvnFiles = mvnContents.stream().iterator();
        Iterator<String> gradleFiles = gradleContents.stream().iterator();

        if (!mvnFiles.hasNext() || !gradleFiles.hasNext()) {
            throw new IllegalStateException("One of the jars: " + maven + ", " + gradle + " is empty! Exitting");
        }
        String mvnFile = mvnFiles.next();
        String gradleFile = gradleFiles.next();

        while (mvnFiles.hasNext() && gradleFiles.hasNext()) {
            if (mvnFile.equals(gradleFile)) {
                compareFiles(new File(mvnDir.toFile(), mvnFile), new File(gradleDir.toFile(), gradleFile));
                mvnFile = mvnFiles.next();
                gradleFile = gradleFiles.next();
            } else if (mvnFile.hashCode() < gradleFile.hashCode()) {
                System.out.println("+" + mvnFile);
                mvnFile = mvnFiles.next();
            } else {
                System.out.println("-" + gradleFile);
                gradleFile = gradleFiles.next();
            }
        }

        mvnFiles.forEachRemaining(e -> System.out.println("+" + e));
        gradleFiles.forEachRemaining(e -> System.out.println("-" + e));
        System.out.println();
        System.out.println();
    }

    private void compareFiles(File mavenFile, File gradleFile) {
        String mavenFileName = mavenFile.getAbsolutePath();
        String gradleFileName = gradleFile.getAbsolutePath();
        String mvnToCompare = mavenFileName;
        String gradleToCompare = gradleFileName;

        if (mavenFileName.endsWith(".class")) {
            String gradleBytecodeFile = extractBytecode(gradleFileName);
            String mvnBytecodeFile = extractBytecode(mavenFileName);

            mvnToCompare = mvnBytecodeFile;
            gradleToCompare = gradleBytecodeFile;

        }

        OSUtils.CommandExecutor exec = OSUtils.executor("diff " + mvnToCompare + " " + gradleToCompare).exec();
        int status = exec.getStatus();
        if (status != 0) {
            System.out.println("* " + mavenFile.getAbsolutePath() + " X " + gradleFile.getAbsolutePath());
            if (verbose) System.out.println(StringUtils.join(exec.getOut(), "\n"));
        }
    }

    private String extractBytecode(String fileName) {
        String bytecodeFileName = fileName.replace(".class", ".bytecode");
        OSUtils.executor("javap -c " + fileName).failOnInvalidStatusCode().toFile(bytecodeFileName).exec();
        return bytecodeFileName;
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalStateException("Run the program in the project directory");
        }
        if (workDir.exists()) {
            FileUtils.deleteDirectory(workDir);
        }
        workDir.mkdirs();

        new JarDif().analyze();
    }
}
