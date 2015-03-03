package com.jicka.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Mickael Boudignot
 */
class ASMClass {

    /**
     * Field to store the only instance of this class (singleton).
     */
    private final static ASMClass instance = new ASMClass();

    /**
     * Method to return the only instance of this class.
     *
     * @return the instance of ASMClass.
     */
    static ASMClass get() {
        return instance;
    }

    /**
     * Class to store information about a field.
     */
    static class Information {

        /**
         * The name of the field.
         */
        final String name;

        /**
         * The description which represent the type of the field.
         */
        final String desc;

        /**
         * The default value of the field.
         */
        final Object value;

        /**
         * Construct a new field from an instance.
         *
         * @param name of the field.
         * @param desc Description which represent the type of the field.
         * @param value The default value of the field.
         */
        private Information(String name, String desc, Object value) {
            this.name = name;
            this.desc = desc;
            this.value = value;
        }
    }

    /**
     * Number of instance field in the target program. This id increases when a
     * new instance field is discovered (when the method addClass is called).
     */
    private int id = 0;

    /**
     * Stucture to store fields. It associates a class and all its fields.
     */
    private final HashMap<String, List<Information>> objects = new HashMap<>();

    /**
     * This class is private and we don't want the generate another instance of
     * this.
     */
    private ASMClass() {
    }

    /**
     * Called by the classVisitor when a new Class is discovered. Store this
     * class is the storage "objects" with an empty list of fields for this
     * moment.
     *
     * @param name
     */
    public void addClass(String name) {
        objects.put(name + JickaThreadLocal.CLASSEXTENSION, new ArrayList<>());
        id++;
    }

    /**
     * When a field is discovered by the classVisitor, we associate this field
     * and its information at the class container.
     *
     * @param owner The name of the class container.
     * @param name The name of the field.
     * @param desc Description which represent the type of the field.
     * @param value The default value of the field.
     */
    public void addField(String owner, String name, String desc, Object value) {
        objects.get(owner + JickaThreadLocal.CLASSEXTENSION).add(new Information(name, desc, value));
    }

    /**
     * Return a list of all classes stored during inspection.
     *
     * @return a set to avoid duplicate.
     */
    public Set<String> getClasses() {
        return objects.keySet();
    }

    /**
     * Return a list of all fields in a class.
     *
     * @param owner The name of the class where we want the fields.
     * @return a list of fields
     */
    public List<Information> getFields(String owner) {
        return objects.get(owner);
    }

    /**
     * Return the max number of fields in all target program.
     *
     * @return an interger.
     */
    public int getMaxId() {
        return id;
    }
}
