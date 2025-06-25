package org.ipan.hedging.telegram.mapdb;

import java.io.IOException;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GenericSerializer<T> implements Serializer<T> {
    private final ObjectMapper objectMapper;
    private final Class<T> clazz;

    public GenericSerializer(Class<T> clazz, ObjectMapper objectMapper) {
        this.clazz = clazz;
        this.objectMapper = objectMapper;
    }

    @Override
    public void serialize(DataOutput2 out, T value) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    @Override
    public T deserialize(DataInput2 input, int available) throws IOException {
        int len = input.readInt();
        byte[] bytes = new byte[len];
        input.readFully(bytes);
        return objectMapper.readValue(bytes, clazz);
    }
}
