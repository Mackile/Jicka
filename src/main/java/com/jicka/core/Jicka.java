package com.jicka.core;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;

/**
 *
 * @author Mickael Boudignot
 */
public class Jicka {
    
    private static String[] excludes = new String[0];

    /**
     * Method which transform a class to the same class with the semantic.
     *
     * @param classBuffer The original byte array which will be converted by
     * jicka to apply semantic.
     * @return a byte array converted.
     */
    public static byte[] transform(byte[] classBuffer) {

        /* Load the class from byteBuffer */
        byte[] classBufferReturn = classBuffer;
        ClassReader cr = new ClassReader(classBufferReturn);
        String className = cr.getClassName();

        /* Check if we have to apply the semantic */
        if (inspect(className)
                && !className.startsWith("java/")
                && !className.startsWith("javax/")
                && !className.startsWith("sun/")
                && !className.startsWith("com/sun/")
                && !className.startsWith("com/jicka/")) {

            /* Call ASM to inpect and modify the code */
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new JickaVisitor(ASM5, cw, className);
            cr.accept(cv, 0);
            return cw.toByteArray();
        }

        /* Return the default byteBuffer */
        return classBufferReturn;
    }

    /**
     * Method which transform a jar to another jar with the semantic.
     *
     * @param input The input jar file.
     * @param output The output path which represent where the ouput jar will be
     * written.
     * @param classLoader A class loader is needed to load dynamically classes
     * like LocalHeap and JickaThreadLocal.
     * @throws Exception The transformation can return Exception during E/O of
     * files.
     */
    public static void transform(String input, String output, ClassLoader classLoader) throws Exception {

        /* Create temp Firectory to extarct, modify and recreate a JAR */
        Path tmpPath = Files.createTempDirectory("jicka-tmp-");

        /* Load data */
        JarFile inputJar = new JarFile(input);
        List<String> classFiles = inputJar.extractClassFile(tmpPath);

        /* For each element, apply modifications */
        for (String classFile : classFiles) {

            /* Open class */
            byte[] classBuffer = Files.readAllBytes(Paths.get(classFile));

            /* Write output class */
            try (DataOutputStream dout = new DataOutputStream(new FileOutputStream(new File(classFile)))) {
                dout.write(Jicka.transform(classBuffer));
                dout.flush();
            }
        }

        /* Create all class Data */
        MethodVisitor mv;
        for (String object : ASMClass.get().getClasses()) {

            /* Create class */
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(52, ACC_PUBLIC + ACC_SUPER, object, null, "java/lang/Object", null);

            ASMClass.get().getFields(object).stream().forEach((information) -> {
                cw.visitField(ACC_PUBLIC, information.name, information.desc, information.desc, information.value);
            });

            /* Create public constructor */
            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

                /* Init all field */
                for (ASMClass.Information information : ASMClass.get().getFields(object)) {
                    if (information.value != null) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitLdcInsn(information.value);
                        mv.visitFieldInsn(PUTFIELD, object, information.name, information.desc);
                    }
                }

                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }

            /* Close class */
            cw.visitEnd();

            /* Write the class */
            try (DataOutputStream dout = new DataOutputStream(new FileOutputStream(Paths.get(tmpPath.toString(), object + ".class").toFile()))) {
                dout.write(cw.toByteArray());
                dout.flush();
            }
        }


        /* Creates folders to store LocalHeap */
        if (!Files.exists(Paths.get(tmpPath.toString(), "com"))) {
            Files.createDirectory(Paths.get(tmpPath.toString(), "com"));
        }
        if (!Files.exists(Paths.get(tmpPath.toString(), "com", "jicka"))) {
            Files.createDirectory(Paths.get(tmpPath.toString(), "com", "jicka"));
        }
        if (!Files.exists(Paths.get(tmpPath.toString(), "com", "jicka", "core"))) {
            Files.createDirectory(Paths.get(tmpPath.toString(), "com", "jicka", "core"));
        }

        /* Create Configuration file */
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(52, ACC_PUBLIC + ACC_SUPER, "com/jicka/core/Configuration", null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "STATICMAXSIZE", "I", "I", ASMFieldStatic.get().getMaxId());
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "FIELDMAXSIZE", "I", "I", ASMClass.get().getMaxId());
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        try (DataOutputStream dout = new DataOutputStream(new FileOutputStream(Paths.get(tmpPath.toString(), "com/jicka/core/Configuration.class").toFile()))) {
            dout.write(cw.toByteArray());
            dout.flush();
        }

        /* Copy LocalHeap */
        copyClass("/com/jicka/core/LocalHeap.class", LocalHeap.class, Paths.get(tmpPath.toString(), "com", "jicka", "core", "LocalHeap.class").toFile());

        /* Copy JickaThreadLocal */
        copyClass("/com/jicka/core/JickaThreadLocal.class", JickaThreadLocal.class, Paths.get(tmpPath.toString(), "com", "jicka", "core", "JickaThreadLocal.class").toFile());

        /* Pack modification into output jar */
        JarFile outputJar = new JarFile(output);
        outputJar.loadPath(tmpPath);
        outputJar.compress();

        /* Remove tmp folder */
        deleteFolder(tmpPath.toFile());
    }

    /**
     * Method which delete a folder recursively.
     *
     * @param folder which will be delete.
     */
    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    /**
     * Method to load an class an write it as a class file.
     *
     * @param clz The class to load.
     * @param loader The class loader to load the class.
     * @param file The file to write the class file.
     * @throws Exception An exception can be genered by E/O and not found class.
     */
    private static void copyClass(String clz, Class loader, File file) throws Exception {
        byte[] buffer;
        try (InputStream localHeapStream = loader.getResourceAsStream(clz);
                FileOutputStream outStream2 = new FileOutputStream(file)) {
            buffer = new byte[1024];
            int byteread;
            while ((byteread = localHeapStream.read(buffer)) != -1) {
                outStream2.write(buffer, 0, byteread);
            }
        }
    }
    
    /**
     * Method to add a list of excuded packages.
     *
     * @param excludes An array contain the list of excuded packages.
     */
    public static void defineExclude(String[] excludes) {
        for(int i =0; i<excludes.length; i++) {
            excludes[i] = excludes[i].replace(".", "/");
        }
        
        Jicka.excludes = excludes;
    }
    
    /**
     * Method to check if we have to inpect or not this class.
     *
     * @param owner The name of the class which contain the field.
     */
    static boolean inspect(String owner) {
        for (String exclude : excludes) {
            if (owner.contains(exclude)) {
                return false;
            }
        }
        return true;
    }
}
