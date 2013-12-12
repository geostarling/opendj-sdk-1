/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.mockito.invocation.InvocationOnMock;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.EnumPropertyDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;

/**
 * Provides Mockito mocks for Configuration objects with default values
 * corresponding to those defined in xml configuration files.
 * <p>
 * These mocks can be used like any other mocks, e.g, you can define stubs using
 * {@code when} method or verify calls using {@code verify} method.
 * <p>
 * Example:
 *
 * <pre>
 * {
 *     &#064;code
 *     LDAPConnectionHandlerCfg mockCfg = mockCfg(LDAPConnectionHandlerCfg.class);
 *     assertThat(mockCfg.getMaxRequestSize()).isEqualTo(5 * 1000 * 1000);
 * }
 * </pre>
 */
public final class ConfigurationMock {

    private static final ConfigAnswer configAnswer = new ConfigAnswer();

    /**
     * Returns a mock for the provided configuration class.
     * <p>
     * If a setting has a default value, the mock automatically returns the
     * default value when the getter is called on the setting.
     * <p>
     * It is possible to override this default behavior with the usual methods
     * calls with Mockito (e.g, {@code when} method).
     *
     * @param <T>
     *            The type of configuration.
     * @param configClass
     *            The configuration class.
     * @return a mock
     */
    public static <T extends Configuration> T mockCfg(Class<T> configClass) {
        return mock(configClass, configAnswer);
    }

    /**
     * A stubbed answer for Configuration objects, allowing to return default
     * value for settings when available.
     */
    private static class ConfigAnswer implements org.mockito.stubbing.Answer<Object> {

        /** {@inheritDoc} */
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            String definitionClassName = toDefinitionClassName(invocation.getMethod().getDeclaringClass()
                    .getName());
            Class<?> definitionClass = Class.forName(definitionClassName);
            ManagedObjectDefinition<?, ?> definition = (ManagedObjectDefinition<?, ?>) definitionClass.getMethod(
                    "getInstance").invoke(null);
            Method getPropertyDefMethod =
                    definitionClass.getMethod(invocation.getMethod().getName() + "PropertyDefinition");
            Class<?> propertyReturnType = getPropertyReturnType(getPropertyDefMethod);
            return getDefaultValue(definition, getPropertyDefMethod, propertyReturnType);
        }

        /**
         * Returns the type of values returned by the property.
         */
        private Class<?> getPropertyReturnType(Method getPropertyDefMethod) {
            Class<?> returnClass = getPropertyDefMethod.getReturnType();
            return ((ParameterizedType) returnClass.getGenericSuperclass())
                    .getActualTypeArguments()[0].getClass();
        }

        /**
         * Retrieve class name of definition from class name of configuration.
         * <p>
         * Convert class name "[package].admin.server.FooCfg" to
         * "[package].admin.meta.FooCfgDef"
         */
        private String toDefinitionClassName(String configClassName) {
            return configClassName.replaceAll("\\.admin\\.server", ".admin.meta") + "Defn";
        }

        /**
         * Returns the default value corresponding to the provided property
         * definition getter method from the provided managed object definition.
         *
         * @param <T>
         *            The data type of values provided by the property
         *            definition.
         * @param definition
         *            The definition of the managed object.
         * @param getPropertyDefMethod
         *            The method to retrieve the property definition from the
         *            definition.
         * @param propertyReturnClass
         *            The class of values provided by the property definition.
         * @return the default value of property definition, or
         *         {@code null} if there is no default value.
         * @throws Exception
         *             If an error occurs.
         */
        @SuppressWarnings({ "unchecked", "unused" })
        private <T> Object getDefaultValue(ManagedObjectDefinition<?, ?> definition,
                Method getPropertyDefMethod, Class<T> propertyReturnClass) throws Exception {
            PropertyDefinition<T> propertyDefinition = (PropertyDefinition<T>) getPropertyDefMethod.invoke(definition);
            DefaultBehaviorProvider<T> defaultBehaviorProvider = (DefaultBehaviorProvider<T>) propertyDefinition
                    .getClass().getMethod("getDefaultBehaviorProvider").invoke(propertyDefinition);
            MockProviderVisitor<T> visitor = new MockProviderVisitor<T>(propertyDefinition);
            Collection<T> values = defaultBehaviorProvider.accept(visitor, null);

            if (propertyDefinition instanceof EnumPropertyDefinition) {
                // enum values returned as a sorted set
                return values;
            } else {
                // single value returned
                return values.iterator().next();
            }
        }

    }

    /** Visitor used to retrieve the default value. */
    private static class MockProviderVisitor<T> implements DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

        /** The property definition used to decode the values. */
        private PropertyDefinition<T> propertyDef;

        MockProviderVisitor(PropertyDefinition<T> propertyDef) {
            this.propertyDef = propertyDef;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /**
         * Returns the default value for the property as a collection.
         */
        @Override
        public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> provider, Void p) {
            SortedSet<T> values = new TreeSet<T>();
            for (String stringValue : provider.getDefaultValues()) {
                values.add(propertyDef.decodeValue(stringValue));
            }
            return values;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }
    }
}