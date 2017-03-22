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

package org.jboss.prod.jardif.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 3/22/17
 * Time: 10:35 AM
 */
public class OSUtils {

    public static List<String> runCommand(String s) {
        return runCommandIn(s, null);
    }

    public static List<String> runCommandIn(String command, Path directory) {
        return new CommandExecutor(command)
                .directory(directory)
                .failOnInvalidStatusCode()
                .exec()
                .getOut();
    }

    public static CommandExecutor executor(String s) {
        return new CommandExecutor(s);
    }

    public static class CommandExecutor {
        private final ProcessBuilder builder;
        private boolean failOnStatusCode = false;
        private int status;
        private List<String> out = new ArrayList<>();
        private String outputFile = null;

        public CommandExecutor(String command) {
            this.builder = new ProcessBuilder(unescape(command.split("\\s")));

        }

        public CommandExecutor directory(Path directory) {
            if (directory != null) {
                builder.directory(directory.toFile());
            }
            return this;
        }

        public CommandExecutor failOnInvalidStatusCode() {
            failOnStatusCode = true;
            return this;
        }

        public CommandExecutor exec() {
            try {
                builder.redirectErrorStream(true);
                Process process = builder.start();
                read(process.getInputStream(), out);
                process.waitFor();
                status = process.exitValue();

                if (outputFile != null) {
                    File outFile = new File(outputFile);//.replaceAll("\\$", "dollar"));
                    if (!outFile.createNewFile()) {
                        throw new IllegalStateException("Could not create output file " + outputFile);
                    }
                    try (FileWriter writer = new FileWriter(outFile)) {
                        writer.append(StringUtils.join(out, "\n"));
                    }
                }
                if (failOnStatusCode && status != 0) {
                    throw new IllegalStateException("Failed to execute command " + builder.command() + ". Status code: " + status + " Process output: " + out);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                throw new IllegalStateException(("Failed to execute command " + builder.command() + ". Process output: " + out), e);
            }
            return this;
        }

        public int getStatus() {
            return status;
        }

        public List<String> getOut() {
            return out;
        }

        private static String[] unescape(String[] input) {
            return (String[]) Stream.of(input).map(CommandExecutor::stripQuoteMarks).toArray(String[]::new);
        }

        private static String stripQuoteMarks(String s) {
            for (String quoteMark : Arrays.asList("\"", "'")) {
                if (s.startsWith(quoteMark) && s.endsWith(quoteMark)) {
                    s = s.substring(1, s.length() - 1);
                }
            }
            return s;
        }

        private static void read(InputStream inputStream, List<String> out) throws IOException {
            try (InputStreamReader streamReader = new InputStreamReader(inputStream);
                 BufferedReader reader = new BufferedReader(streamReader)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.add(line);
                }
            }
        }

        public CommandExecutor toFile(String outputFile) {
            this.outputFile = outputFile;
            return this;
        }
    }
}
