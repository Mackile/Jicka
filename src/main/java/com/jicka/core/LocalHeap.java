package com.jicka.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;

/**
 *
 * @author Mickael Boudignot
 */
public class LocalHeap {

    /**
     * Enumeration to define all types by using int values.
     */
    public static final int OBJECT = 1;
    public static final int INTEGER = 2;
    public static final int LONG = 3;
    public static final int DOUBLE = 4;
    public static final int BOOLEAN = 5;
    public static final int FLOAT = 6;
    public static final int BYTE = 7;
    public static final int SHORT = 8;
    public static final int CHAR = 9;

    /**
     * Array to store the type of all static fields.
     */
    private static int[] staticType = new int[Configuration.STATICMAXSIZE];

    /**
     * Array to store the flag volatile of all static fields.
     */
    private static boolean[] staticVolatile = new boolean[Configuration.STATICMAXSIZE];

    /**
     * Array to store the setters of all static fields.
     */
    private static MethodHandle[] staticSetterMethodHandle = new MethodHandle[Configuration.STATICMAXSIZE];

    /**
     * Array to store the getters of all static fields.
     */
    private static MethodHandle[] staticGetterMethodHandle = new MethodHandle[Configuration.STATICMAXSIZE];

    /**
     * A local lock to push data in common stack from all Threads.
     */
    private static final Object lockLocalHeap = new Object();

    /**
     * List of all Threads started.
     */
    private static final LinkedList<LocalHeap> threads = new LinkedList<>();

    /**
     * Structure to store a LocalHeap for each Thread.
     */
    private static final ThreadLocal<LocalHeap> localStorage = new ThreadLocal<>();

    /**
     * Method called when a Thread is started. This method create a localHeap
     * and store it.
     *
     * @throws Throwable
     */
    public static void createLocalHeap() throws Throwable {
        LocalHeap lh = new LocalHeap();
        lh.refreshStaticAll();
        localStorage.set(lh);

        /* Add thread to the list */
        synchronized (lockLocalHeap) {
            threads.add(lh);
        }
    }

    /**
     * Restore the local heap from a Thread.
     *
     * @return The localHeap associate from the Thread who call this method.
     */
    private static LocalHeap getLocalHeap() {
        return localStorage.get();
    }

    /**
     * Method called to detroy le localHeap of a Thread.
     */
    public static void removeLocalHeap() {
        localStorage.remove();
    }

    /**
     * Stack of write (flush).
     */
    private int staticRefreshTop = 0;
    private int fieldRefreshTop = 0;
    private int[] staticRefreshOffsets = new int[Configuration.STATICMAXSIZE];
    private WeakReference[] fieldRefreshOffsets = new WeakReference[Configuration.FIELDMAXSIZE];

    /**
     * Stack of write (flush).
     */
    private int staticFlushTop = 0;
    private int fieldFlushTop = 0;
    private int[] staticFlushOffsets = new int[Configuration.STATICMAXSIZE];
    private WeakReference[] fieldFlushOffsets = new WeakReference[Configuration.FIELDMAXSIZE];

    /**
     * Array to store all local primitive static fields.
     */
    private final long[] staticValuesPrimitiveLocal = new long[Configuration.STATICMAXSIZE];

    /**
     * Array to store all heap primitive static fields.
     */
    private final long[] staticValuesPrimitiveHeap = new long[Configuration.STATICMAXSIZE];

    /**
     * Array to store all local objects static fields.
     */
    private final Object[] staticValuesObjectLocal = new Object[Configuration.STATICMAXSIZE];

    /**
     * Array to store all heap objects static fields.
     */
    private final Object[] staticValuesObjectHeap = new Object[Configuration.STATICMAXSIZE];

    /**
     * Field to indentidy the current block of the Thread.
     */
    private int block = 0;

    /**
     * Array to store all affected block for each static field.
     */
    private final int[] staticBlock = new int[Configuration.STATICMAXSIZE];

    /**
     * Return a int type from a string type.
     *
     * @param type The string type.
     * @return An in based on local enumeration for each type.
     */
    private static int getType(String type) {
        switch (type) {
            case "J":
                return LONG;
            case "D":
                return DOUBLE;
            case "Z":
                return BOOLEAN;
            case "F":
                return FLOAT;
            case "I":
                return INTEGER;
            case "B":
                return BYTE;
            case "S":
                return SHORT;
            case "C":
                return CHAR;
            default:
                return OBJECT;
        }
    }

    /**
     * Method called to create a setter/getter of a static field.
     *
     * @param lookup Factory to create method handles.
     * @param offset The offset of the static field in array storage.
     * @param classe The owner of the field.
     * @param name The name of the field.
     * @param type The type of the field.
     * @param isFinal A flag to identify if the flag is final or not.
     * @param isVolatile A flag to identify if the flag is volatile or not.
     * @throws Throwable
     */
    public static void createMethodHandles(MethodHandles.Lookup lookup, int offset, String classe, String name, String type, boolean isFinal, boolean isVolatile) throws Throwable {

        /* If not enought space (only with agent) */
        if (offset >= staticVolatile.length) {
            Configuration.STATICMAXSIZE = offset + 1;
            staticVolatile = Arrays.copyOf(staticVolatile, Configuration.STATICMAXSIZE);
            staticType = Arrays.copyOf(staticType, Configuration.STATICMAXSIZE);
            staticGetterMethodHandle = Arrays.copyOf(staticGetterMethodHandle, Configuration.STATICMAXSIZE);
            staticSetterMethodHandle = Arrays.copyOf(staticSetterMethodHandle, Configuration.STATICMAXSIZE);
        }

        /* Store the volatile tag */
        staticVolatile[offset] = isVolatile;

        /* Get the type of the element */
        int localType = getType(type);
        staticType[offset] = localType;

        /* Get the classe of the element */
        Class classeType;
        if (localType == OBJECT) {
            classeType = Class.forName(type.replace("/", ".").substring(1, type.length() - 1));
        } else {
            classeType = Class.forName("[" + type).getComponentType();
        }

        /* Create MethodHandles */
        staticGetterMethodHandle[offset] = lookup.findStaticGetter(Class.forName(classe.replace("/", ".")), name, classeType);
        if (!isFinal) {
            staticSetterMethodHandle[offset] = lookup.findStaticSetter(Class.forName(classe.replace("/", ".")), name, classeType);
        }
    }

    /**
     * Store an offset on the top of the stack refresh static field.
     *
     * @param offset The offset of the static field in array storage.
     */
    private void putStaticRefreshOffsets(int offset) {
        if (staticRefreshOffsets.length == staticRefreshTop) {
            staticRefreshOffsets = Arrays.copyOf(staticRefreshOffsets, staticRefreshOffsets.length * 2);
        }
        staticRefreshOffsets[staticRefreshTop] = offset;
        staticRefreshTop++;
    }

    /**
     * Store an offset on the top of the stack flush static field.
     *
     * @param offset The offset of the static field in array storage.
     */
    private void putStaticFlushOffsets(int offset) {
        if (staticFlushOffsets.length == staticFlushTop) {
            staticFlushOffsets = Arrays.copyOf(staticFlushOffsets, staticFlushOffsets.length * 2);
        }
        staticFlushOffsets[staticFlushTop] = offset;
        staticFlushTop++;
    }

    /**
     * Return the top of the stack into the static resfresh array.
     *
     * @return an offset represent the field that have to be updated from the
     * heap.
     */
    private int popStaticRefreshOffsets() {
        if (staticRefreshTop == 0) {
            return -1;
        }
        staticRefreshTop--;
        return staticRefreshOffsets[staticRefreshTop];
    }

    /**
     * Return the top of the stack into the static flush array.
     *
     * @return an offset represent the field that have to be written in the
     * heap.
     */
    private int popStaticFlushOffsets() {
        if (staticFlushTop == 0) {
            return -1;
        }
        staticFlushTop--;
        return staticFlushOffsets[staticFlushTop];
    }

    /**
     * Store an instance on the top of the stack refresh instance field.
     *
     * @param instance which have to be updated.
     */
    private void putFieldRefreshOffsets(Object instance) {
        if (fieldRefreshOffsets.length == fieldRefreshTop) {
            fieldRefreshOffsets = Arrays.copyOf(fieldRefreshOffsets, fieldRefreshOffsets.length * 2);
        }
        fieldRefreshOffsets[fieldRefreshTop] = new WeakReference<>(instance);
        fieldRefreshTop++;
    }

    /**
     * Store an offset on the top of the stack flush instance field.
     *
     * @param instance which is modified.
     */
    private void putFieldFlushOffsets(Object instance) {
        if (fieldFlushOffsets.length == fieldFlushTop) {
            fieldFlushOffsets = Arrays.copyOf(fieldFlushOffsets, fieldFlushOffsets.length * 2);
        }
        fieldFlushOffsets[fieldFlushTop] = new WeakReference<>(instance);
        fieldFlushTop++;
    }

    /**
     * Return the top of the stack into the field refresh array.
     *
     * @return A weakReference on the instance.
     */
    private WeakReference popFieldRefreshOffsets() {
        if (fieldRefreshTop == 0) {
            return null;
        }
        fieldRefreshTop--;
        return fieldRefreshOffsets[fieldRefreshTop];
    }

    /**
     * Return the top of the stack into the field flush array.
     *
     * @return A weakReference on the instance.
     */
    private WeakReference popFieldFlushOffsets() {
        if (fieldFlushTop == 0) {
            return null;
        }
        fieldFlushTop--;
        return fieldFlushOffsets[fieldFlushTop];
    }

    /**
     * Refresh all data from the Heap into localHeap.
     *
     * @throws Throwable
     */
    public static void refresh() throws Throwable {
        synchronized (lockLocalHeap) {
            refreshStatic();
            refreshField();
        }
    }

    /**
     * Refresh a specific static field from the Heap into localHeap.
     *
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    private void resfreshStatic(int offset) throws Throwable {

        if (staticGetterMethodHandle[offset] == null) {
            return;
        }

        switch (staticType[offset]) {

            case OBJECT:
                Object heapObject = staticGetterMethodHandle[offset].invoke();
                staticValuesObjectLocal[offset] = heapObject;
                staticValuesObjectHeap[offset] = heapObject;
                break;

            case BYTE:
                byte heapByte = (byte) staticGetterMethodHandle[offset].invokeExact();
                staticValuesPrimitiveLocal[offset] = heapByte;
                staticValuesPrimitiveHeap[offset] = heapByte;
                break;

            case SHORT:
                short heapShort = (short) staticGetterMethodHandle[offset].invokeExact();
                staticValuesPrimitiveLocal[offset] = heapShort;
                staticValuesPrimitiveHeap[offset] = heapShort;
                break;

            case BOOLEAN:
                boolean heapBoolean = (boolean) staticGetterMethodHandle[offset].invokeExact();
                long valueBoolean = (heapBoolean) ? 1 : 0;
                staticValuesPrimitiveLocal[offset] = valueBoolean;
                staticValuesPrimitiveHeap[offset] = valueBoolean;
                break;

            case INTEGER:
                int heapInteger = (int) staticGetterMethodHandle[offset].invokeExact();
                staticValuesPrimitiveLocal[offset] = heapInteger;
                staticValuesPrimitiveHeap[offset] = heapInteger;
                break;

            case FLOAT:
                float heapFloat = (float) staticGetterMethodHandle[offset].invokeExact();
                staticValuesPrimitiveLocal[offset] = (long) Float.floatToIntBits(heapFloat);
                staticValuesPrimitiveHeap[offset] = (long) Float.floatToIntBits(heapFloat);
                break;

            case LONG:
                long heapLong = (long) staticGetterMethodHandle[offset].invokeExact();
                staticValuesPrimitiveLocal[offset] = heapLong;
                staticValuesPrimitiveHeap[offset] = heapLong;
                break;

            case DOUBLE:
                double heapDouble = (double) staticGetterMethodHandle[offset].invokeExact();
                long valueDouble = Double.doubleToLongBits(heapDouble);
                staticValuesPrimitiveLocal[offset] = valueDouble;
                staticValuesPrimitiveHeap[offset] = valueDouble;
                break;

            case CHAR:
                char heapChar = (char) staticGetterMethodHandle[offset].invokeExact();
                staticValuesPrimitiveLocal[offset] = heapChar;
                staticValuesPrimitiveHeap[offset] = heapChar;
                break;
        }
    }

    /**
     * Refresh only modified static field from the Heap into localHeap.
     *
     * @throws Throwable
     */
    private static void refreshStatic() throws Throwable {
        LocalHeap storage = getLocalHeap();
        int offset;
        while ((offset = storage.popStaticRefreshOffsets()) != -1) {
            storage.resfreshStatic(offset);
        }
    }

    /**
     * Refresh all static field from the Heap into localHeap.
     *
     * @throws Throwable
     */
    private void refreshStaticAll() throws Throwable {

        /* Refresh Static Fields */
        for (int offset = 0; offset < staticGetterMethodHandle.length; offset++) {
            if (staticGetterMethodHandle[offset] != null) {
                resfreshStatic(offset);
            }
        }
    }

    /**
     * Refresh all instance field from the Heap into localHeap.
     *
     * @throws ReflectiveOperationException
     */
    private static void refreshField() throws ReflectiveOperationException {
        LocalHeap storage = getLocalHeap();
        WeakReference reference;
        Object instance;

        while ((reference = storage.popFieldRefreshOffsets()) != null) {
            instance = reference.get();
            if (instance == null) {
                continue;
            }

            /* Get Data */
            Field field = getField(instance, JickaThreadLocal.FIELDNAME);
            JickaThreadLocal data = (JickaThreadLocal) field.get(instance);
            refreshFieldAll(instance, data.get());
        }
    }

    /**
     * Copy all fields from an Instance to the reflect Instance Data Storage
     *
     * @param instance The original object which represent the Heap data.
     * @param data The reflect object data which is a copy of the instance.
     * @throws NoSuchFieldException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    public static void refreshFieldAll(Object instance, Object data) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {

        /* Load classes */
        Class<?> clsInstance = instance.getClass();
        Class<?> clsData = data.getClass();

        /* Update all field of the data storage */
        Field[] fields = clsData.getFields();
        for (Field field : fields) {

            /* Check if the field isn't a metadata */
            if (field.getName().equals(JickaThreadLocal.FIELDNAME)
                    || field.getName().endsWith(JickaThreadLocal.BLOCKEXTENSION)
                    || field.getName().endsWith(JickaThreadLocal.HEAPEXTENSION)
                    || field.getName().endsWith(JickaThreadLocal.VOLATILEEXTENSION)) {
                continue;
            }

            /* Get information from instance */
            Field information = clsInstance.getDeclaredField(field.getName());
            information.setAccessible(true);
            switch (field.getGenericType().toString()) {

                case "byte":
                    field.setByte(data, information.getByte(instance));
                    break;

                case "short":
                    field.setShort(data, information.getShort(instance));
                    break;

                case "char":
                    field.setChar(data, information.getChar(instance));
                    break;

                case "boolean":
                    field.setBoolean(data, information.getBoolean(instance));
                    break;

                case "int":
                    field.setInt(data, information.getInt(instance));
                    break;

                case "float":
                    field.setFloat(data, information.getFloat(instance));
                    break;

                case "long":
                    field.setLong(data, information.getLong(instance));
                    break;

                case "double":
                    field.setDouble(data, information.getDouble(instance));
                    break;

                default:
                    field.set(data, information.get(instance));
                    break;
            }
        }
    }

    /**
     * Method called to flush all local data in the current Thread into the
     * Heap.
     *
     * @throws Throwable
     */
    public static void flush() throws Throwable {
        synchronized (lockLocalHeap) {
            flushStatic();
            flushField();
        }
    }

    /**
     * Method called to flush all local static field into the Heap.
     *
     * @throws Throwable
     */
    private static void flushStatic() throws Throwable {
        LocalHeap storage = getLocalHeap();
        int offset;
        while ((offset = storage.popStaticFlushOffsets()) != -1) {

            /* Update local heap */
            storage.staticValuesObjectHeap[offset] = storage.staticValuesObjectLocal[offset];

            /* Push the data on the real heap */
            switch (staticType[offset]) {

                case OBJECT:
                    staticSetterMethodHandle[offset].invoke(storage.staticValuesObjectLocal[offset]);
                    break;

                case BYTE:
                    byte valueByte = (byte) storage.staticValuesPrimitiveLocal[offset];
                    staticSetterMethodHandle[offset].invoke(valueByte);
                    break;

                case SHORT:
                    short valueShort = (short) storage.staticValuesPrimitiveLocal[offset];
                    staticSetterMethodHandle[offset].invoke(valueShort);
                    break;

                case BOOLEAN:
                    boolean valueBoolean = (storage.staticValuesPrimitiveLocal[offset] == 1);
                    staticSetterMethodHandle[offset].invoke(valueBoolean);
                    break;

                case INTEGER:
                    staticSetterMethodHandle[offset].invoke((int) storage.staticValuesPrimitiveLocal[offset]);
                    break;

                case FLOAT:
                    float valueFloat = Float.intBitsToFloat((int) storage.staticValuesPrimitiveLocal[offset]);
                    staticSetterMethodHandle[offset].invoke(valueFloat);
                    break;

                case LONG:
                    long valueLong = storage.staticValuesPrimitiveLocal[offset];
                    staticSetterMethodHandle[offset].invoke(valueLong);
                    break;

                case DOUBLE:
                    long valueDouble = storage.staticValuesPrimitiveLocal[offset];
                    staticSetterMethodHandle[offset].invoke(Double.longBitsToDouble(valueDouble));
                    break;

                case CHAR:
                    char valueChar = (char) storage.staticValuesPrimitiveLocal[offset];
                    staticSetterMethodHandle[offset].invoke(valueChar);
                    break;
            }

            /* Note to all other thread the modification */
            for (LocalHeap lh : threads) {
                if (!storage.equals(lh)) {
                    lh.putStaticRefreshOffsets(offset);
                }
            }
        }
    }

    /**
     * Method called to flush all local instance fied into the Heap.
     *
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    private static void flushField() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        LocalHeap storage = getLocalHeap();
        WeakReference reference;
        Object instance, data;
        while ((reference = storage.popFieldFlushOffsets()) != null) {
            instance = reference.get();
            data = storage.popFieldFlushOffsets().get();

            /* Check if the garbage detroy instance */
            if (instance == null || data == null) {
                continue;
            }

            /* Update all fields */
            Class<?> clsInstance = instance.getClass();
            Class<?> clsData = data.getClass();
            Field[] fields = clsData.getFields();
            for (Field field : fields) {

                /* Check if the field isn't a metadata */
                if (field.getName().endsWith(JickaThreadLocal.BLOCKEXTENSION)
                        || field.getName().endsWith(JickaThreadLocal.HEAPEXTENSION)
                        || field.getName().endsWith(JickaThreadLocal.VOLATILEEXTENSION)) {
                    continue;
                }

                /* Update local heap and heap object */
                Field updateInstance = clsInstance.getDeclaredField(field.getName());
                updateInstance.setAccessible(true);
                Field updateData = clsData.getField(field.getName() + JickaThreadLocal.HEAPEXTENSION);

                /* Update from the good type */
                switch (field.getGenericType().toString()) {

                    case "byte":
                        updateData.setByte(data, field.getByte(data));
                        updateInstance.setByte(instance, field.getByte(data));
                        break;

                    case "short":
                        updateData.setShort(data, field.getShort(data));
                        updateInstance.setShort(instance, field.getShort(data));
                        break;

                    case "boolean":
                        updateData.setBoolean(data, field.getBoolean(data));
                        updateInstance.setBoolean(instance, field.getBoolean(data));
                        break;

                    case "int":
                        updateData.setInt(data, field.getInt(data));
                        updateInstance.setInt(instance, field.getInt(data));
                        break;

                    case "float":
                        updateData.setFloat(data, field.getFloat(data));
                        updateInstance.setFloat(instance, field.getFloat(data));
                        break;

                    case "long":
                        updateData.setLong(data, field.getLong(data));
                        updateInstance.setLong(instance, field.getLong(data));
                        break;

                    case "double":
                        updateData.setDouble(data, field.getDouble(data));
                        updateInstance.setDouble(instance, field.getDouble(data));
                        break;

                    case "char":
                        updateData.setChar(data, field.getChar(data));
                        updateInstance.setChar(instance, field.getChar(data));
                        break;

                    default:
                        updateData.set(data, field.get(data));
                        updateInstance.set(instance, field.get(data));
                        break;
                }
            }

            /* Note to all other thread the modification of the instance*/
            for (LocalHeap lh : threads) {
                if (!storage.equals(lh)) {
                    lh.putFieldRefreshOffsets(instance);
                }
            }
        }
    }

    /**
     *
     */
    public static void increaseBlock() {
        LocalHeap storage = getLocalHeap();
        storage.putStaticFlushOffsets(-1);
        storage.block++;
    }

    /**
     *
     */
    public static void decreaseBlock() {
        LocalHeap storage = getLocalHeap();
        storage.block--;
    }

    /**
     *
     * @param object
     * @param offset
     * @throws Throwable
     */
    public static void setStaticObject(Object object, int offset) throws Throwable {
        LocalHeap storage = getLocalHeap();

        if (object != storage.staticValuesObjectHeap[offset]
                && (storage.staticValuesObjectLocal[offset] == storage.staticValuesObjectHeap[offset]
                || storage.staticBlock[offset] != storage.block)) {
            storage.putStaticFlushOffsets(offset);
        }

        storage.staticValuesObjectLocal[offset] = object;
        storage.staticBlock[offset] = storage.block;

        /* If volatile, write all data */
        if (staticVolatile[offset]) {
            flush();
        }
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticByte(byte object, int offset) throws Throwable {
        setStaticLong(object, offset);
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticShort(short object, int offset) throws Throwable {
        setStaticLong(object, offset);
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticBoolean(boolean object, int offset) throws Throwable {
        int value = (object) ? 1 : 0;
        setStaticLong(value, offset);
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticInteger(int object, int offset) throws Throwable {
        setStaticLong(object, offset);
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticFloat(float object, int offset) throws Throwable {
        setStaticLong(Float.floatToRawIntBits(object), offset);
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticLong(long object, int offset) throws Throwable {
        LocalHeap storage = getLocalHeap();

        if (object != storage.staticValuesPrimitiveHeap[offset]
                && (storage.staticValuesPrimitiveLocal[offset] == storage.staticValuesPrimitiveHeap[offset]
                || storage.staticBlock[offset] != storage.block)) {
            storage.putStaticFlushOffsets(offset);
        }

        storage.staticValuesPrimitiveLocal[offset] = object;
        storage.staticBlock[offset] = storage.block;

        /* If volatile, write all data */
        if (staticVolatile[offset]) {
            flush();
        }
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticDouble(double object, int offset) throws Throwable {
        long total = Double.doubleToRawLongBits(object);
        setStaticLong(total, offset);
    }

    /**
     * Change the value of the static field.
     *
     * @param object The value of the field
     * @param offset The offset of the static field in array storage.
     * @throws Throwable
     */
    public static void setStaticChar(char object, int offset) throws Throwable {
        setStaticLong(object, offset);
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static Object getStaticObject(int offset) throws Throwable {
        LocalHeap storage = getLocalHeap();

        /* If volatile field, update all fields before */
        if (staticVolatile[offset]) {
            refresh();
        }

        return storage.staticValuesObjectLocal[offset];
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static byte getStaticByte(int offset) throws Throwable {
        return (byte) getStaticLong(offset);
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static short getStaticShort(int offset) throws Throwable {
        return (short) getStaticLong(offset);
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static boolean getStaticBoolean(int offset) throws Throwable {
        long value = getStaticLong(offset);
        return value == 1;
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static int getStaticInteger(int offset) throws Throwable {
        return (int) getStaticLong(offset);
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static float getStaticFloat(int offset) throws Throwable {
        long value = getStaticLong(offset);
        return Float.intBitsToFloat((int) value);
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static long getStaticLong(int offset) throws Throwable {
        LocalHeap storage = getLocalHeap();

        /* If volatile field, update all fields before */
        if (staticVolatile[offset]) {
            refresh();
        }

        return storage.staticValuesPrimitiveLocal[offset];
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static double getStaticDouble(int offset) throws Throwable {
        long value = getStaticLong(offset);
        return Double.longBitsToDouble(value);
    }

    /**
     * Get the value of the static field.
     *
     * @param offset The offset of the static field in array storage.
     * @return The value of the field.
     * @throws Throwable
     */
    public static char getStaticChar(int offset) throws Throwable {
        return (char) getStaticLong(offset);
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldObject(Object instance, Object value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        if (value != fieldHeap.get(data)
                && (fieldVariable.get(data) == fieldHeap.get(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.set(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldByte(Object instance, byte value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getByte(data)
                && (fieldVariable.getByte(data) == fieldHeap.getByte(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setByte(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldShort(Object instance, short value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getShort(data)
                && (fieldVariable.getShort(data) == fieldHeap.getShort(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setShort(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldBoolean(Object instance, boolean value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getBoolean(data)
                && (fieldVariable.getBoolean(data) == fieldHeap.getBoolean(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setBoolean(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldInteger(Object instance, int value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getInt(data)
                && (fieldVariable.getInt(data) == fieldHeap.getInt(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setInt(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldFloat(Object instance, float value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getFloat(data)
                && (fieldVariable.getFloat(data) == fieldHeap.getFloat(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setFloat(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldLong(Object instance, long value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getLong(data)
                && (fieldVariable.getLong(data) == fieldHeap.getLong(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setLong(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldDouble(Object instance, double value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getDouble(data)
                && (fieldVariable.getDouble(data) == fieldHeap.getDouble(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setDouble(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Change the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param value The value of the field.
     * @param name The name of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static void setFieldChar(Object instance, char value, String name) throws ReflectiveOperationException, Throwable {

        Field field = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) field.get(instance)).get();

        LocalHeap storage = getLocalHeap();
        Field fieldVariable = getField(data, name);
        Field fieldHeap = getField(data, name + JickaThreadLocal.HEAPEXTENSION);
        Field fieldBlock = getField(data, name + JickaThreadLocal.BLOCKEXTENSION);

        /* Put instance if we must write data at the end of the Sync */
        if (value != fieldHeap.getChar(data)
                && (fieldVariable.getChar(data) == fieldHeap.getChar(data)
                || fieldBlock.getInt(data) != storage.block)) {
            storage.putFieldFlushOffsets(data);
            storage.putFieldFlushOffsets(instance);
        }

        fieldVariable.setChar(data, value);
        fieldBlock.setInt(data, storage.block);

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            flush();
        }
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     */
    private static Field getField(Object instance, String name) throws ReflectiveOperationException {
        Class<?> cls = instance.getClass();
        return cls.getField(name);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static Object getFieldObject(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.get(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static byte getFieldByte(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getByte(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static short getFieldShort(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getShort(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static boolean getFieldBoolean(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getBoolean(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static int getFieldInteger(Object instance, String name) throws ReflectiveOperationException, Throwable {
        
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getInt(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static float getFieldFloat(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getFloat(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static long getFieldLong(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getLong(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static double getFieldDouble(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getDouble(data);
    }

    /**
     * Get the value of the instance field.
     *
     * @param instance The instance which have the field.
     * @param name The name of the field.
     * @return The value of the field.
     * @throws ReflectiveOperationException
     * @throws Throwable
     */
    public static char getFieldChar(Object instance, String name) throws ReflectiveOperationException, Throwable {
        Field fieldInstance = getField(instance, JickaThreadLocal.FIELDNAME);
        Object data = ((JickaThreadLocal) fieldInstance.get(instance)).get();

        Field fieldVolatile = getField(data, name + JickaThreadLocal.VOLATILEEXTENSION);
        if (fieldVolatile.getBoolean(data)) {
            refresh();
        }

        Field field = getField(data, name);
        return field.getChar(data);
    }
}
