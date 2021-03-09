package com.github.lhotari.pulsarinspector;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.apache.pulsar.functions.api.Record;
import org.junit.jupiter.api.Test;

class PulsarInspectorSourceTest {

    @Test
    void shouldReturnReport() throws Exception {
        PulsarInspectorSource source = new PulsarInspectorSource();
        Record<String> record = source.read();
        String report = record.getValue();
        System.out.println(report);
        assertNotNull(report);
    }

}