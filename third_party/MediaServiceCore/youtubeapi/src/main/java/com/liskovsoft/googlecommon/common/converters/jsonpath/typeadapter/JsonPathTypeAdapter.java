package com.liskovsoft.googlecommon.common.converters.jsonpath.typeadapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPathObj;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath;
import com.liskovsoft.googlecommon.common.helpers.ReflectionHelper;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JsonPathTypeAdapter<T> {
    private static final String TAG = JsonPathTypeAdapter.class.getSimpleName();
    private static final ConcurrentMap<Class<?>, FieldDescriptor[]> FIELD_METADATA = new ConcurrentHashMap<>();
    private final ParseContext mParser;
    private final Class<?> mType;

    public JsonPathTypeAdapter(ParseContext parser, Class<?> type) {
        mParser = parser;
        mType = type;
    }

    public JsonPathTypeAdapter(ParseContext parser, Type type) {
        mParser = parser;
        mType = (Class<?>) type;
    }

    @SuppressWarnings("unchecked")
    public final T read(InputStream is) {
        is = process(is);

        DocumentContext parser = mParser.parse(is);

        String[] jsonPath = getJsonPath(getGenericType());

        if (jsonPath != null) { // annotation on the same collection class
            for (String path : jsonPath) {
                try {
                    Object jsonValue = parser.read(path);
                    if (jsonValue == null) {
                        throw new NullPointerException();
                    }
                    return (T) readType(getGenericType(), jsonValue);
                } catch (PathNotFoundException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            return null;
        }

        T result = (T) readType(getGenericType(), parser);

        //if (result == null) {
        //    // Dump root object
        //    ReflectionHelper.dumpDebugInfo(getGenericType(), jsonContent);
        //}

        return result;
    }

    /**
     * Enable additional processing like skipping first line etc
     */
    protected InputStream process(InputStream is) {
        return is;
    }

    private Class<?> getGenericType() {
        return mType;
    }

    private Object readType(Class<?> type, Object jsonValue) {
        if (type == null || jsonValue == null) {
            return null;
        }

        return readType(type, mParser.parse(jsonValue));
    }

    private Object readType(Class<?> type, DocumentContext parser) {
        if (type == null || parser == null) {
            return null;
        }

        Object obj = null;
        boolean done = false;

        try {
            Constructor<?> constructor = type.getConstructor();
            obj = constructor.newInstance();

            FieldDescriptor[] fields = getFieldMetadata(type);

            for (FieldDescriptor field : fields) {
                boolean result = processField(field, obj, type, parser);

                // At least one field is set
                if (result) {
                    done = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return done ? obj : null;
    }

    private boolean processField(FieldDescriptor descriptor, Object obj, Class<?> type, DocumentContext parser) {
        //Log.d(TAG, "Processing on thread: %s", Thread.currentThread().getName());

        Field field = descriptor.field;
        boolean done = false;
        String[] jsonPath = descriptor.jsonPath;

        if (jsonPath == null) {
            return false;
        }

        Object jsonVal = null;

        for (String path : jsonPath) {
            try {
                jsonVal = parser.read(path);
                break;
            } catch (PathNotFoundException e) {
                Log.d(TAG, type.getSimpleName() + ": Path not found: " + path);
            }
        }

        if (jsonVal == null) {
            return false;
        }

        try {
            if (JsonPathObj.class.isAssignableFrom(field.getType())) {
                Object val = readType(field.getType(), jsonVal);
                field.set(obj, val);
            } else if (jsonVal instanceof JsonArray) {
                List<Object> list = null;
                Class<?> myType = descriptor.getGenericParamType();

                if (myType == null) {
                    Log.e(TAG, "Please, supply generic field for the list type: " + field);
                    return false;
                }

                for (Object jsonObj : (JsonArray) jsonVal) {
                    Object item;

                    if (jsonObj instanceof JsonPrimitive) {
                        item = parsePrimitive((JsonPrimitive) jsonObj);
                    } else {
                        item = readType(myType, jsonObj);
                    }

                    if (item != null) {
                        if (list == null) {
                            list = new ArrayList<>();
                        }

                        list.add(item);
                    }
                }

                field.set(obj, list);
            } else if (jsonVal instanceof JsonPrimitive) {
                Object val = parsePrimitive((JsonPrimitive) jsonVal);

                field.set(obj, val);
            } else if (jsonVal instanceof JsonObject) {
                Object val = readType(field.getType(), jsonVal);
                field.set(obj, val);
            }

            done = field.get(obj) != null; // field is set
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "%s: Incompatible json value found %s. Same path on different types?", field.getType().getSimpleName(), jsonVal);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "%s: Illegal access with value %s.", field.getType().getSimpleName(), jsonVal);
        }

        return done;
    }

    private static FieldDescriptor[] getFieldMetadata(Class<?> type) {
        FieldDescriptor[] cached = FIELD_METADATA.get(type);
        if (cached != null) {
            return cached;
        }

        List<FieldDescriptor> descriptors = new ArrayList<>();
        for (Field field : ReflectionHelper.getAllFields(type)) {
            field.setAccessible(true);
            descriptors.add(new FieldDescriptor(field, getJsonPath(field), ReflectionHelper.getGenericParams(field)));
        }

        FieldDescriptor[] computed = descriptors.toArray(new FieldDescriptor[0]);
        FieldDescriptor[] existing = FIELD_METADATA.putIfAbsent(type, computed);
        return existing != null ? existing : computed;
    }

    private static String[] getJsonPath(Class<?> type) {
        Annotation[] annotations = type.getAnnotations();

        return getJsonPath(annotations);
    }

    private static String[] getJsonPath(Field field) {
        Annotation[] annotations = field.getAnnotations();

        return getJsonPath(annotations);
    }

    private static String[] getJsonPath(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof JsonPath) {
                return ((JsonPath) annotation).value();
            }
        }

        return null;
    }

    private Object parsePrimitive(JsonPrimitive jsonVal) {
        Object val;

        if (jsonVal.isNumber()) {
            if (jsonVal.toString().contains(".")) {
                val = jsonVal.getAsFloat(); // Float
            } else {
                val = jsonVal.getAsInt(); // Integer
            }
        } else if (jsonVal.isBoolean()) {
            val = jsonVal.getAsBoolean(); // Boolean
        } else {
            val = jsonVal.getAsString(); // String
        }

        return val;
    }

    private static final class FieldDescriptor {
        private final Field field;
        private final String[] jsonPath;
        private final Type[] genericParams;

        private FieldDescriptor(Field field, String[] jsonPath, Type[] genericParams) {
            this.field = field;
            this.jsonPath = jsonPath;
            this.genericParams = genericParams;
        }

        private Class<?> getGenericParamType() {
            if (genericParams != null && genericParams.length == 1) {
                return (Class<?>) genericParams[0];
            }

            return null;
        }
    }
}
