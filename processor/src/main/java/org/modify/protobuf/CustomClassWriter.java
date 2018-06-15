package org.modify.protobuf;

import static org.objectweb.asm.Opcodes.ASM4;
import static org.objectweb.asm.Opcodes.V1_5;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 *
 * @author baeldung
 * @param <String>
 */
public class CustomClassWriter {

    ClassReader reader;
    ClassWriter writer;
    AddInterfaceAdapter addInterfaceAdapter;
    final static String CLASSNAME = "java.lang.Integer";
    final static String CLONEABLE = "java/lang/Cloneable";

    public CustomClassWriter() {

        try {
            reader = new ClassReader(CLASSNAME);
            writer = new ClassWriter(reader, 0);

        } catch (IOException ex) {
            Logger.getLogger(CustomClassWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public CustomClassWriter(byte[] contents) {
        reader = new ClassReader(contents);
        writer = new ClassWriter(reader, 0);
    }

    public byte[] addInterface() {
        addInterfaceAdapter = new AddInterfaceAdapter(writer, CLONEABLE);
        reader.accept(addInterfaceAdapter, 0);
        return writer.toByteArray();
    }

    public static class AddInterfaceAdapter extends ClassVisitor {
        private final  String interfaceName;
        
        public AddInterfaceAdapter(ClassVisitor cv, String interfaceName) {
            super(ASM4, cv);
            this.interfaceName = interfaceName;
        }

        @Override
        public void visit(int version, int access, String name,
                String signature, String superName, String[] interfaces) {
            String[] holding = new String[interfaces.length + 1];
            holding[holding.length - 1] = interfaceName;
            System.arraycopy(interfaces, 0, holding, 0, interfaces.length);

            cv.visit(V1_5, access, name, signature, superName, holding);
        }

    }
}
