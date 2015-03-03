package com.jicka.core;

import com.jicka.core.JickaVisitor.ModifierMethodWriter;
import java.util.List;
import static org.objectweb.asm.Opcodes.*;

/**
 *
 * @author Mickael Boudignot
 */
class JickaModifier {

    /**
     * Field to change configuration about location of the LocalHeap class.
     */
    private final static String LOCALHEAP = "com/jicka/core/LocalHeap";

    /**
     * Field to change configuration about location of the JickaThreadLocal
     * class.
     */
    private final static String JICKATL = "com/jicka/core/JickaThreadLocal";

    /**
     * Store the method writer to allow modification of the current class.
     */
    private final ModifierMethodWriter mw;

    /**
     * The current class that is in modification.
     */
    private final String className;

    /**
     * Create a new JickaModifier to modify the code of the class and inject the
     * new semantic.
     *
     * @param mw The method writer.
     * @param className The current name of the classe.
     */
    JickaModifier(ModifierMethodWriter mw, String className) {
        this.mw = mw;
        this.className = className;
    }

    /**
     * Called to create and initialize the LocalThread of the Thread. This
     * method is called when visitor detect a new Thread.
     */
    public void startThread() {
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, "createLocalHeap", "()V", false);
    }

    /**
     * Method called during the initialisation of a static block to create all
     * Getter/Setter (MethodHandles) for each static field.
     */
    public void createMethodHandle() {
        List<ASMMethodHandle.Information> methodHandles = ASMMethodHandle.get().getMethodHandle();
        for (ASMMethodHandle.Information smh : methodHandles) {

            /* Create and store a MethodHandles to allow modification during execution */
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false, false);
            pushOffset(smh.owner, smh.name, smh.desc);
            mw.visitLdcInsn(smh.owner);
            mw.visitLdcInsn(smh.name);
            mw.visitLdcInsn(smh.desc);
            mw.visitLdcInsn(smh.isFinal);
            mw.visitLdcInsn(smh.isVolatile);
            mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, "createMethodHandles", "(Ljava/lang/invoke/MethodHandles$Lookup;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)V", false, false);
        }
        ASMMethodHandle.get().clearMethodHandle();
    }

    /**
     * Method called when the init block of an instance is finished to
     * initialize the ThreadLocal of the instance.
     */
    public void createInitBlock() {

        /* Load the current instance */
        mw.visitVarInsn(ALOAD, 0);

        /* Create our ThreadLocal */
        mw.visitTypeInsn(NEW, JICKATL);

        /* Initialize ThreadLocal with the instance */
        mw.visitInsn(DUP);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitMethodInsn(INVOKESPECIAL, JICKATL, "<init>", "(Ljava/lang/Object;)V", false, false);

        /* Store the ThreadLocal in a special field of the instance */
        mw.visitFieldInsn(PUTFIELD, className, JickaThreadLocal.FIELDNAME, "Ljava/lang/ThreadLocal;", false);
    }

    /**
     * Increase block of the localThread. This method is called when visitor
     * detect a MonitorEnter.
     */
    public void lock() {
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, "increaseBlock", "()V", false, false);
    }

    /**
     * Decrease block of the localThread. This method is called when visitor
     * detect a MonitorExit.
     */
    public void unlock() {
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, "decreaseBlock", "()V", false, false);
    }

    /**
     * Read data from heap to update local storage. This method is called when
     * visitor detect an refresh needed (volatile or MonitorEnter).
     */
    public void refresh() {
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, "refresh", "()V", false, false);
    }

    /**
     * Write data from the local storage to the heap. This method is called when
     * visitor detect an flush needed (volatile or MonitorExit).
     */
    public void flush() {
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, "flush", "()V", false, false);
    }

    /**
     * Push on the top of the stack the offset of the static field.
     *
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc The description which represent the type of the field.
     */
    private void pushOffset(String owner, String name, String desc) {
        int offset = ASMFieldStatic.get().getOffset(owner, name, desc);
        mw.visitLdcInsn(offset);
    }

    /**
     * Change the value of a local static field.
     *
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc The description which represent the type of the field.
     */
    public void setStaticVariable(String owner, String name, String desc) {

        /* Push the offset of the variable */
        pushOffset(owner, name, desc);

        /* Calcul signature */
        String method;
        String signature;
        switch (desc) {

            case "B":
                method = "setStaticByte";
                signature = "B";
                break;
            case "S":
                method = "setStaticShort";
                signature = "S";
                break;
            case "C":
                method = "setStaticChar";
                signature = "C";
                break;
            case "I":
                method = "setStaticInteger";
                signature = "I";
                break;
            case "J":
                method = "setStaticLong";
                signature = "J";
                break;
            case "D":
                method = "setStaticDouble";
                signature = "D";
                break;
            case "Z":
                method = "setStaticBoolean";
                signature = "Z";
                break;
            case "F":
                method = "setStaticFloat";
                signature = "F";
                break;
            default:
                method = "setStaticObject";
                signature = "Ljava/lang/Object;";
                break;
        }

        /* Put variable in the local storage */
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, method, String.format("(%sI)V", signature), false, false);
    }

    /**
     * Get the value of the static field from the LocalHeap.
     * 
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc The description which represent the type of the field.
     */
    public void getStaticVariable(String owner, String name, String desc) {

        /* Push the offset of the variable */
        pushOffset(owner, name, desc);

        /* Calcul signature */
        String method;
        String signature;
        boolean cast = false;
        switch (desc) {
            case "B":
                method = "getStaticByte";
                signature = "B";
                break;
            case "S":
                method = "getStaticShort";
                signature = "S";
                break;
            case "C":
                method = "getStaticChar";
                signature = "C";
                break;
            case "I":
                method = "getStaticInteger";
                signature = "I";
                break;
            case "J":
                method = "getStaticLong";
                signature = "J";
                break;
            case "D":
                method = "getStaticDouble";
                signature = "D";
                break;
            case "Z":
                method = "getStaticBoolean";
                signature = "Z";
                break;
            case "F":
                method = "getStaticFloat";
                signature = "F";
                break;
            default:
                method = "getStaticObject";
                signature = "Ljava/lang/Object;";
                cast = true;
                break;
        }

        /* Get variable from local storage */
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, method, String.format("(I)%s", signature), false, false);

        /* If object, cast it */
        if (cast) {
            if (desc.startsWith("L") && desc.endsWith(";")) {
                desc = desc.substring(1, desc.length() - 1);
            }
            mw.visitTypeInsn(CHECKCAST, desc);
        }
    }

    /**
     * Change the value of a local instance field.
     * 
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc The description which represent the type of the field.
     */
    public void setFieldVariable(String owner, String name, String desc) {

        /* Push the name of the field to store on the stack */
        mw.visitLdcInsn(name);

        /* Calcul signature */
        String method;
        String signature;
        switch (desc) {
            case "B":
                method = "setFieldByte";
                signature = "B";
                break;
            case "S":
                method = "setFieldShort";
                signature = "S";
                break;
            case "C":
                method = "setFieldChar";
                signature = "C";
                break;
            case "I":
                method = "setFieldInteger";
                signature = "I";
                break;
            case "J":
                method = "setFieldLong";
                signature = "J";
                break;
            case "D":
                method = "setFieldDouble";
                signature = "D";
                break;
            case "Z":
                method = "setFieldBoolean";
                signature = "Z";
                break;
            case "F":
                method = "setFieldFloat";
                signature = "F";
                break;
            default:
                method = "setFieldObject";
                signature = "Ljava/lang/Object;";
                break;
        }

        /* Put variable in the local storage */
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, method, String.format("(Ljava/lang/Object;%sLjava/lang/String;)V", signature), false, false);
    }

    /**
     * Get the value of the instance field from the LocalHeap.
     * 
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc The description which represent the type of the field.
     */
    public void getFieldVariable(String owner, String name, String desc) {

        /* Push the name of the field to load on the stack */
        mw.visitLdcInsn(name);

        /* Calcul signature */
        String method;
        String signature;
        boolean cast = false;
        switch (desc) {
            case "B":
                method = "getFieldByte";
                signature = "B";
                break;
            case "S":
                method = "getFieldShort";
                signature = "S";
                break;
            case "C":
                method = "getFieldChar";
                signature = "C";
                break;
            case "I":
                method = "getFieldInteger";
                signature = "I";
                break;
            case "J":
                method = "getFieldLong";
                signature = "J";
                break;
            case "D":
                method = "getFieldDouble";
                signature = "D";
                break;
            case "Z":
                method = "getFieldBoolean";
                signature = "Z";
                break;
            case "F":
                method = "getFieldFloat";
                signature = "F";
                break;
            default:
                method = "getFieldObject";
                signature = "Ljava/lang/Object;";
                cast = true;
                break;
        }

        /* Get variable from the local storage */
        mw.visitMethodInsn(INVOKESTATIC, LOCALHEAP, method, String.format("(Ljava/lang/Object;Ljava/lang/String;)%s", signature), false, false);

        /* If it's an Object, cast it */
        if (cast) {
            if (desc.startsWith("L") && desc.endsWith(";")) {
                desc = desc.substring(1, desc.length() - 1);
            }
            mw.visitTypeInsn(CHECKCAST, desc);
        }
    }
}
