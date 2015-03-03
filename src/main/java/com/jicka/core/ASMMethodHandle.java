package com.jicka.core;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mickael Boudignot
 */
class ASMMethodHandle {

    /**
     * Field to store the only instance of this class (singleton).
     */
    private final static ASMMethodHandle instance = new ASMMethodHandle();

    /**
     * Method to return the only instance of this class.
     *
     * @return the instance of ASMClass.
     */
    static ASMMethodHandle get() {
        return instance;
    }

    /**
     * Class to store information about a static field.
     */
    static class Information {

        /**
         * The name of the class container.
         */
        public final String owner;

        /**
         * The description which represent the type of the field.
         */
        public final String name;

        /**
         * The description which represent the type of the field.
         */
        public final String desc;

        /**
         * A flag to know if it's a final field.
         */
        public final boolean isFinal;

        /**
         * A flag to know if it's a volatile field.
         */
        public final boolean isVolatile;

        /**
         * Construct a new static field.
         *
         * @param owner The name of the class container.
         * @param name The name of the field.
         * @param desc The description which represent the type of the field.
         * @param isFinal A flag to know if it's a final field.
         * @param isVolatile A flag to know if it's a volatile field.
         */
        private Information(String owner, String name, String desc, boolean isFinal, boolean isVolatile) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.isFinal = isFinal;
            this.isVolatile = isVolatile;
        }
    }

    /**
     * Structure to store all static field. There will be use when we want to
     * create each method handle to get and set a static field in target
     * program.
     */
    private final List<Information> methodHandle = new ArrayList<>();

    /**
     * This class is private and we don't want the generate another instance of
     * this.
     */
    private ASMMethodHandle() {
    }

    /**
     * Method which add a static field in storage.
     *
     * @param owner The name of the class container.
     * @param name The description which represent the type of the field.
     * @param desc The description which represent the type of the field.
     * @param isFinal A flag to know if it's a final field.
     * @param isVolatile A flag to know if it's a volatile field.
     */
    public void addMethodHandle(String owner, String name, String desc, boolean isFinal, boolean isVolatile) {
        methodHandle.add(new Information(owner, name, desc, isFinal, isVolatile));
    }

    /**
     * Method to empty the storage. This method is called when we write all
     * method handles.
     */
    public void clearMethodHandle() {
        methodHandle.clear();
    }

    /**
     * Method which return a list of all static fields.
     *
     * @return a list which will be used to generate all method handles
     */
    public List<Information> getMethodHandle() {
        return methodHandle;
    }
}
