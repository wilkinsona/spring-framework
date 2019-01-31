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

import org.springframework.util.Assert;

/**
 * {@link AnnotationType} backed by an {@link Annotation} {@link Class} and
 * implemented using standard Java reflection.
 *
 * @author Phillip webb
 * @since 5.2
 */
class StandardAnnotationType implements AnnotationType {

	private final Class<? extends Annotation> type;

	private final DeclaredAnnotations declaredAnnotations;

	private final AttributeTypes attributeTypes;

	StandardAnnotationType(Class<? extends Annotation> type) {
		Assert.notNull(type, "Type must not be null");
		this.type = type;
		this.declaredAnnotations = DeclaredAnnotations.from(type,
				type.getDeclaredAnnotations());
		this.attributeTypes = new StandardAttributeTypes(type);
	}

	@Override
	public String getClassName() {
		return this.type.getName();
	}

	@Override
	public DeclaredAnnotations getDeclaredAnnotations() {
		return this.declaredAnnotations;
	}

	@Override
	public AttributeTypes getAttributeTypes() {
		return this.attributeTypes;
	}

	@Override
	public String toString() {
		return AnnotationString.get(this);
	}

}
