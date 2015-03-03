package com.jicka.core;

import java.util.HashMap;

/**
 *
 * @author Mickael Boudignot
 */
class ASMFieldStatic {

    /**
     * Field to store the only instance of this class (singleton).
     */
    private final static ASMFieldStatic instance = new ASMFieldStatic();

    /**
     * Method to return the only instance of this class.
     *
     * @return the instance of ASMFieldStatic.
     */
    static ASMFieldStatic get() {
        return instance;
    }

    /**
     * Number of static field in the target program. This id increases when a
     * new instance field is discovered (when the method addClass is called).
     */
    private int id = 0;

    /**
     * Stucture to store static fields. It associates a class and all its
     * fields.
     */
    private final HashMap<String, Integer> offsets = new HashMap<>();

    /**
     * This class is private and we don't want the generate another instance of
     * this.
     */
    private ASMFieldStatic() {
    }

    /**
     * When a static field is discovered by the classVisitor, we associate this
     * field and its information at the class container.
     *
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc Description which represent the type of the field.
     * @param value The default value of the field.
     */
    public void putOffset(String owner, String name, String desc) {
        String hashString = getHashString(owner, name, desc);
        Integer offset = offsets.get(hashString);
        if (offset == null) {
            offsets.put(hashString, id);
            id++;
        }
    }

    /**
     * Method to get the offset of a specific field. This offset represent an id
     * to store the data in localHeap on the target program.
     *
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc Description which represent the type of the field.
     * @return an integer who represent an unique id for this field.
     */
    public int getOffset(String owner, String name, String desc) {
        String hashString = getHashString(owner, name, desc);
        if (!offsets.containsKey(hashString)) {
            putOffset(owner, name, desc);
        }
        return offsets.get(getHashString(owner, name, desc));
    }

    /**
     * Return the max number of fields in all target program.
     *
     * @return an interger.
     */
    public int getMaxId() {
        return id;
    }

    /**
     * Method to generate an unique string like an hashCode for each static
     * field.
     *
     * @param owner The name of the class which contains the static field
     * @param name The name of the field.
     * @param desc Description which represent the type of the field.
     * @return an unique string which represent the field.
     */
    private String getHashString(String owner, String name, String desc) {
        return String.format("%s.%s.%s", owner, name, desc);
    }
}
