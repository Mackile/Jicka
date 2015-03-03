package com.jicka.core;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 *
 * @author Mickael Boudignot
 */
class JickaVisitor extends ClassVisitor {

    /**
     * The name of the current class visited.
     */
    private final String className;

    /**
     * Flag to identify the first field.
     */
    private boolean firstField = true;

    /**
     * Create a new JickaVisitor.
     *
     * @param i The version of ASM.
     * @param cv The classWriter of ASM to add modification.
     * @param className The name of the class.
     */
    JickaVisitor(int i, ClassWriter cv, String className) {
        super(i, cv);
        this.className = className;

        /* Add storage for field */
        ASMClass.get().addClass(className);
    }

    /**
     * Inner class to identify all methods and instructions.
     */
    class ModifierMethodWriter extends MethodVisitor {

        /**
         * The name of the current visited method.
         */
        private final String methodName;

        /**
         * The description of the current method which represent the signature
         * of it.
         */
        private final String desc;

        /**
         * The JickaModifier which will modify instructions to apply semantic.
         */
        private final JickaModifier semantic;

        /**
         * Create a new ModifierMethodWriter.
         *
         * @param api The version of ASM.
         * @param mv The MethodVisitor of ASM to visit all methods.
         * @param methodName The name of the current visited method.
         * @param desc The description of the current method which represent the
         * signature of it.
         */
        ModifierMethodWriter(int api, MethodVisitor mv, String methodName, String desc) {
            super(api, mv);
            this.methodName = methodName;
            this.desc = desc;
            semantic = new JickaModifier(this, className);
        }

        /**
         * Method call when a method start.
         */
        @Override
        public void visitCode() {
            super.visitCode();

            if (methodName.equals("main") && desc.equals("([Ljava/lang/String;)V")) {

                /* Detect main */
                semantic.startThread();

            } else if (methodName.equals("run") && desc.equals("()V")) {

                /* Detect run method */
                semantic.startThread();

            } else if (methodName.equals("<clinit>")) {

                /* Detetc static block init */
                semantic.createMethodHandle();
            }
        }

        /**
         * Method call when an instruction is called.
         *
         * @param opcode The OPCODE of the instruction.
         */
        @Override
        public void visitInsn(int opcode) {
            visitInsn(opcode, true);
        }

        /**
         * A specific method of visitInsn to implement the interception or not.
         *
         * @param opcode The OPCODE of the instruction.
         * @param intercept A flag to know if we implement the semantic.
         */
        public void visitInsn(int opcode, boolean intercept) {
            if (intercept) {
                if (opcode == Opcodes.MONITORENTER) {

                    /* Synchronized enter */
                    super.visitInsn(opcode);
                    semantic.lock();
                    semantic.refresh();
                    return;

                } else if (opcode == Opcodes.MONITOREXIT) {

                    /* Synchronized exit */
                    semantic.flush();
                    semantic.unlock();

                } else if (opcode == Opcodes.RETURN && methodName.equals("<init>")) {

                    /* Class init */
                    semantic.createInitBlock();
                }
            }

            super.visitInsn(opcode);
        }

        /**
         * Visits a field instruction. A field instruction is an instruction
         * that loads or stores the value of a field of an object.
         *
         * @param opcode The OPCODE of the instruction.
         * @param owner The name of the class container.
         * @param name The name of the field.
         * @param desc The description which represent the type of the field.
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            visitFieldInsn(opcode, owner, name, desc, true);
        }

        /**
         * A specific method of visitFieldInsn to implement the interception or
         * not.
         *
         * @param opcode The OPCODE of the instruction.
         * @param owner The name of the class container.
         * @param name The name of the field.
         * @param desc The description which represent the type of the field.
         * @param intercept A flag to know if we implement the semantic.
         */
        public void visitFieldInsn(int opcode, String owner, String name, String desc, boolean intercept) {

            if (intercept) {

                /* Check if we are in field that isn't from JDK */
                if (!owner.startsWith("java/")
                        && !"<init>".equals(methodName)
                        && !"<clinit>".equals(methodName)
                        && Jicka.inspect(owner)) {

                    /* Copy static instruction into local stack */
                    if (opcode == Opcodes.PUTSTATIC) {
                        semantic.setStaticVariable(owner, name, desc);
                        return;
                    } else if (opcode == Opcodes.GETSTATIC) {
                        semantic.getStaticVariable(owner, name, desc);
                        return;
                    } else if (opcode == Opcodes.PUTFIELD) {
                        semantic.setFieldVariable(owner, name, desc);
                        return;
                    } else if (opcode == Opcodes.GETFIELD) {
                        semantic.getFieldVariable(owner, name, desc);
                        return;
                    }
                }
            }

            super.visitFieldInsn(opcode, owner, name, desc);
        }

        /**
         * Visits a method instruction. A method instruction is an instruction
         * that invokes a method.
         *
         * @param opcode The OPCODE of the instruction.
         * @param owner The name of the class container.
         * @param name The name of the field.
         * @param desc The description which represent the type of the field.
         * @param itf If the method's owner class is an interface.
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            visitMethodInsn(opcode, owner, name, desc, itf, true);
        }

        /**
         * A specific method of visitMethodInsn to implement the interception or
         * not.
         *
         * @param opcode The OPCODE of the instruction.
         * @param owner The name of the class container.
         * @param name The name of the field.
         * @param desc The description which represent the type of the field.
         * @param itf If the method's owner class is an interface.
         * @param intercept A flag to know if we implement the semantic.
         */
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf, boolean intercept) {

            if (intercept) {

                /* Detect lock method */
                if (opcode == Opcodes.INVOKEINTERFACE
                        && owner.equals("java/util/concurrent/locks/Lock")
                        && name.equals("lock")
                        && desc.equals("()V")) {
                    semantic.lock();
                    semantic.refresh();

                } else if (opcode == Opcodes.INVOKEINTERFACE
                        && owner.equals("java/util/concurrent/locks/Lock")
                        && name.equals("unlock")
                        && desc.equals("()V")) {

                    /* Unlock */
                    semantic.flush();
                    semantic.unlock();

                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    /**
     * Visits a method of the class. This method must return a new MethodVisitor
     * instance (or null) each time it is called, i.e., it should not return a
     * previously returned visitor.
     *
     * @param access The field's access flags (see Opcodes). This parameter also
     * indicates if the field is synthetic and/or deprecated.
     * @param name The field's access flags (see Opcodes). This parameter also
     * indicates if the field is synthetic and/or deprecated.
     * @param desc The field's descriptor (see Type).
     * @param signature The method's signature. May be null if the method
     * parameters, return type and exceptions do not use generic types.
     * @param exceptions The internal names of the method's exception classes
     * (see getInternalName). May be null.
     * @return An object to visit the byte code of the method, or null if this
     * class visitor is not interested in visiting the code of this method.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

        /* Call the modifier for each method */
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        ModifierMethodWriter mvw = new ModifierMethodWriter(api, mv, name, desc);
        return mvw;
    }

    /**
     * Visits a field of the class.
     *
     * @param access The field's access flags (see Opcodes). This parameter also
     * indicates if the field is synthetic and/or deprecated.
     * @param name The field's access flags (see Opcodes). This parameter also
     * indicates if the field is synthetic and/or deprecated.
     * @param desc The field's descriptor (see Type).
     * @param signature The field's signature. May be null if the field's type
     * does not use generic types.
     * @param value The field's initial value. This parameter, which may be null
     * if the field does not have an initial value, must be an Integer, a Float,
     * a Long, a Double or a String (for int, float, long or String fields
     * respectively). This parameter is only used for static fields. Its value
     * is ignored for non static fields, which must be initialized through
     * bytecode instructions in constructors or methods.
     * @return A visitor to visit field annotations and attributes, or null if
     * this class visitor is not interested in visiting these annotations and
     * attributes.
     */
    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        String options = Integer.toBinaryString(access);

        /*  Inject the jickaInstanceId Field */
        if (firstField) {

            /* public final ThreadLocal jickaInstanceId = null; */
            super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, JickaThreadLocal.FIELDNAME, "Ljava/lang/ThreadLocal;", null, null);
            firstField = !firstField;
        }

        /* Check if Final */
        boolean isFinal = false;
        if (options.length() >= 5) {
            if (options.charAt(options.length() - 5) == '1') {
                isFinal = true;
            }
        }

        /* Check if volatile */
        boolean isVolatile = false;
        if (options.length() >= 7) {
            if (options.charAt(options.length() - 7) == '1') {
                isVolatile = true;
            }
        }

        /* Check if Static */
        boolean isStatic = false;
        if (options.length() >= 4) {
            if (options.charAt(options.length() - 4) == '1') {
                isStatic = true;
            }
        }

        if (isStatic) {

            /* Calcul offsets */
            ASMFieldStatic.get().putOffset(className, name, desc);
            ASMMethodHandle.get().addMethodHandle(className, name, desc, isFinal, isVolatile);

        } else {

            /* Create fields for class data */
            ASMClass.get().addField(className, name, desc, null);
            ASMClass.get().addField(className, name + JickaThreadLocal.HEAPEXTENSION, desc, null);
            ASMClass.get().addField(className, name + JickaThreadLocal.BLOCKEXTENSION, "I", null);
            ASMClass.get().addField(className, name + JickaThreadLocal.VOLATILEEXTENSION, "Z", isVolatile);
        }

        /* Create the field in the current instance */
        return super.visitField(access, name, desc, signature, value);
    }
}
