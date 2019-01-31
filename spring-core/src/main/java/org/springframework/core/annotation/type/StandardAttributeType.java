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
import java.lang.reflect.Method;

import org.springframework.util.ClassUtils;

/**
 * {@link AttributeType} backed by an {@link Annotation} {@link Method} and
 * implemented using standard Java reflection.
 *
 * @author Phillip webb
 * @since 5.2
 */
class StandardAttributeType implements AttributeType {

	private final Method method;

	StandardAttributeType(Method method) {
		this.method = method;
		method.setAccessible(true);
	}

	@Override
	public String getAttributeName() {
		return this.method.getName();
	}

	@Override
	public String getClassName() {
		return getClassName(this.method.getReturnType());
	}

	private String getClassName(Class<?> type) {
		if (type.isArray()) {
			return getClassName(type.getComponentType()) + ClassUtils.ARRAY_SUFFIX;
		}
		return type.getName();
	}

	@Override
	public DeclaredAnnotations getDeclaredAnnotations() {
		return DeclaredAnnotations.from(this.method, this.method.getDeclaredAnnotations());
	}

	@Override
	public Object getDefaultValue() {
		return AttributeValue.convert(this.method.getDefaultValue());
	}

	@Override
	public String toString() {
		return AnnotationString.get(this);
	}

}
