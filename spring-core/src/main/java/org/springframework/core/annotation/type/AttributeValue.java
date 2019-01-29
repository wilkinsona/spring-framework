/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.annotation.type;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared conversion and validation logic for attribute values.
 *
 * @author Phillip Webb
 * @since 5.2
 */
class AttributeValue {

	public static final Set<Class<?>> SUPPORTED_TYPES;
	static {
		Set<Class<?>> supportedTypes = new LinkedHashSet<>();
		supportedTypes.add(Byte.class);
		supportedTypes.add(byte[].class);
		supportedTypes.add(Boolean.class);
		supportedTypes.add(boolean[].class);
		supportedTypes.add(Character.class);
		supportedTypes.add(char[].class);
		supportedTypes.add(Short.class);
		supportedTypes.add(short[].class);
		supportedTypes.add(Integer.class);
		supportedTypes.add(int[].class);
		supportedTypes.add(Long.class);
		supportedTypes.add(long[].class);
		supportedTypes.add(Float.class);
		supportedTypes.add(float[].class);
		supportedTypes.add(Double.class);
		supportedTypes.add(double[].class);
		supportedTypes.add(String.class);
		supportedTypes.add(String[].class);
		supportedTypes.add(ClassReference.class);
		supportedTypes.add(ClassReference[].class);
		supportedTypes.add(EnumValueReference.class);
		supportedTypes.add(EnumValueReference[].class);
		supportedTypes.add(DeclaredAttributes.class);
		supportedTypes.add(DeclaredAttributes[].class);
		SUPPORTED_TYPES = Collections.unmodifiableSet(supportedTypes);
	}

	public static boolean isSupportedType(Class<?> type) {
		if (Object[].class.equals(type)) {
			return true;
		}
		for (Class<?> supported : SUPPORTED_TYPES) {
			if(supported.isAssignableFrom(type)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static Object convert(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Class) {
			return ClassReference.from((Class<?>) value);
		}
		if (value instanceof Class<?>[]) {
			Class<?>[] classes = (Class<?>[]) value;
			ClassReference[] references = new ClassReference[classes.length];
			for (int i = 0; i < classes.length; i++) {
				references[i] = ClassReference.from(classes[i]);
			}
			return references;
		}
		if (value instanceof Enum<?>) {
			return EnumValueReference.from((Enum<?>) value);
		}
		if (value instanceof Enum<?>[]) {
			Enum<?>[] enums = (Enum<?>[]) value;
			EnumValueReference[] references = new EnumValueReference[enums.length];
			for (int i = 0; i < enums.length; i++) {
				references[i] = EnumValueReference.from(enums[i]);
			}
			return references;
		}
		if (value instanceof Annotation) {
			return new StandardDeclaredAnnotation((Annotation) value).getAttributes();
		}
		if (value instanceof Annotation[]) {
			Annotation[] annotations = (Annotation[]) value;
			DeclaredAttributes[] attributes = new DeclaredAttributes[annotations.length];
			for (int i = 0; i < attributes.length; i++) {
				attributes[i] = new StandardDeclaredAnnotation(
						annotations[i]).getAttributes();
			}
			return attributes;
		}
		if (value instanceof Map) {
			return DeclaredAttributes.from((Map<String, Object>) value);
		}
		if (value instanceof Map[]) {
			Map<String, Object>[] maps = (Map[]) value;
			DeclaredAttributes[] attributes = new DeclaredAttributes[maps.length];
			for (int i = 0; i < attributes.length; i++) {
				attributes[i] = DeclaredAttributes.from(maps[i]);
			}
			return attributes;
		}
		return value;
	}

}
