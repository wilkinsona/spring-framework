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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link AttributeTypes} backed by an {@link Annotation} {@link Class} and
 * implemented using standard Java reflection.
 *
 * @author Phillip webb
 * @since 5.2
 */
class StandardAttributeTypes implements AttributeTypes {

	private Map<String, AttributeType> attributeTypes;

	StandardAttributeTypes(Class<? extends Annotation> type) {
		this.attributeTypes = extractAttributeTypes(type.getDeclaredMethods());
	}

	private Map<String, AttributeType> extractAttributeTypes(Method[] methods) {
		Map<String, AttributeType> attributeTypes = new LinkedHashMap<>();
		for (Method method : methods) {
			if (isAttributeMethod(method)) {
				StandardAttributeType attributeType = new StandardAttributeType(method);
				attributeTypes.put(attributeType.getAttributeName(), attributeType);
			}
		}
		return Collections.unmodifiableMap(attributeTypes);
	}

	private boolean isAttributeMethod(Method method) {
		return (method.getParameterCount() == 0 && method.getReturnType() != void.class);
	}

	@Override
	public Iterator<AttributeType> iterator() {
		return this.attributeTypes.values().iterator();
	}

	@Override
	public Set<String> attributeNames() {
		return this.attributeTypes.keySet();
	}

	@Override
	public AttributeType get(String name) {
		return this.attributeTypes.get(name);
	}

	@Override
	public String toString() {
		return AnnotationString.get(this);
	}

}
