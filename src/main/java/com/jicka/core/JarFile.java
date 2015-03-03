package com.jicka.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Mickael Boudignot
 */
class JarFile {

    /**
     * The path of the current jar.
     */
    private final String jarPath;

    /**
     * The path of the virtual jar (isn't created yet)
     */
    private Path path;

    /**
     * Create a new JarFile from a input jar path.
     *
     * @param jarPath The path of the jar.
     */
    JarFile(String jarPath) {
        this.jarPath = jarPath;
    }

    /**
     * Load a virtual path to compress it.
     * 
     * @param path The virtual path.
     */
    public void loadPath(Path path) {
        this.path = path;
    }

    /**
     * Extract content of the jar and list .class.
     *
     * @param outputPath Le path where the jar will be extract.
     * @return A list of path for each class
     * @throws IOException
     */
    public List<String> extractClassFile(Path outputPath) throws IOException {
        List<String> classFiles = new ArrayList<>();

        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath);
        java.util.Enumeration enumEntries = jar.entries();
        while (enumEntries.hasMoreElements()) {
            JarEntry file = (JarEntry) enumEntries.nextElement();
            File f = new File(outputPath + java.io.File.separator + file.getName());
            if (file.isDirectory()) {
                f.mkdir();
                continue;
            }
            try (InputStream is = jar.getInputStream(file); FileOutputStream fos = new FileOutputStream(f)) {
                while (is.available() > 0) {
                    fos.write(is.read());
                }
            }

            /* Add element to list */
            if (file.getName().contains(".class") && !file.getName().contains("$1.class")) {
                classFiles.add(Paths.get(outputPath.toString(), file.getName()).toString());
            }
        }
        return classFiles;
    }

    /**
     * Method which convert a path to a jar.
     *
     * @throws IOException
     * @throws Exception
     */
    public void compress() throws IOException, Exception {
        if (path == null) {
            throw new IllegalArgumentException("Path is nos defined");
        }
        zipFolder(path.toString(), jarPath);
    }

    /**
     * Convert a folder to a zip.
     *
     * @param srcFolder Input folder.
     * @param destZipFile The path of the zip.
     * @throws Exception
     */
    private static void zipFolder(String srcFolder, String destZipFile) throws Exception {
        ZipOutputStream zip;
        FileOutputStream fileWriter;

        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);

        File folder = new File(srcFolder);
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                addFolderToZip("", file.toString(), zip);
            } else {
                addFileToZip("", file.getAbsolutePath(), zip);
            }
        }
        zip.flush();
        zip.close();
    }

    /**
     * Method to add a file into a zip.
     *
     * @param path Le path to write the folder into the zip.
     * @param srcFolder Le path of the folder to include into the zip.
     * @param zip The zip file which will contain the folder.
     * @throws Exception
     */
    private static void addFileToZip(String path, String srcFile, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            FileInputStream in = new FileInputStream(srcFile);
            zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
            while ((len = in.read(buf)) > 0) {
                zip.write(buf, 0, len);
            }
        }
    }

    /**
     * Method to add a folder into a zip.
     *
     * @param path Le path to write the folder into the zip.
     * @param srcFolder Le path of the folder to include into the zip.
     * @param zip The zip file which will contain the folder.
     * @throws Exception
     */
    private static void addFolderToZip(String path, String srcFolder, ZipOutputStream zip)
            throws Exception {

        File folder = new File(srcFolder);

        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
            } else {
                addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
            }
        }
    }
}
