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

import org.springframework.util.Assert;

/**
 * ASM based annotation resolver used by
 * {@link AnnotationType#resolve(String, ClassLoader)}.
 *
 * @author Phillip Webb
 * @since 5.2
 */
final class AnnotationTypeResolver {

	public static AnnotationType resolve(String className, ClassLoader classLoader) {
		Assert.hasText(className, "ClassName must not be null");
		return resolve(forName(className, classLoader));
	}

	public static AnnotationType resolve(Class<? extends Annotation> annotationClass) {
		return new StandardAnnotationType(annotationClass);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Annotation> forName(String className,
			ClassLoader classLoader) {
		try {
			return (Class<? extends Annotation>) Class.forName(className, false,
					classLoader);
		}
		catch (Exception ex) {
			throw new UnresolvableAnnotationTypeException(className, ex);
		}
	}

	public static void clearCache() {
	}

}
