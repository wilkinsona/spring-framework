/*
 * Copyright 2002-2018 the original author or authors.
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
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import org.springframework.util.Assert;

/**
 * {@link DeclaredAttributes} backed by an {@link Annotation} and implemented
 * using standard Java reflection.
 *
 * @author Phillip Webb
 * @since 5.2
 */
public class StandardDeclaredAttributes extends AbstractDeclaredAttributes {

	private final Annotation annotation;

	private volatile Method[] attributeMethods;

	private volatile Names names;

	StandardDeclaredAttributes(Annotation annotation) {
		Assert.notNull(annotation, "Annotation must not be null");
		this.annotation = annotation;
	}

	/**
	 * Return the backing source annotation.
	 * @return the source annotation
	 */
	public Annotation getAnnotation() {
		return this.annotation;
	}

	@Override
	public Set<String> names() {
		Names names = this.names;
		if (names != null) {
			return names;
		}
		names = new Names();
		this.names = names;
		return names;
	}

	@Override
	public Object get(String name) {
		for (Method attributeMethod : getAttributeMethods()) {
			if (attributeMethod != null && attributeMethod.getName().equals(name)) {
				return get(attributeMethod);
			}
		}
		return null;
	}

	private Method[] getAttributeMethods() {
		Method[] result = this.attributeMethods;
		if (result != null) {
			return result;
		}
		result = this.annotation.annotationType().getDeclaredMethods();
		for (int i = 0; i < result.length; i++) {
			if (isAttributeMethod(result[i])) {
				result[i].setAccessible(true);
			}
			else {
				result[i] = null;
			}
		}
		this.attributeMethods = result;
		return result;
	}

	private boolean isAttributeMethod(Method method) {
		return (method.getParameterCount() == 0 && method.getReturnType() != void.class);
	}

	private Object get(Method method) {
		try {
			method.setAccessible(true);
			return AttributeValue.convert(method.invoke(this.annotation));
		}
		catch (Throwable ex) {
			throw new IllegalStateException(
					"Could not obtain annotation attribute value for " + method, ex);
		}
	}

	@Override
	public String toString() {
		return AnnotationString.get(this);
	}

	/**
	 * Set implementation used to return the attribute names.
	 */
	private class Names extends AbstractSet<String> {

		@Override
		public Iterator<String> iterator() {
			return Arrays.stream(getAttributeMethods()).filter(Objects::nonNull).map(
					Method::getName).iterator();
		}

		@Override
		public int size() {
			int result = 0;
			for (Method method : getAttributeMethods()) {
				result += method != null ? 1 : 0;
			}
			return result;
		}

	}

}
