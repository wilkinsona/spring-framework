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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * ASM based annotation resolver used by
 * {@link AnnotationType#resolve(String, ClassLoader)}.
 *
 * @author Phillip Webb
 * @since 5.2
 */
final class AnnotationTypeResolver {

	private static Map<ClassLoader, AnnotationTypeResolver> resolverCache = new ConcurrentReferenceHashMap<>();

	private final ClassLoader classLoader;

	private final Map<String, Object> cache = new ConcurrentHashMap<>();

	public AnnotationTypeResolver(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	private AnnotationType doResolve(String className) {
		return extractResult(
				this.cache.computeIfAbsent(className, this::computeAnnotationType));
	}

	private AnnotationType doResolve(Class<? extends Annotation> annotationClass) {
		return extractResult(this.cache.computeIfAbsent(annotationClass.getName(),
				this::computeAnnotationType));
	}

	private Object computeAnnotationType(String className) {
		try {
			return computeAnnotationType(forName(className));
		}
		catch (RuntimeException ex) {
			return ex;
		}
	}

	private Object computeAnnotationType(Class<? extends Annotation> annotationClass) {
		try {
			return new StandardAnnotationType(annotationClass);
		}
		catch (RuntimeException ex) {
			return ex;
		}
	}

	private AnnotationType extractResult(Object result) {
		if (result instanceof AnnotationType) {
			return (AnnotationType) result;
		}
		throw (RuntimeException) result;
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Annotation> forName(String className) {
		try {
			return (Class<? extends Annotation>) Class.forName(className, false,
					classLoader);
		}
		catch (Exception ex) {
			throw new UnresolvableAnnotationTypeException(className, ex);
		}
	}

	public static AnnotationType resolve(String className, ClassLoader classLoader) {
		return get(classLoader).doResolve(className);
	}

	public static AnnotationType resolve(Class<? extends Annotation> annotationClass) {
		return get(annotationClass.getClassLoader()).doResolve(annotationClass);
	}

	static AnnotationTypeResolver get(@Nullable ClassLoader classLoader) {
		classLoader = classLoader != null ? classLoader
				: ClassUtils.getDefaultClassLoader();
		if (classLoader == null) {
			return createResolver(classLoader);
		}
		return resolverCache.computeIfAbsent(classLoader,
				AnnotationTypeResolver::createResolver);
	}

	private static AnnotationTypeResolver createResolver(ClassLoader classLoader) {
		return new AnnotationTypeResolver(classLoader);
	}

	static void clearCache() {
		resolverCache.clear();
	}

	//
	//
	// public static AnnotationType resolve(String className, ClassLoader
	// classLoader) {
	// classLoader = classLoader != null ? classLoader
	// : ClassUtils.getDefaultClassLoader();
	// resolverCache.computeIfAbsent(classLoader,
	// AnnotationTypeResolver::createResolver);
	//
	//
	// resolverCache.get(classLoader).resolver(className);
	// Assert.hasText(className, "ClassName must not be null");
	// return resolve(forName(className, classLoader));
	// }
	//
	// private static AsmAnnotationTypeResolver createResolver(ClassLoader
	// classLoader) {
	// DefaultResourceLoader resourceLoader = new
	// DefaultResourceLoader(classLoader);
	// return new AsmAnnotationTypeResolver(resourceLoader);
	// }
	//
	//
	//

	//

}
