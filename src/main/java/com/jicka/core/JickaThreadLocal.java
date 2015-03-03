package com.jicka.core;

/**
 *
 * @author Mickael Boudignot
 * @param <T>
 */
public class JickaThreadLocal<T> extends ThreadLocal {

    /**
     * Field to change configuration about extension tag for class.
     */
    public static final String CLASSEXTENSION = "JickaData";

    /**
     * Field to change configuration about extension tag for heap field.
     */
    public static final String HEAPEXTENSION = "JickaHeap";

    /**
     * Field to change configuration about extension tag for block field.
     */
    public static final String BLOCKEXTENSION = "JickaBlock";

    /**
     * Field to change configuration about extension tag for volatile field.
     */
    public static final String VOLATILEEXTENSION = "JickaVolatile";

    /**
     * Field to change configuration about the name of the specific field that
     * will be generated in each class to access to local thread
     * (JickaThreadLocal).
     */
    public static final String FIELDNAME = "threadLocalData";

    /**
     *
     */
    private final Object object;

    /**
     * Create a ThreadLocal that will be initialize from the current object.
     *
     * @param object The current object.
     */
    public JickaThreadLocal(Object object) {
        this.object = object;
    }

    /**
     * Create a Reflect Object Data from an instance.
     *
     * @return a new Object Data
     */
    @Override
    @SuppressWarnings("unchecked")
    protected T initialValue() {
        try {
            T data = (T) Class.forName(object.getClass().getCanonicalName() + CLASSEXTENSION).newInstance();
            LocalHeap.refreshFieldAll(object, data);
            return data;
        } catch (NoSuchFieldException | IllegalArgumentException | ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            System.out.println(ex);
            return null;
        }
    }
}
