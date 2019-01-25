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
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
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

	private static final Map<AnnotatedElement, Results[]> cache = new ConcurrentReferenceHashMap<>();

	private AnnotationsScanner() {
	}

	/**
	 * Scan the given source using the specified strategy.
	 * @param source the source to scan
	 * @param searchStrategy the search strategy to use
	 * @return a {@link Collection} of {@link DeclaredAnnotations}.
	 */
	public static Results scan(AnnotatedElement source, SearchStrategy searchStrategy) {
		Results[] cached = cache.get(source);
		int cacheIndex = searchStrategy.ordinal();
		if (cached != null && cached[cacheIndex] != null) {
			return cached[cacheIndex];
		}
		Results results = getResults(source, searchStrategy);
		if (results != Results.NONE) {
			if (cached == null) {
				cached = new Results[SearchStrategy.values().length];
				cache.put(source, cached);
			}
			cached[cacheIndex] = results;
		}
		return results;
	}

	private static Results getResults(AnnotatedElement source,
			SearchStrategy searchStrategy) {
		if (source instanceof Class) {
			return ClassAnnotationsScanner.getResults((Class<?>) source, searchStrategy);
		}
		if (source instanceof Method) {
			return MethodAnnotationsScanner.getResults((Method) source, searchStrategy);
		}
		return ElementAnnotationsScanner.getResults(source, searchStrategy);
	}

	static void clearCache() {
		cache.clear();
		TypeHierarchy.superclassesCache.clear();
		TypeHierarchy.superclassesAndInterfacesCache.clear();
		MethodAnnotationsScanner.methodsCache.clear();
	}

	/**
	 * Scanner used for {@link Class} {@link AnnotatedElement elements}.
	 */
	private static class ClassAnnotationsScanner {

		public static Results getResults(Class<?> source, SearchStrategy searchStrategy) {
			switch (searchStrategy) {
				case DIRECT:
					return getDirect(source);
				case INHERITED_ANNOTATIONS:
					return getInheritedAnnotations(source);
				case SUPER_CLASS:
					if (source.getSuperclass() == Object.class) {
						return getDirect(source);
					}
					return getWithHierarchy(source, TypeHierarchy::superclasses);
				case EXHAUSTIVE:
					if (source.getSuperclass() == Object.class
							&& source.getInterfaces().length == 0) {
						return getDirect(source);
					}
					return getWithHierarchy(source,
							TypeHierarchy::superclassesAndInterfaces);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private static Results getDirect(Class<?> source) {
			Annotation[] annotations = source.getDeclaredAnnotations();
			if (Results.isIgnorable(annotations)) {
				return Results.NONE;
			}
			return Results.of(DeclaredAnnotations.from(source, annotations));
		}

		private static Results getInheritedAnnotations(Class<?> source) {
			Annotation[] annotations = source.getAnnotations();
			if (Results.isIgnorable(annotations)) {
				return Results.NONE;
			}
			Set<Class<?>> types = getAnnotationTypes(annotations);
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			for (Class<?> type : TypeHierarchy.superclasses(source)) {
				Set<Annotation> declaredAnnotations = new LinkedHashSet<>(types.size());
				for (Annotation candidate : type.getDeclaredAnnotations()) {
					if (types.remove(candidate.annotationType())) {
						declaredAnnotations.add(candidate);
					}
				}
				aggregates.add(DeclaredAnnotations.from(type, declaredAnnotations));
			}
			return Results.of(aggregates);
		}

		private static Set<Class<?>> getAnnotationTypes(Annotation[] annotations) {
			Set<Class<?>> types = new HashSet<>(annotations.length);
			for (Annotation annotation : annotations) {
				types.add(annotation.annotationType());
			}
			return types;
		}

		private static Results getWithHierarchy(Class<?> source,
				Function<Class<?>, TypeHierarchy> hierarchyFactory) {
			TypeHierarchy hierarchy = hierarchyFactory.apply(source);
			List<DeclaredAnnotations> aggregates = new ArrayList<>(hierarchy.size());
			for (Class<?> type : hierarchy) {
				Annotation[] annotations = type.getDeclaredAnnotations();
				if (Results.isIgnorable(annotations)) {
					aggregates.add(DeclaredAnnotations.NONE);
				}
				else {
					aggregates.add(DeclaredAnnotations.from(type, annotations));
				}
			}
			return Results.of(aggregates);
		}

	}

	/**
	 * Scanner used for {@link Method} {@link AnnotatedElement elements}.
	 */
	private static class MethodAnnotationsScanner extends AnnotationsScanner {

		private static final Method[] NO_METHODS = {};

		private static final Annotation[] NO_ANNOTATIONS = {};

		private static final Map<Class<?>, Method[]> methodsCache = new ConcurrentReferenceHashMap<>(
				256);

		static Results getResults(Method source, SearchStrategy searchStrategy) {
			Class<?> declaringClass = source.getDeclaringClass();
			switch (searchStrategy) {
				case DIRECT:
					return getDirect(source);
				case INHERITED_ANNOTATIONS:
					return AnnotationsScanner.scan(source, SearchStrategy.DIRECT);
				case SUPER_CLASS:
					if (declaringClass.getSuperclass() == Object.class) {
						return getDirect(source);
					}
					return getWithHierarchy(declaringClass, source,
							TypeHierarchy::superclasses);
				case EXHAUSTIVE:
					if (declaringClass.getSuperclass() == Object.class
							&& declaringClass.getInterfaces().length == 0) {
						return getDirect(source);
					}
					return getWithHierarchy(declaringClass, source,
							TypeHierarchy::superclassesAndInterfaces);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private static Results getDirect(Method source) {
			return Results.of(getAnnotations(source));
		}

		private static Results getWithHierarchy(Class<?> declaringClass, Method source,
				Function<Class<?>, TypeHierarchy> hierarchyFactory) {
			if (Modifier.isPrivate(source.getModifiers())) {
				return AnnotationsScanner.scan(source, SearchStrategy.DIRECT);
			}
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			aggregates.add(getAnnotations(source));
			for (Class<?> candidateClass : hierarchyFactory.apply(declaringClass)) {
				if (candidateClass != declaringClass) {
					for (Method candidateMethod : getMethods(candidateClass)) {
						if (isOverride(source, candidateClass, candidateMethod)) {
							aggregates.add(getAnnotations(candidateMethod));
						}
					}
				}
			}
			return Results.of(aggregates);
		}

		private static DeclaredAnnotations getAnnotations(Method method) {
			Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
			Annotation[] methodAnnotations = method.getDeclaredAnnotations();
			Annotation[] bridgedMethodAnnotations = bridgedMethod != method
					? bridgedMethod.getDeclaredAnnotations()
					: NO_ANNOTATIONS;
			if (hasOnlyIgnorable(methodAnnotations)
					&& hasOnlyIgnorable(bridgedMethodAnnotations)) {
				return DeclaredAnnotations.NONE;
			}
			Set<Annotation> annotations = new LinkedHashSet<>(
					methodAnnotations.length + bridgedMethodAnnotations.length);
			for (Annotation annotation : methodAnnotations) {
				annotations.add(annotation);
			}
			for (Annotation annotation : bridgedMethodAnnotations) {
				annotations.add(annotation);
			}
			return DeclaredAnnotations.from(method, annotations);
		}

		private static boolean hasOnlyIgnorable(Annotation[] annotations) {
			for (Annotation annotation : annotations) {
				if (!isIgnorable(annotation.annotationType())) {
					return false;
				}
			}
			return true;
		}

		private static boolean isIgnorable(Class<?> type) {
			return (type == Nullable.class || type == Deprecated.class);
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

		private static Results getResults(AnnotatedElement source,
				SearchStrategy searchStrategy) {
			if (searchStrategy != SearchStrategy.DIRECT) {
				return AnnotationsScanner.scan(source, SearchStrategy.DIRECT);
			}
			Annotation[] annotations = source.getDeclaredAnnotations();
			if (annotations.length != 0) {
				return Results.of(DeclaredAnnotations.from(source, annotations));
			}
			return Results.NONE;
		}

	}

	/**
	 * Provides ordered access to the superclass and interface hierarchy of a given class.
	 */
	private static final class TypeHierarchy implements Iterable<Class<?>> {

		private static final Map<Class<?>, TypeHierarchy> superclassesCache = new ConcurrentReferenceHashMap<>();

		private static final Map<Class<?>, TypeHierarchy> superclassesAndInterfacesCache = new ConcurrentReferenceHashMap<>();

		private final Set<Class<?>> hierarchy = new LinkedHashSet<>();

		private TypeHierarchy(Class<?> type, boolean includeInterfaces) {
			collect(type, includeInterfaces);
		}

		private void collect(Class<?> type, boolean includeInterfaces) {
			if (type == null || Object.class.equals(type)) {
				return;
			}
			this.hierarchy.add(type);
			collect(type.getSuperclass(), includeInterfaces);
			if (includeInterfaces) {
				for (Class<?> interfaceType : type.getInterfaces()) {
					collect(interfaceType, includeInterfaces);
				}
			}
		}

		@Override
		public Iterator<Class<?>> iterator() {
			return this.hierarchy.iterator();
		}

		public int size() {
			return this.hierarchy.size();
		}

		public static TypeHierarchy superclasses(Class<?> type) {
			return superclassesCache.computeIfAbsent(type,
					key -> new TypeHierarchy(type, false));
		}

		public static TypeHierarchy superclassesAndInterfaces(Class<?> type) {
			return superclassesAndInterfacesCache.computeIfAbsent(type,
					key -> new TypeHierarchy(type, true));
		}

	}

	/**
	 * A {@link Collection} of {@link DeclaredAnnotations} returned from the scanner.
	 */
	static final class Results extends AbstractCollection<DeclaredAnnotations> {

		private static final Results NONE = new Results(Collections.emptySet());

		private final Collection<DeclaredAnnotations> values;

		private Results(Collection<DeclaredAnnotations> values) {
			this.values = values;
		}

		@Override
		public Iterator<DeclaredAnnotations> iterator() {
			return this.values.iterator();
		}

		@Override
		public int size() {
			return this.values.size();
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
			return (type == Nullable.class || type == Deprecated.class);
		}

		static Results of(DeclaredAnnotations declaredAnnotations) {
			if (declaredAnnotations == DeclaredAnnotations.NONE) {
				return NONE;
			}
			return new Results(Collections.singleton(declaredAnnotations));
		}

		static Results of(Collection<DeclaredAnnotations> aggregates) {
			for (DeclaredAnnotations annotations : aggregates) {
				if (annotations != DeclaredAnnotations.NONE) {
					return new Results(Collections.unmodifiableCollection(aggregates));
				}
			}
			return NONE;
		}

	}

}
