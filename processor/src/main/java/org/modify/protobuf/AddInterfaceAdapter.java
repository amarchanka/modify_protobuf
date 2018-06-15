package org.modify.protobuf;

import static org.objectweb.asm.Opcodes.ASM4;
import static org.objectweb.asm.Opcodes.V1_5;

import java.util.Arrays;

import org.objectweb.asm.ClassVisitor;

public class AddInterfaceAdapter extends ClassVisitor {
    private final String interfaceName;
    private boolean modified;

    public AddInterfaceAdapter(ClassVisitor cv, String interfaceName) {
        super(ASM4, cv);
        this.interfaceName = interfaceName;
    }

    @Override
    public void visit(int version, int access, String name,
            String signature, String superName, String[] interfaces) {
        if (!Arrays.asList(interfaces).contains(interfaceName)) {
            String[] holding = new String[interfaces.length + 1];
            holding[holding.length - 1] = interfaceName;
            System.arraycopy(interfaces, 0, holding, 0, interfaces.length);
            cv.visit(V1_5, access, name, signature, superName, holding);
            modified = true;
        } else {
            cv.visit(V1_5, access, name, signature, superName, interfaces);
            modified = false;
        }
    }

    public boolean isModified() {
        return modified;
    }
}
