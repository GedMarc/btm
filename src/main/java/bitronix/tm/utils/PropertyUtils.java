/*
 * Copyright (C) 2006-2013 Bitronix Software (http://www.bitronix.be)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bitronix.tm.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Smart reflection helper.
 *
 * @author Ludovic Orban
 */
public final class PropertyUtils
{
	private static final String DASH_QUOTE_SPACE = "' - '";
	private static final String IS_NOT_ACCESSIBLE = "' is not accessible";
	private static final String PROPERTY_STRING = "property '";
	private static final String ACCESS_EXCEPTION = "' access threw an exception";
	private static final String IN_CLASS_STRING = "' in class '";

	/**
	 * Constructor PropertyUtils creates a new PropertyUtils instance.
	 */
	private PropertyUtils()
	{
		//No config required
	}

	/**
	 * Set a {@link Map} of direct or indirect properties on the target object.
	 *
	 * @param target
	 * 		the target object on which to set the properties.
	 * @param properties
	 * 		a {@link Map} of String/Object pairs.
	 *
	 * @throws PropertyException
	 * 		if an error happened while trying to set a property.
	 */
	public static void setProperties(Object target, Map<String, Object> properties)
	{
		for (Map.Entry<String, Object> entry : properties.entrySet())
		{
			String name = entry.getKey();
			Object value = entry.getValue();
			setProperty(target, name, value);
		}
	}

	/**
	 * Set a direct or indirect property (dotted property: prop1.prop2.prop3) on the target object. This method tries
	 * to be smart in the way that intermediate properties currently set to null are set if it is possible to create
	 * and set an object. Conversions from propertyValue to the proper destination type are performed when possible.
	 *
	 * @param target
	 * 		the target object on which to set the property.
	 * @param propertyName
	 * 		the name of the property to set.
	 * @param propertyValue
	 * 		the value of the property to set.
	 *
	 * @throws PropertyException
	 * 		if an error happened while trying to set the property.
	 */
	public static void setProperty(Object target, String propertyName, Object propertyValue)
	{
		String[] propertyNames = propertyName.split("\\.");

		StringBuilder visitedPropertyName = new StringBuilder();
		Object currentTarget = target;
		Object parentTarget = target;
		String parentName = null;
		int i = 0;
		while (i < propertyNames.length - 1)
		{
			String name = propertyNames[i];
			Object result = callGetter(currentTarget, name);
			if (result == null)
			{
				// try to instantiate the object & set it in place
				Class<?> propertyType = getPropertyType(target, name);
				try
				{
					result = propertyType.getDeclaredConstructor()
					                     .newInstance();
				}
				catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex)
				{
					throw new PropertyException("cannot set property '" + propertyName + DASH_QUOTE_SPACE + name + "' is null and cannot be auto-filled", ex);
				}
				callSetter(currentTarget, name, result);
			}

			parentTarget = currentTarget;
			currentTarget = result;
			visitedPropertyName.append(name);
			visitedPropertyName.append('.');
			i++;

			// if it's a Map object -> the non-visited part of the key should be used
			// as this Map's object key so stop iterating over the dotted properties.
			if (currentTarget instanceof Map)
			{
				parentName = name;
				break;
			}
		}

		String lastPropertyName = propertyName.substring(visitedPropertyName.length());
		if (currentTarget instanceof Map)
		{
			@SuppressWarnings("unchecked")
			Map<String, Object> p = (Map<String, Object>) currentTarget;
			p.put(lastPropertyName, propertyValue.toString());
			if (parentName != null)
			{
				callSetter(parentTarget, parentName, p);
			}
		}
		else
		{
			setDirectProperty(currentTarget, lastPropertyName, propertyValue);
		}
	}

	/**
	 * Method callGetter ...
	 *
	 * @param target
	 * 		of type Object
	 * @param propertyName
	 * 		of type String
	 *
	 * @return Object
	 *
	 * @throws PropertyException
	 * 		when
	 */
	private static Object callGetter(Object target, String propertyName)
	{
		Method getter = getGetter(target, propertyName);
		try
		{
			return getter.invoke(target);
		}
		catch (IllegalAccessException ex)
		{
			throw new PropertyException(PROPERTY_STRING + propertyName + IS_NOT_ACCESSIBLE, ex);
		}
		catch (InvocationTargetException ex)
		{
			throw new PropertyException(PROPERTY_STRING + propertyName + ACCESS_EXCEPTION, ex);
		}
	}

	/**
	 * Method getPropertyType ...
	 *
	 * @param target
	 * 		of type Object
	 * @param propertyName
	 * 		of type String
	 *
	 * @return Class<?>
	 */
	private static Class<?> getPropertyType(Object target, String propertyName)
	{
		String getterName = "get" + propertyName.substring(0, 1)
		                                        .toUpperCase() + propertyName.substring(1);
		String getterIsName = "is" + propertyName.substring(0, 1)
		                                         .toUpperCase() + propertyName.substring(1);
		Method[] methods = target.getClass()
		                         .getMethods();
		for (Method method : methods)
		{
			if ((method.getName()
			           .equals(getterName) || method.getName()
			                                        .equals(getterIsName)) && !method.getReturnType()
			                                                                         .equals(void.class) && method.getParameterTypes().length == 0)
			{
				return method.getReturnType();
			}
		}
		throw new PropertyException("no property '" + propertyName + IN_CLASS_STRING + target.getClass()
		                                                                                     .getName() + "'");
	}

	/**
	 * Method callSetter ...
	 *
	 * @param target
	 * 		of type Object
	 * @param propertyName
	 * 		of type String
	 * @param parameter
	 * 		of type Object
	 *
	 * @throws PropertyException
	 * 		when
	 */
	private static void callSetter(Object target, String propertyName, Object parameter)
	{
		Method setter = getSetter(target, propertyName);
		try
		{
			setter.invoke(target, parameter);
		}
		catch (IllegalAccessException ex)
		{
			throw new PropertyException(PROPERTY_STRING + propertyName + IS_NOT_ACCESSIBLE, ex);
		}
		catch (InvocationTargetException ex)
		{
			throw new PropertyException(PROPERTY_STRING + propertyName + ACCESS_EXCEPTION, ex);
		}
	}

	/**
	 * Set a direct property on the target object. Conversions from propertyValue to the proper destination type
	 * are performed whenever possible.
	 *
	 * @param target
	 * 		the target object on which to set the property.
	 * @param propertyName
	 * 		the name of the property to set.
	 * @param propertyValue
	 * 		the value of the property to set.
	 *
	 * @throws PropertyException
	 * 		if an error happened while trying to set the property.
	 */
	private static void setDirectProperty(Object target, String propertyName, Object propertyValue)
	{
		Method setter = getSetter(target, propertyName);
		Class<?> parameterType = setter.getParameterTypes()[0];
		try
		{
			if (propertyValue != null)
			{
				Object transformedPropertyValue = transform(propertyValue, parameterType);
				setter.invoke(target, transformedPropertyValue);
			}
			else
			{
				setter.invoke(target);
			}
		}
		catch (IllegalAccessException ex)
		{
			throw new PropertyException(PROPERTY_STRING + propertyName + IS_NOT_ACCESSIBLE, ex);
		}
		catch (InvocationTargetException ex)
		{
			throw new PropertyException(PROPERTY_STRING + propertyName + ACCESS_EXCEPTION, ex);
		}
	}

	/**
	 * Method getGetter ...
	 *
	 * @param target
	 * 		of type Object
	 * @param propertyName
	 * 		of type String
	 *
	 * @return Method
	 */
	private static Method getGetter(Object target, String propertyName)
	{
		String getterName = "get" + propertyName.substring(0, 1)
		                                        .toUpperCase() + propertyName.substring(1);
		String getterIsName = "is" + propertyName.substring(0, 1)
		                                         .toUpperCase() + propertyName.substring(1);
		Method[] methods = target.getClass()
		                         .getMethods();
		for (Method method : methods)
		{
			if ((method.getName()
			           .equals(getterName) || method.getName()
			                                        .equals(getterIsName)) && !method.getReturnType()
			                                                                         .equals(void.class) && method.getParameterTypes().length == 0)
			{
				return method;
			}
		}
		throw new PropertyException("no readable property '" + propertyName + IN_CLASS_STRING + target.getClass()
		                                                                                              .getName() + "'");
	}

	/**
	 * Method getSetter ...
	 *
	 * @param target
	 * 		of type Object
	 * @param propertyName
	 * 		of type String
	 *
	 * @return Method
	 */
	private static Method getSetter(Object target, String propertyName)
	{
		if (propertyName == null || "".equals(propertyName))
		{
			throw new PropertyException("encountered invalid null or empty property name");
		}
		String setterName = "set" + propertyName.substring(0, 1)
		                                        .toUpperCase() + propertyName.substring(1);
		Method[] methods = target.getClass()
		                         .getMethods();
		for (Method method : methods)
		{
			if (method.getName()
			          .equals(setterName) && method.getReturnType()
			                                       .equals(void.class) && method.getParameterTypes().length == 1)
			{
				return method;
			}
		}
		throw new PropertyException("no writeable property '" + propertyName + IN_CLASS_STRING + target.getClass()
		                                                                                               .getName() + "'");
	}

	/**
	 * Method transform ...
	 *
	 * @param value
	 * 		of type Object
	 * @param destinationClass
	 * 		of type Class<?>
	 *
	 * @return Object
	 */
	private static Object transform(Object value, Class<?> destinationClass)
	{
		if (value.getClass() == destinationClass)
		{
			return value;
		}

		if (value.getClass() == boolean.class || value.getClass() == Boolean.class ||
		    value.getClass() == byte.class || value.getClass() == Byte.class ||
		    value.getClass() == short.class || value.getClass() == Short.class ||
		    value.getClass() == int.class || value.getClass() == Integer.class ||
		    value.getClass() == long.class || value.getClass() == Long.class ||
		    value.getClass() == float.class || value.getClass() == Float.class ||
		    value.getClass() == double.class || value.getClass() == Double.class
		)
		{
			return value;
		}

		if ((destinationClass == boolean.class || destinationClass == Boolean.class) && value.getClass() == String.class)
		{
			return Boolean.valueOf((String) value);
		}
		if ((destinationClass == byte.class || destinationClass == Byte.class) && value.getClass() == String.class)
		{
			return Byte.valueOf((String) value);
		}
		if ((destinationClass == short.class || destinationClass == Short.class) && value.getClass() == String.class)
		{
			return Short.valueOf((String) value);
		}
		if ((destinationClass == int.class || destinationClass == Integer.class) && value.getClass() == String.class)
		{
			return Integer.valueOf((String) value);
		}
		if ((destinationClass == long.class || destinationClass == Long.class) && value.getClass() == String.class)
		{
			return Long.valueOf((String) value);
		}
		if ((destinationClass == float.class || destinationClass == Float.class) && value.getClass() == String.class)
		{
			return Float.valueOf((String) value);
		}
		if ((destinationClass == double.class || destinationClass == Double.class) && value.getClass() == String.class)
		{
			return Double.valueOf((String) value);
		}

		throw new PropertyException("cannot convert values of type '" + value.getClass()
		                                                                     .getName() + "' into type '" + destinationClass + "'");
	}

	/**
	 * Return a comma-separated String of r/w properties of the specified object.
	 *
	 * @param obj
	 * 		the object to introspect.
	 *
	 * @return a a comma-separated String of r/w properties.
	 */
	public static String propertiesToString(Object obj)
	{
		StringBuilder sb = new StringBuilder();
		Map<String, Object> properties = new TreeMap<>(getProperties(obj));
		Iterator<String> it = properties.keySet()
		                                .iterator();
		while (it.hasNext())
		{
			String property = it.next();
			Object val = getProperty(obj, property);
			sb.append(property);
			sb.append("=");
			sb.append(val);
			if (it.hasNext())
			{
				sb.append(", ");
			}
		}
		return sb.toString();
	}

	/**
	 * Build a map of direct javabeans properties of the target object. Only read/write properties (ie: those who have
	 * both a getter and a setter) are returned.
	 *
	 * @param target
	 * 		the target object from which to get properties names.
	 *
	 * @return a Map of String with properties names as key and their values
	 *
	 * @throws PropertyException
	 * 		if an error happened while trying to get a property.
	 */
	@SuppressWarnings("WeakerAccess")
	public static Map<String, Object> getProperties(Object target)
	{
		Map<String, Object> properties = new HashMap<>();
		Class<?> clazz = target.getClass();
		Method[] methods = clazz.getMethods();
		for (Method method : methods)
		{
			String name = method.getName();
			if (method.getModifiers() == Modifier.PUBLIC && method.getParameterTypes().length == 0 && (name.startsWith("get") || name.startsWith("is")) &&
			    containsSetterForGetter(clazz, method))
			{
				String propertyName;
				if (name.startsWith("get"))
				{
					propertyName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
				}
				else if (name.startsWith("is"))
				{
					propertyName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
				}
				else
				{
					throw new PropertyException("method '" + name + "' is not a getter, thereof no setter can be found");
				}

				try
				{
					Object propertyValue = method.invoke(target);
					if (propertyValue instanceof Map)
					{
						@SuppressWarnings("unchecked")
						Map<String, Object> propertiesContent = getNestedProperties(propertyName, (Map<String, Object>) propertyValue);
						properties.putAll(propertiesContent);
					}
					else
					{
						properties.put(propertyName, propertyValue);
					}
				}
				catch (IllegalAccessException | InvocationTargetException ex)
				{
					throw new PropertyException("cannot set property '" + propertyName + DASH_QUOTE_SPACE + name + "' is null and cannot be auto-filled", ex);
				}

			} // if
		} // for
		return properties;
	}

	/**
	 * Get a direct or indirect property (dotted property: prop1.prop2.prop3) on the target object.
	 *
	 * @param target
	 * 		the target object from which to get the property.
	 * @param propertyName
	 * 		the name of the property to get.
	 *
	 * @return the value of the specified property.
	 *
	 * @throws PropertyException
	 * 		if an error happened while trying to get the property.
	 */
	public static Object getProperty(Object target, String propertyName)
	{
		String[] propertyNames = propertyName.split("\\.");
		Object currentTarget = target;
		for (int i = 0; i < propertyNames.length; i++)
		{
			String name = propertyNames[i];
			Object result = callGetter(currentTarget, name);
			if (result == null && i < propertyNames.length - 1)
			{
				throw new PropertyException("cannot get property '" + propertyName + DASH_QUOTE_SPACE + name + "' is null");
			}
			currentTarget = result;
		}

		return currentTarget;
	}

	/**
	 * Method containsSetterForGetter ...
	 *
	 * @param clazz
	 * 		of type Class ?
	 * @param method
	 * 		of type Method
	 *
	 * @return boolean
	 */
	private static boolean containsSetterForGetter(Class<?> clazz, Method method)
	{
		String methodName = method.getName();
		String setterName;

		if (methodName.startsWith("get"))
		{
			setterName = "set" + methodName.substring(3);
		}
		else if (methodName.startsWith("is"))
		{
			setterName = "set" + methodName.substring(2);
		}
		else
		{
			throw new PropertyException("method '" + methodName + "' is not a getter, thereof no setter can be found");
		}

		Method[] methods = clazz.getMethods();
		for (Method m : methods)
		{
			if (m.getName()
			     .equals(setterName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Method getNestedProperties ...
	 *
	 * @param prefix
	 * 		of type String
	 * @param properties
	 * 		of type Map String, Object
	 *
	 * @return Map String,Object
	 */
	private static Map<String, Object> getNestedProperties(String prefix, Map<String, Object> properties)
	{
		Map<String, Object> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : properties.entrySet())
		{
			String name = entry.getKey();
			String value = (String) entry.getValue();
			result.put(prefix + '.' + name, value);
		}
		return result;
	}

}
