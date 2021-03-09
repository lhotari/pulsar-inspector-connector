package com.github.lhotari.pulsarinspector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.Source;
import org.apache.pulsar.io.core.SourceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarInspectorSource implements Source<String> {
    private final static Logger LOG = LoggerFactory.getLogger(PulsarInspectorSource.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void open(Map<String, Object> map, SourceContext sourceContext) throws Exception {

    }

    @Override
    public Record<String> read() throws Exception {
        Thread.sleep(5000L);
        return new Record<String>() {
            @Override
            public Optional<String> getKey() {
                return Optional.empty();
            }

            @Override
            public String getValue() {
                return createFullReportAsJsonString();
            }
        };
    }

    @Override
    public void close() throws Exception {

    }

    private String createFullReportAsJsonString() {
        Map<String, Object> resultMap = new LinkedHashMap<>();

        addClassInfo(getClass(), resultMap);
        addClassInfo(ObjectMapper.class, resultMap);
        addClassInfo(Source.class, resultMap);
        addClassInfo(Logger.class, resultMap);

        String classloaderReport = createClassloaderReport(Thread.currentThread().getContextClassLoader());
        LOG.info("ClassLoader report {}", classloaderReport);
        resultMap.put("classloader_report", classloaderReport);
        if (getClass().getClassLoader() != Thread.currentThread().getContextClassLoader()) {
            resultMap.put("function_classloader_report",
                    createClassloaderReport(getClass().getClassLoader()));
        }
        try {
            String classloaderStats = createClassloaderStats();
            LOG.info("ClassLoader stats {}", classloaderStats);
            resultMap.put("classloader_stats", classloaderStats);
        } catch (Exception e) {
            LOG.error("Error getting classloader stats", e);
        }

        try {
            return objectMapper.writeValueAsString(resultMap);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addClassInfo(Class<?> clazz, Map<String, Object> resultMap) {
        resultMap.put(clazz.getName(), getClassInfo(clazz));
    }

    private Map<String, String> getClassInfo(Class<?> clazz) {
        Map<String, String> classInfo = new LinkedHashMap<>();
        classInfo.put("source", getCodeSource(clazz));
        classInfo.put("classloader", clazz.getClassLoader().toString());
        return classInfo;
    }

    private String getCodeSource(Class<?> clazz) {
        return clazz.getProtectionDomain().getCodeSource()
                .getLocation().toExternalForm();
    }

    private String createClassloaderReport(ClassLoader classLoader) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter out = new PrintWriter(stringWriter);
        printClassLoaderInfo(classLoader, out, "  ");
        out.close();
        return stringWriter.toString();
    }

    private void printClassLoaderInfo(ClassLoader classLoader, PrintWriter out, String indent) {
        out.print(indent);
        out.println("Classloader: " + classLoader.toString());
        out.print(indent);
        out.println("identityHashCode:" + System.identityHashCode(classLoader));
        if (classLoader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) classLoader).getURLs();
            for (URL url : urls) {
                out.print(indent);
                out.println(url.toExternalForm());
            }
        }
        if (classLoader.getParent() != null) {
            out.print(indent);
            out.println("Parent:");
            printClassLoaderInfo(classLoader.getParent(), out, indent + "  ");
        }
    }

    private String createClassloaderStats() throws IOException, InterruptedException {
        File tempFile = File.createTempFile("jcmdoutput", ".txt");
        ProcessBuilder processBuilder = new ProcessBuilder(findJcmd().toString(),
                getProcessId(), "VM.classloader_stats")
                .redirectErrorStream(true)
                .redirectOutput(tempFile);
        int retval = processBuilder
                .start()
                .waitFor();
        if (retval != 0) {
            LOG.error("Command {} failed with retval {}", processBuilder.command(), retval);
        }
        String classloaderStats = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);
        tempFile.delete();
        return classloaderStats;
    }

    Path findJcmd() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        if (javaHome.endsWith("jre")) {
            javaHome = javaHome.getParent();
        }
        return javaHome.resolve("bin/jcmd").toAbsolutePath();
    }

    private static String getProcessId() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

}
