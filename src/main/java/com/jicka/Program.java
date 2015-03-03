package com.jicka;

import com.jicka.core.Jicka;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 *
 * @author Mickael Boudignot
 */
class Program {

    /**
     * Main class of the project. This method is called when the program start.
     * It takes two arguments, the first is the input of the jar which will be
     * convert and the second the output jar (the converted jar).
     *
     * @param args are the arguments of the program
     * @throws Exception The transformation can return Exception during E/O of
     * files.
     */
    public static void main(String[] args) throws Exception {

        /* Get parameter */
        if (args.length < 2) {
            System.err.println("Usage :\njava - jar Jicka.jar [inputJar] [outputJar] [optionnal exclude packages]");
            System.exit(1);
        }

        if (!Files.exists(Paths.get(args[0]))) {
            System.err.println(String.format("The file '%s' doesn't exist.", args[0]));
            System.exit(1);
        }

        /* Transform */
        Jicka.defineExclude(Arrays.copyOfRange(args, 2, args.length));
        Jicka.transform(args[0], args[1], Program.class.getClassLoader());
    }
}
