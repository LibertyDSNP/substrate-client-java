package com.strategyobject.substrateclient.scale.writers;

import com.strategyobject.substrateclient.common.utils.Convert;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListWriterTest {
    @SneakyThrows
    @Test
    void write() {
        val listWriter = new ListWriter<>(new U16Writer());

        val stream = new ByteArrayOutputStream();
        listWriter.write(Arrays.asList(4, 8, 15, 16, 23, 42), stream);
        val actual = Convert.toHex(stream.toByteArray());

        assertEquals("0x18040008000f00100017002a00", actual);
    }

    @SneakyThrows
    @Test
    void writeEmpty() {
        val listWriter = new ListWriter<>(new U16Writer());

        val stream = new ByteArrayOutputStream();
        listWriter.write(new ArrayList<>(), stream);
        val actual = Convert.toHex(stream.toByteArray());

        assertEquals("0x00", actual);
    }
}