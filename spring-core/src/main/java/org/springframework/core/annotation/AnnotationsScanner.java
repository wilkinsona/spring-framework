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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.type.DeclaredAnnotation;
import org.springframework.core.annotation.type.DeclaredAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Scanner search for {@link DeclaredAnnotations} on the hierarchy of an
 * {@link AnnotatedElement}.
 *
 * @author Phillip Webb
 * @since 5.2
 */
class AnnotationsScanner {

	private static Results cachedLastResult = null;

//	private static final Map<AnnotatedElement, DeclaredAnnotations> declaredAnnotationsCache = new ConcurrentReferenceHashMap<>(
//			256);

	private static final Annotation[] NO_ANNOTATIONS = {};

	private AnnotationsScanner() {
	}

	/**
	 * Scan the given source using the specified strategy.
	 * @param source the source to scan
	 * @param searchStrategy the search strategy to use
	 * @return a {@link Collection} of {@link DeclaredAnnotations}.
	 */
	public static List<DeclaredAnnotations> scan(AnnotatedElement source,
			SearchStrategy searchStrategy) {
		Results lastResult = cachedLastResult;
		if (lastResult != null && lastResult.isFor(source, searchStrategy)) {
			return lastResult.getResults();
		}
		List<DeclaredAnnotations> results = getResults(source, searchStrategy);
		cachedLastResult = new Results(source, searchStrategy, results);
		return results;
	}

	private static List<DeclaredAnnotations> getResults(AnnotatedElement source,
			SearchStrategy searchStrategy) {
		if (source instanceof Class) {
			return ClassAnnotationsScanner.getResults((Class<?>) source, searchStrategy);
		}
		if (source instanceof Method) {
			return MethodAnnotationsScanner.getResults((Method) source, searchStrategy);
		}
		return ElementAnnotationsScanner.getResults(source, searchStrategy);
	}

	static DeclaredAnnotations getDeclaredAnnotations(AnnotatedElement element) {
		Annotation[] annotations = element.getDeclaredAnnotations();
		Annotation[] bridgedMethodAnnotations = getBridgeMethodAnnotations(element);
		if (isIgnorable(annotations) && isIgnorable(bridgedMethodAnnotations)) {
			return DeclaredAnnotations.NONE;
		}
		if (bridgedMethodAnnotations.length == 0) {
			return DeclaredAnnotations.from(element, annotations);
		}
		Set<Annotation> merged = new LinkedHashSet<>(
				annotations.length + bridgedMethodAnnotations.length);
		for (Annotation annotation : annotations) {
			merged.add(annotation);
		}
		for (Annotation annotation : bridgedMethodAnnotations) {
			merged.add(annotation);
		}
		return DeclaredAnnotations.from(element, merged);
	}

	static boolean isIgnorable(Annotation[] annotations) {
		for (Annotation annotation : annotations) {
			if (!isIgnorable(annotation.annotationType())) {
				return false;
			}
		}
		return true;
	}

	static boolean isIgnorable(Class<?> type) {
		return (type == Nullable.class || type == Deprecated.class
				|| type == FunctionalInterface.class);
	}

	static List<DeclaredAnnotations> asList(DeclaredAnnotations declaredAnnotations) {
		if (declaredAnnotations == DeclaredAnnotations.NONE) {
			return Collections.emptyList();
		}
		return Collections.singletonList(declaredAnnotations);
	}

	private static Annotation[] getBridgeMethodAnnotations(AnnotatedElement element) {
		if (element instanceof Method) {
			Method bridgeMethod = BridgeMethodResolver.findBridgedMethod(
					(Method) element);
			if (bridgeMethod != element) {
				return bridgeMethod.getAnnotations();
			}
		}
		return NO_ANNOTATIONS;
	}

	static void clearCache() {
		cachedLastResult = null;
		MethodAnnotationsScanner.methodsCache.clear();
	}

	/**
	 * Scanner used for {@link Class} {@link AnnotatedElement elements}.
	 */
	private static class ClassAnnotationsScanner {

		public static List<DeclaredAnnotations> getResults(Class<?> source,
				SearchStrategy searchStrategy) {
			switch (searchStrategy) {
				case DIRECT:
					return getDirect(source);
				case INHERITED_ANNOTATIONS:
					return getInheritedAnnotations(source);
				case SUPER_CLASS:
					if (source.getSuperclass() == Object.class) {
						return getDirect(source);
					}
					return getSuperclass(source);
				case EXHAUSTIVE:
					if (source.getSuperclass() == Object.class
							&& source.getInterfaces().length == 0) {
						return getDirect(source);
					}
					return getExhaustive(source);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private static List<DeclaredAnnotations> getDirect(Class<?> source) {
			return asList(getDeclaredAnnotations(source));
		}

		private static List<DeclaredAnnotations> getInheritedAnnotations(
				Class<?> source) {
			Annotation[] annotations = source.getAnnotations();
			if (isIgnorable(annotations)) {
				return Collections.emptyList();
			}
			Set<String> types = getAnnotationTypes(annotations);
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			while (source != null && source != Object.class) {
				DeclaredAnnotations declaredAnnotations = getDeclaredAnnotations(source);
				Set<DeclaredAnnotation> relevant = new LinkedHashSet<>(types.size());
				for (DeclaredAnnotation candidate : declaredAnnotations) {
					if (types.remove(candidate.getType())) {
						relevant.add(candidate);
					}
				}
				if (relevant.size() != declaredAnnotations.size()) {
					declaredAnnotations = DeclaredAnnotations.of(source, relevant);
				}
				aggregates.add(declaredAnnotations);
				source = source.getSuperclass();
			}
			return aggregates;
		}

		private static Set<String> getAnnotationTypes(Annotation[] annotations) {
			Set<String> types = new HashSet<>(annotations.length);
			for (Annotation annotation : annotations) {
				types.add(annotation.annotationType().getName());
			}
			return types;
		}

		private static List<DeclaredAnnotations> getSuperclass(Class<?> source) {
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			collect(aggregates, source, false);
			return aggregates;
		}

		private static List<DeclaredAnnotations> getExhaustive(Class<?> source) {
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			collect(aggregates, source, true);
			return aggregates;
		}

		private static void collect(List<DeclaredAnnotations> aggregates, Class<?> type,
				boolean includeInterfaces) {
			aggregates.add(getDeclaredAnnotations(type));
			Class<?> superclass = type.getSuperclass();
			if (superclass != Object.class && superclass != null) {
				collect(aggregates, superclass, includeInterfaces);
			}
			if (includeInterfaces) {
				for (Class<?> interfaceType : type.getInterfaces()) {
					collect(aggregates, interfaceType, includeInterfaces);
				}
			}
		}

	}

	/**
	 * Scanner used for {@link Method} {@link AnnotatedElement elements}.
	 */
	private static class MethodAnnotationsScanner extends AnnotationsScanner {

		private static final Method[] NO_METHODS = {};

		private static final Map<Class<?>, Method[]> methodsCache = new ConcurrentReferenceHashMap<>(
				256);

		static List<DeclaredAnnotations> getResults(Method source,
				SearchStrategy searchStrategy) {
			Class<?> declaringClass = source.getDeclaringClass();
			boolean privateMethod = Modifier.isPrivate(source.getModifiers());
			switch (searchStrategy) {
				case DIRECT:
					return getDirect(source);
				case INHERITED_ANNOTATIONS:
					return AnnotationsScanner.scan(source, SearchStrategy.DIRECT);
				case SUPER_CLASS:
					if (privateMethod || declaringClass.getSuperclass() == Object.class) {
						return getDirect(source);
					}
					return getSuperclass(source, declaringClass);
				case EXHAUSTIVE:
					if (privateMethod || (declaringClass.getSuperclass() == Object.class
							&& declaringClass.getInterfaces().length == 0)) {
						return getDirect(source);
					}
					return getExhaustive(source, declaringClass);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private static List<DeclaredAnnotations> getDirect(Method source) {
			return asList(getDeclaredAnnotations(source));
		}

		private static List<DeclaredAnnotations> getSuperclass(Method source,
				Class<?> declaringClass) {
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			aggregates.add(getDeclaredAnnotations(source));
			collect(aggregates, source, declaringClass, false, false);
			return aggregates;
		}

		private static List<DeclaredAnnotations> getExhaustive(Method source,
				Class<?> declaringClass) {
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			aggregates.add(getDeclaredAnnotations(source));
			collect(aggregates, source, declaringClass, false, true);
			return aggregates;
		}

		private static void collect(List<DeclaredAnnotations> aggregates, Method source,
				Class<?> candidateClass, boolean searchMethods,
				boolean includeInterfaces) {
			if (searchMethods) {
				for (Method candidateMethod : getMethods(candidateClass)) {
					if (isOverride(source, candidateClass, candidateMethod)) {
						aggregates.add(getDeclaredAnnotations(candidateMethod));
					}
				}
			}
			Class<?> superclass = candidateClass.getSuperclass();
			if (superclass != Object.class && superclass != null) {
				collect(aggregates, source, superclass, true, includeInterfaces);
			}
			if (includeInterfaces) {
				for (Class<?> interfaceType : candidateClass.getInterfaces()) {
					collect(aggregates, source, interfaceType, true, includeInterfaces);
				}
			}
		}

		private static Method[] getMethods(Class<?> type) {
			if (type == Object.class) {
				return NO_METHODS;
			}
			if (type.isInterface() && ClassUtils.isJavaLanguageInterface(type)) {
				return NO_METHODS;
			}
			Method[] result = methodsCache.get(type);
			if (result == null) {
				result = type.isInterface() ? type.getMethods()
						: type.getDeclaredMethods();
				methodsCache.put(type, result.length == 0 ? NO_METHODS : result);
			}
			return result;
		}

		private static boolean isOverride(Method method, Class<?> candidateClass,
				Method candidateMethod) {
			if (!candidateClass.isInterface()
					&& Modifier.isPrivate(candidateMethod.getModifiers())) {
				return false;
			}
			if (!candidateMethod.getName().equals(method.getName())) {
				return false;
			}
			return hasSameParameterTypes(method, candidateMethod);
		}

		private static boolean hasSameParameterTypes(Method m1, Method m2) {
			if (m2.getParameterCount() != m1.getParameterCount()) {
				return false;
			}
			Class<?>[] types = m1.getParameterTypes();
			if (Arrays.equals(m2.getParameterTypes(), types)) {
				return true;
			}
			Class<?> implementationClass = m1.getDeclaringClass();
			for (int i = 0; i < types.length; i++) {
				Class<?> resolved = ResolvableType.forMethodParameter(m2, i,
						implementationClass).resolve();
				if (types[i] != resolved) {
					return false;
				}
			}
			return true;
		}

	}

	/**
	 * Scanner used for other {@link AnnotatedElement annotated elements}.
	 */
	private static class ElementAnnotationsScanner extends AnnotationsScanner {

		private static List<DeclaredAnnotations> getResults(AnnotatedElement source,
				SearchStrategy searchStrategy) {
			if (searchStrategy != SearchStrategy.DIRECT) {
				return AnnotationsScanner.scan(source, SearchStrategy.DIRECT);
			}
			Annotation[] annotations = source.getDeclaredAnnotations();
			if (annotations.length == 0) {
				return Collections.emptyList();
			}
			return asList(DeclaredAnnotations.from(source, annotations));
		}

	}

	private static class Results {

		private final AnnotatedElement source;

		private final SearchStrategy searchStrategy;

		private final List<DeclaredAnnotations> results;

		Results(AnnotatedElement source, SearchStrategy searchStrategy,
				List<DeclaredAnnotations> results) {
			this.source = source;
			this.searchStrategy = searchStrategy;
			this.results = results;
		}

		public boolean isFor(AnnotatedElement source, SearchStrategy searchStrategy) {
			return this.source == source && this.searchStrategy == searchStrategy;
		}

		public List<DeclaredAnnotations> getResults() {
			return this.results;
		}

	}

}
