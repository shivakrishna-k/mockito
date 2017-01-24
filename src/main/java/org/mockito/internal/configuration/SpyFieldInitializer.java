package org.mockito.internal.configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.util.MockUtil;
import org.mockito.internal.util.reflection.FieldReader;
import org.mockito.internal.util.reflection.FieldSetter;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.withSettings;
import static org.mockito.internal.util.StringJoiner.join;

class SpyFieldInitializer {
    public static void initializeSpy(Object testInstance, Field field) {
        FieldReader fieldReader = new FieldReader(testInstance, field);
        try {
            Object instance = fieldReader.read();
            assertNotInterface(instance, field.getType());
            if (MockUtil.isMock(instance)) {
                // instance has been spied earlier
                // for example happens when MockitoAnnotations.initMocks is called two times.
                Mockito.reset(instance);
            } else if (instance != null) {
                FieldSetter.setField(testInstance, field, spyInstance(field, instance));
            } else {
                FieldSetter.setField(testInstance, field, spyNewInstance(testInstance, field));
            }
        } catch (Exception e) {
            throw new MockitoException("Unable to initialize @Spy annotated field '" + field.getName() + "'.\n" + e.getMessage(), e);
        }
    }

    private static void assertNotInterface(Object testInstance, Class<?> type) {
        type = testInstance != null ? testInstance.getClass() : type;
        if (type.isInterface()) {
            throw new MockitoException("Type '" + type.getSimpleName() + "' is an interface and it cannot be spied on.");
        }
    }

    private static Object spyInstance(Field field, Object instance) {
        return Mockito.mock(instance.getClass(),
                            withSettings().spiedInstance(instance)
                                          .defaultAnswer(CALLS_REAL_METHODS)
                                          .name(field.getName()));
    }

    private static Object spyNewInstance(Object testInstance, Field field)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        MockSettings settings = withSettings().defaultAnswer(CALLS_REAL_METHODS)
                                              .name(field.getName());
        Class<?> type = field.getType();
        if (type.isInterface()) {
            return Mockito.mock(type, settings.useConstructor());
        }
        int modifiers = type.getModifiers();
        if (typeIsPrivateAbstractInnerClass(type, modifiers)) {
            throw new MockitoException(join("@Spy annotation can't initialize private abstract inner classes.",
                                            "  inner class: '" + type.getSimpleName() + "'",
                                            "  outer class: '" + type.getEnclosingClass().getSimpleName() + "'",
                                            "",
                                            "You should augment the visibility of this inner class"));
        }
        if (typeIsNonStaticInnerClass(type, modifiers)) {
            Class<?> enclosing = type.getEnclosingClass();
            if (!enclosing.isInstance(testInstance)) {
                throw new MockitoException(join("@Spy annotation can only initialize inner classes declared in the test.",
                                                "  inner class: '" + type.getSimpleName() + "'",
                                                "  outer class: '" + enclosing.getSimpleName() + "'",
                                                ""));
            }
            return Mockito.mock(type, settings.useConstructor()
                                              .outerInstance(testInstance));
        }

        Constructor<?> constructor = noArgConstructorOf(type);
        if (Modifier.isPrivate(constructor.getModifiers())) {
            constructor.setAccessible(true);
            return Mockito.mock(type, settings.spiedInstance(constructor.newInstance()));
        } else {
            return Mockito.mock(type, settings.useConstructor());
        }
    }

    private static Constructor<?> noArgConstructorOf(Class<?> type) {
        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new MockitoException("Please ensure that the type '" + type.getSimpleName() + "' has a no-arg constructor.");
        }
        return constructor;
    }

    private static boolean typeIsNonStaticInnerClass(Class<?> type, int modifiers) {
        return !Modifier.isStatic(modifiers) && type.getEnclosingClass() != null;
    }

    private static boolean typeIsPrivateAbstractInnerClass(Class<?> type, int modifiers) {
        return Modifier.isPrivate(modifiers) && Modifier.isAbstract(modifiers) && type.getEnclosingClass() != null;
    }
}