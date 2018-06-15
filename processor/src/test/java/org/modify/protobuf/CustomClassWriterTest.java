package org.modify.protobuf;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.modify.protobuf.CustomClassWriter.AddInterfaceAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.testng.annotations.Test;

import protobuf_unittest.FirstData;

public class CustomClassWriterTest {
    
    private String toFilename(Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    }

    @Test(enabled = false)
    public void testName() throws IOException {
        String file = FirstData.class.getProtectionDomain().getCodeSource().getLocation().getFile() + toFilename(FirstData.class);
        ClassReader reader;
        try (FileInputStream is = new FileInputStream(file)) {
            reader = new ClassReader(is);
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        AddInterfaceAdapter addInterfaceAdapter = new AddInterfaceAdapter(writer, "org/modify/protobuf/Data");
        reader.accept(addInterfaceAdapter, 0);
        try (FileOutputStream fw = new FileOutputStream(file)) {
            byte[] array = writer.toByteArray();
            fw.write(array, 0, array.length);
        }
    }
}
